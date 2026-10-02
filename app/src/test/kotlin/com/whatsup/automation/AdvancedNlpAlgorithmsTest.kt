package com.whatsup.automation

import com.whatsup.automation.domain.nlp.*
import org.junit.Assert.*
import org.junit.Test

class AdvancedNlpAlgorithmsTest {

    // ═══════════════════════════════════════════════════════════
    // 1. اختبارات محرك كشف النفي والعدول واللهجات العربية
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `test dialectal negation detection across all Arabic dialects`() {
        // نفي باللهجة اليمنية
        val yemeniResult1 = ArabicDialectalNegationEngine.analyzeNegationScope("سجل", "مانشتيش تسجل رقمنا ياخي")
        assertTrue("يجب كشف النفي اليمني (مانشتيش)", yemeniResult1.isNegated)

        val yemeniResult2 = ArabicDialectalNegationEngine.analyzeNegationScope("احفظ", "ماباش تحفظ اسمي")
        assertTrue("يجب كشف النفي اليمني (ماباش)", yemeniResult2.isNegated)

        // نفي باللهجة المصرية
        val egyptianResult = ArabicDialectalNegationEngine.analyzeNegationScope("سجل", "مش عايزك تسجلني عندك خالص")
        assertTrue("يجب كشف النفي المصري (مش عايز)", egyptianResult.isNegated)

        val egyptianResult2 = ArabicDialectalNegationEngine.analyzeNegationScope("ضيفني", "بلاش تضيفني")
        assertTrue("يجب كشف النفي المصري (بلاش)", egyptianResult2.isNegated)

        // نفي باللهجة الخليجية
        val gulfResult = ArabicDialectalNegationEngine.analyzeNegationScope("احفظني", "ما ابغى تحفظني يا غالي")
        assertTrue("يجب كشف النفي الخليجي (ما ابغى)", gulfResult.isNegated)

        // نفي باللهجة الشامية
        val levantineResult = ArabicDialectalNegationEngine.analyzeNegationScope("سجلني", "ما بدي تسجلني بنوب")
        assertTrue("يجب كشف النفي الشامي (ما بدي)", levantineResult.isNegated)

        // صيغة التقابل (لا ... ولا ...)
        val neitherNorResult = ArabicDialectalNegationEngine.analyzeNegationScope("سلام", "لا سلام ولا كلام")
        assertTrue("يجب كشف صيغة لا...ولا", neitherNorResult.isNegated)

        // سياق نقل الكلام والحكايات (Hearsay)
        val hearsayResult = ArabicDialectalNegationEngine.analyzeNegationScope("سجلني", "هو قالي سجلني عندك")
        assertTrue("يجب كشف سياق نقل الكلام", hearsayResult.isHearsay)

        // طلب إيجابي صريح (يجب ألا يعتبر منفياً)
        val positiveResult = ArabicDialectalNegationEngine.analyzeNegationScope("سجلني", "سجلني عندك باسم علي محمد")
        assertFalse("الطلب الإيجابي يجب ألا يكون منفياً", positiveResult.isNegated)
    }

    // ═══════════════════════════════════════════════════════════
    // 2. اختبارات محرك الصوتيات والتجانس اللفظي العربي (Soundex)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `test Arabic Phonetic Soundex and Name Homophony`() {
        // تطابق تام
        val scoreExact = ArabicPhoneticSoundex.getPhoneticSimilarity("محمد", "محمد")
        assertEquals(1.0f, scoreExact, 0.01f)

        // تجانس أسماء بالمدود والهمزات
        val scoreAlaa = ArabicPhoneticSoundex.getPhoneticSimilarity("علا", "علاء")
        assertTrue("علا وعلاء يجب أن تكون متقاربة صوتياً", scoreAlaa >= 0.75f)

        val scoreDia = ArabicPhoneticSoundex.getPhoneticSimilarity("ضياء", "ضيا")
        assertTrue("ضياء وضيا يجب أن تكون متطابقة صوتياً", scoreDia >= 0.75f)

        val scoreTaha = ArabicPhoneticSoundex.getPhoneticSimilarity("طه", "طها")
        assertTrue("طه وطها يجب أن تكون متطابقة صوتياً", scoreTaha >= 0.75f)

