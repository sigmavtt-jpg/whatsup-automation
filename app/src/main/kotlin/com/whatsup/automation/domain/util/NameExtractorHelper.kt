package com.whatsup.automation.domain.util

/**
 * محرك استخراج الأسماء الشامل والمنقى (Universal Name & Emoji Extractor).
 * مصمم لالتقاط كافة صياغات تسجيل الأسماء في جميع اللهجات العربية
 * مع الحفاظ الكامل على كافة الإيموجي والرموز والزخارف (مثل: علي 😍💔😁😁💕👌£¥©²•é–→).
 * 
 * مزود بفلتر صارم لاستبعاد الكلمات المحظورة، والأسئلة، والتحايا، والأرقام الصرفة.
 */
object NameExtractorHelper {

    // قائمة الكلمات الدلالية الشاملة لكافة اللهجات العربية مرتبة بالأطول أولاً لتفادي القص الجزئي
    private val DEFAULT_TRIGGERS = listOf(
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
        "اسمي هو", "اسمي انا", "اسمي", "انا اسمي", "أنا اسمي",
        "معاك اخوك", "معك اخوك", "معاكم", "معكم", "معاك", "معك",
        "انا اخوك", "أنا اخوك", "انا اختك", "أنا أختك", "اختك", "أختك", "اخوك", "أخوك",
        "انا هو", "أنا هو", "انا", "أنا", "الاسم"
    ).sortedByDescending { it.length }

    // قائمة الكلمات المحظورة التي لا يمكن أن تكون اسماً بشرياً بمفردها
    private val INVALID_STOP_WORDS = setOf(
        "سجلني", "احفظني", "ضيفني", "خزنني", "سجل", "احفظ", "خزن", "ضيف",
        "سجلني عندك", "احفظني عندك", "ضيفني عندك", "خزنني عندك",
        "سجل اسمي", "احفظ اسمي", "خزن اسمي", "ضيف اسمي",
        "سجل رقمي", "احفظ رقمي", "خزن رقمي", "ضيف رقمي",
        "سجل بياناتي", "احفظ بياناتي", "خزن بياناتي",
        "اسمي", "الاسم", "بياناتي", "حسابي", "رقمي", "الرقم", "هاتفي",
        "تمام", "اوكي", "اوك", "اوكيه", "طيب", "شكرا", "شكراً", "مشكور", "تسلم",
        "كيفك", "كيف حالك", "شخبارك", "وينك", "وين", "هلا", "اهلين", "أهلاً",
        "مرحبا", "مرحباً", "سلام", "السلام", "عليكم", "وعليكم", "ورحمة", "ورحمه",
        "وبركاته", "وبركاتة", "الله", "خير", "خيرا", "بارك", "فيك", "فيكم",
        "يحفظك", "يعطيك", "العافية", "عافية", "صحة", "يسعد", "صباحك", "مساك",
        "أمان", "استودعكم", "والسرور", "والياسمين", "الورد", "الفل", "نورك", "منور", "منورة",
        "ايش", "شو", "ليه", "ليش", "بكم", "كم", "حبيبي", "يا غالي", "يا طيب", "ياخي", "اخوي", "يا اخ",
        "ولا شي", "مافي شي", "جزاك الله", "جزاك الله خير", "وصل", "خلاص",
        "ايوه", "نعم", "لا", "ماشي", "حياك", "حياك الله", "صباح الخير", "مساء الخير",
        "جمعة مباركة", "نقطة", "خدمة", "استفسار", "طلب", "عرض", "سؤال", "معلومات", "تفاصيل",
        "اتفقنا", "ابشر", "علم", "وصلت", "موافق", "صار", "تم"
    )

