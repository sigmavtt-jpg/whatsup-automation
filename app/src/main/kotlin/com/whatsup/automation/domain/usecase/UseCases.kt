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
    private val deviceContactsManager: DeviceContactsManager
) {
    suspend fun execute(message: IncomingMessage): ProcessResult {
        if (message.isGroupMessage || 
            message.senderPhone.contains("@newsletter") || 
            message.senderPhone.contains("@broadcast") ||
            message.senderPhone.contains("newsletter") ||
            message.senderPhone.isBlank()) {
            return ProcessResult.Ignored("تجاهل — رسالة مجموعة أو قناة إخبارية")
        }

        // 1. درع منع تكرار وانتحال جهات الاتصال (Anti-Duplication Shield) والتسجيل التلقائي الذكي
        val targetPhone = message.realPhone ?: message.senderPhone
        val cleanPhone = targetPhone.replace("[^0-9]".toRegex(), "")
        val isSenderLid = message.isLid || deviceContactsManager.isLid(targetPhone)

        val registerPattern = Regex("""(سجلني عندك|سجل اسمي|احفظ رقمي|اسمي هو|سجلني|احفظني|اسمي)\s*[:=,-]?\s*([a-zA-Z\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]+(?:\s+[a-zA-Z\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF\uFB50-\uFDFF\uFE70-\uFEFF]+)*)""")
        val match = registerPattern.find(message.text)
        if (match != null) {
            val rawExtracted = match.groupValues[2].trim()
            val extractedName = if (rawExtracted.startsWith("باسم ") && rawExtracted.length > 5) {
                rawExtracted.removePrefix("باسم ").trim()
            } else if (rawExtracted.startsWith("باسم") && rawExtracted.length > 4) {
                rawExtracted.removePrefix("باسم").trim()
            } else {
                rawExtracted
            }
            if (extractedName.length in 2..30 && !extractedName.any { it.isDigit() }) {
                // منع حفظ معرفات الـ LID الخام في دفتر الهاتف إذا لم يتوفر رقم حقيقي
                if (isSenderLid && message.realPhone.isNullOrBlank()) {
                    val replyMsg = "مرحباً بك! يرجى إرسال رقم هاتفك لتسجيلك بنجاح ✅"
                    return ProcessResult.Executed("درع منع التكرار — حماية LID", listOf(ActionResult.ReplySent(replyMsg)))
                }

                val existingName = deviceContactsManager.getContactDisplayName(targetPhone)
                    ?: deviceContactsManager.getContactDisplayName(cleanPhone)

                if (existingName != null && existingName.equals(extractedName, ignoreCase = true)) {
                    // الشخص مسجل مسبقاً بنفس الاسم تماماً — تجنب الحشو والتكرار
                    val replyMsg = "أنت مسجل لدينا بالفعل باسم $existingName ✅"
                    logRepository.insertLog(
                        ActivityLog(
                            senderPhone = targetPhone,
                            messageText = message.text,
                            matchedRule = "درع منع التكرار",
                            actionExecuted = "تأكيد الاسم المسجل مسبقاً ($existingName)",
                            status = LogStatus.SUCCESS,
                            extractedName = existingName
                        )
                    )
                    return ProcessResult.Executed("درع منع التكرار", listOf(ActionResult.ReplySent(replyMsg)))
                } else if (existingName != null) {
                    // نفس الحساب يطلب تعديل اسمه (مثلاً من أحمد إلى محمد) — تحديث الاسم القديم بدلاً من حشو جهات مكررة
                    val updated = deviceContactsManager.saveOrUpdateContact(extractedName, targetPhone)
                    if (updated) {
                        val replyMsg = "تم تحديث اسمك إلى $extractedName بنجاح ✅"
                        val actionDesc = "تحديث جهة اتصال ($existingName ➔ $extractedName) + إرسال رد"
                        logRepository.insertLog(
                            ActivityLog(
                                senderPhone = targetPhone,
                                messageText = message.text,
                                matchedRule = "درع منع التكرار والتحديث الذكي",
                                actionExecuted = actionDesc,
                                status = LogStatus.SUCCESS,
                                extractedName = extractedName
                            )
                        )
                        return ProcessResult.Executed("درع منع التكرار والتحديث الذكي", listOf(ActionResult.ReplySent(replyMsg)))
                    } else {
                        val replyMsg = "عذراً، تعذر تحديث الاسم حالياً ⚠️"
                        logRepository.insertLog(
                            ActivityLog(
                                senderPhone = targetPhone,
                                messageText = message.text,
                                matchedRule = "درع منع التكرار والتحديث الذكي",
                                actionExecuted = "فشل تحديث جهة اتصال ($existingName ➔ $extractedName)",
                                status = LogStatus.FAILURE,
                                extractedName = extractedName
                            )
                        )
                        return ProcessResult.Executed("درع منع التكرار والتحديث الذكي — فشل", listOf(ActionResult.ReplySent(replyMsg)))
                    }
                } else {
                    // جهة اتصال جديدة تسجل لأول مرة
                    val saved = deviceContactsManager.saveOrUpdateContact(extractedName, targetPhone)
                    if (saved) {
                        val replyMsg = "تم حفظك باسم $extractedName بنجاح ✅"
                        val actionDesc = "حفظ جهة اتصال ($extractedName) + إرسال رد"
                        logRepository.insertLog(
                            ActivityLog(
                                senderPhone = targetPhone,
                                messageText = message.text,
                                matchedRule = "التسجيل التلقائي الذكي",
                                actionExecuted = actionDesc,
                                status = LogStatus.SUCCESS,
                                extractedName = extractedName
                            )
                        )
                        return ProcessResult.Executed("التسجيل التلقائي الذكي", listOf(ActionResult.ReplySent(replyMsg)))
                    } else {
                        val replyMsg = "عذراً، تعذر حفظ جهة الاتصال حالياً ⚠️"
                        logRepository.insertLog(
                            ActivityLog(
                                senderPhone = targetPhone,
                                messageText = message.text,
                                matchedRule = "التسجيل التلقائي الذكي",
                                actionExecuted = "فشل حفظ جهة اتصال ($extractedName)",
                                status = LogStatus.FAILURE,
                                extractedName = extractedName
                            )
                        )
                        return ProcessResult.Executed("التسجيل التلقائي الذكي — فشل", listOf(ActionResult.ReplySent(replyMsg)))
                    }
                }
            }
        }

        val rules = matchRuleUseCase.getEnabledRulesSorted()

        for (rule in rules) {
            if (matchRuleUseCase.matches(rule, message.text)) {
                return executeRuleActions(rule, message)
            }
        }

        // تجاهل صامت تماماً بدون أي كتابة في قاعدة البيانات
        return ProcessResult.NoMatch
    }

    private suspend fun executeRuleActions(
        rule: Rule,
        message: IncomingMessage
    ): ProcessResult {
        val targetPhone = message.realPhone ?: message.senderPhone
        val results = mutableListOf<ActionResult>()

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
                    results.add(
                        ActionResult.ReplySent(finalReply)
                    )
                }
            }
        }

        val actionDesc = results.joinToString(" + ") { it.description }
        val hasError = results.any { it is ActionResult.Error }
        val logStatus = if (hasError) LogStatus.FAILURE else LogStatus.SUCCESS

        val contactName = deviceContactsManager.getContactDisplayName(targetPhone)
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