        // أسماء متباعدة تماماً
        val scoreDiff = ArabicPhoneticSoundex.getPhoneticSimilarity("علي", "محمود")
        assertTrue("علي ومحمود يجب أن تكون متباعدة صوتياً", scoreDiff < 0.60f)
    }

    // ═══════════════════════════════════════════════════════════
    // 3. اختبارات آلة ليفنشتاين وشجرة البادئات (Schulz & Mihov Trie)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `test Levenshtein Trie Automaton with heavy typos`() {
        val trie = LevenshteinTrieAutomaton(listOf("سجلني", "احفظني", "ضيفني", "اسمي"))

        // خطأ إملائي بإضافة أحرف (سجلنييي -> سجلني)
        val matches1 = trie.search("سجلنييي", maxDistance = 2)
        assertTrue("يجب التقاط سجلنييي", matches1.any { it.matchedKeyword == "سجلني" })

        // خطأ إملائي بتبديل حرف (احفطني -> احفظني)
        val matches2 = trie.search("احفطني", maxDistance = 1)
        assertTrue("يجب التقاط احفطني", matches2.any { it.matchedKeyword == "احفظني" })

        // كلمة غير موجودة ومتباعدة
        val matchesNone = trie.search("سيارة", maxDistance = 2)
        assertTrue("يجب ألا يطابق سيارة", matchesNone.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════
    // 4. اختبارات مصنف أفعال الكلام الحوارية (Speech Acts)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `test Arabic Speech Act Classifier distinguishes inquiries from directives`() {
        // سؤال واستفسار (Question)
        val qResult = ArabicSpeechActClassifier.classify("هل تعرف المهندس أحمد؟")
        assertEquals(SpeechAct.QUESTION, qResult.act)

        val qResult2 = ArabicSpeechActClassifier.classify("بكم سعر الاشتراك لو سمحت؟")
        assertEquals(SpeechAct.QUESTION, qResult2.act)

        // إخبار عن النفس (Assertive)
        val aResult = ArabicSpeechActClassifier.classify("أنا اسمي نبيل الأحمدي")
        assertEquals(SpeechAct.ASSERTIVE, aResult.act)

        // أمر أو طلب صريح (Directive)
        val dResult = ArabicSpeechActClassifier.classify("احفظ رقمي وسجلني")
        assertEquals(SpeechAct.DIRECTIVE, dResult.act)

        // شكر ومشاعر (Expressive)
        val eResult = ArabicSpeechActClassifier.classify("شكراً جزيلاً ما قصرت")
        assertEquals(SpeechAct.EXPRESSIVE, eResult.act)

        // تحية اجتماعية (Phatic)
        val pResult = ArabicSpeechActClassifier.classify("السلام عليكم ورحمة الله وبركاته")
        assertEquals(SpeechAct.PHATIC, pResult.act)
    }

    // ═══════════════════════════════════════════════════════════
    // 5. اختبارات درع الطاقة وكشف الضوضاء (Energy-Based OOD Scorer)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `test Energy-based OOD Scorer differentiates in-scope vs noise`() {
        // تدريب نماذج أولية لكلمات التسجيل
        val proto1 = EnergyOodScorer.extractFeatureVector("سجلني عندك باسم علي")
        val proto2 = EnergyOodScorer.extractFeatureVector("احفظ رقمي اسمي محمد")
        val prototypes = listOf(proto1, proto2)

        // رسالة مشابهة وموافقة للنطاق (In-Distribution)
        val inScopeEnergy = EnergyOodScorer.computeEnergyScore("سجل اسمي عندك يا غالي", prototypes)

        // رسالة ضوضاء وسوالف خارج النطاق تماماً (Out-of-Distribution)
        val oodEnergy = EnergyOodScorer.computeEnergyScore("اليوم الجو حار جداً في صنعاء", prototypes)

        assertTrue(
            "طاقة الرسالة في النطاق ($inScopeEnergy) يجب أن تكون أقل من طاقة الضوضاء ($oodEnergy)",
            inScopeEnergy < oodEnergy
        )
    }

    // ═══════════════════════════════════════════════════════════
    // 6. اختبارات مصفوفة دمج الأدلة وحسم القرار (Evidence Fusion Matrix)
    // ═══════════════════════════════════════════════════════════

    @Test
    fun `test Evidence Fusion Matrix prevents blind automated replies`() {
        // حالة 1: طلب صريح ويقين عالي -> تنفيذ مباشر
        val decision1 = EvidenceFusionMatrix.evaluate(
            incomingText = "سجلني عندك باسم علي الشامي",
            targetKeyword = "سجلني",
            recentTurn = null
        )
        assertTrue("يجب تنفيذ الطلب الصريح فوراً", decision1 is FusionDecision.ExecuteDirectly)

        // حالة 2: نفي صريح -> عدم الرد العشوائي وتحويل لـ HandleNegation
        val decisionNegated = EvidenceFusionMatrix.evaluate(
            incomingText = "لا تسجلني عندك",
            targetKeyword = "سجلني",
            recentTurn = null
        )
        assertTrue("يجب كشف النفي ومنع التسجيل", decisionNegated is FusionDecision.HandleNegationOrCancel)

        // حالة 3: سؤال عام لا يخص التسجيل -> حفظ صامت بدون رد أعمى
        val decisionQuestion = EvidenceFusionMatrix.evaluate(
            incomingText = "هل المحل مفتوح الآن؟",
            targetKeyword = "سجلني",
            recentTurn = null
        )
        assertTrue("يجب تجاهل السؤال العام صامتاً", decisionQuestion is FusionDecision.SilentLog)
    }
}
