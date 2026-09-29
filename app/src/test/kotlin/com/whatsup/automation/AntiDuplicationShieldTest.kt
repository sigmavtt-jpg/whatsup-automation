package com.whatsup.automation

import com.whatsup.automation.data.local.contacts.DeviceContactsManager
import com.whatsup.automation.domain.model.*
import com.whatsup.automation.domain.repository.LogRepository
import com.whatsup.automation.domain.repository.RuleRepository
import com.whatsup.automation.domain.usecase.MatchRuleUseCase
import com.whatsup.automation.domain.usecase.ProcessIncomingMessageUseCase
import com.whatsup.automation.domain.usecase.ProcessResult
import com.whatsup.automation.domain.usecase.ActionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AntiDuplicationShieldTest {

    private lateinit var fakeDeviceContactsManager: FakeDeviceContactsManager
    private lateinit var fakeRuleRepository: FakeRuleRepository
    private lateinit var fakeLogRepository: FakeLogRepository
    private lateinit var matchRuleUseCase: MatchRuleUseCase
    private lateinit var useCase: ProcessIncomingMessageUseCase

    @Before
    fun setup() {
        fakeDeviceContactsManager = FakeDeviceContactsManager()
        fakeRuleRepository = FakeRuleRepository()
        fakeLogRepository = FakeLogRepository()
        matchRuleUseCase = MatchRuleUseCase(fakeRuleRepository)
        useCase = ProcessIncomingMessageUseCase(
            ruleRepository = fakeRuleRepository,
            matchRuleUseCase = matchRuleUseCase,
            logRepository = fakeLogRepository,
            deviceContactsManager = fakeDeviceContactsManager
        )
    }

    @Test
    fun testLidDetection() {
        val manager = DeviceContactsManager()
        assertTrue(manager.isLid("97517970702387@lid"))
        assertTrue(manager.isLid("+97517970702387"))
        assertTrue(manager.isLid("97517970702387"))
        assertFalse(manager.isLid("+967771234567"))
        assertFalse(manager.isLid("771234567"))
        assertFalse(manager.isLid("+14155552671"))
    }

    @Test
    fun testFirstTimeRegistrationSavesContact() = runBlocking {
        val message = IncomingMessage(
            id = "msg1",
            senderPhone = "+967771234567",
            text = "سجلني أحمد علي"
        )

        val result = useCase.execute(message)

        assertTrue(result is ProcessResult.Executed)
        val executed = result as ProcessResult.Executed
        assertEquals("التسجيل التلقائي الذكي", executed.ruleName)
        assertEquals("تم حفظك باسم أحمد علي بنجاح ✅", (executed.actions.first() as ActionResult.ReplySent).message)
        assertEquals("أحمد علي", fakeDeviceContactsManager.getContactDisplayName("+967771234567"))
    }

    @Test
    fun testDuplicateRegistrationWithSameNameAvoidsDuplicateAndConfirms() = runBlocking {
        fakeDeviceContactsManager.savedContacts["+967771234567"] = "أحمد علي"

        val message = IncomingMessage(
            id = "msg2",
            senderPhone = "+967771234567",
            text = "سجلني أحمد علي"
        )

        val result = useCase.execute(message)

        assertTrue(result is ProcessResult.Executed)
        val executed = result as ProcessResult.Executed
        assertEquals("درع منع التكرار", executed.ruleName)
        assertEquals("أنت مسجل لدينا بالفعل باسم أحمد علي ✅", (executed.actions.first() as ActionResult.ReplySent).message)
    }

    @Test
    fun testUpdateNameFromSameAccountUpdatesExistingContact() = runBlocking {
        // شخص مسجل مسبقاً باسم أحمد
        fakeDeviceContactsManager.savedContacts["+967771234567"] = "أحمد"

        // يرسل طلباً جديداً لتغيير اسمه إلى محمد
        val message = IncomingMessage(
            id = "msg3",
            senderPhone = "+967771234567",
            text = "سجلني محمد اليافعي"
        )

        val result = useCase.execute(message)

        assertTrue(result is ProcessResult.Executed)
        val executed = result as ProcessResult.Executed
        assertEquals("درع منع التكرار والتحديث الذكي", executed.ruleName)
        assertEquals("تم تحديث اسمك إلى محمد اليافعي بنجاح ✅", (executed.actions.first() as ActionResult.ReplySent).message)
        // تم تحديث الاسم في السجل الفعلي دون إنشاء جهة مكررة
        assertEquals("محمد اليافعي", fakeDeviceContactsManager.getContactDisplayName("+967771234567"))
    }

    @Test
    fun testRawLidWithoutRealPhoneRefusesDirectSaving() = runBlocking {
        val message = IncomingMessage(
            id = "msg4",
            senderPhone = "97517970702387",
            text = "سجلني طارق",
            isLid = true,
            realPhone = null
        )

        val result = useCase.execute(message)

        assertTrue(result is ProcessResult.Executed)
        val executed = result as ProcessResult.Executed
        assertEquals("درع منع التكرار — حماية LID", executed.ruleName)
        assertNull(fakeDeviceContactsManager.getContactDisplayName("97517970702387"))
    }

    @Test
    fun testMultiWordArabicNameRegistration() = runBlocking {
        val message = IncomingMessage(
            id = "msg6",
            senderPhone = "+967771234567",
            text = "سجلني باسم أحمد محمد صالح اليافعي"
        )

        val result = useCase.execute(message)

        assertTrue(result is ProcessResult.Executed)
        val executed = result as ProcessResult.Executed
        assertEquals("التسجيل التلقائي الذكي", executed.ruleName)
        assertEquals("أحمد محمد صالح اليافعي", fakeDeviceContactsManager.getContactDisplayName("+967771234567"))
    }

    @Test
    fun testLidWithResolvedRealPhoneSavesToRealPhone() = runBlocking {
        val message = IncomingMessage(
            id = "msg5",
            senderPhone = "+967779876543",
            text = "سجلني باسم خالد",
            isLid = true,
            realPhone = "+967779876543",
            lid = "97517970702387"
        )

        val result = useCase.execute(message)

        assertTrue(result is ProcessResult.Executed)
        val executed = result as ProcessResult.Executed
        assertEquals("التسجيل التلقائي الذكي", executed.ruleName)
        assertEquals("خالد", fakeDeviceContactsManager.getContactDisplayName("+967779876543"))
    }

    @Test
    fun testExpandedArabicRegistrationPhrases() = runBlocking {
        val phrases = listOf(
            "احفظني ياسر الشميري" to "ياسر الشميري",
            "سجل اسمي مهند" to "مهند",
            "احفظ رقمي مروان" to "مروان",
            "سجلني عندك باسم بلال" to "بلال",
            "اسمي هو وضاح" to "وضاح",
            "اسمي: عمار الحكيمي" to "عمار الحكيمي"
        )

        for ((idx, pair) in phrases.withIndex()) {
            val phone = "+96777112233$idx"
            val message = IncomingMessage(
                id = "phrase_msg_$idx",
                senderPhone = phone,
                text = pair.first
            )
            val result = useCase.execute(message)
            assertTrue(result is ProcessResult.Executed)
            val executed = result as ProcessResult.Executed
            assertEquals("التسجيل التلقائي الذكي", executed.ruleName)
            assertEquals(pair.second, fakeDeviceContactsManager.getContactDisplayName(phone))
        }
    }
}

class FakeDeviceContactsManager : DeviceContactsManager() {
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

class FakeRuleRepository : RuleRepository {
    override fun getAllRules(): Flow<List<Rule>> = flowOf(emptyList())
    override fun getEnabledRules(): Flow<List<Rule>> = flowOf(emptyList())
    override suspend fun getRuleById(id: Long): Rule? = null
    override suspend fun insertRule(rule: Rule): Long = 1L
    override suspend fun updateRule(rule: Rule) {}
    override suspend fun deleteRule(id: Long) {}
    override suspend fun toggleRule(id: Long, enabled: Boolean) {}
}

class FakeLogRepository : LogRepository {
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
