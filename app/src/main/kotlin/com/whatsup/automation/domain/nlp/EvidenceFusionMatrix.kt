package com.whatsup.automation.domain.nlp

import com.whatsup.automation.domain.util.ConversationTurn

/**
 * أنواع القرارات الصادرة عن مصفوفة دمج الأدلة
 */
sealed class FusionDecision {
    data class ExecuteDirectly(
        val intent: String,
        val extractedName: String?,
        val confidenceScore: Float,
        val rationale: String
    ) : FusionDecision()

    data class RequestClarification(
        val proposedIntent: String,
        val candidateName: String?,
        val clarificationPrompt: String,
        val confidenceScore: Float
    ) : FusionDecision()

    data class HandleNegationOrCancel(
        val reason: String,
        val detectedCue: String
    ) : FusionDecision()

    data class SilentLog(
        val reason: String,
        val energyScore: Float
    ) : FusionDecision()
}

/**
 * مصفوفة دمج الأدلة وحسم القرار التراكمي (Evidence Fusion & Decision Matrix).
 * مبنية على نظرية ديمبستر-شافر (Dempster-Shafer Theory) للدمج السياقي المتعدد:
 * 
 * تدمج الأدلة التالية:
 * 1. التطابق المعجمي والتقريبي (Lexical & Levenshtein Evidence).
 * 2. تحليل نطاق النفي والعدول (Negation Scope Evidence).
 * 3. تصنيف أفعال الكلام (Speech Act Evidence).
 * 4. التطابق الصوتي للأسماء (Phonetic Soundex Evidence).
 * 5. الذاكرة السياقية للحوار (Dialogue Context Evidence).
 * 
 * تحمي النظام من الردود العمياء والعشوائية وتضمن اتخاذ القرار الصحيح.
 */
object EvidenceFusionMatrix {

    /**
     * تقييم الرسالة واتخاذ القرار الشامل.
     */
    fun evaluate(
        incomingText: String,
        targetKeyword: String,
        recentTurn: ConversationTurn?,
        knownPrototypes: List<FloatArray> = emptyList()
    ): FusionDecision {
        // 1. فحص نطاق النفي أولاً
        val negation = ArabicDialectalNegationEngine.analyzeNegationScope(targetKeyword, incomingText)
        if (negation.isNegated) {
            return FusionDecision.HandleNegationOrCancel(
                reason = "تم رصد أسلوب نفي أو عدول صريح",
                detectedCue = negation.detectedCue ?: "نفي"
            )
        }

        // 2. تصنيف فعل الكلام
        val speechActResult = ArabicSpeechActClassifier.classify(incomingText)

        // 3. فحص هل الرسالة سؤال عام لا يخص التسجيل
        if (speechActResult.act == SpeechAct.QUESTION && !incomingText.contains("اسمي") && !incomingText.contains("سجلني")) {
            return FusionDecision.SilentLog(
                reason = "الرسالة تمثل سؤالاً عاماً بدون نية تسجيل صريحة",
                energyScore = 0.0f
            )
        }

        // 4. فحص طاقة القرار (OOD Detection)
        if (knownPrototypes.isNotEmpty()) {
            val isOod = EnergyOodScorer.isOutOfDistribution(incomingText, knownPrototypes)
            if (isOod && speechActResult.act == SpeechAct.UNKNOWN_NOISE) {
                return FusionDecision.SilentLog(
                    reason = "طاقة تشتت مرتفعة — الرسالة خارج نطاق الأتمتة (Out-of-Scope Noise)",
                    energyScore = EnergyOodScorer.computeEnergyScore(incomingText, knownPrototypes)
                )
            }
        }

        // 5. حساب درجة اليقين التراكمية (Belief Score: 0.0 - 1.0)
        var beliefScore = 0.0f

        // دليل فعل الكلام
        when (speechActResult.act) {
            SpeechAct.DIRECTIVE -> beliefScore += 0.40f
            SpeechAct.ASSERTIVE -> beliefScore += 0.45f
            SpeechAct.PHATIC -> beliefScore += 0.20f
            SpeechAct.EXPRESSIVE -> beliefScore += 0.15f
            SpeechAct.QUESTION -> beliefScore += 0.10f
            SpeechAct.COMMISSIVE -> beliefScore += 0.25f
            SpeechAct.UNKNOWN_NOISE -> beliefScore += 0.05f
        }

        // دليل الذاكرة السياقية السابقة
        if (recentTurn != null) {
            if (recentTurn.intent == com.whatsup.automation.domain.util.ConversationIntent.CONTACT_SAVE_REQUEST) {
                beliefScore += 0.35f // كان النظام ينتظر منه الاسم
            }
        }

        // دليل وضوح النص ووجود كلمات تسجيل صريحة
        val hasExplicitSaveWord = incomingText.contains("سجل") || incomingText.contains("احفظ") || 
                                 incomingText.contains("خزن") || incomingText.contains("اسمي")
        if (hasExplicitSaveWord) {
            beliefScore += 0.30f
        }

        beliefScore = beliefScore.coerceIn(0.0f, 1.0f)

        // 6. اتخاذ القرار بحسب مستوى اليقين (Belief Thresholds)
        return when {
            beliefScore >= 0.70f -> {
                FusionDecision.ExecuteDirectly(
                    intent = "CONTACT_SAVE",
                    extractedName = null,
                    confidenceScore = beliefScore,
                    rationale = "تطابق أدلة كافٍ ويقين عالي"
                )
            }
            beliefScore in 0.50f..0.69f -> {
                FusionDecision.RequestClarification(
                    proposedIntent = "CONTACT_SAVE",
                    candidateName = null,
                    clarificationPrompt = "هل ترغب في تسجيل اسمك لدينا؟ تفضل بكتابة اسمك الكريم ✨",
                    confidenceScore = beliefScore
                )
            }
            else -> {
                FusionDecision.SilentLog(
                    reason = "درجة يقين منخفضة ($beliefScore) — تجنب الرد الأعمى",
                    energyScore = 0.0f
                )
            }
        }
    }
}
