package com.whatsup.automation.domain.util

import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

/**
 * محرك فك شفرة Spintax والمتغيرات الديناميكية وسياق الوقت.
 * يدعم توليد صياغات نصوص عشوائية ومتنوعة بالكامل لمنع تطابق بصمة الرسائل وحماية الحساب من الحظر.
 */
object SpintaxEngine {

    /**
     * معالجة نص كامل يتضمن كلاً من:
     * 1. فك خيارات Spintax مثل: {{أهلاً|مرحباً|حياك الله}} أو {أهلاً|مرحباً}
     * 2. تعويض المتغيرات الديناميكية: {name}, {first_name}, {phone}, {time_greeting}, {day_name}, {random_id}
     */
    fun process(
        template: String,
        recipientName: String = "",
        recipientPhone: String = "",
        customVariables: Map<String, String> = emptyMap()
    ): String {
        if (template.isBlank()) return ""

        // 1. فك Spintax أولاً
        val spunText = evaluateSpintax(template)

        // 2. تجهيز المتغيرات الديناميكية
        val cleanName = recipientName.trim().ifBlank { "صديقنا" }
        val firstName = cleanName.split(" ").firstOrNull()?.trim()?.ifBlank { "صديقنا" } ?: "صديقنا"
        val timeGreeting = getTimeBasedGreeting()
        val dayName = getArabicDayName()
        val randomId = Random.nextInt(10, 99).toString()

        var result = spunText
            .replace("{name}", cleanName, ignoreCase = true)
            .replace("{الاسم}", cleanName, ignoreCase = true)
            .replace("{first_name}", firstName, ignoreCase = true)
            .replace("{الاسم_الاول}", firstName, ignoreCase = true)
            .replace("{phone}", recipientPhone, ignoreCase = true)
            .replace("{الرقم}", recipientPhone, ignoreCase = true)
            .replace("{time_greeting}", timeGreeting, ignoreCase = true)
            .replace("{التحية}", timeGreeting, ignoreCase = true)
            .replace("{day_name}", dayName, ignoreCase = true)
            .replace("{اليوم}", dayName, ignoreCase = true)
            .replace("{random_id}", randomId, ignoreCase = true)

        // تعويض أي متغيرات مخصصة إضافية
        for ((key, value) in customVariables) {
            result = result.replace("{$key}", value, ignoreCase = true)
        }

        return result
    }

    /**
     * فك شفرة Spintax بشكل متكرر لدعم الصياغات المزدوجة والمتداخلة.
     * يدعم صيغ: {{خيار1|خيار2|خيار3}} و {خيار1|خيار2|خيار3}
     */
    fun evaluateSpintax(text: String): String {
        var current = text
        val spintaxRegex = Regex("""\{\{([^{}]+)\}\}""")
        val singleBraceSpintax = Regex("""\{([^{}|]+(?:\|[^{}|]+)+)\}""")

        var maxIterations = 10
        while (maxIterations > 0 && (spintaxRegex.containsMatchIn(current) || singleBraceSpintax.containsMatchIn(current))) {
            current = spintaxRegex.replace(current) { matchResult ->
                val options = matchResult.groupValues[1].split('|')
                options[Random.nextInt(options.size)].trim()
            }

            current = singleBraceSpintax.replace(current) { matchResult ->
                val options = matchResult.groupValues[1].split('|')
                options[Random.nextInt(options.size)].trim()
            }
            maxIterations--
        }

        return current
    }

    /**
     * تقسيم النص إلى قوالب دورية متعددة في حال قام المستخدم بوضع فاصل `---` بين الرسائل.
     */
    fun splitRotatingTemplates(rawText: String): List<String> {
        val templates = rawText.split(Regex("""(?m)^-{3,}\s*$"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return if (templates.isEmpty()) listOf(rawText) else templates
    }

    /**
     * اختيار القالب المناسب حسب رقم المستلم وحجم الدفعة (مثال: كل 10 مستلمين قالب مختلف).
     */
    fun getTemplateForIndex(rawText: String, recipientIndex: Int, batchSize: Int = 10): String {
        val templates = splitRotatingTemplates(rawText)
        if (templates.size <= 1) return rawText

        val effectiveBatch = if (batchSize <= 0) 10 else batchSize
        val templateIndex = (recipientIndex / effectiveBatch) % templates.size
        return templates[templateIndex]
    }

    /**
     * الحصول على تحية الوقت المناسبة بالعربية (صباح الخير / مساء الخير).
     */
    fun getTimeBasedGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (hour in 4..11) {
            "صباح الخير"
        } else if (hour in 12..16) {
            "مساء الخير"
        } else if (hour in 17..23) {
            "مساء النور والسرور"
        } else {
            "أهلاً وسهلاً"
        }
    }

    /**
     * الحصول على اسم اليوم باللغة العربية.
     */
    fun getArabicDayName(): String {
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return when (dayOfWeek) {
            Calendar.SATURDAY -> "السبت"
            Calendar.SUNDAY -> "الأحد"
            Calendar.MONDAY -> "الاثنين"
            Calendar.TUESDAY -> "الثلاثاء"
            Calendar.WEDNESDAY -> "الأربعاء"
            Calendar.THURSDAY -> "الخميس"
            Calendar.FRIDAY -> "الجمعة"
            else -> "اليوم"
        }
    }
}
