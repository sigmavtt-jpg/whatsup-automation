package com.whatsup.automation.domain.util

/**
 * كاشف الخطاب المنقول والسرد غير المباشر (Quoted & Narrative Speech Detector).
 * يميز بين الأمر المباشر الموجه للبوت لحفظ الاسم، وبين الجمل السردية التي تروي حواراً مع طرف ثالث.
 */
object QuotedSpeechDetector {

    // بوادئ وأفعال القول والنقل للطرف الثالث
    private val THIRD_PERSON_NARRATIVE_PREFIXES = listOf(
        "قال لي", "قالي", "قالك", "قال لك", "قال له", "قالت لي", "قالت",
        "قلت له", "قلتله", "وقلت له", "وقلتله", "قلت لها", "وقلت لها",
        "كنت اكلم", "كنت اتكلم", "كنت اقول", "كنت اقل", "كنت احاكي",
        "واحد قال", "واحد كتب", "واحد ارسل", "واحد كلمني", "شخص قال", "شخص ارسل", "صاحبي قال",
        "هو قال", "هي قالت", "هم قالوا", "فلان قال", "الرجال قال",
        "يقول لي", "يقولي", "تقول لي", "تقولي", "كلمني وقال", "كلمني", "اتصل بي", "رسل لي", "ارسل لي",
        "قال احفظني", "قال سجلني", "وقال احفظني", "وقال سجلني"
    )

    // الأسئلة الميتادلالية حول الأوامر (مثل: هل كلمة سجلني تعتبر أمر؟)
    private val META_QUESTION_PATTERNS = listOf(
        "هل كلمة", "هل يعتبر", "ايش معنى", "شو معنى", "كيف يعني", "ليش تسجلني", "لماذا تسجلني",
        "مين قلك", "من قالك", "باي حق", "ليش سجلتني"
    )

    /**
     * التحقق مما إذا كان النص يمثل كلاماً منقولاً أو سرداً لحوار مع طرف ثالث وليس أمراً مباشراً.
     */
    fun isQuotedOrIndirect(text: String): Boolean {
        if (text.isBlank()) return false
        val norm = ArabicMorphologyHelper.normalize(text)

        // 1. فحص أسئلة الاستفسار الميتا أو الإنكار
        for (pattern in META_QUESTION_PATTERNS) {
            val normPattern = ArabicMorphologyHelper.normalize(pattern)
            if (norm.contains(normPattern)) {
                return true
            }
        }

        // 2. إذا كانت الرسالة عبارة عن سؤال مجرد متبوع بعلامة استفهام "سجلني؟" بدون اسم
        val cleanNoSymbols = norm.replace("?", "").replace("؟", "").trim()
        if ((norm.endsWith("?") || norm.endsWith("؟")) && cleanNoSymbols in listOf("سجلني", "احفظني", "ضيفني", "خزنني")) {
            return true
        }

        // 3. فحص بوادئ السرد ونقل القول المقترنة بكلمات التسجيل
        for (prefix in THIRD_PERSON_NARRATIVE_PREFIXES) {
            val normPrefix = ArabicMorphologyHelper.normalize(prefix)
            if (norm.contains(normPrefix)) {
                // إذا وردت بادئة النقل قبل أو مع فعل التسجيل
                val registrationWords = listOf("سجلني", "احفظني", "ضيفني", "خزنني", "سجل", "احفظ", "خزن")
                if (registrationWords.any { norm.contains(ArabicMorphologyHelper.normalize(it)) }) {
                    return true
                }
            }
        }

        // 4. فحص الأنماط المنقولة بواسطة فواصل أو تنصيص: مثل قال "سجلني"
        if (text.contains("\"") || text.contains("«") || text.contains("“") || text.contains("'")) {
            for (prefix in THIRD_PERSON_NARRATIVE_PREFIXES) {
                if (norm.contains(ArabicMorphologyHelper.normalize(prefix))) {
                    return true
                }
            }
        }

        return false
    }
}
