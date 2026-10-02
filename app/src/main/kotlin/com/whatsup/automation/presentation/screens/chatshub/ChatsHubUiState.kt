package com.whatsup.automation.presentation.screens.chatshub

/**
 * تبويبات مركز المحادثات والتسجيل.
 */
enum class ChatsHubTab(val title: String) {
    UNREGISTERED("غير المسجلين"),
    REGISTERED_ALL("المسجلين فعلياً"),
    REGISTERED_TODAY("المسجلين اليوم (24h)")
}

/**
 * عنصر جهة اتصال غير مسجلة (وصلت رسالة خلال 24 ساعة ولم تسجل في جهات الاتصال).
 */
data class UnregisteredContactItem(
    val phone: String,
    val cleanPhone: String,
    val lastMessage: String,
    val timestamp: Long,
    val timeAgoFormatted: String
)

/**
 * عنصر جهة اتصال مسجلة فعلياً في دفتر الهاتف / النظام.
 */
data class RegisteredContactItem(
    val name: String,
    val phone: String,
    val source: String = "دفتر الهاتف"
)

/**
 * عنصر جهة اتصال تم حفظها اليوم (خلال نافذة 24 ساعة).
 */
data class RegisteredTodayItem(
    val name: String,
    val phone: String,
    val cleanPhone: String,
    val savedAt: Long,
    val timeAgoFormatted: String,
    val hoursRemaining: Int
)

/**
 * حالة واجهة مستخدم مركز المحادثات والتسجيل.
 */
data class ChatsHubUiState(
    val selectedTab: ChatsHubTab = ChatsHubTab.UNREGISTERED,
    val unregisteredList: List<UnregisteredContactItem> = emptyList(),
    val registeredAllList: List<RegisteredContactItem> = emptyList(),
    val registeredTodayList: List<RegisteredTodayItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
) {
    val unregisteredCount: Int get() = unregisteredList.size
    val registeredAllCount: Int get() = registeredAllList.size
    val registeredTodayCount: Int get() = registeredTodayList.size
}
