package com.whatsup.automation

import com.whatsup.automation.domain.model.PatternType
import com.whatsup.automation.domain.model.Rule
import com.whatsup.automation.domain.model.RuleAction
import com.whatsup.automation.domain.repository.RuleRepository
import com.whatsup.automation.domain.usecase.MatchRuleUseCase
import com.whatsup.automation.domain.util.ArabicMorphologyHelper
import com.whatsup.automation.domain.util.QuotedSpeechDetector
import com.whatsup.automation.domain.util.SpintaxEngine
import com.whatsup.automation.domain.util.StrictNegationAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حزمة اختبارات شاملة لمحرك مطابقة القواعد، معالجة النصوص العربية،
 * واكتشاف النفي والكلام المنقول، ومحرك Spintax (TDD & Quality Suite).
 */
class AutomationEngineRuleMatchingTest {

    private val fakeRuleRepository = object : RuleRepository {
        override fun getAllRules(): Flow<List<Rule>> = flowOf(emptyList())
        override fun getEnabledRules(): Flow<List<Rule>> = flowOf(emptyList())
        override suspend fun getRuleById(id: Long): Rule? = null
        override suspend fun insertRule(rule: Rule): Long = 1L
        override suspend fun updateRule(rule: Rule) {}
        override suspend fun deleteRule(id: Long) {}
        override suspend fun toggleRule(id: Long, enabled: Boolean) {}
    }

    private val matchRuleUseCase = MatchRuleUseCase(fakeRuleRepository)

    @Test
    fun testExactMatch() {
        val rule = Rule(
            name = "Exact Test",
            description = "Exact match test rule",
            patternType = PatternType.EXACT_MATCH,
            patternValue = "السلام عليكم",
            actions = listOf(RuleAction.SendReply("وعليكم السلام")),
            priority = 1
        )

        assertTrue(matchRuleUseCase.matches(rule, "السلام عليكم"))
        assertTrue(matchRuleUseCase.matches(rule, "  السلام عليكم  "))
        assertFalse(matchRuleUseCase.matches(rule, "السلام عليكم ورحمة الله"))
    }

    @Test
    fun testContainsMatch() {
        val rule = Rule(
            name = "Contains Test",
            description = "Contains match test rule",
            patternType = PatternType.CONTAINS,
            patternValue = "السعر، بكم",
            actions = listOf(RuleAction.SendReply("الأسعار تبدأ من 50$")),
            priority = 1
        )

        assertTrue(matchRuleUseCase.matches(rule, "مرحبا بكم لو سمحت"))
        assertTrue(matchRuleUseCase.matches(rule, "كم السعر حالياً؟"))
        assertFalse(matchRuleUseCase.matches(rule, "أين موقعكم؟"))
    }

    @Test
    fun testStartsWithMatch() {
        val rule = Rule(
            name = "StartsWith Test",
            description = "StartsWith match test rule",
            patternType = PatternType.STARTS_WITH,
            patternValue = "طلب، حجز",
            actions = listOf(RuleAction.SendReply("تم استلام طلبك")),
            priority = 1
        )

        assertTrue(matchRuleUseCase.matches(rule, "طلب صيانة عاجل"))
        assertTrue(matchRuleUseCase.matches(rule, "حجز موعد غداً"))
        assertFalse(matchRuleUseCase.matches(rule, "أريد عمل طلب جديد"))
    }

    @Test
    fun testRegexMatch() {
        val rule = Rule(
            name = "Regex Test",
            description = "Regex match test rule",
            patternType = PatternType.REGEX,
            patternValue = "^[0-9]{4}$",
            actions = listOf(RuleAction.SendReply("رمز تحقق صحيح")),
            priority = 1
        )

        assertTrue(matchRuleUseCase.matches(rule, "1234"))
        assertFalse(matchRuleUseCase.matches(rule, "12345"))
        assertFalse(matchRuleUseCase.matches(rule, "abcd"))
    }

