package com.whatsup.automation.data.engine

import android.content.Context
import com.whatsup.automation.data.security.SessionKeystore
import com.whatsup.automation.domain.model.ConnectionState
import com.whatsup.automation.domain.model.IncomingMessage
import com.whatsup.automation.domain.model.StatusStory
import com.whatsup.automation.domain.model.StatusMediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.whatsup.automation.domain.model.ActivityLog
import com.whatsup.automation.domain.model.LogStatus
import com.whatsup.automation.domain.repository.LogRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * المحرك المحلي لإدارة جلسة واتساب والتواصل مع طبقة البروتوكول.
 * يعمل بالكامل داخل الهاتف بدون خادم خارجي.
 */
@Singleton
class WhatsAppEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionKeystore: SessionKeystore,
    private val baileysBridgeManager: BaileysBridgeManager,
    private val logRepository: LogRepository,
    private val deviceContactsManager: com.whatsup.automation.data.local.contacts.DeviceContactsManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionState = MutableStateFlow<ConnectionState>(
        if (sessionKeystore.isSessionActive()) ConnectionState.Connected else ConnectionState.Disconnected
    )
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _qrCode = MutableStateFlow<String?>(null)
    val qrCode: StateFlow<String?> = _qrCode.asStateFlow()

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<IncomingMessage> = _incomingMessages.asSharedFlow()

    private val _incomingStatuses = MutableSharedFlow<StatusStory>(extraBufferCapacity = 64)
    val incomingStatuses: SharedFlow<StatusStory> = _incomingStatuses.asSharedFlow()

    private val enginePrefs = context.getSharedPreferences("whatsup_engine_prefs", Context.MODE_PRIVATE)
    private val _isAutomationPaused = MutableStateFlow(enginePrefs.getBoolean("automation_paused", false))
    val isAutomationPaused: StateFlow<Boolean> = _isAutomationPaused.asStateFlow()

    fun setAutomationPaused(paused: Boolean) {
        enginePrefs.edit().putBoolean("automation_paused", paused).apply()
        _isAutomationPaused.value = paused
        logDiagnosticEvent("ENGINE", if (paused) "⏸️ تم تجميد الأتمتة مؤقتاً (كتم الردود والحفظ مع بقاء الاتصال آمناً)." else "▶️ تم استئناف الأتمتة والردود بنجاح.")
    }

    fun toggleAutomationPause() {
        setAutomationPaused(!_isAutomationPaused.value)
    }

    private val _engineDiagnostics = MutableStateFlow<List<String>>(
        listOf("[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}][SYSTEM] Engine initialized successfully.")
    )
    val engineDiagnostics: StateFlow<List<String>> = _engineDiagnostics.asStateFlow()

    fun logDiagnosticEvent(tag: String, message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = "[$timestamp][$tag] $message"
        _engineDiagnostics.update { current ->
            (current + entry).takeLast(100)
        }
    }

    init {
        logDiagnosticEvent("ENGINE", "Engine starting, checking session status...")
        // ربط مسجل جهات الاتصال لعرض أحداث أذونات وحفظ جهات الاتصال
        deviceContactsManager.diagnosticLogger = { msg ->
            logDiagnosticEvent("Contacts", msg)
        }
        // تنظيف دوري لأي جهات اتصال تالفة تم حفظها كمعرفات LID مشفرة سابقاً
        scope.launch(Dispatchers.IO) {
            delay(1500L)
            val cleaned = deviceContactsManager.cleanupLidContactsFromDevice()
            if (cleaned > 0) {
                logDiagnosticEvent("Contacts", "تم تنظيف $cleaned جهة اتصال LID تالفة من دليل الهاتف بنجاح.")
            }
        }
        // تسجيل الاستماع المباشر لكافة أحداث Baileys عبر الجسر
        baileysBridgeManager.setEventListener { rawLine ->
            scope.launch {
                handleRawEngineEvent(rawLine)
            }
        }
    }

    private val handledMessageTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * تسجيل الرسالة كمعالجة لمنع تكرار الردود بين الخدمات المختلفة (مثل NotificationListener و Baileys).
     * يعتمد على مطابقة آخر 8 أرقام للهاتف لضمان تطابق الأرقام المحلية والدولية والأسماء.
     */
    fun markMessageHandled(senderPhone: String, text: String) {
        val cleanDigits = senderPhone.replace("[^0-9]".toRegex(), "")
        val phoneKey = if (cleanDigits.length >= 7) cleanDigits.takeLast(8) else senderPhone.trim()
        val key = "$phoneKey|${text.trim()}"
        val now = System.currentTimeMillis()
        handledMessageTimestamps[key] = now

        // إذا كان هناك اسم مرسل أو صيغة مختلفة، نسجل المفتاح الخام أيضاً
        val rawTrim = senderPhone.trim()
        if (rawTrim.isNotBlank() && rawTrim != phoneKey) {
            handledMessageTimestamps["$rawTrim|${text.trim()}"] = now
        }

        if (handledMessageTimestamps.size > 200) {
            val expireTime = now - 120_000L
            handledMessageTimestamps.entries.removeIf { it.value < expireTime }
        }
    }

    /**
     * محاولة حجز معالجة الرسالة ذرياً (Atomic Single-Flight Claim).
     * يعود بـ true إذا تم حجز الرسالة بنجاح ولم يسبق معالجتها،
     * ويعود بـ false إذا كانت الرسالة قد حُجزت أو عولجت مسبقاً من مسار آخر لمنع أي رد مزدوج.
     */
    fun tryClaimMessageProcessing(senderPhone: String, text: String, windowMillis: Long = 60_000L): Boolean {
        val cleanDigits = senderPhone.replace("[^0-9]".toRegex(), "")
        val phoneKey = if (cleanDigits.length >= 7) cleanDigits.takeLast(8) else senderPhone.trim()
        val key = "$phoneKey|${text.trim()}"
        val now = System.currentTimeMillis()

        synchronized(handledMessageTimestamps) {
            val timestamp = handledMessageTimestamps[key]
            if (timestamp != null && (now - timestamp) < windowMillis) {
                return false
            }

            val rawTrim = senderPhone.trim()
            if (rawTrim.isNotBlank()) {
                val rawTimestamp = handledMessageTimestamps["$rawTrim|${text.trim()}"]
                if (rawTimestamp != null && (now - rawTimestamp) < windowMillis) {
                    return false
                }
            }

            handledMessageTimestamps[key] = now
            if (rawTrim.isNotBlank() && rawTrim != phoneKey) {
                handledMessageTimestamps["$rawTrim|${text.trim()}"] = now
            }

            if (handledMessageTimestamps.size > 300) {
                val expireTime = now - 120_000L
                handledMessageTimestamps.entries.removeIf { it.value < expireTime }
            }

            return true
        }
    }


    private val _isDirectAutomationActive = MutableStateFlow(false)
    val isDirectAutomationActive: StateFlow<Boolean> = _isDirectAutomationActive.asStateFlow()

    /**
     * هل المحرك الأساسي (Baileys عبر الربط بالرقم) متصل وجاهز لمعالجة وإرسال كل شيء؟
     */
    fun isMasterEngineConnected(): Boolean = _connectionState.value is ConnectionState.Connected

    /**
     * فحص ما إذا كانت هناك جلسة واتساب نشطة ومحفوظة مسبقاً.
     */
    fun hasSavedSession(): Boolean {
        val authDir = java.io.File(context.filesDir, "baileys_auth")
        val credsFile = java.io.File(authDir, "creds.json")
        return sessionKeystore.isSessionActive() || (credsFile.exists() && credsFile.length() > 50)
    }

    /**
     * التحقق من الحالة الحقيقية للاتصال وتحديثها لجلسة Baileys.
     */
    fun refreshRealConnectionState(): Boolean {
        val isSessionActive = hasSavedSession()
        _connectionState.value = if (isSessionActive) {
            ConnectionState.Connected
        } else {
            ConnectionState.Disconnected
        }
        return isSessionActive
    }

    /**
     * بدء تشغيل المحرك: إذا كانت الجلسة موجودة مسبقاً يبدأ الجسر فورياً لإبقاء الأتمتة نشطة 24/7.
     * أما إذا لم تكن الجلسة مقترنة، ينتظر طلب الاقتران عند الطلب (On-Demand) لتوفير البطارية وتفادي انقطاعات المقبس المتكررة.
     */
    fun startEngine() {
        if (hasSavedSession()) {
            logDiagnosticEvent("ENGINE", "جلسة محفوظة موجودة، بدء تشغيل محرك Baileys تلقائياً...")
            baileysBridgeManager.start()
            _connectionState.value = ConnectionState.Connected
        } else {
            logDiagnosticEvent("ENGINE", "لا توجد جلسة محفوظة — المحرك في وضع الانتظار عند الطلب لتوفير موارد الهاتف.")
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /**
     * إيقاف المحرك وفصل الاتصال.
     */
    fun stopEngine() {
        baileysBridgeManager.stop()
        _connectionState.value = ConnectionState.Disconnected
        _qrCode.value = null
        _pairingCode.value = null
    }

    /**
     * توليد رمز QR ديناميكي للاقتران عبر مقبس Baileys.
     */
    fun generateNewQr() {
        scope.launch {
            _connectionState.value = ConnectionState.AwaitingPairing
            baileysBridgeManager.start()
        }
    }

    /**
     * طلب كود اقتران (Pairing Code) رسمي من خوادم واتساب عبر محرك Baileys.
     */
    fun requestPairingCode(phoneNumber: String) {
        scope.launch {
            _connectionState.value = ConnectionState.AwaitingPairing
            _pairingCode.value = null
            baileysBridgeManager.start()
            val cleanPhone = phoneNumber.filter { it.isDigit() }
            if (cleanPhone.isEmpty()) {
                _connectionState.value = ConnectionState.Error("رقم الهاتف غير صالح")
                return@launch
            }
            sessionKeystore.setSessionPhone(cleanPhone)

            val isReady = try {
                withTimeoutOrNull(12_000L) {
                    baileysBridgeManager.isBridgeReady.first { it }
                } ?: false
            } catch (e: Exception) {
                false
            }

            if (!isReady) {
                _connectionState.value = ConnectionState.Error("تعذر الاتصال بمحرك واتساب المحلي")
                return@launch
            }

            delay(500)

            val success = baileysBridgeManager.requestPairingCode(cleanPhone)
            if (!success) {
                _connectionState.value = ConnectionState.Error("تعذر الاتصال بمحرك واتساب المحلي")
            }
        }
    }

    /**
     * تأكيد الاقتران بنجاح وتفعيل الجلسة.
     */
    fun confirmPairingSuccess(phone: String) {
        sessionKeystore.setSessionActive(true)
        sessionKeystore.setSessionPhone(phone)
        sessionKeystore.setLastConnectedTimestamp(System.currentTimeMillis())
        _connectionState.value = ConnectionState.Connected
        _qrCode.value = null
        _pairingCode.value = null
    }

    /**
     * تفعيل الأتمتة المباشرة للإشعارات في النظام.
     */
    fun activateDirectAutomation() {
        _isDirectAutomationActive.value = true
    }

    /**
     * تسجيل الخروج ومسح الجلسة المخزنة في Keystore وإعلام المحرك.
     */
    fun logout() {
        scope.launch {
            baileysBridgeManager.logout()
            handleSessionLoggedOut()
        }
    }

    /**
     * تنظيف ومعالجة حالة الخروج محلياً دون إعادة إرسال أمر LOGOUT للمحرك لتفادي الحلقات التكرارية.
     */
    private fun handleSessionLoggedOut() {
        baileysBridgeManager.clearAuthState()
        sessionKeystore.clearSession()
        _connectionState.value = ConnectionState.Disconnected
        _qrCode.value = null
        _pairingCode.value = null
    }

    /**
     * إرسال رسالة نصية عبر مقبس واتساب الحقيقي مع دعم محاكي السلوك البشري.
     */
    suspend fun sendMessage(
        recipientPhone: String,
        messageText: String,
        typingDelayMs: Long? = null,
        markRead: Boolean = false,
        messageId: String? = null,
        readDelayMs: Long? = null
    ): Boolean {
        // لا نمنع الإرسال بناءً على حالة الاتصال المحلية فقط — الجسر يتعامل مع الانتظار داخلياً
        val cleanPhone = recipientPhone.filter { it.isDigit() }
        if (cleanPhone.length < 7) {
            logDiagnosticEvent("SEND", "Invalid phone number: $recipientPhone")
            return false
        }
        logDiagnosticEvent("SEND", "Sending message to $cleanPhone (${messageText.take(20)}...) [TypingDelay: ${typingDelayMs ?: "default"}ms, MarkRead: $markRead]")
        return baileysBridgeManager.sendMessage(
            recipient = cleanPhone,
            text = messageText,
            typingDelayMs = typingDelayMs,
            markRead = markRead,
            messageId = messageId,
            readDelayMs = readDelayMs
        )
    }

    /**
     * إرسال تأكيد قراءة رسالة محددة.
     */
    suspend fun markMessageRead(chatJid: String, messageId: String, participant: String? = null): Boolean {
        return baileysBridgeManager.markMessageRead(chatJid, messageId, participant)
    }

    /**
     * محاكاة أو استقبال رسالة واردة من واتساب ومعالجتها عبر خط الأنابيب (Pipeline).
     */
    suspend fun receiveMessage(message: IncomingMessage) {
        _incomingMessages.emit(message)
    }

    /**
     * محاكاة سريعة لاختبار القواعد عبر شاشة المحاكي.
     */
    suspend fun simulateIncomingMessage(senderPhone: String, text: String): IncomingMessage {
        val incoming = IncomingMessage(
            id = "SIM_${UUID.randomUUID().toString().take(8)}",
            senderPhone = senderPhone,
            text = text,
            timestamp = Instant.now(),
            isGroupMessage = false
        )
        _incomingMessages.emit(incoming)
        return incoming
    }

    /**
     * استقبال أو محاكاة حالة جديدة من واتساب ونشرها في النظام.
     */
    suspend fun receiveStatus(status: StatusStory) {
        _incomingStatuses.emit(status)
    }

    /**
     * إرسال تأكيد مشاهدة الحالة إلى واتساب.
     */
    suspend fun markStatusViewed(statusId: String, senderPhone: String = "", participant: String? = null): Boolean {
        logDiagnosticEvent("STATUS", "Marking status $statusId viewed (sender: $senderPhone, participant: $participant)")
        val cmd = org.json.JSONObject().apply {
            put("action", "VIEW_STATUS")
            put("statusId", statusId)
            put("senderPhone", senderPhone)
            if (!participant.isNullOrBlank()) {
                put("participant", participant)
            }
        }
        return baileysBridgeManager.sendCommand(cmd)
    }

    /**
     * إرسال تفاعل إيموجي على الحالة إلى واتساب.
     */
    suspend fun reactToStatus(statusId: String, emoji: String = "💚", senderPhone: String = "", participant: String? = null): Boolean {
        logDiagnosticEvent("STATUS", "Reacting $emoji to status $statusId (sender: $senderPhone, participant: $participant)")
        val cmd = org.json.JSONObject().apply {
            put("action", "REACT_STATUS")
            put("statusId", statusId)
            put("senderPhone", senderPhone)
            put("emoji", emoji)
            if (!participant.isNullOrBlank()) {
                put("participant", participant)
            }
        }
        return baileysBridgeManager.sendCommand(cmd)
    }

    /**
     * طلب جلب كل الحالات الموجودة (القديمة والجديدة) من واتساب.
     * يُستدعى تلقائياً عند الاتصال، ويمكن استدعاؤه يدوياً من الواجهة أو الخدمة.
     */
    suspend fun fetchAllStatuses(): Boolean {
        return baileysBridgeManager.fetchStatuses()
    }

    /**
     * معالجة أحداث IPC الواردة من محرك Baileys المحلي (index.js).
     * صيغة الحدث: [WHATSAPP_EVENT]{"event":"...", "data":{...}}
     */
    suspend fun handleRawEngineEvent(rawLine: String) {
        if (!rawLine.startsWith("[WHATSAPP_EVENT]")) return
        val jsonPayload = rawLine.removePrefix("[WHATSAPP_EVENT]").trim()
        try {
            val json = org.json.JSONObject(jsonPayload)
            val event = json.optString("event")
            val data = json.optJSONObject("data") ?: org.json.JSONObject()

            logDiagnosticEvent("EVENT", "Received: $event")
            when (event) {
                "BRIDGE_READY" -> {
                    val authDir = java.io.File(context.filesDir, "baileys_auth")
                    val credsFile = java.io.File(authDir, "creds.json")
                    if (credsFile.exists() && credsFile.length() > 50) {
                        sessionKeystore.setSessionActive(true)
                    }
                    logDiagnosticEvent("BRIDGE", "Baileys local bridge running. Awaiting socket connection...")
                }
                "QR_CODE" -> {
                    val qr = data.optString("qr")
                    if (qr.isNotEmpty()) {
                        _qrCode.value = qr
                        _connectionState.value = ConnectionState.AwaitingPairing
                        logDiagnosticEvent("PAIRING", "QR Code received (${qr.take(15)}...)")
                    }
                }
                "PAIRING_CODE" -> {
                    val code = data.optString("code")
                    if (code.isNotEmpty()) {
                        _pairingCode.value = code
                        _connectionState.value = ConnectionState.AwaitingPairing
                        logDiagnosticEvent("PAIRING", "Pairing Code generated: $code")
                    }
                }
                "CONNECTION_STATE" -> {
                    val status = data.optString("status")
                    logDiagnosticEvent("CONNECTION", "State changed to $status")
                    when (status) {
                        "CONNECTED" -> {
                            val userObj = data.optJSONObject("user")
                            val phone = userObj?.optString("id")
                                ?.replace("@s.whatsapp.net", "")
                                ?.substringBefore(":")
                                ?.filter { it.isDigit() } ?: ""
                            confirmPairingSuccess(phone)
                            logDiagnosticEvent("CONNECTION", "Connected successfully as $phone")
                        }
                        "DISCONNECTED" -> {
                            val reconnecting = data.optBoolean("reconnecting", false)
                            _connectionState.value = if (reconnecting) {
                                ConnectionState.Reconnecting()
                            } else {
                                ConnectionState.Disconnected
                            }
                        }
                    }
                }
                "INCOMING_MESSAGE" -> {
                    val id = data.optString("id", UUID.randomUUID().toString())
                    val senderPhone = data.optString("senderPhone").takeIf { it.isNotBlank() && it != "null" } ?: ""
                    val realPhone = data.optString("realPhone").takeIf { it.isNotBlank() && it != "null" }
                    val lid = data.optString("lid").takeIf { it.isNotBlank() && it != "null" }
                    val isLid = data.optBoolean("isLid", false)
                    val rawSenderName = data.optString("senderName", "").takeIf { it != "null" } ?: ""
                    val text = data.optString("text")
                    val isGroup = data.optBoolean("isGroup", false)
                    val ts = data.optLong("timestamp", System.currentTimeMillis())

                    val effectivePhone = realPhone ?: senderPhone

                    if (effectivePhone.isNotEmpty() && text.isNotEmpty()) {
                        val cleanDigits = effectivePhone.replace("[^0-9]".toRegex(), "")

                        // البحث وفق الأولويات الثلاث: 1. الهاتف، 2. واتساب/pushName، 3. جيميل
                        val resolved = deviceContactsManager.resolveContactNameWithPriority(effectivePhone, rawSenderName)
                        val senderName = resolved?.name
                            ?: if (!isLid && cleanDigits.length in 7..15) {
                                "+$cleanDigits"
                            } else {
                                "جهة اتصال غير مسجلة"
                            }

                        val senderDisplay = if (resolved != null) "${resolved.name} ($effectivePhone)" else senderName
                        logDiagnosticEvent("MESSAGE", "Incoming from $senderDisplay: \"${text.take(30)}\"")
                        val message = IncomingMessage(
                            id = id,
                            senderPhone = effectivePhone,
                            text = text,
                            timestamp = Instant.ofEpochMilli(ts),
                            isGroupMessage = isGroup,
                            senderName = senderName,
                            isLid = isLid,
                            realPhone = realPhone,
                            lid = lid
                        )
                        _incomingMessages.emit(message)
                    }
                }
                "INCOMING_STATUS", "STATUS_RECEIVED" -> {
                    val id = data.optString("id", UUID.randomUUID().toString())
                    val senderPhone = data.optString("senderPhone").takeIf { it.isNotBlank() && it != "null" } ?: ""
                    val participant = data.optString("participant").takeIf { it.isNotBlank() && it != "null" }
                    val realPhone = data.optString("realPhone").takeIf { it.isNotBlank() && it != "null" }
                    val lid = data.optString("lid").takeIf { it.isNotBlank() && it != "null" }
                    val isLid = data.optBoolean("isLid", false)
                    val rawSenderName = data.optString("senderName", "").takeIf { it != "null" } ?: ""
                    val mediaTypeStr = data.optString("mediaType", "TEXT")
                    val textContent = data.optString("textContent").takeIf { it.isNotEmpty() && it != "null" }
                    val ts = data.optLong("timestamp", System.currentTimeMillis())

                    val effectivePhone = realPhone ?: senderPhone
                    val cleanDigits = effectivePhone.replace("[^0-9]".toRegex(), "")

                    // البحث وفق الأولويات الثلاث: 1. الهاتف، 2. واتساب/pushName، 3. جيميل
                    val resolved = deviceContactsManager.resolveContactNameWithPriority(effectivePhone, rawSenderName)
                    val senderName = resolved?.name
                        ?: if (!isLid && cleanDigits.length in 7..15) {
                            "+$cleanDigits"
                        } else {
                            "جهة اتصال غير مسجلة"
                        }

                    val mediaType = try {
                        StatusMediaType.valueOf(mediaTypeStr)
                    } catch (_: Exception) {
                        StatusMediaType.TEXT
                    }

                    logDiagnosticEvent("STATUS", "New status from $senderName ($mediaType)")
                    val story = StatusStory(
                        id = id,
                        senderPhone = effectivePhone,
                        senderName = senderName,
                        mediaType = mediaType,
                        textContent = textContent,
                        timestamp = Instant.ofEpochMilli(ts),
                        isLid = isLid,
                        realPhone = realPhone,
                        lid = lid,
                        participant = participant
                    )
                    _incomingStatuses.emit(story)
                }
                "LOGGED_OUT" -> {
                    logDiagnosticEvent("SESSION", "Logged out from WhatsApp")
                    handleSessionLoggedOut()
                }
                "ERROR", "FATAL_ERROR" -> {
                    val errorMsg = data.optString("error", "حدث خطأ في محرك واتساب")
                    logDiagnosticEvent("ERROR", errorMsg)
                    _connectionState.value = ConnectionState.Error(errorMsg)
                }
                "STATUS_VIEWED" -> {
                    val statusId = data.optString("id")
                    val rawPhone = data.optString("senderPhone", "")
                    val realPhone = data.optString("realPhone").takeIf { it.isNotEmpty() }
                    val rawSenderName = data.optString("senderName", "")
                    val effectivePhone = realPhone ?: rawPhone
                    val cleanPhone = effectivePhone.replace("[^0-9]".toRegex(), "")
                    val resolved = deviceContactsManager.resolveContactNameWithPriority(effectivePhone, rawSenderName)
                    val displayName = resolved?.name ?: if (cleanPhone.length in 7..15) "+$cleanPhone" else "حالة واتساب"

                    logDiagnosticEvent("STATUS", "Status viewed for $displayName ($statusId)")
                    try {
                        logRepository.insertLog(
                            ActivityLog(
                                senderPhone = if (cleanPhone.isNotEmpty()) "+$cleanPhone" else "status@broadcast",
                                messageText = "مشاهدة حالة: $displayName",
                                actionExecuted = "مشاهدة حالة واتساب ($displayName)",
                                status = LogStatus.SUCCESS
                            )
                        )
                    } catch (_: Exception) {}
                }
                "STATUS_REACTED" -> {
                    val statusId = data.optString("id")
                    val emoji = data.optString("emoji", "💚")
                    val rawPhone = data.optString("senderPhone", "")
                    val realPhone = data.optString("realPhone").takeIf { it.isNotEmpty() }
                    val rawSenderName = data.optString("senderName", "")
                    val effectivePhone = realPhone ?: rawPhone
                    val cleanPhone = effectivePhone.replace("[^0-9]".toRegex(), "")
                    val resolved = deviceContactsManager.resolveContactNameWithPriority(effectivePhone, rawSenderName)
                    val displayName = resolved?.name ?: if (cleanPhone.length in 7..15) "+$cleanPhone" else "حالة واتساب"

                    logDiagnosticEvent("STATUS", "Status reacted with $emoji for $displayName ($statusId)")
                    try {
                        logRepository.insertLog(
                            ActivityLog(
                                senderPhone = if (cleanPhone.isNotEmpty()) "+$cleanPhone" else "status@broadcast",
                                messageText = "تفاعل $emoji على حالة: $displayName",
                                actionExecuted = "تفاعل ($emoji) مع حالة $displayName",
                                status = LogStatus.SUCCESS
                            )
                        )
                    } catch (_: Exception) {}
                }
                "STATUS_FETCH_START" -> {
                    val msg = data.optString("message", "جارٍ جلب الحالات...")
                    logDiagnosticEvent("STATUS_FETCH", msg)
                }
                "STATUS_FETCH_DONE" -> {
                    val count = data.optInt("count", 0)
                    val msg = data.optString("message", "اكتمل جلب الحالات")
                    logDiagnosticEvent("STATUS_FETCH", "✅ $msg (عدد الحالات المجلوبة: $count)")
                }
                "WHATSAPP_CONTACTS_SYNC" -> {
                    val contactsArray = data.optJSONArray("contacts")
                    if (contactsArray != null && contactsArray.length() > 0) {
                        val pairs = mutableListOf<Pair<String, String>>()
                        for (i in 0 until contactsArray.length()) {
                            val c = contactsArray.optJSONObject(i) ?: continue
                            val phone = c.optString("phone")
                            val name = c.optString("name")
                            if (phone.isNotEmpty() && name.isNotEmpty()) {
                                pairs.add(Pair(phone, name))
                            }
                        }
                        deviceContactsManager.updateWhatsAppContacts(pairs)
                        logDiagnosticEvent("CONTACTS", "تمت مزامنة ${pairs.size} جهة اتصال محفوظة من واتساب.")
                    }
                }
                "WHATSAPP_CONTACT_UPDATED" -> {
                    val phone = data.optString("phone")
                    val name = data.optString("name")
                    if (phone.isNotEmpty() && name.isNotEmpty()) {
                        deviceContactsManager.updateSingleWhatsAppContact(phone, name)
                        logDiagnosticEvent("CONTACTS", "تحديث اسم جهة اتصال واتساب: $name ($phone)")
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
