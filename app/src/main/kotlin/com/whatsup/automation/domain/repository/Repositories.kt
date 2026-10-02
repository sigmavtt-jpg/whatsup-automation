package com.whatsup.automation.domain.repository

import com.whatsup.automation.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * واجهة مستودع القواعد — تعرّف العمليات المطلوبة لإدارة القواعد.
 */
interface RuleRepository {
    fun getAllRules(): Flow<List<Rule>>
    fun getEnabledRules(): Flow<List<Rule>>
    suspend fun getRuleById(id: Long): Rule?
    suspend fun insertRule(rule: Rule): Long
    suspend fun updateRule(rule: Rule)
    suspend fun deleteRule(id: Long)
    suspend fun toggleRule(id: Long, enabled: Boolean)
}

/**
 * واجهة مستودع السجلات — توثيق كل عملية تمر بالمحرك.
 */
interface LogRepository {
    fun getAllLogs(): Flow<List<ActivityLog>>
    fun getRecentLogs(limit: Int): Flow<List<ActivityLog>>
    suspend fun insertLog(log: ActivityLog): Long
    suspend fun clearAllLogs()
    suspend fun getStats(): DashboardStats
    suspend fun pruneOldLogs(keepCount: Int = 1000): Int
}

/**
 * واجهة مستودع جهات الاتصال — إدارة جهات الاتصال المحفوظة آلياً.
 */

/**
 * واجهة مستودع الحالات — تخزين واستعراض حالات واتساب وإعدادات المشاهدة والتفاعل التلقائي.
 */
interface StatusRepository {
    fun getAllStatuses(): Flow<List<StatusStory>>
    suspend fun getStatusById(statusId: String): StatusStory?
    suspend fun insertStatus(status: StatusStory)
    suspend fun markAsViewed(statusId: String, timestamp: Long = System.currentTimeMillis())
    suspend fun markStatusViewed(statusId: String)
    suspend fun updateReaction(statusId: String, emoji: String, timestamp: Long = System.currentTimeMillis())
    suspend fun reactToStatus(statusId: String, emoji: String)
    suspend fun viewAllStatuses(emoji: String? = null)
    fun getSettings(): Flow<StatusAutomationSettings>
    suspend fun updateSettings(settings: StatusAutomationSettings)
    suspend fun deleteExpiredStatuses()
}

/**
 * واجهة مستودع المجموعات للبث المباشر والسجلات.
 */
interface GroupRepository {
    fun getAllGroups(): Flow<List<Group>>
    fun getGroupMembers(groupId: Long): Flow<List<GroupMember>>
    suspend fun createGroup(name: String, description: String, members: List<GroupMember>): Long
    suspend fun createPartitionedGroups(baseName: String, description: String, members: List<GroupMember>, partitionSize: Int): List<Long>
    suspend fun addMemberToGroup(groupId: Long, member: GroupMember): Long
    suspend fun removeMemberFromGroup(memberId: Long)
    suspend fun deleteGroup(groupId: Long)
    fun getLogsForGroup(groupId: Long): Flow<List<GroupMessageLog>>
    fun getInsiteLogsForGroup(groupId: Long): Flow<List<GroupMessageLog>>
    fun getCompletedLogsForGroup(groupId: Long): Flow<List<GroupMessageLog>>
    suspend fun sendBroadcastMessageToGroup(groupId: Long, messageText: String, onProgress: ((sent: Int, total: Int) -> Unit)? = null)
    fun isBroadcastPaused(groupId: Long): Flow<Boolean>
    fun pauseBroadcast(groupId: Long)
    fun resumeBroadcast(groupId: Long)
    fun cancelBroadcast(groupId: Long)
}

/**
 * واجهة مستودع إعدادات تنسيق وتوسيم جهات الاتصال عند الحفظ.
 */
interface ContactFormattingRepository {
    fun getSettings(): Flow<ContactFormattingSettings>
    suspend fun updateSettings(settings: ContactFormattingSettings)
}

/**
 * واجهة مستودع درع الحماية ومحاكي السلوك البشري لمنع الحظر.
 */
interface AntiBanRepository {
    fun getSettings(): Flow<AntiBanSettings>
    suspend fun updateSettings(settings: AntiBanSettings)
}
