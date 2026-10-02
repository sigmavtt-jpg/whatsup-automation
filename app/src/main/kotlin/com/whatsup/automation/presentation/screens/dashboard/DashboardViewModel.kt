package com.whatsup.automation.presentation.screens.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsup.automation.data.engine.WhatsAppEngine
import com.whatsup.automation.domain.model.ActivityLog
import com.whatsup.automation.domain.model.ConnectionState
import com.whatsup.automation.domain.model.DashboardStats
import com.whatsup.automation.data.local.entity.SyncedContactDao
import com.whatsup.automation.domain.repository.LogRepository
import com.whatsup.automation.service.ServicePermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isAssistantActive: Boolean = false,
    val isAutomationPaused: Boolean = false,
    val stats: DashboardStats = DashboardStats(),
    val recentLogs: List<ActivityLog> = emptyList(),
    val isRefreshing: Boolean = false,
    val isWritePermissionGranted: Boolean = true,
    val terminalLines: List<String> = listOf(
        "[$] تهيئة محرك الأتمتة المحلي...",
        "[$] التحقق من الجلسات الآمنة في Android KeyStore...",
        "[$] نظام Room جاهز للاستقبال والفرز الآلي."
    )
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whatsAppEngine: WhatsAppEngine,
    private val logRepository: LogRepository,
    private val syncedContactDao: SyncedContactDao,
    private val deviceContactsManager: com.whatsup.automation.data.local.contacts.DeviceContactsManager,
    private val notificationRepository: com.whatsup.automation.domain.repository.NotificationRepository,
    private val serviceHealthMonitor: com.whatsup.automation.service.ServiceHealthMonitor
) : ViewModel() {

    val healthState = serviceHealthMonitor.healthState
    val notifications: StateFlow<List<com.whatsup.automation.domain.model.AppNotification>> = notificationRepository.notifications
    val unreadNotificationsCount: StateFlow<Int> = notificationRepository.unreadCount

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    fun markNotificationsAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
        }
    }

    fun clearNotifications() {
        viewModelScope.launch {
            notificationRepository.clearAll()
        }
    }

    init {
        checkPermissions()
        observeConnectionState()
        observeAssistantState()
        observeAutomationPauseState()
        observeContacts()
        observeLogs()
        observeEngineDiagnostics()
        refreshStats()
        startAutoRefresh()
    }

    private fun observeAutomationPauseState() {
        viewModelScope.launch {
            whatsAppEngine.isAutomationPaused.collect { isPaused ->
                _uiState.update { it.copy(isAutomationPaused = isPaused) }
            }
        }
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                refreshDashboard()
            }
        }
    }

    private fun checkPermissions() {
        val hasWrite = ServicePermissionManager.hasWriteContactsPermission(context)
        val hasRead = ServicePermissionManager.hasReadContactsPermission(context)
        _uiState.update { it.copy(isWritePermissionGranted = hasWrite && hasRead) }
    }

    private fun observeAssistantState() {
        viewModelScope.launch {
            whatsAppEngine.isDirectAutomationActive.collect { active ->
                _uiState.update { it.copy(isAssistantActive = active) }
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            checkPermissions()
            whatsAppEngine.refreshRealConnectionState()
            refreshStats()
            kotlinx.coroutines.delay(400)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun observeConnectionState() {
        viewModelScope.launch {
            whatsAppEngine.connectionState.collect { state ->
                _uiState.update { current ->
                    val line = when (state) {
                        is ConnectionState.Connected -> "[$] ✅ تم الاتصال بواتساب بنجاح — المحرك نشط."
                        is ConnectionState.AwaitingPairing -> "[$] ⏳ في انتظار الاقتران (QR أو Pairing Code)..."
                        is ConnectionState.Disconnected -> "[$] ⏸️ المحرك في وضع التوقف."
                        is ConnectionState.Reconnecting -> "[$] 🔄 جارٍ إعادة محاولة الاتصال..."
                        is ConnectionState.Error -> "[$] ❌ خطأ: ${state.message}"
                    }
                    current.copy(
                        connectionState = state,
                        terminalLines = (current.terminalLines + line).takeLast(10)
                    )
                }
            }
        }
    }

    private fun observeEngineDiagnostics() {
        viewModelScope.launch {
            whatsAppEngine.engineDiagnostics.collect { diagnostics ->
                val contactEvents = diagnostics.filter { it.contains("[Contacts]") }.takeLast(3)
                if (contactEvents.isNotEmpty()) {
                    val formattedLines = contactEvents.map { "[$] ${it.substringAfter("] ")}" }
                    _uiState.update { current ->
                        current.copy(
                            terminalLines = (current.terminalLines + formattedLines).distinct().takeLast(10)
                        )
                    }
                }
            }
        }
    }

    
    private fun observeContacts() {
        viewModelScope.launch {
            try {
                val count = syncedContactDao.getCount()
                _uiState.update { it.copy(stats = it.stats.copy(totalSavedContacts = count)) }
            } catch (e: Exception) {}
        }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            logRepository.getRecentLogs(10).collect { logs ->
                _uiState.update { current ->
                    val newLines = logs.take(3).map { log ->
                        val cleanDigits = log.senderPhone.replace("[^0-9]".toRegex(), "")
                        val deviceName = if (cleanDigits.length >= 7) {
                            deviceContactsManager.getContactDisplayName(log.senderPhone)
                                ?: deviceContactsManager.getContactDisplayName(cleanDigits)
                        } else null
                        val displayIdentifier = deviceName ?: if (cleanDigits.length >= 7) "+$cleanDigits" else "محادثة"
                        "[$] [$displayIdentifier] -> ${log.actionExecuted}"
                    }
                    current.copy(
                        recentLogs = logs,
                        terminalLines = (current.terminalLines + newLines).distinct().takeLast(10)
                    )
                }
                refreshStats()
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val stats = logRepository.getStats()
            _uiState.update { it.copy(stats = stats) }
        }
    }

    fun toggleService() {
        if (_uiState.value.connectionState is ConnectionState.Connected) {
            whatsAppEngine.toggleAutomationPause()
        } else {
            whatsAppEngine.startEngine()
        }
    }
}
