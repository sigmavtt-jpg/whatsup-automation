package com.whatsup.automation.data.engine

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.*
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير جسر Baileys المحلي (On-Device WhatsApp Bridge).
 * يقوم بنسخ الحزمة وتشغيل محرك Baileys محلياً داخل الهاتف بدون أي خوادم خارجية،
 * ويدير الاتصال ثنائي الاتجاه عبر IPC (Standard I/O) أو Local TCP Loopback Socket.
 */
@Singleton
class BaileysBridgeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()

    @Volatile private var process: Process? = null
    @Volatile private var processWriter: BufferedWriter? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var socketWriter: BufferedWriter? = null

    @Volatile private var isRunning = false
    private var eventListener: ((String) -> Unit)? = null

    private val _isBridgeReady = MutableStateFlow(false)
    val isBridgeReady: StateFlow<Boolean> = _isBridgeReady.asStateFlow()

    companion object {
        private const val TAG = "BaileysBridge"
        private const val LOCAL_PORT = 6789
        private const val BUNDLE_FILENAME = "bundle.cjs"
    }

    /**
     * تسجيل مستمع الأحداث الواردة من محرك Baileys
     */
    fun setEventListener(listener: (String) -> Unit) {
        this.eventListener = listener
    }

    /**
     * بدء تشغيل الجسر واستخراج الحزم
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                prepareAssets()
                startBridgeProcess()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Baileys bridge", e)
            }
        }
    }

    /**
     * نسخ ملف bundle.cjs من assets إلى مسار ملفات التطبيق الداخلية الخاصة
     */
    private fun prepareAssets() {
        val engineDir = File(context.filesDir, "baileys_engine")
        if (!engineDir.exists()) engineDir.mkdirs()

        val authDir = File(context.filesDir, "baileys_auth")
        if (!authDir.exists()) authDir.mkdirs()

        val targetBundle = File(engineDir, BUNDLE_FILENAME)
        try {
            context.assets.open("nodejs-project/$BUNDLE_FILENAME").use { input ->
                FileOutputStream(targetBundle).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Successfully extracted $BUNDLE_FILENAME to ${targetBundle.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract bundle from assets: ${e.message}")
        }
    }

    /**
     * تشغيل العملية المحلية أو محاولة الاتصال بالمقبس المحلي
     */
    private suspend fun startBridgeProcess() {
        val engineDir = File(context.filesDir, "baileys_engine")
        val bundleFile = File(engineDir, BUNDLE_FILENAME)
        val authDir = File(context.filesDir, "baileys_auth")

        // 1. تشغيل محرك Node.js 18 المدمج محلياً عبر JNI في مسار خلفي
        if (bundleFile.exists()) {
            if (!NodeRunner.isNativeLoaded) {
                Log.w(TAG, "Native node libraries not loaded, skipping JNI NodeRunner execution.")
            } else {
                scope.launch(Dispatchers.IO) {
                    try {
                        Log.i(TAG, "Launching embedded Node.js 18 via JNI NodeRunner...")
                        val args = arrayOf("node", bundleFile.absolutePath, authDir.absolutePath)
                        val result = NodeRunner.startNodeWithArguments(args)
                        Log.i(TAG, "NodeRunner finished with exit code: $result")
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error running Node.js via JNI", t)
                    }
                }
            }
        } else {
            Log.w(TAG, "Bundle file not found at ${bundleFile.absolutePath}")
        }

        // 2. الاتصال بالمقبس المحلي Loopback Socket (127.0.0.1:6789)
        connectToLocalSocket()
    }

    /**
     * محاولة الاتصال بالمقبس المحلي Loopback Socket (127.0.0.1:6789)
     */
    private suspend fun connectToLocalSocket() {
        withContext(Dispatchers.IO) {
            var attempts = 0
            while (isRunning && attempts < 40) {
                try {
                    Log.d(TAG, "Attempting to connect to local socket (127.0.0.1:$LOCAL_PORT), attempt ${attempts + 1}/40...")
                    val s = Socket("127.0.0.1", LOCAL_PORT)
                    socket = s
                    socketWriter = BufferedWriter(OutputStreamWriter(s.getOutputStream()))

                    scope.launch {
                        val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                        try {
                            while (isRunning) {
                                val currentLine = reader.readLine() ?: break
                                onRawLineReceived(currentLine)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Socket reader closed: ${e.message}")
                        } finally {
                            _isBridgeReady.value = false
                        }
                    }
                    _isBridgeReady.value = true
                    Log.i(TAG, "Connected to Baileys via local socket on port $LOCAL_PORT")
                    break
                } catch (e: Exception) {
                    attempts++
                    delay(400)
                }
            }
            if (socket == null) {
                Log.w(TAG, "Could not establish local socket connection to port $LOCAL_PORT after $attempts attempts.")
            }
        }
    }

    /**
     * معالجة السطر الوارد وتوجيهه لمستمع الأحداث
     */
    private fun onRawLineReceived(line: String) {
        if (line.startsWith("[WHATSAPP_EVENT]")) {
            eventListener?.invoke(line)
        } else {
            Log.d(TAG, "Baileys Log: $line")
        }
    }

    /**
     * إرسال أمر JSON إلى محرك Baileys
     */
    suspend fun sendCommand(command: JSONObject): Boolean = commandMutex.withLock {
        val payload = command.toString()
        return withContext(Dispatchers.IO) {
            try {
                if (socketWriter == null && processWriter == null && isRunning) {
                    Log.d(TAG, "Bridge socket not yet connected, waiting up to 10s for bridge to be ready...")
                    withTimeoutOrNull(10_000L) {
                        isBridgeReady.first { it }
                    }
                }

                // إرسال عبر Process stdin
                processWriter?.let { writer ->
                    writer.write(payload)
                    writer.newLine()
                    writer.flush()
                    return@withContext true
                }
                // أو إرسال عبر Local Socket
                socketWriter?.let { writer ->
                    writer.write(payload)
                    writer.newLine()
                    writer.flush()
                    return@withContext true
                }
                Log.w(TAG, "No active channel to send command to Baileys: $payload")
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command to Baileys", e)
                false
            }
        }
    }

    /**
     * طلب كود الاقتران عبر رقم الهاتف
     */
    suspend fun requestPairingCode(phone: String): Boolean {
        val cmd = JSONObject().apply {
            put("action", "PAIR_CODE")
            put("phone", phone)
        }
        return sendCommand(cmd)
    }

    /**
     * إرسال رسالة نصية عبر واتساب
     */
    suspend fun sendMessage(recipient: String, text: String, id: String = java.util.UUID.randomUUID().toString()): Boolean {
        val cmd = JSONObject().apply {
            put("action", "SEND_MESSAGE")
            put("recipient", recipient)
            put("text", text)
            put("id", id)
        }
        return sendCommand(cmd)
    }

    /**
     * إرسال تأكيد مشاهدة الحالة
     */
    suspend fun markStatusViewed(statusId: String, senderPhone: String): Boolean {
        val cmd = JSONObject().apply {
            put("action", "VIEW_STATUS")
            put("statusId", statusId)
            put("senderPhone", senderPhone)
        }
        return sendCommand(cmd)
    }

    /**
     * إرسال تفاعل إيموجي على الحالة
     */
    suspend fun reactToStatus(statusId: String, senderPhone: String, emoji: String): Boolean {
        val cmd = JSONObject().apply {
            put("action", "REACT_STATUS")
            put("statusId", statusId)
            put("senderPhone", senderPhone)
            put("emoji", emoji)
        }
        return sendCommand(cmd)
    }

    /**
     * طلب جلب كل الحالات الموجودة (القديمة والجديدة) من status@broadcast
     */
    suspend fun fetchStatuses(): Boolean {
        val cmd = JSONObject().apply {
            put("action", "FETCH_STATUSES")
        }
        return sendCommand(cmd)
    }

    /**
     * تسجيل الخروج ومسح الجلسة
     */
    suspend fun logout(): Boolean {
        val cmd = JSONObject().apply {
            put("action", "LOGOUT")
        }
        return sendCommand(cmd)
    }

    /**
     * مسح مجلد المصادقة والجلسة لتجديد مفاتيح التشفير
     */
    fun clearAuthState() {
        try {
            val authDir = File(context.filesDir, "baileys_auth")
            if (authDir.exists()) {
                authDir.deleteRecursively()
                authDir.mkdirs()
                Log.d(TAG, "Cleared baileys_auth folder successfully.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing auth state: ${e.message}")
        }
    }

    /**
     * إيقاف المحرك وإغلاق كافة المسارات
     */
    fun stop() {
        isRunning = false
        _isBridgeReady.value = false
        try {
            processWriter?.close()
            process?.destroy()
        } catch (_: Exception) {}
        try {
            socketWriter?.close()
            socket?.close()
        } catch (_: Exception) {}
        process = null
        processWriter = null
        socket = null
        socketWriter = null
    }
}
