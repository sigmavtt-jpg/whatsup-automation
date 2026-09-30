package com.whatsup.automation.domain.usecase

import com.whatsup.automation.domain.model.*
import com.whatsup.automation.domain.repository.LogRepository
import com.whatsup.automation.domain.repository.RuleRepository
import com.whatsup.automation.data.local.contacts.DeviceContactsManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * حالة الاستخدام الرئيسية: معالجة الرسائل الواردة.
 *
 * تمثل القسم 5 من وثيقة الآلية — تمرر كل رسالة على القواعد بالترتيب
 * وتنفّذ أول قاعدة مطابقة ثم تتوقف.
 */

@Singleton
class ProcessIncomingMessageUseCase @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val matchRuleUseCase: MatchRuleUseCase,
    private val logRepository: LogRepository,
    private val deviceContactsManager: DeviceContactsManager,
    private val contactFormattingRepository: com.whatsup.automation.domain.repository.ContactFormattingRepository
) {
    suspend fun execute(message: IncomingMessage): ProcessResult {
        if (message.isGroupMessage || 
            message.senderPhone.contains("@newsletter") || 
            message.senderPhone.contains("@broadcast") ||
            message.senderPhone.contains("newsletter") ||
            message.senderPhone.isBlank()) {
            return ProcessResult.Ignored("تجاهل — رسالة مجموعة أو قناة إخبارية")
        }

        val rules = matchRuleUseCase.getEnabledRulesSorted()

        for (rule in rules) {
            if (matchRuleUseCase.matches(rule, message.text)) {
                return executeRuleActions(rule, message)
            }
        }

        // تجاهل صامت تماماً بدون أي كتابة في قاعدة البيانات إذا لم تطابق أي قاعدة
        return ProcessResult.NoMatch
    }

    private suspend fun executeRuleActions(
        rule: Rule,
        message: IncomingMessage
    ): ProcessResult {
        val targetPhone = message.realPhone ?: message.senderPhone
        val cleanPhone = targetPhone.replace("[^0-9]".toRegex(), "")
        val isSenderLid = message.isLid || deviceContactsManager.isLid(targetPhone)
        val results = mutableListOf<ActionResult>()
        var extractedContactName: String? = null

        for (action in rule.actions) {
            when (action) {
                is RuleAction.SendReply -> {
                    val replyOptions = action.message.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    val finalReply = if (replyOptions.isNotEmpty()) {
                        replyOptions.random()
                    } else {
                        action.message
                    }
                    results.add(ActionResult.ReplySent(finalReply))
                }

                is RuleAction.SaveContactAndReply -> {
                    // استخراج الاسم من الرسالة بذكاء
                    val registerPattern = Regex("""(سجلني عندك|سجل اسمي|احفظ رقمي|اسمي هو|سجلني|احفظني|اسمي)\s*[:=,-]?\s*([a-zA-Z\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]+(?:\s+[a-zA-Z\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]+)*)""")
                    val match = registerPattern.find(message.text)
                    val rawExtracted = if (match != null) {
                        match.groupValues[2].trim()
                    } else {
                        // استخراج الكلمات بعد الكلمة المفتاحية في القاعدة
                        var textWithoutPattern = message.text
                        rule.patternValue.split(',', '،').forEach { kw ->
                            textWithoutPattern = textWithoutPattern.replace(kw.trim(), "", ignoreCase = true)
                        }
                        textWithoutPattern.trim(':', '-', '=', ',', '،', ' ')
                    }

                    val nameCandidate = if (rawExtracted.startsWith("باسم ") && rawExtracted.length > 5) {
                        rawExtracted.removePrefix("باسم ").trim()
                    } else if (rawExtracted.startsWith("باسم") && rawExtracted.length > 4) {
                        rawExtracted.removePrefix("باسم").trim()
                    } else {
                        rawExtracted
                    }

                    val finalExtractedName = if (nameCandidate.length in 2..35 && !nameCandidate.any { it.isDigit() }) {
                        nameCandidate
                    } else {
                        message.senderName.takeIf { it.isNotBlank() && !it.startsWith("+") } ?: "جهة اتصال جديدة"
                    }

                    extractedContactName = finalExtractedName

                    // منع حفظ معرفات LID بدون رقم هاتف حقيقي
                    if (isSenderLid && message.realPhone.isNullOrBlank()) {
                        val replyMsg = "مرحباً بك! يرجى إرسال رقم هاتفك لتسجيلك بنجاح ✅"
                        results.add(ActionResult.ReplySent(replyMsg))
                    } else {
                        val formattingSettings = try {
                            contactFormattingRepository.getSettings().first()
                        } catch (_: Exception) {
                            ContactFormattingSettings()
                        }

                        // الاسم المنسق النظيف لدفتر الهاتف
                        val phonebookFormattedName = formattingSettings.formatForPhonebook(finalExtractedName)

                        val existingName = deviceContactsManager.getContactDisplayName(targetPhone)
                            ?: deviceContactsManager.getContactDisplayName(cleanPhone)

                        if (existingName != null && (existingName.equals(finalExtractedName, ignoreCase = true) || existingName.equals(phonebookFormattedName, ignoreCase = true))) {
                            // مسجل مسبقاً
                            val replyTemplate = action.replyMessage.ifBlank { "أنت مسجل لدينا بالفعل باسم {name} ✅" }
                            val finalReply = replyTemplate.replace("{name}", finalExtractedName)
                            results.add(ActionResult.ReplySent(finalReply))
                        } else {
                            val saved = deviceContactsManager.saveOrUpdateContact(phonebookFormattedName, targetPhone)
                            if (saved) {
                                val replyTemplate = action.replyMessage.ifBlank { "تم حفظك باسم {name} بنجاح ✅" }
                                val finalReply = replyTemplate.replace("{name}", finalExtractedName)
                                results.add(ActionResult.ReplySent(finalReply))
                            } else {
                                val replyMsg = "عذراً، تعذر حفظ جهة الاتصال حالياً ⚠️"
                                results.add(ActionResult.ReplySent(replyMsg))
                            }
                        }
                    }
                }
            }
        }

        val actionDesc = results.joinToString(" + ") { it.description }
        val hasError = results.any { it is ActionResult.Error }
        val logStatus = if (hasError) LogStatus.FAILURE else LogStatus.SUCCESS

        val contactName = extractedContactName
            ?: deviceContactsManager.getContactDisplayName(targetPhone)
            ?: message.senderName.takeIf { it.isNotBlank() }

        logRepository.insertLog(
            ActivityLog(
                senderPhone = targetPhone,
                messageText = message.text,
                matchedRule = "${rule.name} (${rule.patternValue})",
                actionExecuted = actionDesc,
                status = logStatus,
                extractedName = contactName
            )
        )

        return ProcessResult.Executed(rule.name, results)
    }
}

