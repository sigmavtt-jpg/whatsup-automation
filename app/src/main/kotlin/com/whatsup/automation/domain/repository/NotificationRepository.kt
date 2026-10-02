package com.whatsup.automation.domain.repository

import com.whatsup.automation.domain.model.AppNotification
import com.whatsup.automation.domain.model.NotificationType
import kotlinx.coroutines.flow.StateFlow

interface NotificationRepository {
    val notifications: StateFlow<List<AppNotification>>
    val unreadCount: StateFlow<Int>
    suspend fun postNotification(title: String, message: String, type: NotificationType)
    suspend fun markAllAsRead()
    suspend fun clearAll()
}
