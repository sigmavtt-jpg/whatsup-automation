package com.whatsup.automation.domain.util

/**
 * معالج الصرف العربي والتجذير الخفيف (Light-10 Stemmer & Normalizer).
 * يقوم بتطبيع الحروف وتجريد الكلمات من الزوائد وحروف العطف والجر والضمائر
 * لالتقاط كافة صياغات تسجيل الأسماء والردود التلقائية بأعلى دقة ممكنة.
 */
object ArabicMorphologyHelper {

    // التشكيل وعلامات التنوين والمد
    private val DIACRITICS_REGEX = Regex("""[\u064B-\u065F\u0670\u0640]""")

    // الحروف المزدوجة والأشكال المختلفة
    private val ALEF_REGEX = Regex("""[أإآٱ]""")
    private val YAA_REGEX = Regex("""[ىي]""")
    private val TAA_MARBUTA_REGEX = Regex("""[ة]""")

    // البادئات العربية الشائعة (حروف العطف، الجر، التعريف، وأحرف المضارعة والأمر)
    private val PREFIXES = listOf(
        "وال", "فال", "بال", "كال", "لل",
        "وس", "فس", "ول", "فل", "وب", "فب",
        "ال", "ت", "ي", "ن", "ا", "و", "ف", "ل", "ب", "س"
    )

    // اللواحق العربية الشائعة (ضمائر الملكية والمفعولية)
    private val SUFFIXES = listOf(
        "كما", "كم", "كن", "هما", "هم", "هن",
        "نا", "ني", "ها", "هم", "وا", "تم", "ين", "ون", "ات",
        "ي", "ك", "ه"
    )

    /**
     * تطبيع شامل للنص العربي:
     * - إزالة التشكيل وحركات الإعراب والتطويل (ـ).
     * - توحيد أشكال الألف (أ، إ، آ، ٱ -> ا).
     * - توحيد التاء المربوطة (ة -> ه).
     * - توحيد الياء والألف المقصورة (ى -> ي).
     * - تحويل الأرقام المشرقية (٠-٩) إلى أرقام معيارية (0-9).
     */
    fun normalize(text: String): String {
        if (text.isBlank()) return ""

        var normalized = DIACRITICS_REGEX.replace(text, "")
        normalized = ALEF_REGEX.replace(normalized, "ا")
        normalized = TAA_MARBUTA_REGEX.replace(normalized, "ه")
        normalized = YAA_REGEX.replace(normalized, "ي")

        // اختزال الحروف المكررة أكثر من مرتين (تطويل وإطناب) مثل "اقصدددد" -> "اقصد"
        normalized = Regex("""(.)\1{2,}""").replace(normalized, "$1")

        // تحويل الأرقام العربية الهندية
        val arabicDigits = "٠١٢٣٤٥٦٧٨٩"
        val standardDigits = "0123456789"
        val charArray = normalized.toCharArray()
        for (i in charArray.indices) {
            val idx = arabicDigits.indexOf(charArray[i])
            if (idx >= 0) {
                charArray[i] = standardDigits[idx]
            }
        }

        return String(charArray).trim()
    }

    /**
     * تجذير خفيف للكلمة العربية الواحدة (Light Stemming).
     * يزيل البادئات واللواحق دون إفساد أصل الكلمة.
     */
    fun stemWord(rawWord: String): String {
        var word = normalize(rawWord).trim()
        if (word.length <= 3) return word

        // 1. إزالة البادئات
        for (prefix in PREFIXES) {
            if (word.startsWith(prefix) && word.length - prefix.length >= 3) {
                word = word.removePrefix(prefix)
                break
            }
        }

        // 2. إزالة اللواحق
        for (suffix in SUFFIXES) {
            if (word.endsWith(suffix) && word.length - suffix.length >= 3) {
                word = word.removeSuffix(suffix)
                break
            }
        }

        return word
    }

    /**
     * التحقق مما إذا كانت الكلمة المفتاحية موجودة في النص مع مراعاة كافة التصريفات والزوائد.
     * يمنع منعاً باتاً المطابقة المعكوسة (كالتحقق مما إذا كانت الكلمة المفتاحية تحتوي على كلمة قصيرة من النص مثل "لا").
     */
    fun isFuzzyMatch(keyword: String, text: String): Boolean {
        val normKeyword = normalize(keyword).trim()
        val normText = normalize(text).trim()

        if (normKeyword.isBlank() || normText.isBlank()) return false

        // 1. مطابقة مباشرة بحدود واضحة تدعم اللغة العربية تماماً
        // نتحقق من وجود الكلمة محاطة ببداية/نهاية النص أو مسافات/علامات ترقيم
        val directPattern = Regex("""(?:^|[\s,،.:;!؟?/-])${Regex.escape(normKeyword)}(?:$|[\s,،.:;!؟?/-])""", RegexOption.IGNORE_CASE)
        if (directPattern.containsMatchIn(normText)) {
            return true
        }

        val stemKeyword = stemWord(normKeyword)
        val tokens = normText.split(Regex("""[\s,،.:;!؟?/-]+""")).filter { it.isNotBlank() }

        // الكلمات القصيرة جداً (أقل من 3 أحرف مثل "لا"، "ما"، "هو") تتطلب مطابقة دقيقة تامة فقط
        if (stemKeyword.length < 3) {
            return tokens.any { it == normKeyword }
        }

        return tokens.any { token ->
            val stem = stemWord(token)
            if (stem.length < 3) return@any false

            // تطابق الجذع بدقة تامة
            if (stem == stemKeyword) return@any true

            // تطابق الكلمة بالبادئة فقط إذا كان الجذع طويلاً (4 أحرف فأكثر) لتجنب الخلط
            if (stemKeyword.length >= 4 && (stem.startsWith(stemKeyword) || token.startsWith(normKeyword))) {
                return@any true
            }

            false
        }
    }
}
