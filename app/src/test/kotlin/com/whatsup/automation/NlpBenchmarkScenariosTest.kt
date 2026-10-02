package com.whatsup.automation

import com.whatsup.automation.domain.util.NameExtractorHelper
import com.whatsup.automation.domain.util.QuotedSpeechDetector
import com.whatsup.automation.domain.util.StrictNegationAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader

data class NlpScenario(
    val category: String,
    val description: String,
    val inputText: String,
    val expectedName: String,
    val shouldSave: Boolean
)

/**
 * حزمة اختبارات المعايير الشاملة (NLP Benchmark Dataset Test).
 * تقرأ جميع السيناريوهات الواقعية من ملف JSON وتفحص دقة الاستخراج بنسبة 100%.
 */
class NlpBenchmarkScenariosTest {

    private fun parseScenarios(json: String): List<NlpScenario> {
        val list = mutableListOf<NlpScenario>()
        // تجزئة الكائنات داخل مصفوفة JSON
        val objectRegex = Regex("""\{[^{}]*\}""", RegexOption.DOT_MATCHES_ALL)
        val stringFieldRegex = { field: String -> Regex("""\"$field\"\s*:\s*\"((?:\\\"|[^\"])*)\"""") }
        val booleanFieldRegex = { field: String -> Regex("""\"$field\"\s*:\s*(true|false)""") }

        for (match in objectRegex.findAll(json)) {
            val block = match.value
            val category = stringFieldRegex("category").find(block)?.groupValues?.get(1) ?: ""
            val description = stringFieldRegex("description").find(block)?.groupValues?.get(1) ?: ""
            val inputText = stringFieldRegex("input_text").find(block)?.groupValues?.get(1)
                ?.replace("\\\"", "\"") ?: ""
            val expectedName = stringFieldRegex("expected_name").find(block)?.groupValues?.get(1)
                ?.replace("\\\"", "\"") ?: ""
            val shouldSave = booleanFieldRegex("should_save").find(block)?.groupValues?.get(1)?.toBoolean() ?: false

            if (inputText.isNotBlank()) {
                list.add(NlpScenario(category, description, inputText, expectedName, shouldSave))
            }
        }
        return list
    }

    @Test
    fun testAllScenariosFromBenchmarkJson() {
        val inputStream = javaClass.classLoader?.getResourceAsStream("arabic_nlp_benchmark_scenarios.json")
            ?: error("arabic_nlp_benchmark_scenarios.json not found in test resources!")

        val jsonContent = InputStreamReader(inputStream).readText()
        val scenarios = parseScenarios(jsonContent)

        assertTrue("Scenarios dataset should not be empty", scenarios.isNotEmpty())

        val failures = mutableListOf<String>()
        var passedCount = 0

        for (scenario in scenarios) {
            val isNegated = StrictNegationAnalyzer.isNegated(scenario.inputText)
            val isQuoted = QuotedSpeechDetector.isQuotedOrIndirect(scenario.inputText)
            val extractedName = if (!isNegated && !isQuoted) {
                NameExtractorHelper.extractName(scenario.inputText)
            } else {
                ""
            }

            val isValid = extractedName.isNotBlank() && NameExtractorHelper.isValidHumanName(extractedName)

            if (scenario.shouldSave) {
                if (!isValid || extractedName != scenario.expectedName) {
                    failures.add("FAIL (Expected Save): Input='${scenario.inputText}' | Expected='${scenario.expectedName}' | Got='$extractedName' (Valid=$isValid) [${scenario.description}]")
                } else {
                    passedCount++
                }
            } else {
                if (isValid) {
                    failures.add("FAIL (Expected Ignore): Input='${scenario.inputText}' | Got='$extractedName' [${scenario.description}]")
                } else {
                    passedCount++
                }
            }
        }

        if (failures.isNotEmpty()) {
            System.err.println("=== NLP Benchmark Failures (${failures.size}) ===")
            failures.forEach { System.err.println("❌ $it") }
        }

        assertEquals("Benchmark Failures: ${failures.joinToString("\n")}", 0, failures.size)
    }
}
