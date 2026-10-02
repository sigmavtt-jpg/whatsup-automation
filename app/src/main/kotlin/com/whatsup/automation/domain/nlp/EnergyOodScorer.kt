package com.whatsup.automation.domain.nlp

import com.whatsup.automation.domain.util.ArabicMorphologyHelper
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * محرك كشف العينات خارج النطاق وحساب طاقة القرار (Energy-Based OOD Scorer).
 * مقتبس من أبحاث (Energy-based Out-of-distribution Detection - NeurIPS & EMNLP).
 * 
 * يحسب طاقة التشتت للرسالة لمنع الردود التلقائية العشوائية على السوالف العامة والضوضاء:
 * - تجزئة المقاطع الفرعية عبر خوارزمية MurmurHash3 (Subword Feature Hashing).
 * - حساب دالة الطاقة الحرة: E(x; T) = -T * ln( sum( exp( f_i(x) / T ) ) )
 * - عزل الرسائل ذات الطاقة المرتفعة وتوجيهها للحفظ الصامت (Silent Log) بدون أي رد.
 */
object EnergyOodScorer {

    private const val VECTOR_DIM = 256
    private const val DEFAULT_TEMPERATURE = 1.0f

    /**
     * تجزئة سلسلة نصية إلى فهرس محدد
     */
    private fun hashString(text: String): Int {
        var h = 0x811c9dc5.toInt()
        for (c in text) {
            h = (h xor c.code) * 0x01000193
        }
        return (h and 0x7FFFFFFF) % VECTOR_DIM
    }

    /**
     * توليد متجه الميزات الفرعية (Subword Character 2-gram & 3-gram Feature Vector) للنص
     */
    fun extractFeatureVector(rawText: String): FloatArray {
        val vector = FloatArray(VECTOR_DIM)
        val normalized = ArabicMorphologyHelper.normalize(rawText).trim()
        if (normalized.isBlank()) return vector

        val words = normalized.split(Regex("""[\s,،.:;!؟?/-]+""")).filter { it.isNotBlank() }

        for (word in words) {
            val stemmed = ArabicMorphologyHelper.stemWord(word)
            
            // 1. تجزئة الكلمة كاملة
            val wordHash = hashString(stemmed)
            vector[wordHash] += 1.0f

            // 2. تجزئة المقاطع الثنائية والثلاثية
            if (stemmed.length >= 2) {
                for (i in 0..stemmed.length - 2) {
                    val bigram = stemmed.substring(i, i + 2)
                    vector[hashString(bigram)] += 0.5f
                }
            }
            if (stemmed.length >= 3) {
                for (i in 0..stemmed.length - 3) {
                    val trigram = stemmed.substring(i, i + 3)
                    vector[hashString(trigram)] += 0.8f
                }
            }
        }

        // تسوية L2 Normalization
        var normSum = 0.0f
        for (v in vector) normSum += v * v
        val magnitude = sqrt(normSum.toDouble()).toFloat()
        if (magnitude > 0.0f) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }

        return vector
    }

    /**
     * حساب حاصل الضرب القياسي (Cosine Similarity) بين متجهين
     */
    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        var dot = 0.0f
        val len = minOf(vecA.size, vecB.size)
        for (i in 0 until len) {
            dot += vecA[i] * vecB[i]
        }
        return dot.coerceIn(0.0f, 1.0f)
    }

    /**
     * حساب طاقة القرار (Free Energy Score) للنص مقارنة بمراكز النوايا المعروفة.
     * كلما كانت الرسالة قريبة من النطاق (In-Distribution)، كانت الطاقة أكثر سلبية (أقل قيمة).
     * كلما كانت ضوضاء وخارج النطاق، كانت الطاقة أعلى.
     */
    fun computeEnergyScore(
        rawText: String,
        knownPrototypes: List<FloatArray>,
        temperature: Float = DEFAULT_TEMPERATURE
    ): Float {
        if (knownPrototypes.isEmpty()) return Float.MAX_VALUE
        val inputVector = extractFeatureVector(rawText)

        var sumExp = 0.0
        for (proto in knownPrototypes) {
            val sim = cosineSimilarity(inputVector, proto)
            val logit = sim * 10.0f
            sumExp += exp((logit / temperature).toDouble())
        }

        if (sumExp <= 0.0) return Float.MAX_VALUE
        return -temperature * ln(sumExp).toFloat()
    }

    /**
     * فحص هل الرسالة خارج نطاق الأتمتة تماماً (Out-of-Scope / OOD).
     */
    fun isOutOfDistribution(
        rawText: String,
        knownPrototypes: List<FloatArray>,
        energyThreshold: Float = -2.5f
    ): Boolean {
        val energy = computeEnergyScore(rawText, knownPrototypes)
        return energy > energyThreshold
    }
}
