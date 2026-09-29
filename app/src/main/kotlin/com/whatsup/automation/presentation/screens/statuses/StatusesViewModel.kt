package com.whatsup.automation.presentation.screens.statuses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsup.automation.domain.model.StatusAutomationSettings
import com.whatsup.automation.domain.model.StatusMediaType
import com.whatsup.automation.domain.model.StatusStory
import com.whatsup.automation.domain.repository.StatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

enum class StatusSubTab {
    NEW,
    VIEWED
}

data class StatusesUiState(
    val statuses: List<StatusStory> = emptyList(),
    val settings: StatusAutomationSettings = StatusAutomationSettings(),
    val selectedStatus: StatusStory? = null,
    val selectedSubTab: StatusSubTab = StatusSubTab.NEW,
    val isRefreshing: Boolean = false
) {
    val allCount: Int
        get() = statuses.size
    val newCount: Int
        get() = statuses.count { !it.isViewed && !it.isReacted }
    val viewedCount: Int
        get() = statuses.count { it.isViewed || it.isReacted }
    val totalViewedCount: Int
        get() = viewedCount
    val viewedUniquePersonsCount: Int
        get() = statuses.filter { it.isViewed || it.isReacted }.map { it.senderPhone }.distinct().size

    val displayedStatuses: List<StatusStory>
        get() = when (selectedSubTab) {
            StatusSubTab.NEW -> statuses.filter { !it.isViewed && !it.isReacted }
                .sortedByDescending { it.timestamp.toEpochMilli() }
            StatusSubTab.VIEWED -> statuses.filter { it.isViewed || it.isReacted }
                .sortedByDescending { it.viewedAt?.toEpochMilli() ?: it.timestamp.toEpochMilli() }
        }
}
@HiltViewModel
class StatusesViewModel @Inject constructor(
    private val statusRepository: StatusRepository,
    private val whatsAppEngine: com.whatsup.automation.data.engine.WhatsAppEngine,
    private val deviceContactsManager: com.whatsup.automation.data.local.contacts.DeviceContactsManager,
    ) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusesUiState())
    val uiState: StateFlow<StatusesUiState> = _uiState.asStateFlow()

    fun selectSubTab(tab: StatusSubTab) {
        _uiState.update { it.copy(selectedSubTab = tab) }
    }

    private val forceRefreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            try {
                statusRepository.deleteExpiredStatuses()
            } catch (_: Exception) {}
        }
        observeStatuses()
        observeSettings()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (true) {
                try {
                    statusRepository.deleteExpiredStatuses()
                } catch (_: Exception) {}
                kotlinx.coroutines.delay(30_000)
                forceRefreshTrigger.value = System.currentTimeMillis()
            }
        }
    }

    private fun observeStatuses() {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                statusRepository.getAllStatuses(),
                forceRefreshTrigger,
                deviceContactsManager.contactsUpdateTrigger
            ) { list, _, _ ->
                list.map { status ->
                    val cleanDigits = status.senderPhone.replace("[^0-9]".toRegex(), "")
                    val isLid = status.senderPhone.contains("lid") || cleanDigits.length > 13
                    val rawSender = if (status.senderName.isNotBlank() &&
                        status.senderName != "جهة اتصال غير مسجلة" &&
                        status.senderName != "حالة واتساب" &&
                        status.senderName.lowercase() != "windows" &&
                        !status.senderName.contains("@") &&
                        !status.senderName.contains("lid") &&
                        !status.senderName.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }
                    ) {
                        status.senderName
                    } else null

                    val resolved = deviceContactsManager.resolveContactNameWithPriority(status.senderPhone, rawSender)
                    val resolvedName = resolved?.name
                        ?: if (!isLid && cleanDigits.length in 7..15) {
                            "+$cleanDigits"
                        } else {
                            "جهة اتصال غير مسجلة"
                        }

                    status.copy(
                        senderName = resolvedName
                    )
                }
            }.collect { enriched ->
                _uiState.update { it.copy(statuses = enriched) }
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            statusRepository.getSettings().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun toggleAutoView(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(autoViewEnabled = enabled)
            statusRepository.updateSettings(updated)
        }
    }

    fun toggleAutoReact(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(autoReactEnabled = enabled)
            statusRepository.updateSettings(updated)
        }
    }

    fun setDefaultEmoji(emoji: String) {
        viewModelScope.launch {
            val updated = _uiState.value.settings.copy(defaultEmoji = emoji)
            statusRepository.updateSettings(updated)
        }
    }

    fun markStatusViewed(statusId: String) {
        viewModelScope.launch {
            val status = _uiState.value.statuses.find { it.id == statusId }
            val phone = status?.senderPhone ?: ""
            val participant = status?.participant
            whatsAppEngine.markStatusViewed(statusId, phone, participant)
            statusRepository.markAsViewed(statusId)
        }
    }

    fun reactToStatus(statusId: String, emoji: String) {
        viewModelScope.launch {
            val status = _uiState.value.statuses.find { it.id == statusId }
            val phone = status?.senderPhone ?: ""
            val participant = status?.participant
            whatsAppEngine.reactToStatus(statusId, emoji, phone, participant)
            statusRepository.updateReaction(statusId, emoji)
            statusRepository.markAsViewed(statusId)
        }
    }

    fun viewAndReact(statusId: String, emoji: String? = null) {
        viewModelScope.launch {
            val status = _uiState.value.statuses.find { it.id == statusId }
            val phone = status?.senderPhone ?: ""
            val participant = status?.participant
            val targetEmoji = emoji ?: _uiState.value.settings.defaultEmoji.ifBlank { "💚" }
            whatsAppEngine.markStatusViewed(statusId, phone, participant)
            whatsAppEngine.reactToStatus(statusId, targetEmoji, phone, participant)
            statusRepository.updateReaction(statusId, targetEmoji)
            statusRepository.markAsViewed(statusId)
        }
    }

    fun viewAllStatusesAndReact(emoji: String? = "💚") {
        viewModelScope.launch {
            val list = _uiState.value.statuses
            val targetEmoji = emoji ?: _uiState.value.settings.defaultEmoji.ifBlank { "💚" }
            for (status in list) {
                val phone = status.senderPhone
                val participant = status.participant
                whatsAppEngine.markStatusViewed(status.id, phone, participant)
                whatsAppEngine.reactToStatus(status.id, targetEmoji, phone, participant)
                statusRepository.updateReaction(status.id, targetEmoji)
                statusRepository.markAsViewed(status.id)
                kotlinx.coroutines.delay(350L) // تأخير بسيط لضمان الإرسال السليم عبر واتساب
            }
        }
    }

    fun selectStatus(status: StatusStory?) {
        _uiState.update { it.copy(selectedStatus = status) }
    }

    fun refreshStatuses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            deviceContactsManager.notifyContactsChanged()
            forceRefreshTrigger.value = System.currentTimeMillis()
            try {
                whatsAppEngine.fetchAllStatuses()
            } catch (_: Exception) {}
            delay(1200L)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * طلب جلب كل الحالات الموجودة (قديمة وجديدة) من واتساب الآن.
     * يُستدعى عند الضغط على زر "جلب الحالات" في الواجهة.
     */
    fun fetchLatestStatuses() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                whatsAppEngine.fetchAllStatuses()
            } catch (_: Exception) {}
            delay(6000L) // انتظر وصول الحالات
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}
