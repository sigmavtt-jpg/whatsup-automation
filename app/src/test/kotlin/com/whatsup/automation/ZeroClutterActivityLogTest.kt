package com.whatsup.automation

import com.whatsup.automation.data.local.contacts.DeviceContactsManager
import com.whatsup.automation.domain.model.*
import com.whatsup.automation.domain.repository.LogRepository
import com.whatsup.automation.domain.repository.RuleRepository
import com.whatsup.automation.domain.usecase.ActionResult
import com.whatsup.automation.domain.usecase.MatchRuleUseCase
import com.whatsup.automation.domain.usecase.ProcessIncomingMessageUseCase
import com.whatsup.automation.domain.usecase.ProcessResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ZeroClutterActivityLogTest {

    private lateinit var fakeDeviceContactsManager: ZeroClutterFakeContactsManager
    private lateinit var fakeRuleRepository: ZeroClutterFakeRuleRepository
    private lateinit var fakeLogRepository: ZeroClutterFakeLogRepository
    private lateinit var matchRuleUseCase: MatchRuleUseCase
    private lateinit var useCase: ProcessIncomingMessageUseCase

    @Before
    fun setup() {
        fakeDeviceContactsManager = ZeroClutterFakeContactsManager()
        fakeRuleRepository = ZeroClutterFakeRuleRepository()
        fakeLogRepository = ZeroClutterFakeLogRepository()
        matchRuleUseCase = MatchRuleUseCase(fakeRuleRepository)
        useCase = ProcessIncomingMessageUseCase(
            ruleRepository = fakeRuleRepository,
            matchRuleUseCase = matchRuleUseCase,
            logRepository = fakeLogRepository,
            deviceContactsManager = fakeDeviceContactsManager
        )
    }

    @Test
    fun testUnmatchedIncomingMessageGeneratesZeroLogs() = runBlocking {
        // Given no rules match
        val message = IncomingMessage(
            id = "unmatched_1",
            senderPhone = "+967771234567",
            text = "مرحباً، هل يمكنني الاستفسار عن خدمة معينة غير معرفة؟"
        )

        // When
        val result = useCase.execute(message)

        // Then: ProcessResult.NoMatch and exactly 0 activity logs written
        assertEquals(ProcessResult.NoMatch, result)
        assertEquals(0, fakeLogRepository.logs.size)
    }

    @Test
    fun testIgnoredGroupAndNewsletterMessagesGenerateZeroLogs() = runBlocking {
        val groupMsg = IncomingMessage(
            id = "grp_1",
            senderPhone = "123456789-group@g.us",
            text = "سلام عليكم",
            isGroupMessage = true
        )
        val newsletterMsg = IncomingMessage(
            id = "news_1",
            senderPhone = "12345@newsletter",
            text = "نشرة إخبارية"
        )

        val groupResult = useCase.execute(groupMsg)
        val newsResult = useCase.execute(newsletterMsg)

        assertTrue(groupResult is ProcessResult.Ignored)
        assertTrue(newsResult is ProcessResult.Ignored)
        assertEquals(0, fakeLogRepository.logs.size)
    }

    @Test
    fun testExecutedAutomatedReplyLogsCompleteMetadata() = runBlocking {
        fakeDeviceContactsManager.savedContacts["+967771234567"] = "أبو عمر اليافعي"

        val testRule = Rule(
            id = 10,
            name = "استفسار الأسعار",
            description = "الرد بأسعار الخدمات",
            patternType = PatternType.CONTAINS,
            patternValue = "الأسعار, السعر, كم يكلف",
            actions = listOf(RuleAction.SendReply("قائمة الأسعار: الخدمة أ = 100 ريال")),
            priority = 1,
            isEnabled = true
        )

        fakeRuleRepository.rulesList = listOf(testRule)

        val message = IncomingMessage(
            id = "price_msg_1",
            senderPhone = "+967771234567",
            text = "السلام عليكم، كم يكلف الاشتراك؟",
            senderName = "أبو عمر"
        )

        val result = useCase.execute(message)

        assertTrue(result is ProcessResult.Executed)
        val executed = result as ProcessResult.Executed
        assertEquals("استفسار الأسعار", executed.ruleName)
        assertEquals(1, executed.actions.size)

        // Verify ActivityLog entry: all required fields verified
        assertEquals(1, fakeLogRepository.logs.size)
        val log = fakeLogRepository.logs.first()
        assertEquals("+967771234567", log.senderPhone)
        assertEquals("السلام عليكم، كم يكلف الاشتراك؟", log.messageText)
        assertEquals("استفسار الأسعار (الأسعار, السعر, كم يكلف)", log.matchedRule)
        assertEquals("إرسال رد (\"قائمة الأسعار: الخدمة أ = 100 ريال\")", log.actionExecuted)
        assertEquals(LogStatus.SUCCESS, log.status)
        assertEquals("أبو عمر اليافعي", log.extractedName)
    }

    @Test
    fun testRulePrioritySelectionAndSingleLogCreation() = runBlocking {
        val highPriorityRule = Rule(
            id = 1,
            name = "ترحيب VIP",
            patternType = PatternType.CONTAINS,
            patternValue = "مرحبا",
            actions = listOf(RuleAction.SendReply("أهلاً بعميلنا المميز!")),
            priority = 1,
            isEnabled = true
        )
        val lowPriorityRule = Rule(
            id = 2,
            name = "ترحيب عام",
            patternType = PatternType.CONTAINS,
            patternValue = "مرحبا",
            actions = listOf(RuleAction.SendReply("أهلاً بك")),
            priority = 10,
            isEnabled = true
        )

        fakeRuleRepository.rulesList = listOf(lowPriorityRule, highPriorityRule)

        val message = IncomingMessage(
            id = "msg_vip",
            senderPhone = "+967770000000",
            text = "مرحبا بكم"
        )

        val result = useCase.execute(message)

        assertTrue(result is ProcessResult.Executed)
        assertEquals("ترحيب VIP", (result as ProcessResult.Executed).ruleName)
        assertEquals(1, fakeLogRepository.logs.size)
        assertEquals("ترحيب VIP (مرحبا)", fakeLogRepository.logs.first().matchedRule)
    }
}

