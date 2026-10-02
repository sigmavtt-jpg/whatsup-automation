package com.whatsup.automation.domain.nlp

import com.whatsup.automation.domain.util.ArabicMorphologyHelper

enum class SpeechAct {
    DIRECTIVE,       // طلب أو أمر صريح (احفظني، سجلني، أرسل العرض)
    ASSERTIVE,       // إخبار وتقرير عن النفس أو معلومات (اسمي فلان، رقمي الجديد، أنا المهندس فلان)
    COMMISSIVE,      // وعد والتزام مستقبلي (سأتواصل معك، سأحول المبلغ لاحقاً)
    QUESTION,        // استفهام وسؤال (هل، بكم، متى، أين، كيف، تعرف فلان؟)
    EXPRESSIVE,      // شكر وعواطف وانفعالات (شكراً، جزاك الله خير، تسلم، ما قصرت)
    PHATIC,          // تحايا ومجاملات اجتماعية (السلام عليكم، صباح الخير، جمعة مباركة)
    UNKNOWN_NOISE    // كلام عشوائي أو خارج النطاق
}

object ArabicSpeechActClassifier {

    private val QUESTION_INDICATORS = listOf(
        "؟", "?", "هل", "بكم", "كم", "ايش", "شو", "ليه", "ليش", "لماذا",
        "كيف", "متى", "وين", "فين", "منو", "من هو", "مين", "ما هو", "ماهي",
        "ماذا", "ما اسمك", "ما اسم", "تشتيني", "تقدر", "ممكن", "أين", "اين"
    )

    private val DIRECTIVE_VERBS = listOf(
        "سجل", "احفظ", "خزن", "ضيف", "ارسل", "أرسل", "اعطني", "أعطني",
        "ابعث", "أبعث", "كلمني", "اتصل", "حول", "حفظ", "تسجيل", "تثبيت"
    )

    private val ASSERTIVE_STARTERS = listOf(
        "اسمي", "انا اسمي", "أنا اسمي", "معاك", "معك", "اخوك", "أخوك",
        "الاسم", "حسابي", "بياناتي", "المرسل", "طرفك", "من طرف"
    )

    private val EXPRESSIVE_WORDS = listOf(
        "شكرا", "شكراً", "مشكور", "تسلم", "جزاك الله", "يعطيك العافية", "الله يحفظك",
        "ما قصرت", "ماقصرت", "بيض الله وجهك", "حبيبي", "يا غالي", "يسلمو", "ممتاز", "بارك الله فيك"
    )

    private val PHATIC_GREETINGS = listOf(
        "السلام عليكم", "سلام عليكم", "سلام", "مرحبا", "مرحباً", "هلا", "اهلين",
        "أهلاً", "صباح الخير", "مساء الخير", "صباح النور", "مساء النور", "جمعة مباركة", "حياك الله"
    )

    data class SpeechActResult(
        val act: SpeechAct,
        val confidence: Float,
        val detectedIndicators: List<String>
    )

    fun classify(rawText: String): SpeechActResult {
        val norm = ArabicMorphologyHelper.normalize(rawText).trim()
        if (norm.isBlank()) {
            return SpeechActResult(SpeechAct.UNKNOWN_NOISE, 0.0f, emptyList())
        }

        val tokens = norm.split(Regex("""[\s,،.:;!/-]+""")).filter { it.isNotBlank() }
        val matchedIndicators = mutableListOf<String>()

        // 1. فحص الشكر والانفعالات (EXPRESSIVE)
        val matchedExpressives = EXPRESSIVE_WORDS.filter { norm.contains(ArabicMorphologyHelper.normalize(it)) }
        if (matchedExpressives.isNotEmpty()) {
            matchedIndicators.addAll(matchedExpressives)
            return SpeechActResult(SpeechAct.EXPRESSIVE, 0.95f, matchedIndicators)
        }

        // 2. فحص التحايا والمجاملات (PHATIC)
        val matchedPhatics = PHATIC_GREETINGS.filter { norm.contains(ArabicMorphologyHelper.normalize(it)) }
        if (matchedPhatics.isNotEmpty() && !norm.contains("سجل") && !norm.contains("احفظ")) {
            matchedIndicators.addAll(matchedPhatics)
            return SpeechActResult(SpeechAct.PHATIC, 0.95f, matchedIndicators)
        }

        // 3. فحص الأوامر والطلبات المباشرة (DIRECTIVE) — إذا بدأت الجملة بفعل أمر
        val matchedDirectives = DIRECTIVE_VERBS.filter { d ->
            tokens.any { t -> 
                val stem = ArabicMorphologyHelper.stemWord(t)
                stem.contains(d) || d.contains(stem) || t.startsWith(d)
            }
        }
        if (matchedDirectives.isNotEmpty()) {
            matchedIndicators.addAll(matchedDirectives)
            return SpeechActResult(SpeechAct.DIRECTIVE, 0.92f, matchedIndicators)
        }

        // 4. فحص هل الرسالة سؤال (QUESTION)
        val hasQuestionMark = rawText.contains("؟") || rawText.contains("?")
        val matchedQuestionWords = QUESTION_INDICATORS.filter { q ->
            if (q.length <= 2) tokens.any { it == q } else norm.contains(q)
        }
        if (hasQuestionMark || matchedQuestionWords.isNotEmpty()) {
            matchedIndicators.addAll(matchedQuestionWords)
            if (hasQuestionMark) matchedIndicators.add("؟")
            return SpeechActResult(
                act = SpeechAct.QUESTION,
                confidence = if (hasQuestionMark && matchedQuestionWords.isNotEmpty()) 0.98f else 0.88f,
                detectedIndicators = matchedIndicators
            )
        }

        // 5. فحص هل الرسالة إخبار عن النفس أو الاسم (ASSERTIVE)
        val matchedAssertives = ASSERTIVE_STARTERS.filter { norm.startsWith(it) || norm.contains(" $it") }
        if (matchedAssertives.isNotEmpty()) {
            matchedIndicators.addAll(matchedAssertives)
            return SpeechActResult(SpeechAct.ASSERTIVE, 0.90f, matchedIndicators)
        }

        return SpeechActResult(SpeechAct.UNKNOWN_NOISE, 0.2f, emptyList())
    }
}