    @Test
    fun testStrictNegationShield() {
        // اختبار منع الرد أو الحفظ عند وجود نفي صريح
        assertTrue(StrictNegationAnalyzer.isNegated("لا تسجل رقمي"))
        assertTrue(StrictNegationAnalyzer.isNegated("ممنوع تحفظ الرقم"))
        assertTrue(StrictNegationAnalyzer.isNegated("لا ترد علي"))
        assertFalse(StrictNegationAnalyzer.isNegated("سجل اسمي أحمد"))
    }

    @Test
    fun testQuotedSpeechShield() {
        // اختبار تجاهل الكلام المنقول أو الاقتباسات
        assertTrue(QuotedSpeechDetector.isQuotedOrIndirect("صاحبي قال سجلني"))
        assertTrue(QuotedSpeechDetector.isQuotedOrIndirect("قال لي احفظني"))
        assertFalse(QuotedSpeechDetector.isQuotedOrIndirect("اسمي خالد عبد الله"))
    }

    @Test
    fun testSpintaxEngine() {
        val template = "{أهلاً|مرحباً|يا هلا} بك {عزيزي|أخي الكريم}"
        val result = SpintaxEngine.process(template)

        assertNotNull(result)
        assertTrue(result.startsWith("أهلاً") || result.startsWith("مرحباً") || result.startsWith("يا هلا"))
        assertTrue(result.contains("بك"))
        assertTrue(result.endsWith("عزيزي") || result.endsWith("أخي الكريم"))
    }

    @Test
    fun testArabicMorphologyNormalization() {
        val raw = "أحمد إبراهيم آمنة ة"
        val normalized = ArabicMorphologyHelper.normalize(raw)
        // توحيد الألفات والتاء المربوطة
        assertFalse(normalized.contains("أ"))
        assertFalse(normalized.contains("إ"))
        assertFalse(normalized.contains("آ"))
    }

    @Test
    fun testPureEmojisAndGibberishAreRejected() {
        // اختبار رفض الرموز العشوائية
        assertFalse(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("#@%#$@^$%"))
        assertFalse(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("!@#$$%^&"))
        assertFalse(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("..."))
        
        // اختبار رفض الإيموجيات الصرفة بدون أحرف
        assertFalse(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("❤️❤️❤️"))
        assertFalse(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("😂😂"))
        assertFalse(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("🔥🔥👏"))

        // اختبار قبول الأسماء الحقيقية
        assertTrue(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("أحمد علي"))
        assertTrue(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("محمد"))
        assertTrue(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("John Doe"))
        // قبول الاسم الحقيقي حتى لو كان بجانبه إيموجي
        assertTrue(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("علي 😍"))
    }

    @Test
    fun testIsolatedRegistrationCommandsAreNeverSavedAsNames() {
        // اختبار أن كلمة "سجلني" أو "احفظني" بمفردها لا تُستخرج كاسم
        assertEquals("", com.whatsup.automation.domain.util.NameExtractorHelper.extractName("سجلني"))
        assertEquals("", com.whatsup.automation.domain.util.NameExtractorHelper.extractName("احفظني"))
        assertEquals("", com.whatsup.automation.domain.util.NameExtractorHelper.extractName("سجلني عندك"))
        assertEquals("", com.whatsup.automation.domain.util.NameExtractorHelper.extractName("احفظ رقمي"))
        
        // التحقق من أن كلمة "سجلني" ليست اسماً بشرياً صالحاً
        assertFalse(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("سجلني"))
        assertFalse(com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName("احفظني"))

        // استخراج الاسم الحقيقي عندما يتبع الكلمة المشغلة
        assertEquals("أحمد", com.whatsup.automation.domain.util.NameExtractorHelper.extractName("سجلني أحمد"))
        assertEquals("محمد علي", com.whatsup.automation.domain.util.NameExtractorHelper.extractName("احفظني باسم محمد علي"))
        assertEquals("سالم", com.whatsup.automation.domain.util.NameExtractorHelper.extractName("اسمي سالم"))
    }
}
