package com.whatsup.automation.domain.util

/**
 * أنواع النوايا الدلالية المصنفة.
 */
enum class SemanticIntent {
    REGISTER_CONTACT,     // طلب تسجيل جهة اتصال جديدة
    UPDATE_CONTACT,       // طلب صريح لتحديث أو تعديل اسم جهة اتصال مسجلة مسبقاً
    NEGATED_COMMAND,      // أمر منفي أو طلب كتم صريح
    QUOTED_OR_NARRATIVE,  // كلام منقول أو سرد غير موجه للبوت
    QUESTION_OR_INQUIRY,  // سؤال أو استفسار
    GENERAL_CHAT          // محادثة عادية
}

/**
 * نتيجة تصنيف النية الدلالية مع درجة الثقة.
 */
data class IntentClassificationResult(
    val intent: SemanticIntent,
    val confidence: Float,
    val extractedRawName: String? = null,
    val reason: String = ""
)

/**
 * مصنف النوايا الدلالي الهجين (Semantic Intent Classifier).
 * يقوم بتحليل بنية الجملة، النفي، الخطاب، والسياق لتحديد النية الحقيقية بدقة وتقدير نسبة الثقة.
 */
object SemanticIntentClassifier {

    private val EXPLICIT_UPDATE_TRIGGERS = listOf(
        "غير اسمي الى", "غير اسمي ل", "غير اسمي", "عدل اسمي الى", "عدل اسمي ل", "عدل اسمي",
        "سجلني باسم جديد", "احفظني باسم جديد", "اسمي الحقيقي هو", "اسمي الجديد",
        "سجلني", "احفظني", "ضيفني", "خزنني", "اسمي هو", "اسمي", "انا اسمي"
    )

    private val DIRECT_REGISTRATION_TRIGGERS = listOf(
        "سجلني عندك باسم", "سجلني عندك اسمي", "سجلني عندك يا", "سجلني عندك", "سجلني معك",
        "احفظني عندك باسم", "احفظني عندك اسمي", "احفظني عندك", "احفظني معك",
        "احفظ رقمي عندك باسم", "احفظ رقمي عندك", "احفظ رقمي باسم", "احفظ رقمي",
        "سجل اسمي عندك باسم", "سجل اسمي عندك", "سجل اسمي باسم", "سجل اسمي",
        "سجلني باسم", "سجلني اسمي", "سجلني يا غالي", "سجلني يا طيب", "سجلني",
        "احفظني باسم", "احفظني اسمي", "احفظني يا غالي", "احفظني",
        "خزنني عندك باسم", "خزنني عندك", "خزن رقمي عندك", "خزن رقمي", "خزن اسمي", "خزنني",
        "ضيفني عندك باسم", "ضيفني عندك", "ضيف رقمي عندك", "ضيف رقمي", "ضيف اسمي", "ضيفني",
        "احفظ رقم اخوك", "سجل رقم اخوك", "خزن رقم اخوك", "ضيف رقم اخوك",
        "سجل بياناتي", "احفظ بياناتي", "خزن بياناتي",
        "اسمي هو", "اسمي انا", "اسمي", "انا اسمي",
        "معاك اخوك", "معك اخوك", "معاك", "معك", "الاسم"
    ).sortedByDescending { it.length }

