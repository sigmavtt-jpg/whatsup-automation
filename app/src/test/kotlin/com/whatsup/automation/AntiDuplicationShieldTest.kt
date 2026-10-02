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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var fakeContactFormattingRepo: com.whatsup.automation.domain.repository.ContactFormattingRepository
    private lateinit var fakeNotificationRepo: com.whatsup.automation.domain.repository.NotificationRepository
    private lateinit var mockEngine: com.whatsup.automation.data.engine.WhatsAppEngine
    private lateinit var memoryManager: com.whatsup.automation.domain.util.ConversationMemoryManager

    @Before
    fun setup() {
        fakeDeviceContactsManager = FakeDeviceContactsManager()
        fakeRuleRepository = FakeRuleRepository()
        fakeLogRepository = FakeLogRepository()
        matchRuleUseCase = MatchRuleUseCase(fakeRuleRepository)
        fakeContactFormattingRepo = object : com.whatsup.automation.domain.repository.ContactFormattingRepository {
            override fun getSettings(): Flow<ContactFormattingSettings> = flowOf(ContactFormattingSettings())
            override suspend fun updateSettings(settings: ContactFormattingSettings) {}
        }
        fakeNotificationRepo = object : com.whatsup.automation.domain.repository.NotificationRepository {
            override val notifications: StateFlow<List<AppNotification>> = MutableStateFlow(emptyList())
            override val unreadCount: StateFlow<Int> = MutableStateFlow(0)
            override suspend fun postNotification(title: String, message: String, type: NotificationType) {}
            override suspend fun markAllAsRead() {}
            override suspend fun clearAll() {}
        }
        mockEngine = org.mockito.kotlin.mock()
        org.mockito.kotlin.whenever(mockEngine.isAutomationPaused).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(false))
        memoryManager = com.whatsup.automation.domain.util.ConversationMemoryManager()

        useCase = ProcessIncomingMessageUseCase(
            ruleRepository = fakeRuleRepository,
            matchRuleUseCase = matchRuleUseCase,
            logRepository = fakeLogRepository,
            deviceContactsManager = fakeDeviceContactsManager,
            contactFormattingRepository = fakeContactFormattingRepo,
            whatsAppEngine = mockEngine,
            notificationRepository = fakeNotificationRepo,
            conversationMemoryManager = memoryManager
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

        assertTrue(result is ProcessResult.Ignored)
        val ignored = result as ProcessResult.Ignored
        assertTrue(ignored.reason.contains("مسجلة مسبقاً") || ignored.reason.contains("تجاهل صامت"))
        assertEquals("أحمد علي", fakeDeviceContactsManager.getContactDisplayName("+967771234567"))
    }

    @Test
    fun testUpdateNameFromSameAccountUpdatesExistingContact() = runBlocking {
        // شخص مسجل مسبقاً باسم أحمد
        fakeDeviceContactsManager.savedContacts["+967771234567"] = "أحمد"

        // يرسل طلباً جديداً لتغيير اسمه إلى محمد -> تجاهل صامت وعدم تعديل الاسم
        val message = IncomingMessage(
            id = "msg3",
            senderPhone = "+967771234567",
            text = "سجلني محمد اليافعي"
        )

        val result = useCase.execute(message)

        assertTrue(result is ProcessResult.Ignored)
        val ignored = result as ProcessResult.Ignored
        assertTrue(ignored.reason.contains("مسجلة مسبقاً") || ignored.reason.contains("تجاهل صامت"))
        // تم الاحتفاظ بالاسم الأصلي في السجل الفعلي دون أي تعديل
        assertEquals("أحمد", fakeDeviceContactsManager.getContactDisplayName("+967771234567"))
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

    @Test
    fun testRealWorldScenariosFromDeviceLogs() = runBlocking {
        // 1. قضية راكان: مسجل مسبقاً باسم راكان ويرسل سوالف عامة
        val rakanPhone = "+967770001122"
        fakeDeviceContactsManager.savedContacts[rakanPhone] = "راكان"

        val chatMessage = IncomingMessage(
            id = "rakan_msg_1",
            senderPhone = rakanPhone,
            text = "شروف واللي يدخلو بارقام غريبه ويسكتو مايقولو اسمهم"
        )
        val chatResult = useCase.execute(chatMessage)
        // لا يتم تعديل اسمه نهائياً ولا يتم حفظ جملة السوالف كاسم!
        assertEquals("راكان", fakeDeviceContactsManager.getContactDisplayName(rakanPhone))
        assertFalse("يجب ألا يتم تعديل الاسم", chatResult is ProcessResult.Executed && chatResult.ruleName.contains("تحديث"))

        // راكان يرسل تصحيحاً مع تطويل حروف "اقصدددد"
        val correctionMessage = IncomingMessage(
            id = "rakan_msg_2",
            senderPhone = rakanPhone,
            text = "مله سجلني قلت لك راكان اقصدددد"
        )
        val correctionResult = useCase.execute(correctionMessage)
        // يتم الاحتفاظ باسم راكان النظيف
        assertEquals("راكان", fakeDeviceContactsManager.getContactDisplayName(rakanPhone))

        // 2. قضية درع الكتم الصريح: "ممنوع ترد عليا"
        val silenceMessage = IncomingMessage(
            id = "silence_msg_1",
            senderPhone = "+967779998877",
            text = "صليت باروح اكمل اللي عليا بارد عليك وبس ممنوع ترد عليا"
        )
        val silenceResult = useCase.execute(silenceMessage)
        assertTrue(silenceResult is ProcessResult.Ignored)
        assertEquals("أمر كتم صريح من المستخدم", (silenceResult as ProcessResult.Ignored).reason)

        // 3. قضية "ما اسمع الرسائل"
        val hearingMessage = IncomingMessage(
            id = "hear_msg_1",
            senderPhone = "+967775554433",
            text = "ما اسمع الرسائل"
        )
        val hearingResult = useCase.execute(hearingMessage)
        assertNull(fakeDeviceContactsManager.getContactDisplayName("+967775554433"))
        assertFalse(hearingResult is ProcessResult.Executed)

        // 4. قضية "لا مش تبعنا"
        val notOursMessage = IncomingMessage(
            id = "not_ours_msg_1",
            senderPhone = "+967772221100",
            text = "لا مش تبعنا"
        )
        val notOursResult = useCase.execute(notOursMessage)
        assertNull(fakeDeviceContactsManager.getContactDisplayName("+967772221100"))
        assertFalse(notOursResult is ProcessResult.Executed)
    }

    @Test
    fun testDirectAndPastNegationPatterns() = runBlocking {
        val negationTexts = listOf(
            "لا تسجلني",
            "لا تسجلني يا اخي",
            "ممنوع تسجلني",
            "بلاش تسجلني",
            "انا ما قلت سجلني",
            "ما قلت لك سجلني",
            "قلت له لا تسجلني",
            "من قال لك تسجلني؟",
            "ليش تسجلني؟"
        )

        for ((idx, txt) in negationTexts.withIndex()) {
            val phone = "+96777880011$idx"
            val msg = IncomingMessage(id = "neg_$idx", senderPhone = phone, text = txt)
            val result = useCase.execute(msg)
            assertTrue("Text '$txt' should be ignored or not executed as registration", result is ProcessResult.Ignored || result is ProcessResult.NoMatch)
            assertNull("Phone $phone should not be saved", fakeDeviceContactsManager.getContactDisplayName(phone))
        }
    }

    @Test
    fun testQuotedSpeechAndNarrativeContext() = runBlocking {
        val quotedTexts = listOf(
            "فلان قال لي سجلني علي",
            "وقلت له سجلني علي",
            "واحد كتب لي سجلني",
            "كان يقول لي سجلني باسم عمر"
        )

        for ((idx, txt) in quotedTexts.withIndex()) {
            val phone = "+96777990022$idx"
            val msg = IncomingMessage(id = "quote_$idx", senderPhone = phone, text = txt)
            val result = useCase.execute(msg)
            assertTrue("Text '$txt' should be ignored", result is ProcessResult.Ignored || result is ProcessResult.NoMatch)
            assertNull("Phone $phone should not be saved", fakeDeviceContactsManager.getContactDisplayName(phone))
        }
    }

    @Test
    fun testEmojiAndDecorationPreservation() = runBlocking {
        val phone = "+967771239999"
        val msg = IncomingMessage(
            id = "emoji_msg",
            senderPhone = phone,
            text = "سجلني علي صالح 😂🔥"
        )
        val result = useCase.execute(msg)
        assertTrue(result is ProcessResult.Executed)
        val saved = fakeDeviceContactsManager.getContactDisplayName(phone)
        assertNotNull(saved)
        assertTrue(saved!!.contains("علي صالح"))
    }

    @Test
    fun testStrictAlreadyRegisteredBarrier() = runBlocking {
        val phone = "+967770123456"

        // 1. غير مسجل + "سجلني محمد" -> Executed والاسم يصبح "محمد"
        val msg1 = IncomingMessage(id = "barrier_1", senderPhone = phone, text = "سجلني محمد")
        val result1 = useCase.execute(msg1)
        assertTrue("غير مسجل يجب أن يتم حفظه", result1 is ProcessResult.Executed)
        assertEquals("محمد", fakeDeviceContactsManager.getContactDisplayName(phone))

        // ضبط الاسم يدوياً إلى "أحمد" لاختبار باقي السيناريوهات الإلزامية
        fakeDeviceContactsManager.savedContacts[phone] = "أحمد"
        val initialContactsCount = fakeDeviceContactsManager.savedContacts.size

        // 2. مسجل "أحمد" + "سجلني أحمد" -> No-Op والاسم يبقى "أحمد"
        val msg2 = IncomingMessage(id = "barrier_2", senderPhone = phone, text = "سجلني أحمد")
        val result2 = useCase.execute(msg2)
        assertTrue("مسجل بنفس الاسم يجب أن يتجاهل صامتاً", result2 is ProcessResult.Ignored)
        assertEquals("أحمد", fakeDeviceContactsManager.getContactDisplayName(phone))
        assertEquals(initialContactsCount, fakeDeviceContactsManager.savedContacts.size)

        // 3. مسجل "أحمد" + "سجلني محمد" -> No-Op والاسم يبقى "أحمد"
        val msg3 = IncomingMessage(id = "barrier_3", senderPhone = phone, text = "سجلني محمد")
        val result3 = useCase.execute(msg3)
        assertTrue("مسجل مسبقاً ويرسل اسماً جديداً يجب أن يتجاهل صامتاً", result3 is ProcessResult.Ignored)
        assertEquals("أحمد", fakeDeviceContactsManager.getContactDisplayName(phone))
        assertEquals(initialContactsCount, fakeDeviceContactsManager.savedContacts.size)

        // 4. مسجل "أحمد" + "احفظني علي" -> No-Op والاسم يبقى "أحمد"
        val msg4 = IncomingMessage(id = "barrier_4", senderPhone = phone, text = "احفظني علي")
        val result4 = useCase.execute(msg4)
        assertTrue("مسجل مسبقاً ويرسل احفظني علي يجب أن يتجاهل صامتاً", result4 is ProcessResult.Ignored)
        assertEquals("أحمد", fakeDeviceContactsManager.getContactDisplayName(phone))
        assertEquals(initialContactsCount, fakeDeviceContactsManager.savedContacts.size)

        // 5. تكرار الرسالة عدة مرات -> لا توجد أي mutation إضافية والاسم يبقى "أحمد"
        for (i in 1..5) {
            val repeatMsg = IncomingMessage(id = "repeat_$i", senderPhone = phone, text = "سجلني محمد اليافعي")
            val repeatResult = useCase.execute(repeatMsg)
            assertTrue("التكرار يجب أن يتجاهل دائماً", repeatResult is ProcessResult.Ignored)
            assertEquals("أحمد", fakeDeviceContactsManager.getContactDisplayName(phone))
            assertEquals(initialContactsCount, fakeDeviceContactsManager.savedContacts.size)
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
    override suspend fun pruneOldLogs(keepCount: Int): Int = 0
}
