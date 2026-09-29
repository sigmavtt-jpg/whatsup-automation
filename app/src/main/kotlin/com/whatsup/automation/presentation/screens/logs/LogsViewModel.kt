package com.whatsup.automation.presentation.screens.logs

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsup.automation.data.engine.NodeRunner
import com.whatsup.automation.data.engine.WhatsAppEngine
import com.whatsup.automation.data.security.SessionKeystore
import com.whatsup.automation.domain.model.ActivityLog
import com.whatsup.automation.domain.model.ConnectionState
import com.whatsup.automation.domain.model.LogStatus
import com.whatsup.automation.domain.repository.LogRepository
import com.whatsup.automation.service.ServicePermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ConversationSummary(
    val senderPhone: String,
    val contactName: String?,
    val latestMessageText: String,
    val latestActionExecuted: String,
    val latestTimestamp: java.time.Instant,
    val totalMessages: Int,
    val lastStatus: LogStatus,
    val allLogs: List<ActivityLog>
)

enum class LogsTab {
    CONVERSATIONS, // سجل المحادثات والبطاقات
    RAW_LOGS,      // سجل النشاطات الفردية
    SYSTEM_DIAGNOSTICS // التقرير التشخيصي للنظام
}

data class LogsUiState(
    val selectedTab: LogsTab = LogsTab.CONVERSATIONS,
    val conversations: List<ConversationSummary> = emptyList(),
    val filteredConversations: List<ConversationSummary> = emptyList(),
    val selectedConversation: ConversationSummary? = null,
    val logs: List<ActivityLog> = emptyList(),
    val filteredLogs: List<ActivityLog> = emptyList(),
    val engineDiagnostics: List<String> = emptyList(),
    val contactDisplayNames: Map<String, String> = emptyMap(),
    val selectedFilter: LogStatusFilter = LogStatusFilter.ALL,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false
)

enum class LogStatusFilter {
    ALL,
    SUCCESS,
    FAILURE,
    NO_MATCH,
    SYSTEM_DIAGNOSTICS
}

