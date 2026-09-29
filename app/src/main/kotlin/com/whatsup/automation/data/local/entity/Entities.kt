package com.whatsup.automation.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * كيان قاعدة الأتمتة — مخزّن في Room.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String,
    val patternType: String, // CONTAINS, STARTS_WITH, EXACT_MATCH, REGEX
    val patternValue: String,
    val actionsJson: String, // JSON-serialized list of actions
    val priority: Int,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * كيان سجل النشاط.
 */
@Entity(tableName = "activity_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val senderPhone: String,
    val messageText: String,
    val matchedRule: String? = null,
    val actionExecuted: String,
    val status: String, // SUCCESS, FAILURE, NO_MATCH
    val extractedName: String? = null
)

/**
 * كيان قصة الحالة (Status) في Room.
 */
@Entity(tableName = "statuses")
data class StatusEntity(
    @PrimaryKey
    val id: String,
    val senderPhone: String,
    val senderName: String,
    val mediaType: String, // TEXT, IMAGE, VIDEO
    val textContent: String? = null,
    val mediaUrl: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isViewed: Boolean = false,
    val viewedAt: Long? = null,
    val isReacted: Boolean = false,
    val reactionEmoji: String? = null,
    val reactionSentAt: Long? = null,
    val participant: String? = null
)

/**
 * كيان مجموعة البث.
 */
@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * كيان أعضاء المجموعة.
 */
@Entity(
    tableName = "group_members",
    indices = [Index(value = ["groupId"])]
)
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val contactName: String,
    val phone: String,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * كيان سجل رسائل المجموعة (البث).
 */
@Entity(
    tableName = "group_logs",
    indices = [Index(value = ["groupId"])]
)
data class GroupLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: Long,
    val messageText: String,
    val recipientPhone: String,
    val recipientName: String,
    val status: String, // IN_PROGRESS, COMPLETED, FAILED
    val sentAt: Long? = System.currentTimeMillis(),
    val errorMessage: String? = null
)
