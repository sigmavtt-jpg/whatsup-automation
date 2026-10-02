package com.whatsup.automation.domain.nlp

import com.whatsup.automation.domain.util.ArabicMorphologyHelper

/**
 * محرك الصوتيات والتجانس اللفظي العربي (Arabic Phonetic Soundex with Makharij Rules).
 * مستوحى من أبحاث الصوتيات الحاسوبية ومكتبة arSoundex.
 * 
 * يحل معضلة كتابة الأسماء والكلمات بطرق إملائية متباينة أو مشوهة صوتياً:
 * (مثال: محمد / مهند, علا / علاء, ضياء / ضيا, طه / طها, اسماعيل / إسمعيل).
 */
object ArabicPhoneticSoundex {

    /**
     * تحويل الحرف العربي إلى رمز العائلة الصوتية ومخرج الحرف:
     * - 0: الألفات والهمزات والمدود
     * - 1: الحروف الشفوية (ب، ف، م، و)
     * - 2: الحروف الحلقية (ح، خ، ع، غ، هـ)
     * - 3: الحروف اللسانية والأسنانية والتفشي (ت، ث، د، ذ، ز، س، ش، ص، ض، ط، ظ)
     * - 4: الحروف الذلقية (ر، ل، ن)
     * - 5: أحرف أقصى ووسط اللسان واللهوية (ق، ك، ج، ي)
     */
    private fun getPhoneticDigit(c: Char): Char? {
        return when (c) {
            'ا', 'أ', 'إ', 'آ', 'ٱ', 'ء', 'ئ', 'ؤ', 'ى' -> '0'
            'ب', 'ف', 'م', 'و' -> '1'
            'ح', 'خ', 'ع', 'غ', 'ه', 'ة' -> '2'
            'ت', 'ث', 'د', 'ذ', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ' -> '3'
            'ر', 'ل', 'ن' -> '4'
            'ق', 'ك', 'ج', 'ي' -> '5'
            else -> null
        }
    }

    /**
     * توليد الكود الصوتي (Phonetic Hash) للكلمة العربية.
     * يتكون من أول حرف حقيقي متبوعاً بـ 3 إلى 5 خانات صوتية بدون تكرار متتالي.
     */
    fun computePhoneticCode(rawWord: String, codeLength: Int = 4): String {
        val normalized = ArabicMorphologyHelper.normalize(rawWord).trim()
        if (normalized.isBlank()) return ""

        val firstChar = normalized.first()
        val firstDigit = getPhoneticDigit(firstChar) ?: '0'

        val codeBuilder = StringBuilder()
        codeBuilder.append(firstChar)

        var lastDigit = firstDigit

        for (i in 1 until normalized.length) {
            val c = normalized[i]
            val digit = getPhoneticDigit(c) ?: continue

            // تجنب تكرار نفس الفئة الصوتية المتتالية (De-duplication)
            // وتجاهل الهمزات وحروف العلة الداخلية في الحساب الصوتي
            if (digit != lastDigit && digit != '0') {
                codeBuilder.append(digit)
                lastDigit = digit
            }

            if (codeBuilder.length >= codeLength) break
        }

        // إكمال الطول بالخانة المحايدة '0' إذا كانت الكلمة قصيرة
        while (codeBuilder.length < codeLength) {
            codeBuilder.append('0')
        }

        return codeBuilder.toString()
    }

    /**
     * حساب نسبة التطابق الصوتي بين كلمتين أو اسمين (Phonetic Match Score: 0.0 - 1.0).
     */
    fun getPhoneticSimilarity(word1: String, word2: String): Float {
        val norm1 = ArabicMorphologyHelper.normalize(word1).trim()
        val norm2 = ArabicMorphologyHelper.normalize(word2).trim()

        if (norm1 == norm2) return 1.0f
        if (norm1.isBlank() || norm2.isBlank()) return 0.0f

        val code1 = computePhoneticCode(norm1)
        val code2 = computePhoneticCode(norm2)

        if (code1 == code2) return 0.95f

        // حساب عدد الخانات الصوتية المتطابقة
        var matchingDigits = 0
        val minLen = minOf(code1.length, code2.length)
        for (i in 0 until minLen) {
            if (code1[i] == code2[i]) {
                matchingDigits++
            }
        }

        return (matchingDigits.toFloat() / minLen.coerceAtLeast(1)).coerceIn(0.0f, 1.0f)
    }

    /**
     * هل الاسمان متطابقان صوتياً بدرجة موثوقة (≥ 75%)؟
     */
    fun isPhoneticMatch(word1: String, word2: String, threshold: Float = 0.75f): Boolean {
        return getPhoneticSimilarity(word1, word2) >= threshold
    }
}
