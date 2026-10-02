package com.whatsup.automation.domain.model

import java.time.Instant

enum class NotificationType {
    ERROR,
    WARNING,
    SUCCESS,
    INFO
}

data class AppNotification(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestamp: Instant = Instant.now(),
    val isRead: Boolean = false
)
