package com.whatsup.automation.service

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import com.whatsup.automation.data.engine.WhatsAppEngine
import com.whatsup.automation.domain.model.ConnectionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * نموذج بيانات لصحة واستقرار النظام والخدمات الخلفية.
 */
data class SystemHealthSnapshot(
    val isForegroundServiceRunning: Boolean = false,
    val isNotificationListenerEnabled: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isAutomationActive: Boolean = true,
    val serviceUptimeMillis: Long = 0L,
    val usedMemoryMb: Long = 0L,
    val totalMemoryMb: Long = 0L,
    val memoryUsagePercent: Int = 0,
    val lastHeartbeatTimestamp: Long = System.currentTimeMillis()
) {
    val uptimeFormatted: String
        get() {
            if (serviceUptimeMillis <= 0) return "00:00:00"
            val totalSeconds = serviceUptimeMillis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        }
}

/**
 * حارس الخدمة الذكي ومراقب الأداء الحي (Watchdog & Health Monitor).
 * يراقب استهلاك الذاكرة، صحة الخدمات الخلفية، واستقرار الاتصال بشكل دوري.
 */
@Singleton
class ServiceHealthMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whatsAppEngine: WhatsAppEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var serviceStartTime: Long = 0L

    private val _healthState = MutableStateFlow(SystemHealthSnapshot())
    val healthState: StateFlow<SystemHealthSnapshot> = _healthState.asStateFlow()

    init {
        startMonitoringLoop()
    }

    fun notifyServiceStarted() {
        serviceStartTime = SystemClock.elapsedRealtime()
        updateSnapshot(isServiceRunning = true)
    }

    fun notifyServiceStopped() {
        serviceStartTime = 0L
        updateSnapshot(isServiceRunning = false)
    }

    private fun startMonitoringLoop() {
        scope.launch {
            while (isActive) {
                updateSnapshot(isServiceRunning = serviceStartTime > 0)
                delay(3000) // فحص دوري كل 3 ثوانٍ
            }
        }
    }

    private fun updateSnapshot(isServiceRunning: Boolean) {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        val memPercent = if (maxMem > 0) ((usedMem.toDouble() / maxMem.toDouble()) * 100).toInt() else 0

        val uptime = if (serviceStartTime > 0) {
            SystemClock.elapsedRealtime() - serviceStartTime
        } else 0L

        val isNotifEnabled = try {
            ServicePermissionManager.isNotificationListenerEnabled(context)
        } catch (_: Throwable) {
            false
        }
        val connState = whatsAppEngine.connectionState.value
        val isAutoPaused = whatsAppEngine.isAutomationPaused.value

        _healthState.value = SystemHealthSnapshot(
            isForegroundServiceRunning = isServiceRunning,
            isNotificationListenerEnabled = isNotifEnabled,
            connectionState = connState,
            isAutomationActive = !isAutoPaused,
            serviceUptimeMillis = uptime,
            usedMemoryMb = usedMem,
            totalMemoryMb = maxMem,
            memoryUsagePercent = memPercent.coerceIn(0, 100),
            lastHeartbeatTimestamp = System.currentTimeMillis()
        )
    }
}
