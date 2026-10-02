package com.whatsup.automation.domain.nlp

import com.whatsup.automation.domain.util.ArabicMorphologyHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * آلة ليفنشتاين مع شجرة البادئات (Schulz & Mihov Levenshtein Automata + Trie).
 * مقتبسة من الورقة البحثية الكلاسيكية (Fast String Correction with Levenshtein-Automata).
 * 
 * تتيح البحث التقريبي (Fuzzy Search) في قواميس الكلمات المفتاحية والأسماء
 * بمسافة تعديل أقصاها d <= 2 في زمن خطي O(|W|) بدلاً من الحلقات التربيعية البطيئة O(M*N).
 */
class LevenshteinTrieAutomaton(
    initialKeywords: Collection<String> = emptyList()
) {

    private class TrieNode {
        val children: MutableMap<Char, TrieNode> = HashMap()
        var isTerminal: Boolean = false
        var originalWord: String? = null
    }

    private val root = TrieNode()
    private val normalizedLookup = ConcurrentHashMap<String, String>()

    init {
        insertAll(initialKeywords)
    }

    /**
     * إدراج كلمة في شجرة البادئات
     */
    fun insert(rawKeyword: String) {
        val normalized = ArabicMorphologyHelper.normalize(rawKeyword).trim()
        if (normalized.isBlank()) return

        normalizedLookup[normalized] = rawKeyword

        var current = root
        for (ch in normalized) {
            current = current.children.getOrPut(ch) { TrieNode() }
        }
        current.isTerminal = true
        current.originalWord = rawKeyword
    }

    /**
     * إدراج مجموعة كلمات دفعة واحدة
     */
    fun insertAll(keywords: Collection<String>) {
        for (kw in keywords) {
            insert(kw)
        }
    }

    data class FuzzyMatchResult(
        val matchedKeyword: String,
        val editDistance: Int,
        val similarity: Float
    )

    /**
     * البحث عن أقرب الكلمات المطابقة لكلمة الإدخال مع مسافة تعديل أقصاها maxDistance (افتراضياً 2).
     */
    fun search(inputWord: String, maxDistance: Int = 2): List<FuzzyMatchResult> {
        val normalized = ArabicMorphologyHelper.normalize(inputWord).trim()
        if (normalized.isBlank()) return emptyList()

        val results = mutableListOf<FuzzyMatchResult>()
        val initialRow = (0..normalized.length).toList()

        for ((ch, childNode) in root.children) {
            searchRecursive(
                node = childNode,
                ch = ch,
                word = normalized,
                previousRow = initialRow,
                results = results,
                maxDistance = maxDistance
            )
        }

        return results.sortedBy { it.editDistance }
    }

    private fun searchRecursive(
        node: TrieNode,
        ch: Char,
        word: String,
        previousRow: List<Int>,
        results: MutableList<FuzzyMatchResult>,
        maxDistance: Int
    ) {
        val columns = word.length + 1
        val currentRow = ArrayList<Int>(columns)
        currentRow.add(previousRow[0] + 1)

        for (col in 1 until columns) {
            val insertCost = currentRow[col - 1] + 1
            val deleteCost = previousRow[col] + 1
            val replaceCost = if (word[col - 1] == ch) {
                previousRow[col - 1]
            } else {
                previousRow[col - 1] + 1
            }

            currentRow.add(minOf(insertCost, deleteCost, replaceCost))
        }

        // إذا كانت العقدة كلمة كاملة ومسافة التعديل ضمن الحد المسموح
        if (currentRow.last() <= maxDistance && node.isTerminal && node.originalWord != null) {
            val dist = currentRow.last()
            val maxLen = maxOf(word.length, node.originalWord!!.length).coerceAtLeast(1)
            val sim = (1.0f - (dist.toFloat() / maxLen)).coerceIn(0.0f, 1.0f)
            results.add(FuzzyMatchResult(node.originalWord!!, dist, sim))
        }

        // تقليم الشجرة (Pruning): إذا كان أصغر عنصر في الصف <= maxDistance نستمر في التفرع
        if (currentRow.minOrNull() ?: Int.MAX_VALUE <= maxDistance) {
            for ((nextChar, nextNode) in node.children) {
                searchRecursive(
                    node = nextNode,
                    ch = nextChar,
                    word = word,
                    previousRow = currentRow,
                    results = results,
                    maxDistance = maxDistance
                )
            }
        }
    }

    /**
     * فحص سريع هل يوجد أي تطابق تقريبي في النص
     */
    fun hasFuzzyMatch(text: String, maxDistance: Int = 1): Boolean {
        val words = ArabicMorphologyHelper.normalize(text)
            .split(Regex("""[\s,،.:;!؟?/-]+"""))
            .filter { it.isNotBlank() }

        for (w in words) {
            if (search(w, maxDistance).isNotEmpty()) {
                return true
            }
        }
        return false
    }
}
