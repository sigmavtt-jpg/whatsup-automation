package com.whatsup.automation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.whatsup.automation.data.engine.WhatsAppEngine
import com.whatsup.automation.domain.usecase.ActionResult
import com.whatsup.automation.domain.model.ConnectionState
import com.whatsup.automation.domain.usecase.ProcessResult
import com.whatsup.automation.domain.repository.StatusRepository
import com.whatsup.automation.domain.usecase.ProcessIncomingMessageUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * خدمة أمامية (Foreground Service) تعمل في الخلفية على مدار الساعة.
 * تضمن استمرار تشغيل محرك الأتمتة واستقبال ومعالجة الرسائل والحالات حتى عند إغلاق التطبيق.
 */
@AndroidEntryPoint
class WhatsAppForegroundService : Service() {

    @Inject
    lateinit var whatsAppEngine: WhatsAppEngine

    @Inject
    lateinit var processIncomingMessageUseCase: ProcessIncomingMessageUseCase

    @Inject
    lateinit var statusRepository: StatusRepository

    @Inject
    lateinit var deviceContactsManager: com.whatsup.automation.data.local.contacts.DeviceContactsManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val CHANNEL_ID = "whatsup_automation_channel"
        const val NOTIFICATION_ID = 1001
        const val WARNING_CHANNEL_ID = "whatsup_contacts_warning_channel"
        const val PERMISSION_WARNING_NOTIF_ID = 2002
        const val ACTION_START = "com.whatsup.automation.START_SERVICE"
        const val ACTION_STOP = "com.whatsup.automation.STOP_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, WhatsAppForegroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("WhatsAppForegroundService", "Failed to start foreground service: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WhatsAppForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isBatteryOptimizationIgnored(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
            return true
        }

