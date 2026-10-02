package com.whatsup.automation.domain.util

/**
 * محلل النفي الصريح والمركب (Strict Negation & Scope Analyzer).
 * مسؤول عن كشف كافة أشكال النفي والنهي والاستدراك التي تمنع تنفيذ أوامر التسجيل أو الأتمتة.
 * يعمل محلياً بسرعة فائقة (Deterministic Fast-Path).
 */
object StrictNegationAnalyzer {

    // أدوات النفي والنهي المباشرة المقترنة بأفعال التسجيل والحفظ
    private val DIRECT_NEGATION_PREFIXES = listOf(
        "لا تسجلني", "لا تسجل", "لا تحفظني", "لا تحفظ", "لا تضيفني", "لا تضيف", "لا تخزني", "لا تخزن",
        "ماتسجلني", "ماتسجل", "ماتحفظني", "ماتحفظ", "ماتضيفني", "ماتضيف", "ماتخزني", "ماتخزن",
        "ما تسجلني", "ما تسجل", "ما تحفظني", "ما تحفظ", "ما تضيفني", "ما تضيف", "ما تخزني", "ما تخزن",
        "ممنوع تسجلني", "ممنوع تسجل", "ممنوع تحفظني", "ممنوع تحفظ", "ممنوع تضيفني", "ممنوع تضيف",
        "بلاش تسجلني", "بلاش تسجل", "بلاش تحفظني", "بلاش تحفظ", "بلاش تضيفني",
        "لا تسجل رقمي", "لا تحفظ رقمي", "لا تخزن رقمي", "لا تضيف رقمي",
        "ممنوع تسجل رقمي", "ممنوع تحفظ رقمي", "ممنوع تخزن رقمي",
        "ما تسجل رقمي", "ماتسجل رقمي", "ما تحفظ رقمي", "ماتحفظ رقمي",
        "مش عاوز تسجلني", "مش عايز تسجلني", "ما ابغى اسجل", "ما ابي تسجلني", "لا اريد التسجيل",
        "ما اريدك تسجلني", "ما تشتيش تسجلني", "ماشتي تسجلني", "لا تشتي تسجلني"
    )

    // أنماط نفي الطلب أو نفي القول في الماضي (Compound Past Negation)
    private val PAST_COMPOUND_NEGATIONS = listOf(
        "ما قلت سجلني", "ماقلت سجلني", "انا ما قلت سجلني", "اناما قلت سجلني",
        "ما قلت لك سجلني", "ماقلت لك سجلني", "انا ما قلت لك سجلني",
        "ما طلبت تسجلني", "ماطلبت تسجلني", "انا ما طلبت تسجلني",
        "قلت له لا تسجلني", "قلتلك لا تسجلني", "قلت لك لا تسجلني",
        "ما قلت احفظني", "ماقلت احفظني", "ما طلبت تحفظني",
        "مين قال لك تسجلني", "من قالك تسجلني", "من قال لك سجلني",
        "مين قلك سجلني", "من قلك سجلني", "مين قالك تسجلني"
    )

    // عبارات الكتم ومنع الرد الصريح
    private val SILENCE_DIRECTIVES = listOf(
        "ممنوع ترد", "لا ترد", "لا تراسلني", "اسكت", "بلاش رد", "كافي كلام",
        "لا تكلمني", "ماتردش", "ما ترد", "ما تردش عليا", "لا ترد عليا", "ممنوع ترد عليا"
    )

    /**
     * التحقق مما إذا كانت الرسالة تحتوي على نفي صريح لأمر التسجيل أو الحفظ أو أمراً بالكتم.
     */
    fun isNegated(text: String): Boolean {
        if (text.isBlank()) return false
        val norm = ArabicMorphologyHelper.normalize(text)

        // 1. فحص عبارات الكتم الصريح
        if (SILENCE_DIRECTIVES.any { norm.contains(ArabicMorphologyHelper.normalize(it)) }) {
            return true
        }

        // 2. فحص النفي المباشر المقترن بأفعال التسجيل
        for (pattern in DIRECT_NEGATION_PREFIXES) {
            val normPattern = ArabicMorphologyHelper.normalize(pattern)
            if (norm.startsWith(normPattern) || norm.contains(" $normPattern")) {
                return true
            }
        }

        // 3. فحص نفي الطلب أو القول في الماضي
        for (pattern in PAST_COMPOUND_NEGATIONS) {
            val normPattern = ArabicMorphologyHelper.normalize(pattern)
            if (norm.contains(normPattern)) {
                return true
            }
        }

        // 4. فحص الأنماط التركيبية: أداة نفي + فاصل قصير + فعل تسجيل
        val negationWords = listOf("لا", "ما", "مش", "مو", "ماني", "لسنا", "لست", "بلاش", "ممنوع")
        val registrationWords = listOf("تسجلني", "تسجل", "تحفظني", "تحفظ", "تضيفني", "تضيف", "تخزني", "تخزن")

        val tokens = norm.split(Regex("""\s+""")).filter { it.isNotBlank() }
        for (i in 0 until tokens.size - 1) {
            if (tokens[i] in negationWords) {
                // الكلمة التالية مباشرة
                if (tokens[i + 1] in registrationWords) {
                    return true
                }
                // إذا كانت هناك كلمة وسيطة مثل "لا ابدا تسجلني" أو "لا عاد تسجلني" أو "لا ترجع تسجلني"
                if (i + 2 < tokens.size && tokens[i + 2] in registrationWords) {
                    val middle = tokens[i + 1]
                    if (middle in listOf("عاد", "ترجع", "ترد", "ابدا", "عادك", "تجي", "تروح", "تقوم")) {
                        return true
                    }
                }
            }
        }

        return false
    }
}