/**
 * حالة استخدام: مطابقة القواعد مع نص الرسالة.
 */
@Singleton
class MatchRuleUseCase @Inject constructor(
    private val ruleRepository: RuleRepository
) {
    /**
     * جلب القواعد المفعّلة مرتبة بالأولوية (الأقل رقماً = الأعلى أولوية).
     */
    suspend fun getEnabledRulesSorted(): List<Rule> {
        return ruleRepository.getEnabledRules().first().sortedBy { it.priority }
    }

    /**
     * فحص ما إذا كانت القاعدة تطابق النص المعطى.
     * يدعم إدخال عدة كلمات مفتاحية مفصولة بفواصل (، أو ,) أو أسطر جديدة.
     */
    fun matches(rule: Rule, text: String): Boolean {
        val subPatterns = rule.patternValue.split(',', '،', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (subPatterns.isEmpty()) return false

        return subPatterns.any { pattern ->
            when (rule.patternType) {
                PatternType.CONTAINS -> text.contains(pattern, ignoreCase = true)
                PatternType.STARTS_WITH -> {
                    val trimmedText = text.trim()
                    val trimmedPattern = pattern.trim()
                    trimmedText.startsWith(trimmedPattern, ignoreCase = true) ||
                        // دعم التحيات الشائعة البادئة مثل "سلام، " أو "السلام عليكم، " قبل عبارة البداية
                        trimmedText.removePrefix("السلام عليكم ورحمة الله وبركاته").trimStart('،', ',', ' ', ':', '-').startsWith(trimmedPattern, ignoreCase = true) ||
                        trimmedText.removePrefix("السلام عليكم").trimStart('،', ',', ' ', ':', '-').startsWith(trimmedPattern, ignoreCase = true) ||
                        trimmedText.removePrefix("سلام").trimStart('،', ',', ' ', ':', '-').startsWith(trimmedPattern, ignoreCase = true) ||
                        trimmedText.removePrefix("مرحبا").trimStart('،', ',', ' ', ':', '-').startsWith(trimmedPattern, ignoreCase = true)
                }
                PatternType.EXACT_MATCH -> text.trim().equals(pattern.trim(), ignoreCase = true)
                PatternType.REGEX -> {
                    try {
                        Regex(pattern).containsMatchIn(text)
                    } catch (e: Exception) {
                        false
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════
// نتائج المعالجة والإجراءات
// ═══════════════════════════════════════════════════════════

/** نتيجة معالجة رسالة واردة */
sealed class ProcessResult {
    data class Executed(val ruleName: String, val actions: List<ActionResult>) : ProcessResult()
    data object NoMatch : ProcessResult()
    data class Ignored(val reason: String) : ProcessResult()
}

/** نتيجة تنفيذ إجراء واحد */

sealed class ActionResult {
    abstract val description: String

    data class ReplySent(val message: String) : ActionResult() {
        override val description = "إرسال رد (\"$message\")"
    }

    data class Error(val errorMessage: String) : ActionResult() {
        override val description = "خطأ: $errorMessage"
    }
}