        fun requestIgnoreBatteryOptimizations(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        setupNetworkMonitoring()
        observeConnectionState()
        observeIncomingMessages()
        observeIncomingStatuses()
        scheduleStatusCleanup()
        processExistingUnviewedStatuses()
        scheduleContactsPermissionCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                whatsAppEngine.stopEngine()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            buildNotification("محرك الأتمتة قيد التشغيل..."),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                        )
                    } else {
                        startForeground(NOTIFICATION_ID, buildNotification("محرك الأتمتة قيد التشغيل..."))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("WhatsAppForegroundService", "Failed to start foreground notification: ${e.message}", e)
                }
                whatsAppEngine.startEngine()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // إعادة إطلاق الخدمة فوراً عند قيام المستخدم بمسح التطبيق من قائمة التطبيقات الحديثة
        try {
            val restartIntent = Intent(applicationContext, WhatsAppForegroundService::class.java).apply {
                action = ACTION_START
            }
            val restartPendingIntent = PendingIntent.getService(
                applicationContext,
                101,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
            alarmManager?.set(
                android.app.AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                restartPendingIntent
            )
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkMonitoring()
        releaseWakeLock()
        serviceScope.cancel()
    }

    private fun observeConnectionState() {
        serviceScope.launch {
            whatsAppEngine.connectionState.collectLatest { state ->
                val statusText = when (state) {
                    is ConnectionState.Connected -> "متصل ونشط — الأتمتة تعمل ✅"
                    is ConnectionState.AwaitingPairing -> "في انتظار الربط (QR أو Pairing Code) ⏳"
                    is ConnectionState.Reconnecting -> "جارٍ إعادة الاتصال... 🔄"
                    is ConnectionState.Disconnected -> "غير متصل ⏸️"
                    is ConnectionState.Error -> "خطأ: ${state.message} ⚠️"
                }
                updateNotification(statusText)
            }
        }
    }

    private fun observeIncomingMessages() {
        serviceScope.launch {
            whatsAppEngine.incomingMessages.collect { message ->
                try {
                    // تجاهل الرسائل الناتجة عن إشعارات أندرويد لمنع التكرار
                    if (message.id.startsWith("NOTIF_")) {
                        return@collect
                    }

                    val targetPhone = message.realPhone ?: message.senderPhone

                    // تجاهل قنوات الأخبار والبثوث العامة
                    if (targetPhone.contains("@newsletter") || 
                        targetPhone.contains("@broadcast") || 
                        targetPhone.contains("newsletter") ||
                        targetPhone.startsWith("120363") ||
                        message.isGroupMessage) {
                        return@collect
                    }

                    // حجز المعالجة ذرياً لمنع أي تنافس أو ردود مزدوجة
                    val claimed = whatsAppEngine.tryClaimMessageProcessing(targetPhone, message.text)
                    if (!claimed) {
                        return@collect
                    }
                    if (message.senderName.isNotBlank() && message.senderName != targetPhone) {
                        whatsAppEngine.markMessageHandled(message.senderName, message.text)
                    }

                    // معالجة الرسالة عبر استخدامات الدومين
                    val result = processIncomingMessageUseCase.execute(message)

                    // في حالة تنفيذ قاعدة تتطلب إرسال رد
                    if (result is ProcessResult.Executed) {
                        for (actionResult in result.actions) {
                            when (actionResult) {
                                is ActionResult.ReplySent -> {
                                    if (actionResult.message.isNotBlank()) {
                                        whatsAppEngine.sendMessage(targetPhone, actionResult.message)
                                    }
                                }
                                is ActionResult.Error -> {
                                    // مسجل مسبقاً في ActivityLog
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("WhatsAppForegroundService", "Error processing message: ${e.message}", e)
                }
            }
        }
    }

    private fun observeIncomingStatuses() {
        serviceScope.launch {
            whatsAppEngine.incomingStatuses.collect { status ->
                try {
                    val targetParticipant = status.participant ?: status.senderPhone
                    val targetPhone = status.realPhone ?: status.senderPhone

                    // حفظ الحالة في Room
                    statusRepository.insertStatus(status)

                    val settings = statusRepository.getSettings().first()

                    // المشاهدة التلقائية إذا كانت مفعلة
                    if (settings.autoViewEnabled && !status.isViewed) {
                        whatsAppEngine.markStatusViewed(status.id, targetPhone, targetParticipant)
                        statusRepository.markStatusViewed(status.id)
                    }

                    // التفاعل التلقائي إذا كان مفعلاً
                    if (settings.autoReactEnabled && !status.isReacted) {
                        // تأخير بشري ذكي (1.5 إلى 3 ثوانٍ) لمحاكاة السلوك الطبيعي ومنع حجب التفاعل
                        delay(1500L + (Math.random() * 1200).toLong())
                        val emoji = settings.defaultEmoji.ifBlank { "💚" }
                        whatsAppEngine.reactToStatus(status.id, emoji, targetPhone, targetParticipant)
                        statusRepository.reactToStatus(status.id, emoji)
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("WhatsAppForegroundService", "Error processing status: ${e.message}", e)
                }
            }
        }
    }

    /**
     * تنظيف دوري للحالات المنتهية الصلاحية (أكثر من 24 ساعة) — يعمل كل 6 ساعات.
     */
    private fun scheduleStatusCleanup() {
        serviceScope.launch {
            while (true) {
                try {
                    statusRepository.deleteExpiredStatuses()
                } catch (_: Exception) {}
                delay(6 * 60 * 60 * 1000L) // 6 ساعات
            }
        }
    }

    /**
     * معالجة الحالات الموجودة مسبقاً في Room عند بدء الخدمة:
     * 1. يفحص كل الحالات المحفوظة
     * 2. الحالات التي لم تُشاهَد → يشاهدها تلقائياً إذا كانت الإعدادات تسمح
     * 3. عند الاتصال → يطلب جلب الحالات الحالية من واتساب لتحديث القائمة
     */
    private fun processExistingUnviewedStatuses() {
        serviceScope.launch {
            // انتظر حتى يصبح المحرك متصلاً قبل المعالجة (حتى 60 ثانية)
            var waited = 0
            while (!whatsAppEngine.isMasterEngineConnected() && waited < 60) {
                delay(1000L)
                waited++
            }

            try {
                // طلب جلب الحالات الجديدة والقديمة من واتساب
                whatsAppEngine.fetchAllStatuses()
                delay(5000L) // انتظر حتى تصل الحالات المجلوبة

                // فحص الحالات المحفوظة في Room وإكمال المشاهدة للجديدة
                val settings = statusRepository.getSettings().first()
                val allStatuses = statusRepository.getAllStatuses().first()
                val unviewedStatuses = allStatuses.filter { !it.isViewed && !it.isReacted }

                android.util.Log.d("WhatsAppForegroundService",
                    "Processing ${unviewedStatuses.size} unviewed statuses out of ${allStatuses.size} total")

                for (status in unviewedStatuses) {
                    try {
                        val targetParticipant = status.participant ?: status.senderPhone
                        val targetPhone = status.realPhone ?: status.senderPhone
                        if (settings.autoViewEnabled) {
                            whatsAppEngine.markStatusViewed(status.id, targetPhone, targetParticipant)
                            statusRepository.markStatusViewed(status.id)
                            delay(1200L + (Math.random() * 800).toLong())
                        }
                        if (settings.autoReactEnabled) {
                            val emoji = settings.defaultEmoji.ifBlank { "💚" }
                            whatsAppEngine.reactToStatus(status.id, emoji, targetPhone, targetParticipant)
                            statusRepository.reactToStatus(status.id, emoji)
                            delay(2000L + (Math.random() * 1000).toLong())
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WhatsAppForegroundService",
                            "Failed to process existing status ${status.id}: ${e.message}")
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("WhatsAppForegroundService",
                    "Error processing existing statuses: ${e.message}")
            }
        }
    }

    private fun setupNetworkMonitoring() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    if (whatsAppEngine.connectionState.value is ConnectionState.Disconnected) {
                        whatsAppEngine.startEngine()
                    }
                }

                override fun onLost(network: Network) {
                    updateNotification("انقطع الاتصال بالإنترنت ⚠️")
                }
            }
            networkCallback?.let { connectivityManager?.registerNetworkCallback(request, it) }
        } catch (_: Exception) {}
    }

    private fun unregisterNetworkMonitoring() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WhatsUp::AutomationWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours timeout safety
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة أتمتة واتساب",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار مستمر يضمن عمل محرك الأتمتة في الخلفية"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)

            val warningChannel = NotificationChannel(
                WARNING_CHANNEL_ID,
                "تنبيهات أذونات الأتمتة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات عند فقدان أذونات كتابة وقراءة جهات الاتصال"
                setShowBadge(true)
            }
            manager.createNotificationChannel(warningChannel)
        }
    }

    /**
     * R2: فحص أوتوماتيكي دوري لإذني READ_CONTACTS و WRITE_CONTACTS في الخلفية.
     * في حال فقدان إذن الكتابة (WRITE_CONTACTS)، يتم إظهار تنبيه فوري للمستخدم يطلب منه منح الصلاحية.
     */
    private fun scheduleContactsPermissionCheck() {
        serviceScope.launch {
            var lastWriteState: Boolean? = null
            var lastReadState: Boolean? = null
            while (true) {
                try {
                    val hasWrite = ServicePermissionManager.hasWriteContactsPermission(this@WhatsAppForegroundService)
                    val hasRead = ServicePermissionManager.hasReadContactsPermission(this@WhatsAppForegroundService)
                    val notificationManager = getSystemService(NotificationManager::class.java)

                    // تسجيل تشخيصي وتحديث التنبيه عند تغير حالة الأذونات
                    if (lastWriteState != hasWrite || lastReadState != hasRead) {
                        val stateChanged = lastWriteState != null
                        lastWriteState = hasWrite
                        lastReadState = hasRead
                        val writeStatus = if (hasWrite) "GRANTED" else "DENIED"
                        val readStatus = if (hasRead) "GRANTED" else "DENIED"
                        val saveStatus = if (hasWrite && hasRead) "SUCCESS" else "FAILED"
                        
                        whatsAppEngine.logDiagnosticEvent(
                            "Contacts",
                            "[Contacts] Write permission: $writeStatus, Saved to Phone: $saveStatus"
                        )

                        if (!hasWrite) {
                            showContactsPermissionWarningNotification(notificationManager)
                        } else {
                            notificationManager.cancel(PERMISSION_WARNING_NOTIF_ID)
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("WhatsAppForegroundService", "Error checking contacts permissions: ${e.message}")
                }
                delay(30_000L) // فحص كل 30 ثانية
            }
        }
    }

    private fun showContactsPermissionWarningNotification(manager: NotificationManager) {
        val launchIntent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.parse("package:$packageName")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            202,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val warningNotification = NotificationCompat.Builder(this, WARNING_CHANNEL_ID)
            .setContentTitle("تنبيه: إذن حفظ جهات الاتصال مطلوب ⚠️")
            .setContentText("صلاحية WRITE_CONTACTS مفقودة. اضغط هنا لمنح الإذن لضمان حفظ الأسماء في الهاتف والواتساب.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(PERMISSION_WARNING_NOTIF_ID, warningNotification)
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, com.whatsup.automation.presentation.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhatsApp Automation Engine")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }
}