    /**
     * استخراج الاسم كاملاً من نص الرسالة.
     * يحتفظ بكافة الإيموجي والرموز والأشكال واللغات بدون أي تشويه أو حذف.
     */
    fun extractName(
        text: String,
        customKeywords: List<String> = emptyList(),
        fallbackSenderName: String = ""
    ): String {
        if (text.isBlank()) {
            return ""
        }

        var candidate = text.trim()
        var triggerMatched = false

        // دمج الكلمات المخصصة من المستخدم مع القائمة الافتراضية
        val allTriggers = (customKeywords.filter { it.isNotBlank() } + DEFAULT_TRIGGERS)
            .distinct()
            .sortedByDescending { it.length }

        for (trigger in allTriggers) {
            val escaped = Regex.escape(trigger.trim())
            // إذا كان النص هو نفس الكلمة المشغلة بمفردها (مثل "سجلني"، "احفظني")
            if (candidate.equals(trigger.trim(), ignoreCase = true) ||
                ArabicMorphologyHelper.normalize(candidate).equals(ArabicMorphologyHelper.normalize(trigger.trim()), ignoreCase = true)) {
                return ""
            }

            // 1. فحص البداية: ^trigger [:=,-]? name
            val startPattern = Regex("""(?i)^\s*$escaped\s*[:=,-]?\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
            val startMatch = startPattern.find(candidate)
            if (startMatch != null) {
                val remainder = startMatch.groupValues[1].trim()
                if (remainder.isBlank()) {
                    return ""
                }
                candidate = remainder
                triggerMatched = true
                break
            }

            // 2. فحص الكلمة في أي مكان في النص بحدود فاصلة تدعم العربية
            val anywherePattern = Regex("""(?i)(?:^|[\s,،.:;!؟?/-])$escaped\s*[:=,-]?\s*(.*)$""", RegexOption.DOT_MATCHES_ALL)
            val anywhereMatch = anywherePattern.find(candidate)
            if (anywhereMatch != null) {
                val remainder = anywhereMatch.groupValues[1].trim()
                if (remainder.isBlank()) {
                    return ""
                }
                candidate = remainder
                triggerMatched = true
                break
            }
        }

        // إذا لم تطابق أي كلمة مفتاحية صريحة:
        // نتحقق بدقة صارمة: هل النص عبارة عن اسم صريح بمفرده (1 إلى 5 كلمات نقية للأسماء المركبة)؟
        if (!triggerMatched) {
            val words = candidate.split(Regex("""\s+""")).filter { it.isNotBlank() }
            val isPureShortName = words.size in 1..5 &&
                    candidate.length <= 40 &&
                    !candidate.contains("؟") && !candidate.contains("?") &&
                    !candidate.contains("!") && !candidate.contains(":") &&
                    !hasConversationalTokens(candidate)

            if (!isPureShortName) {
                return ""
            }
        }

        // إزالة البادئات الشائعة وكلمات الحشو والاستدراك
        val fillerPrefixes = listOf(
            "قلت لك ", "ياخي ", "اقصد ", "قصدي ", "غلطت ", "يا طيب ", "يا غالي ",
            "يا اخ ", "يا اخي ", "باسم ", "باسم", "بـ ", "بـ", "هو ", "هو", "انه ", "اني ", "انا ", "أنا "
        )
        for (prefix in fillerPrefixes) {
            if (candidate.startsWith(prefix, ignoreCase = true)) {
                candidate = candidate.substring(prefix.length).trim()
            }
        }

        candidate = candidate.trimStart(':', '-', '=', ',', '،', ' ', '>')

        // اختزال تكرار الحروف في الاسم المرشح (مثل: اقصدددد -> اقصد، راكاننن -> راكان)
        candidate = Regex("""(.)\1{2,}""").replace(candidate, "$1")

        // إزالة ذيول المحادثات والاستفسارات والمدن الشائعة بعد الاسم
        val tailCutoffWords = listOf(
            " حبيت استفسر", " حبيت اسأل", " حبيت اطلب", " استشاري باطنية", " استشاري",
            " سجل رقمي عندك", " سجل رقمي", " سجل اسمي عندك", " سجل اسمي", " احفظ رقمي عندك", " احفظ رقمي",
            " من مارب", " من مأرب", " من صنعاء", " من عدن", " من حضرموت", " من شبوة", " من تعز", " من إب",
            " من الرياض", " من جدة", " من مكة", " من دبي", " من عمان", " من الشام", " من مصر",
            " يا غالي", " يا طيب", " يا اخ", " ياخي", " وبس", " فقط", " لو سمحت", " تسلم"
        )
        for (tail in tailCutoffWords) {
            val idx = candidate.indexOf(tail, ignoreCase = true)
            if (idx > 2) {
                candidate = candidate.substring(0, idx).trim()
            }
        }

        // إذا كان النص متعدد الأسطر، نأخذ السطر الأول
        val firstLine = candidate.lines().firstOrNull()?.trim() ?: candidate

        // تقليص الاسم إلى 4 كلمات كحد أقصى (للسماح بالألقاب مثل "الشيخ ناصر بن مبارك")
        val words = firstLine.split(Regex("""\s+""")).filter { it.isNotBlank() }
        val finalCandidate = if (words.size > 5 && firstLine.none { Character.isSurrogate(it) }) {
            words.take(4).joinToString(" ")
        } else {
            firstLine.take(50).trim()
        }

        return if (isValidHumanName(finalCandidate)) {
            finalCandidate
        } else {
            ""
        }
    }

    private fun hasConversationalTokens(text: String): Boolean {
        val norm = ArabicMorphologyHelper.normalize(text)
        val conversationalWords = setOf(
            "صليت", "باروح", "بروح", "اكمل", "يدخلو", "يدخلوا", "يسكتو", "يسكتوا", "مايقولو", "مايقولوا",
            "اسمع", "شوف", "شروف", "قال", "قلت", "حكيت", "سمعت", "رحت", "جيت", "مش", "مشو", "مشي",
            "ماليش", "مانشتي", "ماباش", "لا", "ما", "بلاش", "ممنوع", "ترد", "عليا", "علينا", "فيك",
            "فيني", "اللي", "الي", "واللي", "والي", "كل", "لما", "حتى", "علشان", "عشان", "تبعنا"
        )
        val tokens = norm.split(Regex("""[\s,،.:;!؟?/-]+""")).filter { it.isNotBlank() }
        return tokens.any { conversationalWords.contains(it) }
    }

    /**
     * فحص هل الاسم المستخرج صالح كاسم بشري حقيقي وليس كلمة دردشة أو رقماً أو سؤالاً أو إيموجي صرفة أو رموزاً عشوائية
     */
    fun isValidHumanName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length < 2) return false
        if (trimmed.contains("؟") || trimmed.contains("?")) return false
        if (isOnlyDigits(trimmed)) return false

        // 1. اشتراط وجود حرفين أبجديين حقيقيين على الأقل (عربي أو إنجليزي) لمنع الإيموجي الصرفة والرموز
        val letterCount = trimmed.count { (it in '\u0600'..'\u06FF') || (it in 'a'..'z') || (it in 'A'..'Z') }
        if (letterCount < 2) return false

        // 2. استبعاد النصوص التي تغلب عليها الرموز وعلامات الترقيم العشوائية (#@%#$@^$%)
        val nonLetterOrSpaceCount = trimmed.count { !it.isLetter() && !it.isWhitespace() && !Character.isSurrogate(it) }
        if (nonLetterOrSpaceCount > letterCount) return false

        val normalized = ArabicMorphologyHelper.normalize(trimmed).lowercase()
        if (INVALID_STOP_WORDS.contains(normalized)) return false

        // إذا كان عبارة عن كلمتين من الكلمات المحظورة مثل "يا غالي"
        val words = normalized.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.all { INVALID_STOP_WORDS.contains(it) }) return false

        return true
    }

    private fun isOnlyDigits(s: String): Boolean {
        val clean = s.replace("[^0-9]".toRegex(), "")
        return clean.length >= 7 && s.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }
    }

    private fun cleanFallbackName(fallback: String): String {
        val trimmed = fallback.trim()
        return if (trimmed.isNotBlank() && !trimmed.startsWith("+") && !trimmed.contains("lid", ignoreCase = true) && !isOnlyDigits(trimmed) && isValidHumanName(trimmed)) {
            trimmed
        } else {
            "جهة اتصال جديدة"
        }
    }
}