@HiltViewModel
class LogsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: LogRepository,
        private val whatsAppEngine: WhatsAppEngine,
    private val sessionKeystore: SessionKeystore,
    private val deviceContactsManager: com.whatsup.automation.data.local.contacts.DeviceContactsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    private val forceRefreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0L)

    init {
        observeLogs()
        observeEngineDiagnostics()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                forceRefreshTrigger.value = System.currentTimeMillis()
            }
        }
    }

    
    
    
    private fun observeLogs() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                logRepository.getAllLogs(),
                forceRefreshTrigger,
                deviceContactsManager.contactsUpdateTrigger
            ) { allLogs: List<ActivityLog>, _: Long, _: Long ->
                val conversations = groupLogsIntoConversations(allLogs)
                Pair(allLogs, conversations)
            }.collect { (allLogs, conversations) ->
                val namesMap = conversations.mapNotNull {
                    if (it.contactName != null) it.senderPhone to it.contactName else null
                }.toMap()
                _uiState.update {
                    it.copy(
                        logs = allLogs,
                        conversations = conversations,
                        contactDisplayNames = namesMap
                    )
                }
            }
        }
    }

    private fun groupLogsIntoConversations(
        allLogs: List<ActivityLog>
    ): List<ConversationSummary> {
        // استبعاد بثوث الحالات status@broadcast والقنوات الإخبارية من بطاقات المحادثات الشخصية
        val filteredLogs = allLogs.filter { log ->
            val p = log.senderPhone.trim()
            p.isNotBlank() &&
            p != "status@broadcast" &&
            !p.contains("@newsletter") &&
            !p.contains("newsletter") &&
            !p.startsWith("120363") // معرفات القنوات الإخبارية في واتساب
        }

        return filteredLogs.groupBy { it.senderPhone }.map { (phone, logs) ->
            val sortedDescending = logs.sortedByDescending { it.timestamp }
            val latest = sortedDescending.first()
            val cleanPhoneDigits = phone.filter { it.isDigit() }

            // R3: في شاشات الحالات والسجل والداشبورد: مصدر الاسم الوحيد والمعتمد هو دفتر أسماء الهاتف الفعلي
            // منع ظهور أي أرقام خام أو معرفات @ أو أسماء داخلية غير مسجلة في الهاتف
            val deviceName = if (cleanPhoneDigits.length >= 7) {
                deviceContactsManager.getContactDisplayName(phone)
                    ?: deviceContactsManager.getContactDisplayName(cleanPhoneDigits)
            } else null

            val contactName = deviceName

            ConversationSummary(
                senderPhone = phone,
                contactName = contactName,
                latestMessageText = latest.messageText,
                latestActionExecuted = latest.actionExecuted,
                latestTimestamp = latest.timestamp,
                totalMessages = logs.size,
                lastStatus = latest.status,
                allLogs = sortedDescending.reversed() // ترتيب تصاعدي لعرض المحادثة
            )
        }.sortedByDescending { it.latestTimestamp }
    }

    private fun applyConversationSearch(conversations: List<ConversationSummary>, query: String): List<ConversationSummary> {
        if (query.isBlank()) return conversations
        val q = query.trim().lowercase()
        return conversations.filter {
            it.senderPhone.contains(q, ignoreCase = true) ||
            (it.contactName?.contains(q, ignoreCase = true) == true) ||
            it.latestMessageText.contains(q, ignoreCase = true) ||
            it.latestActionExecuted.contains(q, ignoreCase = true)
        }
    }

    fun selectTab(tab: LogsTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun openConversation(conversation: ConversationSummary) {
        _uiState.update { it.copy(selectedConversation = conversation) }
    }

    fun closeConversation() {
        _uiState.update { it.copy(selectedConversation = null) }
    }

    
    private fun observeEngineDiagnostics() {
        viewModelScope.launch {
            whatsAppEngine.engineDiagnostics.collect { diagnostics ->
                _uiState.update { it.copy(engineDiagnostics = diagnostics) }
            }
        }
    }

    fun setFilter(filter: LogStatusFilter) {
        _uiState.update { current ->
            current.copy(
                selectedFilter = filter,
                filteredLogs = applyFilterAndSearch(current.logs, filter, current.searchQuery)
            )
        }
    }

    fun onSearchChange(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredLogs = applyFilterAndSearch(current.logs, current.selectedFilter, query),
                filteredConversations = applyConversationSearch(current.conversations, query)
            )
        }
    }

    fun refreshLogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            deviceContactsManager.notifyContactsChanged()
            forceRefreshTrigger.value = System.currentTimeMillis()
            delay(500)
            _uiState.update { current ->
                current.copy(
                    isRefreshing = false,
                    filteredLogs = applyFilterAndSearch(current.logs, current.selectedFilter, current.searchQuery),
                    filteredConversations = applyConversationSearch(current.conversations, current.searchQuery)
                )
            }
        }
    }

    fun clearAllLogs() {
        viewModelScope.launch {
            logRepository.clearAllLogs()
            _uiState.update { it.copy(selectedConversation = null) }
        }
    }

    /**
     * إنشاء تقرير تشخيصي شامل للنظام والمحرك جاهز للنسخ والمشاركة
     */
    fun generateDiagnosticReport(): String {
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val connState = when (val s = whatsAppEngine.connectionState.value) {
            is ConnectionState.Connected -> "Connected (متصل)"
            is ConnectionState.AwaitingPairing -> "Awaiting Pairing (في انتظار الاقتران)"
            is ConnectionState.Reconnecting -> "Reconnecting (جارٍ إعادة الاتصال)"
            is ConnectionState.Disconnected -> "Disconnected (غير متصل)"
            is ConnectionState.Error -> "Error: ${s.message}"
        }
        val isNotifEnabled = ServicePermissionManager.isNotificationListenerEnabled(context)
        val isAccessEnabled = ServicePermissionManager.isAccessibilityServiceEnabled(context)
        val isBatteryIgnored = ServicePermissionManager.isBatteryOptimizationIgnored(context)
        val hasContacts = ServicePermissionManager.hasContactsPermissions(context)
        val sessionPhone = sessionKeystore.getSessionPhone() ?: "غير محدد"
        val isSessionActive = sessionKeystore.isSessionActive()
        val nativeLoaded = NodeRunner.isNativeLoaded

        val currentLogs = _uiState.value.logs
        val successCount = currentLogs.count { it.status == LogStatus.SUCCESS }
        val failCount = currentLogs.count { it.status == LogStatus.FAILURE }
        val noMatchCount = currentLogs.count { it.status == LogStatus.NO_MATCH }

        val recentEngineEvents = _uiState.value.engineDiagnostics.takeLast(25).joinToString("\n")
        val recentActivityLogs = currentLogs.take(15).joinToString("\n") { log ->
            "• [${log.status}] ${log.senderPhone}: \"${log.messageText}\" -> ${log.actionExecuted}${if (log.extractedName != null) " (Name: ${log.extractedName})" else ""}"
        }

        return """
========================================
📋 WhatsUp Automation — Live Diagnostic Report
🕒 Generated at: $now
========================================

📱 [DEVICE & SYSTEM INFO]
• Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})
• Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
• Architecture / ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}

⚙️ [AUTOMATION ENGINE STATUS]
• Connection State: $connState
• Session Active: $isSessionActive
• Registered Phone: $sessionPhone
• Embedded JNI Node.js Loaded: $nativeLoaded

🔐 [SYSTEM PERMISSIONS & SERVICES]
• Notification Listener Service: ${if (isNotifEnabled) "ACTIVE ✅" else "DISABLED ❌"}
• Battery Optimization Ignored (Doze Immunity): ${if (isBatteryIgnored) "YES ✅" else "NO ❌"}
• Contacts Permission (Read/Write): ${if (hasContacts) "GRANTED ✅" else "DENIED ❌"}
• Accessibility Service (Hybrid): ${if (isAccessEnabled) "ACTIVE ✅" else "DISABLED ⚠️"}

📊 [DATABASE ACTIVITY SUMMARY]
• Total Records: ${currentLogs.size}
• Successful Automations: $successCount
• Failures: $failCount
• No-Match/Ignored: $noMatchCount

📜 [RECENT ENGINE LIVE EVENTS]
${if (recentEngineEvents.isNotBlank()) recentEngineEvents else "No engine events recorded yet."}

📝 [RECENT ACTIVITY LOGS]
${if (recentActivityLogs.isNotBlank()) recentActivityLogs else "No activity logs available."}
========================================
        """.trimIndent()
    }

    private fun applyFilterAndSearch(
        logs: List<ActivityLog>,
        filter: LogStatusFilter,
        query: String
    ): List<ActivityLog> {
        return logs.filter { log ->
            val matchesFilter = when (filter) {
                LogStatusFilter.ALL -> true
                LogStatusFilter.SUCCESS -> log.status == LogStatus.SUCCESS
                LogStatusFilter.FAILURE -> log.status == LogStatus.FAILURE
                LogStatusFilter.NO_MATCH -> log.status == LogStatus.NO_MATCH
                LogStatusFilter.SYSTEM_DIAGNOSTICS -> true
            }
            val matchesQuery = query.isBlank() ||
                    log.senderPhone.contains(query, ignoreCase = true) ||
                    log.messageText.contains(query, ignoreCase = true) ||
                    (log.matchedRule?.contains(query, ignoreCase = true) == true) ||
                    (log.extractedName?.contains(query, ignoreCase = true) == true)
            matchesFilter && matchesQuery
        }
    }
}

