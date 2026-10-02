package com.whatsup.automation.data.repository

import android.content.Context
import com.whatsup.automation.domain.model.AppNotification
import com.whatsup.automation.domain.model.NotificationType
import com.whatsup.automation.domain.repository.NotificationRepository
import com.whatsup.automation.data.service.SystemNotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationRepository {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    override suspend fun postNotification(title: String, message: String, type: NotificationType) {
        val item = AppNotification(
            title = title,
            message = message,
            type = type,
            isRead = false
        )
        _notifications.update { current ->
            (listOf(item) + current).take(100)
        }
        _unreadCount.update { it + 1 }

        // إذا كان خطأ أو تحذير، إرسال إشعار في شريط النظام
        if (type == NotificationType.ERROR || type == NotificationType.WARNING) {
            SystemNotificationHelper.sendSystemNotification(
                context = context,
                title = title,
                message = message,
                isError = type == NotificationType.ERROR
            )
        }
    }

    override suspend fun markAllAsRead() {
        _notifications.update { list ->
            list.map { it.copy(isRead = true) }
        }
        _unreadCount.value = 0
    }

    override suspend fun clearAll() {
        _notifications.value = emptyList()
        _unreadCount.value = 0
    }
}