    /**
     * تصنيف النية الدلالية للنص الوارد مع تقدير درجة الثقة.
     */
    fun classify(
        text: String,
        isAlreadyRegistered: Boolean = false,
        existingName: String? = null,
        customKeywords: List<String> = emptyList()
    ): IntentClassificationResult {
        if (text.isBlank()) {
            return IntentClassificationResult(SemanticIntent.GENERAL_CHAT, 1.0f, null, "نص فارغ")
        }

        val norm = ArabicMorphologyHelper.normalize(text)

        // 1. فحص النفي والكتم المسبق
        if (StrictNegationAnalyzer.isNegated(text)) {
            return IntentClassificationResult(
                intent = SemanticIntent.NEGATED_COMMAND,
                confidence = 0.98f,
                extractedRawName = null,
                reason = "تم كشف نفي صريح أو أمر كتم"
            )
        }

        // 2. فحص السرد والخطاب المنقول
        if (QuotedSpeechDetector.isQuotedOrIndirect(text)) {
            return IntentClassificationResult(
                intent = SemanticIntent.QUOTED_OR_NARRATIVE,
                confidence = 0.95f,
                extractedRawName = null,
                reason = "تم كشف كلام منقول أو سرد لحوار مع طرف ثالث"
            )
        }

        // 3. فحص هل النص مجرد سؤال عام أو استفسار
        if (norm.endsWith("?") || norm.endsWith("؟")) {
            val isExplicitRegWithQuestion = DIRECT_REGISTRATION_TRIGGERS.any { norm.startsWith(ArabicMorphologyHelper.normalize(it)) }
            val extracted = NameExtractorHelper.extractName(text, customKeywords)
            if (!isExplicitRegWithQuestion || extracted.isBlank()) {
                return IntentClassificationResult(
                    intent = SemanticIntent.QUESTION_OR_INQUIRY,
                    confidence = 0.90f,
                    extractedRawName = null,
                    reason = "استفسار أو سؤال"
                )
            }
        }

        // 4. استخراج الاسم والتحقق من وجوده
        val allTriggers = (customKeywords.filter { it.isNotBlank() } + DIRECT_REGISTRATION_TRIGGERS).distinct()
        val hasTrigger = allTriggers.any { kw ->
            val normKw = ArabicMorphologyHelper.normalize(kw)
            norm.startsWith(normKw) || norm.contains(" $normKw") || ArabicMorphologyHelper.isFuzzyMatch(kw, text)
        }

        val extractedName = NameExtractorHelper.extractName(text, customKeywords)
        val hasValidName = extractedName.isNotBlank() && NameExtractorHelper.isValidHumanName(extractedName)

        if (hasTrigger && hasValidName) {
            // التحقق من حالة التسجيل المسبق والتحديث
            if (isAlreadyRegistered && existingName != null) {
                val isSameName = existingName.equals(extractedName, ignoreCase = true) ||
                        ArabicMorphologyHelper.normalize(existingName).equals(ArabicMorphologyHelper.normalize(extractedName), ignoreCase = true)

                if (isSameName) {
                    return IntentClassificationResult(
                        intent = SemanticIntent.REGISTER_CONTACT,
                        confidence = 0.95f,
                        extractedRawName = extractedName,
                        reason = "مطابقة لنفس الاسم المسجل مسبقاً (No-Op)"
                    )
                } else {
                    // اسم مختلف -> طلب تحديث صريح
                    val isExplicitUpdate = EXPLICIT_UPDATE_TRIGGERS.any { 
                        val normTrigger = ArabicMorphologyHelper.normalize(it)
                        norm.startsWith(normTrigger) || norm.contains(" $normTrigger")
                    }
                    val confidence = if (isExplicitUpdate) 0.92f else 0.70f

                    return IntentClassificationResult(
                        intent = SemanticIntent.UPDATE_CONTACT,
                        confidence = confidence,
                        extractedRawName = extractedName,
                        reason = if (isExplicitUpdate) "طلب تحديث اسم صريح" else "اسم جديد لجهة مسجلة"
                    )
                }
            } else {
                // تسجيل جديد لأول مرة
                return IntentClassificationResult(
                    intent = SemanticIntent.REGISTER_CONTACT,
                    confidence = 0.95f,
                    extractedRawName = extractedName,
                    reason = "طلب تسجيل جديد مباشر"
                )
            }
        } else if (hasTrigger && !hasValidName) {
            // وردت كلمة "سجل رقمي" أو "احفظني" بدون اسم
            return IntentClassificationResult(
                intent = SemanticIntent.REGISTER_CONTACT,
                confidence = 0.60f,
                extractedRawName = null,
                reason = "طلب تسجيل بدون اسم مصاحب"
            )
        }

        return IntentClassificationResult(
            intent = SemanticIntent.GENERAL_CHAT,
            confidence = 0.85f,
            extractedRawName = null,
            reason = "محادثة عادية"
        )
    }
}