class ZeroClutterFakeContactsManager : DeviceContactsManager() {
    val savedContacts = mutableMapOf<String, String>()
    override fun hasWritePermission(): Boolean = true
    override fun hasReadPermission(): Boolean = true
    override fun getContactDisplayName(phoneNumber: String, pushName: String?): String? {
        val clean = phoneNumber.replace("[^0-9]".toRegex(), "")
        return savedContacts[phoneNumber] ?: savedContacts[clean] ?: savedContacts["+$clean"]
    }
    override fun saveContactToDevice(name: String, phoneNumber: String): Boolean {
        if (isLid(phoneNumber)) return false
        val formatted = normalizePhoneNumber(phoneNumber)
        if (savedContacts.containsKey(formatted)) return false
        savedContacts[formatted] = name
        return true
    }
    override fun saveOrUpdateContact(name: String, phoneNumber: String): Boolean {
        if (isLid(phoneNumber)) return false
        val formatted = normalizePhoneNumber(phoneNumber)
        savedContacts[formatted] = name
        return true
    }
}

class ZeroClutterFakeRuleRepository : RuleRepository {
    var rulesList = listOf<Rule>()
    override fun getAllRules(): Flow<List<Rule>> = flowOf(rulesList)
    override fun getEnabledRules(): Flow<List<Rule>> = flowOf(rulesList.filter { it.isEnabled })
    override suspend fun getRuleById(id: Long): Rule? = rulesList.find { it.id == id }
    override suspend fun insertRule(rule: Rule): Long = 1L
    override suspend fun updateRule(rule: Rule) {}
    override suspend fun deleteRule(id: Long) {}
    override suspend fun toggleRule(id: Long, enabled: Boolean) {}
}

class ZeroClutterFakeLogRepository : LogRepository {
    val logs = mutableListOf<ActivityLog>()
    override fun getAllLogs(): Flow<List<ActivityLog>> = flowOf(logs)
    override fun getRecentLogs(limit: Int): Flow<List<ActivityLog>> = flowOf(logs)
    override suspend fun insertLog(log: ActivityLog): Long {
        logs.add(log)
        return logs.size.toLong()
    }
    override suspend fun clearAllLogs() { logs.clear() }
    override suspend fun getStats(): DashboardStats = DashboardStats()
}
