package com.whatsup.automation.domain.usecase

import com.whatsup.automation.data.local.contacts.DeviceContactsManager
import com.whatsup.automation.domain.model.GroupMember
import com.whatsup.automation.domain.repository.GroupRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * حالة استخدام: تجزئة وإنشاء حملات البث الجماعي المقسمة تلقائياً.
 * تتولى تنظيف البيانات وتوحيد الصيغ والتجزئة لبطاقات متساوية.
 */
@Singleton
class CreatePartitionedCampaignUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val deviceContactsManager: DeviceContactsManager
) {
    suspend fun execute(
        baseName: String,
        description: String,
        members: List<GroupMember>,
        partitionSize: Int = 100,
        isPartitionMode: Boolean = true
    ): List<Long> {
        val trimmedName = baseName.trim()
        if (trimmedName.isBlank() || members.isEmpty()) return emptyList()

        // تنظيف وتوحيد أرقام الهواتف والتأكد من عدم وجود أرقام تالفة
        val sanitizedMembers = members.mapNotNull { member ->
            val cleanPhone = deviceContactsManager.normalizePhoneNumber(member.phone)
            val cleanName = if (member.contactName.isNotBlank()) member.contactName.trim() else "+$cleanPhone"
            if (cleanPhone.length >= 7) {
                member.copy(contactName = cleanName, phone = cleanPhone)
            } else null
        }.distinctBy { it.phone }

        if (sanitizedMembers.isEmpty()) return emptyList()

        return if (isPartitionMode && sanitizedMembers.size > partitionSize) {
            groupRepository.createPartitionedGroups(
                baseName = trimmedName,
                description = description.trim(),
                members = sanitizedMembers,
                partitionSize = partitionSize
            )
        } else {
            val singleGroupId = groupRepository.createGroup(
                name = trimmedName,
                description = description.trim(),
                members = sanitizedMembers
            )
            listOf(singleGroupId)
        }
    }
}

/**
 * حالة استخدام: إرسال بث حملة مع درع مكافحة الحظر وتتبع التقدم.
 */
@Singleton
class SendCampaignBroadcastUseCase @Inject constructor(
    private val groupRepository: GroupRepository
) {
    suspend fun execute(
        groupId: Long,
        messageText: String,
        onProgress: ((sent: Int, total: Int) -> Unit)? = null
    ) {
        val trimmedText = messageText.trim()
        if (trimmedText.isBlank()) return
        groupRepository.sendBroadcastMessageToGroup(
            groupId = groupId,
            messageText = trimmedText,
            onProgress = onProgress
        )
    }

    fun isPaused(groupId: Long): kotlinx.coroutines.flow.Flow<Boolean> = groupRepository.isBroadcastPaused(groupId)
    fun pause(groupId: Long) = groupRepository.pauseBroadcast(groupId)
    fun resume(groupId: Long) = groupRepository.resumeBroadcast(groupId)
    fun cancel(groupId: Long) = groupRepository.cancelBroadcast(groupId)

    /**
     * حساب الوقت التقديري بناءً على فواصل الأمان العشوائية وفترات الاستراحة.
     */
    fun calculateEstimatedTime(memberCount: Int): String {
        if (memberCount <= 0) return "0 ثانية"
        val avgSecondsPerMessage = 6.5
        val cooldownSeconds = 20.0
        val cooldownBatches = memberCount / 25
        val totalSeconds = (memberCount * avgSecondsPerMessage + cooldownBatches * cooldownSeconds).toLong()

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> "~$hours ساعة و $minutes دقيقة"
            minutes > 0 -> "~$minutes دقيقة و $seconds ثانية"
            else -> "~$seconds ثانية"
        }
    }
}

/**
 * حالة استخدام: إدارة أعضاء المجموعات (إضافة وحذف فردي).
 */
@Singleton
class ManageGroupMembersUseCase @Inject constructor(
    private val groupRepository: GroupRepository,
    private val deviceContactsManager: DeviceContactsManager
) {
    suspend fun addMember(groupId: Long, name: String, phone: String): Long? {
        val cleanPhone = deviceContactsManager.normalizePhoneNumber(phone)
        val cleanName = name.trim().ifBlank { "+$cleanPhone" }
        if (cleanPhone.length < 7) return null

        return groupRepository.addMemberToGroup(
            groupId = groupId,
            member = GroupMember(
                groupId = groupId,
                contactName = cleanName,
                phone = cleanPhone
            )
        )
    }

    suspend fun removeMember(memberId: Long) {
        groupRepository.removeMemberFromGroup(memberId)
    }
}
