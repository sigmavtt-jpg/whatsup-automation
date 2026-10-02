package com.whatsup.automation.presentation.screens.chatshub

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatsup.automation.data.local.contacts.DeviceContactsManager
import com.whatsup.automation.domain.model.ActivityLog
import com.whatsup.automation.domain.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ChatsHubViewModel @Inject constructor(
    private val logRepository: LogRepository,
    private val deviceContactsManager: DeviceContactsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatsHubUiState(isLoading = true))
    val uiState: StateFlow<ChatsHubUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun selectTab(tab: ChatsHubTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            combine(
                logRepository.getAllLogs(),
                deviceContactsManager.contactsUpdateTrigger
            ) { logs, _ ->
                processHubData(logs)
            }.flowOn(Dispatchers.Default)
                .collect { stateUpdate ->
                    _uiState.update { current ->
                        current.copy(
                            unregisteredList = stateUpdate.unregisteredList,
                            registeredAllList = stateUpdate.registeredAllList,
                            registeredTodayList = stateUpdate.registeredTodayList,
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun processHubData(logs: List<ActivityLog>): ChatsHubUiState {
        val now = System.currentTimeMillis()
        val twentyFourHoursAgo = Instant.now().minus(Duration.ofHours(24))

        // 1. غير المسجلين (رسائل خلال آخر 24 ساعة بدون وجود بالأسماء)
        val recentLogs = logs.filter { it.timestamp.isAfter(twentyFourHoursAgo) }
        val unregisteredMap = mutableMapOf<String, ActivityLog>()

        for (log in recentLogs) {
            val phone = log.senderPhone.trim()
            if (phone.isBlank() || phone.equals("null", ignoreCase = true) || phone.contains("@") || phone.contains("newsletter") || phone.contains("broadcast")) {
                continue
            }
            // استبعاد سجلات الحالات وتفاعلاتها ومشاهداتها
            if (log.messageText.contains("حالة", ignoreCase = true) ||
                log.messageText.contains("تفاعل", ignoreCase = true) ||
                log.matchedRule?.contains("حالة", ignoreCase = true) == true ||
                log.actionExecuted.contains("حالة", ignoreCase = true)) {
                continue
            }
            val clean = phone.replace("[^0-9]".toRegex(), "")
            if (deviceContactsManager.isLid(phone) || clean.length < 7 || clean.length > 15) {
                continue
            }
            if (!unregisteredMap.containsKey(phone) && !unregisteredMap.containsKey(clean)) {
                val isRegistered = deviceContactsManager.isContactAlreadyRegistered(phone) ||
                        deviceContactsManager.isContactAlreadyRegistered(clean) ||
                        deviceContactsManager.getContactDisplayName(phone) != null ||
                        deviceContactsManager.getContactDisplayName(clean) != null
                if (!isRegistered) {
                    unregisteredMap[phone] = log
                }
            }
        }

        val unregisteredItems = unregisteredMap.values.map { log ->
            val clean = log.senderPhone.replace("[^0-9]".toRegex(), "")
            val logEpoch = log.timestamp.toEpochMilli()
            UnregisteredContactItem(
                phone = log.senderPhone,
                cleanPhone = clean,
                lastMessage = log.messageText,
                timestamp = logEpoch,
                timeAgoFormatted = formatTimeAgo(logEpoch, now)
            )
        }.sortedByDescending { it.timestamp }

        // 2. المسجلين فعلياً في دفتر الهاتف
        val deviceContacts = deviceContactsManager.getAllDeviceContacts()
        val registeredAllItems = deviceContacts.map {
            RegisteredContactItem(
                name = it.name,
                phone = it.phone,
                source = "دفتر أسماء الهاتف"
            )
        }.sortedBy { it.name }

        // 3. المسجلين اليوم (خلال 24 ساعة)
        val todaySavedLogs = recentLogs.filter {
            val phone = it.senderPhone.trim()
            phone.isNotBlank() && !phone.equals("null", ignoreCase = true) && !deviceContactsManager.isLid(phone) &&
            !it.messageText.contains("حالة", ignoreCase = true) &&
            !it.actionExecuted.contains("حالة", ignoreCase = true) &&
            (it.matchedRule?.contains("حفظ", ignoreCase = true) == true ||
             it.actionExecuted.contains("حفظ", ignoreCase = true) ||
             !it.extractedName.isNullOrBlank()) &&
            !it.actionExecuted.contains("تجاهل", ignoreCase = true)
        }

        val registeredTodayMap = mutableMapOf<String, ActivityLog>()
        for (log in todaySavedLogs) {
            val phone = log.senderPhone.trim()
            if (!registeredTodayMap.containsKey(phone)) {
                registeredTodayMap[phone] = log
            }
        }

        val registeredTodayItems = registeredTodayMap.values.map { log ->
            val clean = log.senderPhone.replace("[^0-9]".toRegex(), "")
            val resolvedName = log.extractedName
                ?: deviceContactsManager.getContactDisplayName(log.senderPhone)
                ?: "جهة اتصال جديدة"
            val logEpoch = log.timestamp.toEpochMilli()
            val diffMillis = now - logEpoch
            val hoursElapsed = TimeUnit.MILLISECONDS.toHours(diffMillis).toInt()
            val hoursLeft = maxOf(0, 24 - hoursElapsed)

            RegisteredTodayItem(
                name = resolvedName,
                phone = log.senderPhone,
                cleanPhone = clean,
                savedAt = logEpoch,
                timeAgoFormatted = formatTimeAgo(logEpoch, now),
                hoursRemaining = hoursLeft
            )
        }.sortedByDescending { it.savedAt }

        return ChatsHubUiState(
            unregisteredList = unregisteredItems,
            registeredAllList = registeredAllItems,
            registeredTodayList = registeredTodayItems
        )
    }

    private fun formatTimeAgo(timestamp: Long, now: Long): String {
        val diff = now - timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)

        return when {
            minutes < 1 -> "الآن"
            minutes < 60 -> "منذ $minutes دقيقة"
            hours < 24 -> "منذ $hours ساعة"
            else -> "منذ يوم"
        }
    }

    /**
     * فتح المحادثة مباشرة في تطبيق واتساب الرسمي أو أي عميل واتساب مثبت.
     */
    fun openWhatsAppChat(phoneNumber: String) {
        val cleanPhone = phoneNumber.replace("[^0-9]".toRegex(), "")
        if (cleanPhone.isBlank()) return

        try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage("com.whatsapp")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback لأي متصفح أو تطبيق يدعم الرابط
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }
}
