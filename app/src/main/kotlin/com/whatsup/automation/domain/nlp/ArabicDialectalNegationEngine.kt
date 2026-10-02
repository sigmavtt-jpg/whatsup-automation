package com.whatsup.automation.domain.nlp

import com.whatsup.automation.domain.util.ArabicMorphologyHelper

/**
 * محرك كشف النفي والعدول واللهجات العربية (Arabic Dialectal Negation Scope & Cue Engine).
 * مقتبس من أبحاث مؤتمرات معالجة اللغات الطبيعية (WANLP / ArabicNLP / ACL).
 * 
 * يعالج مشكلة النفي والعدول بكافة اللهجات العربية (اليمنية، الخليجية، المصرية، الشامية، المغربية):
 * - أدوات النفي الصريحة والمركبة مع مراعاة حدود الكلمات (Word Boundaries).
 * - نافذة النطاق الانزلاقية (Scope Window Lattice).
 * - كشف سياقات نقل الكلام وسرد القصص (Quoted/Hearsay Narration).
 * - كشف النفي المزدوج (Double Negation).
 */
object ArabicDialectalNegationEngine {

    private val NEGATION_CUES = listOf(
        // يمني
        "مانشتيش", "ما نشتيش", "مانشتي", "ما نشتي", "ماباش", "ما باش", "ماشيش", "ما شيش",
        "مشو", "مشي", "ماليش", "مافيش", "ما بلا",
        // خليجي
        "ما ابغى", "ماابغى", "ما ودي", "ماودي", "ما نبي", "مانبي", "مو لازم", "ماله داعي", "ما ابغا", "ماابغا",
        // مصري
        "مش عايز", "مش عاوز", "مش حابب", "بلاش", "اوعى", "إوعى", "مش عاوزك", "مش عايزك",
        // شامي
        "ما بدي", "مابدي", "ما رح", "مو هيك", "بلاها", "ماني",
        // مغاربي
        "ما بغيتش", "مابغيتش", "ما خصنيش",
        // فصحى وعامة
        "لا", "لم", "لن", "ليس", "غير", "بدون", "بلا", "إياك", "اياك", "ممنوع", "حذار", "مش", "مو", "ما"
    ).sortedByDescending { it.length }

    private val HEARSAY_VERBS = listOf(
        "قلت له", "قلتله", "قلت لها", "قلتلها", "قلت لهم", "قلتلهم", "قلت",
        "قال لي", "قالي", "قالوا لي", "قالولي", "قال", "قالت", "قالوا",
        "حكيت له", "حكيتله", "حكالي", "يحكي", "يقول", "تقول", "سمعت", "يقولوا"
    ).sortedByDescending { it.length }

    data class NegationAnalysis(
        val isNegated: Boolean,
        val isHearsay: Boolean,
        val detectedCue: String? = null,
        val negatedSpan: String? = null,
        val confidence: Float = 0.0f
    )

    fun analyzeNegationScope(
        targetKeyword: String,
        fullText: String,
        scopeWindowWords: Int = 4
    ): NegationAnalysis {
        val normTarget = ArabicMorphologyHelper.normalize(targetKeyword).trim().lowercase()
        val normText = ArabicMorphologyHelper.normalize(fullText).trim().lowercase()

        if (normTarget.isBlank() || normText.isBlank()) {
            return NegationAnalysis(isNegated = false, isHearsay = false)
        }

        if (normText == normTarget) {
            return NegationAnalysis(isNegated = false, isHearsay = false)
        }

        // 1. فحص سياق نقل الكلام والحكايات
        var detectedHearsay = false
        for (verb in HEARSAY_VERBS) {
            val normVerb = ArabicMorphologyHelper.normalize(verb)
            val regex = Regex("""\b$normVerb\s+(?:انه\s+|له\s+|لها\s+)?(?:['"«])?.*$normTarget""", RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(normText)) {
                detectedHearsay = true
                break
            }
        }

        // 2. صيغة لا...ولا
        val neitherNorRegex = Regex("""\bلا\s+.*$normTarget.*ولا\b|\bلا\s+$normTarget\s+ولا\b""", RegexOption.IGNORE_CASE)
        if (neitherNorRegex.containsMatchIn(normText)) {
            return NegationAnalysis(
                isNegated = true,
                isHearsay = detectedHearsay,
                detectedCue = "لا...ولا",
                negatedSpan = normText,
                confidence = 0.95f
            )
        }

        // 3. فحص أدوات النفي مع الالتزام الصارم بحدود الكلمات (Token-level matching)
        val tokens = normText.split(Regex("""[\s,،.:;!؟?/-]+""")).filter { it.isNotBlank() }
        val targetStem = ArabicMorphologyHelper.stemWord(normTarget)

        for (i in tokens.indices) {
            val currentToken = tokens[i]

            // مطابقة أدوات النفي ككلمة مستقلة أو كبداية لعبارة مركبة
            var matchedCue: String? = null
            var cueTokenCount = 1

            for (cue in NEGATION_CUES) {
                val normCue = ArabicMorphologyHelper.normalize(cue)
                if (normCue.contains(" ")) {
                    val cueWords = normCue.split(" ")
                    if (i + cueWords.size <= tokens.size) {
                        val subTokens = tokens.subList(i, i + cueWords.size).joinToString(" ")
                        if (subTokens == normCue || subTokens.startsWith(normCue)) {
                            matchedCue = cue
                            cueTokenCount = cueWords.size
                            break
                        }
                    }
                } else {
                    if (currentToken == normCue) {
                        matchedCue = cue
                        cueTokenCount = 1
                        break
                    }
                }
            }

            if (matchedCue != null) {
                val scopeStartIndex = i + cueTokenCount
                val scopeEndIndex = (scopeStartIndex + scopeWindowWords).coerceAtMost(tokens.size)

                if (scopeStartIndex < tokens.size) {
                    val windowTokens = tokens.subList(scopeStartIndex, scopeEndIndex)

                    val matchesTarget = windowTokens.any { word ->
                        val wordStem = ArabicMorphologyHelper.stemWord(word)
                        word == normTarget || word.contains(normTarget) || normTarget.contains(word) ||
                        wordStem == targetStem || wordStem.contains(targetStem) || targetStem.contains(wordStem) ||
                        (wordStem.contains("سجل") && targetStem.contains("سجل")) ||
                        (wordStem.contains("حفظ") && targetStem.contains("حفظ")) ||
                        (wordStem.contains("ضيف") && targetStem.contains("ضيف"))
                    }

                    if (matchesTarget) {
                        val isDoubleNegative = windowTokens.firstOrNull() in listOf("مشكلة", "غلط", "مانع", "عيب")
                        if (!isDoubleNegative) {
                            return NegationAnalysis(
                                isNegated = true,
                                isHearsay = detectedHearsay,
                                detectedCue = matchedCue,
                                negatedSpan = windowTokens.joinToString(" "),
                                confidence = 0.95f
                            )
                        }
                    }
                }
            }
        }

        return NegationAnalysis(
            isNegated = false,
            isHearsay = detectedHearsay,
            confidence = if (detectedHearsay) 0.8f else 0.1f
        )
    }
}
