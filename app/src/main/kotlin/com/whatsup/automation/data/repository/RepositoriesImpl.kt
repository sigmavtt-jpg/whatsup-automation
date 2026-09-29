package com.whatsup.automation.data.repository

import com.whatsup.automation.data.local.dao.LogDao
import com.whatsup.automation.data.local.dao.RuleDao
import com.whatsup.automation.data.local.entity.LogEntity
import com.whatsup.automation.data.local.entity.RuleEntity
import com.whatsup.automation.domain.model.*
import com.whatsup.automation.domain.repository.LogRepository
import com.whatsup.automation.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * تنفيذ مستودع القواعد باستخدام Room.
 */
@Singleton
class RuleRepositoryImpl @Inject constructor(
    private val ruleDao: RuleDao
) : RuleRepository {

    override fun getAllRules(): Flow<List<Rule>> {
        return ruleDao.getAllRules().map { list -> list.map { it.toDomain() } }
    }

    override fun getEnabledRules(): Flow<List<Rule>> {
        return ruleDao.getEnabledRules().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getRuleById(id: Long): Rule? {
        return ruleDao.getRuleById(id)?.toDomain()
    }

    override suspend fun insertRule(rule: Rule): Long {
        return ruleDao.insertRule(rule.toEntity())
    }

    override suspend fun updateRule(rule: Rule) {
        ruleDao.updateRule(rule.toEntity())
    }

    override suspend fun deleteRule(id: Long) {
        ruleDao.deleteRule(id)
    }

    override suspend fun toggleRule(id: Long, enabled: Boolean) {
        ruleDao.toggleRule(id, enabled)
    }
}

/**
 * تنفيذ مستودع السجلات والإحصائيات.
 */
@Singleton
class LogRepositoryImpl @Inject constructor(
    private val logDao: LogDao,
    private val syncedContactDao: com.whatsup.automation.data.local.entity.SyncedContactDao
) : LogRepository {

    override fun getAllLogs(): Flow<List<ActivityLog>> {
        return logDao.getAllLogs().map { list -> list.map { it.toDomain() } }
    }

    override fun getRecentLogs(limit: Int): Flow<List<ActivityLog>> {
        return logDao.getRecentLogs(limit).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun insertLog(log: ActivityLog): Long {
        return logDao.insertLog(log.toEntity())
    }

    override suspend fun clearAllLogs() {
        logDao.clearAllLogs()
    }

    override suspend fun getStats(): DashboardStats {
        val total = logDao.getTotalCount()
        val contacts = syncedContactDao.getCount()
        val replies = logDao.getRepliesSentCount()
        val ignored = logDao.getNoMatchCount()
        return DashboardStats(
            totalProcessed = total,
            totalSavedContacts = contacts,
            totalRepliesSent = replies,
            totalIgnored = ignored
        )
    }
}

/**
 * تنفيذ مستودع جهات الاتصال — يحفظ في قاعدة بيانات Room المحلية
 * ويقوم بالمزامنة الفورية مع دفتر جهات اتصال هاتف أندرويد الفعلي.
 */
/** */
@Singleton
class StatusRepositoryImpl @Inject constructor(
    private val statusDao: com.whatsup.automation.data.local.dao.StatusDao,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : com.whatsup.automation.domain.repository.StatusRepository {

    private companion object {
        const val PREFS_NAME = "whatsup_status_settings"
        const val KEY_AUTO_VIEW = "auto_view_enabled"
        const val KEY_AUTO_REACT = "auto_react_enabled"
        const val KEY_DEFAULT_EMOJI = "default_emoji"
        const val KEY_AUTO_DOWNLOAD = "auto_download_media"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val settingsFlow = kotlinx.coroutines.flow.MutableStateFlow(
        com.whatsup.automation.domain.model.StatusAutomationSettings(
            autoViewEnabled = prefs.getBoolean(KEY_AUTO_VIEW, true),
            autoReactEnabled = prefs.getBoolean(KEY_AUTO_REACT, true),
            defaultEmoji = prefs.getString(KEY_DEFAULT_EMOJI, "💚") ?: "💚",
            autoDownloadMedia = prefs.getBoolean(KEY_AUTO_DOWNLOAD, false)
        )
    )

    override fun getAllStatuses(): Flow<List<com.whatsup.automation.domain.model.StatusStory>> {
        return statusDao.getAllStatuses().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getStatusById(statusId: String): com.whatsup.automation.domain.model.StatusStory? {
        return statusDao.getStatusById(statusId)?.toDomain()
    }

    override suspend fun insertStatus(status: com.whatsup.automation.domain.model.StatusStory) {
        statusDao.insertStatus(status.toEntity())
    }

    override suspend fun markAsViewed(statusId: String, timestamp: Long) {
        statusDao.markAsViewed(statusId, timestamp)
    }

    override suspend fun markStatusViewed(statusId: String) {
        statusDao.markAsViewed(statusId, System.currentTimeMillis())
    }

    override suspend fun updateReaction(statusId: String, emoji: String, timestamp: Long) {
        statusDao.updateReaction(statusId, emoji, timestamp)
    }

    override suspend fun reactToStatus(statusId: String, emoji: String) {
        statusDao.updateReaction(statusId, emoji, System.currentTimeMillis())
    }

    override suspend fun viewAllStatuses(emoji: String?) {
        val statuses = statusDao.getAllStatuses().first()
        val now = System.currentTimeMillis()
        for (status in statuses) {
            if (!status.isViewed) {
                statusDao.markAsViewed(status.id, now)
            }
            if (emoji != null && (!status.isReacted || status.reactionEmoji != emoji)) {
                statusDao.updateReaction(status.id, emoji, now)
            }
        }
    }

    override fun getSettings(): Flow<com.whatsup.automation.domain.model.StatusAutomationSettings> {
        return settingsFlow
    }

    override suspend fun updateSettings(settings: com.whatsup.automation.domain.model.StatusAutomationSettings) {
        prefs.edit()
            .putBoolean(KEY_AUTO_VIEW, settings.autoViewEnabled)
            .putBoolean(KEY_AUTO_REACT, settings.autoReactEnabled)
            .putString(KEY_DEFAULT_EMOJI, settings.defaultEmoji)
            .putBoolean(KEY_AUTO_DOWNLOAD, settings.autoDownloadMedia)
            .apply()
        settingsFlow.value = settings
    }

    override suspend fun deleteExpiredStatuses() {
        // حذف الحالات التي مضى عليها أكثر من 24 ساعة (مدة صلاحية حالة واتساب)
        val expiryTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        statusDao.deleteExpiredStatuses(expiryTime)
    }
}

// ----------------- دوال التحويل المساعدة (Mappers) -----------------


internal fun RuleEntity.toDomain(): Rule {
    val actionsList = mutableListOf<RuleAction>()
    try {
        val array = JSONArray(actionsJson)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            when (obj.optString("type")) {
                "SEND_REPLY" -> actionsList.add(
                    RuleAction.SendReply(obj.optString("message"))
                )
                
            }
        }
    } catch (_: Exception) {}

    return Rule(
        id = id,
        name = name,
        description = description,
        patternType = try { PatternType.valueOf(patternType) } catch (_: Exception) { PatternType.CONTAINS },
        patternValue = patternValue,
        actions = actionsList,
        priority = priority,
        isEnabled = isEnabled,
        createdAt = Instant.ofEpochMilli(createdAt)
    )
}

internal fun Rule.toEntity(): RuleEntity {
    val array = JSONArray()
    for (action in actions) {
        val obj = JSONObject()
        when (action) {
            is RuleAction.SendReply -> {
                obj.put("type", "SEND_REPLY")
                obj.put("message", action.message)
            }
            
        }
        array.put(obj)
    }

    return RuleEntity(
        id = id,
        name = name,
        description = description,
        patternType = patternType.name,
        patternValue = patternValue,
        actionsJson = array.toString(),
        priority = priority,
        isEnabled = isEnabled,
        createdAt = createdAt.toEpochMilli()
    )
}

internal fun LogEntity.toDomain(): ActivityLog {
    return ActivityLog(
        id = id,
        timestamp = Instant.ofEpochMilli(timestamp),
        senderPhone = senderPhone,
        messageText = messageText,
        matchedRule = matchedRule,
        actionExecuted = actionExecuted,
        status = try { LogStatus.valueOf(status) } catch (_: Exception) { LogStatus.NO_MATCH },
        extractedName = extractedName
    )
}

internal fun ActivityLog.toEntity(): LogEntity {
    return LogEntity(
        id = id,
        timestamp = timestamp.toEpochMilli(),
        senderPhone = senderPhone,
        messageText = messageText,
        matchedRule = matchedRule,
        actionExecuted = actionExecuted,
        status = status.name,
        extractedName = extractedName
    )
}

internal fun com.whatsup.automation.data.local.entity.StatusEntity.toDomain(): com.whatsup.automation.domain.model.StatusStory {
    return com.whatsup.automation.domain.model.StatusStory(
        id = id,
        senderPhone = senderPhone,
        senderName = senderName,
        mediaType = try {
            com.whatsup.automation.domain.model.StatusMediaType.valueOf(mediaType)
        } catch (_: Exception) {
            com.whatsup.automation.domain.model.StatusMediaType.TEXT
        },
        textContent = textContent,
        mediaUrl = mediaUrl,
        timestamp = Instant.ofEpochMilli(timestamp),
        isViewed = isViewed,
        viewedAt = viewedAt?.let { Instant.ofEpochMilli(it) },
        isReacted = isReacted,
        reactionEmoji = reactionEmoji,
        reactionSentAt = reactionSentAt?.let { Instant.ofEpochMilli(it) },
        participant = participant
    )
}

internal fun com.whatsup.automation.domain.model.StatusStory.toEntity(): com.whatsup.automation.data.local.entity.StatusEntity {
    return com.whatsup.automation.data.local.entity.StatusEntity(
        id = id,
        senderPhone = senderPhone,
        senderName = senderName,
        mediaType = mediaType.name,
        textContent = textContent,
        mediaUrl = mediaUrl,
        timestamp = timestamp.toEpochMilli(),
        isViewed = isViewed,
        viewedAt = viewedAt?.toEpochMilli(),
        isReacted = isReacted,
        reactionEmoji = reactionEmoji,
        reactionSentAt = reactionSentAt?.toEpochMilli(),
        participant = participant
    )
}

/**
 * تنفيذ مستودع المجموعات.
 */
@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val groupDao: com.whatsup.automation.data.local.dao.GroupDao,
    private val groupMemberDao: com.whatsup.automation.data.local.dao.GroupMemberDao,
    private val groupLogDao: com.whatsup.automation.data.local.dao.GroupLogDao,
    private val whatsAppEngine: com.whatsup.automation.data.engine.WhatsAppEngine,
    private val logRepository: LogRepository
) : com.whatsup.automation.domain.repository.GroupRepository {

    override fun getAllGroups(): Flow<List<Group>> {
        return groupDao.getAllGroupsWithMemberCount().map { list ->
            list.map { item ->
                Group(
                    id = item.id,
                    name = item.name,
                    description = item.description,
                    createdAt = Instant.ofEpochMilli(item.createdAt),
                    memberCount = item.memberCount
                )
            }
        }
    }

    override fun getGroupMembers(groupId: Long): Flow<List<GroupMember>> {
        return groupMemberDao.getMembersForGroup(groupId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun createGroup(name: String, description: String, members: List<GroupMember>): Long {
        val groupId = groupDao.insertGroup(
            com.whatsup.automation.data.local.entity.GroupEntity(
                name = name,
                description = description,
                createdAt = System.currentTimeMillis()
            )
        )
        val memberEntities = members.map { member ->
            com.whatsup.automation.data.local.entity.GroupMemberEntity(
                groupId = groupId,
                contactName = member.contactName,
                phone = member.phone,
                addedAt = System.currentTimeMillis()
            )
        }
        groupMemberDao.insertMembers(memberEntities)
        return groupId
    }

    override suspend fun deleteGroup(groupId: Long) {
        groupDao.deleteGroup(groupId)
        groupMemberDao.deleteMembersForGroup(groupId)
    }

    override fun getLogsForGroup(groupId: Long): Flow<List<GroupMessageLog>> {
        return groupLogDao.getLogsForGroup(groupId).map { list -> list.map { it.toDomain() } }
    }

    override fun getInsiteLogsForGroup(groupId: Long): Flow<List<GroupMessageLog>> {
        return groupLogDao.getInsiteLogsForGroup(groupId).map { list -> list.map { it.toDomain() } }
    }

    override fun getCompletedLogsForGroup(groupId: Long): Flow<List<GroupMessageLog>> {
        return groupLogDao.getCompletedLogsForGroup(groupId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun sendBroadcastMessageToGroup(groupId: Long, messageText: String) {
        val members = groupMemberDao.getMembersForGroupSync(groupId)
        val group = groupDao.getGroupById(groupId) ?: return

        for (member in members) {
            val logId = groupLogDao.insertLog(
                com.whatsup.automation.data.local.entity.GroupLogEntity(
                    groupId = groupId,
                    messageText = messageText,
                    recipientPhone = member.phone,
                    recipientName = member.contactName,
                    status = GroupLogStatus.IN_PROGRESS.name,
                    sentAt = System.currentTimeMillis()
                )
            )

            try {
                val sent = whatsAppEngine.sendMessage(member.phone, messageText)
                if (sent) {
                    groupLogDao.updateLogStatus(
                        id = logId,
                        status = GroupLogStatus.COMPLETED.name,
                        sentAt = System.currentTimeMillis(),
                        errorMessage = null
                    )
                    logRepository.insertLog(
                        ActivityLog(
                            senderPhone = member.phone,
                            messageText = messageText,
                            matchedRule = "رسالة بث للمجموعة (${group.name})",
                            actionExecuted = "تم الإرسال لـ ${member.contactName}",
                            status = LogStatus.SUCCESS
                        )
                    )
                } else {
                    groupLogDao.updateLogStatus(
                        id = logId,
                        status = GroupLogStatus.COMPLETED.name,
                        sentAt = System.currentTimeMillis(),
                        errorMessage = "تمت الجدولة/محاولة الإرسال في الخلفية"
                    )
                    logRepository.insertLog(
                        ActivityLog(
                            senderPhone = member.phone,
                            messageText = messageText,
                            matchedRule = "رسالة بث للمجموعة (${group.name})",
                            actionExecuted = "تمت الجدولة لـ ${member.contactName}",
                            status = LogStatus.SUCCESS
                        )
                    )
                }
            } catch (e: Exception) {
                groupLogDao.updateLogStatus(
                    id = logId,
                    status = GroupLogStatus.FAILED.name,
                    sentAt = System.currentTimeMillis(),
                    errorMessage = e.message
                )
                logRepository.insertLog(
                    ActivityLog(
                        senderPhone = member.phone,
                        messageText = messageText,
                        matchedRule = "رسالة بث للمجموعة (${group.name})",
                        actionExecuted = "فشل الإرسال لـ ${member.contactName}: ${e.message}",
                        status = LogStatus.FAILURE
                    )
                )
            }
        }
    }
}

internal fun com.whatsup.automation.data.local.entity.GroupEntity.toDomain(memberCount: Int): Group {
    return Group(
        id = id,
        name = name,
        description = description,
        createdAt = Instant.ofEpochMilli(createdAt),
        memberCount = memberCount
    )
}

internal fun com.whatsup.automation.data.local.entity.GroupMemberEntity.toDomain(): GroupMember {
    return GroupMember(
        id = id,
        groupId = groupId,
        contactName = contactName,
        phone = phone,
        addedAt = Instant.ofEpochMilli(addedAt)
    )
}

internal fun com.whatsup.automation.data.local.entity.GroupLogEntity.toDomain(): GroupMessageLog {
    return GroupMessageLog(
        id = id,
        groupId = groupId,
        messageText = messageText,
        recipientPhone = recipientPhone,
        recipientName = recipientName,
        status = try { GroupLogStatus.valueOf(status) } catch (_: Exception) { GroupLogStatus.IN_PROGRESS },
        sentAt = sentAt?.let { Instant.ofEpochMilli(it) },
        errorMessage = errorMessage
    )
}


