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
    private val contactFormattingRepository: com.whatsup.automation.domain.repository.ContactFormattingRepository,
    private val whatsAppEngine: com.whatsup.automation.data.engine.WhatsAppEngine,
    private val notificationRepository: com.whatsup.automation.domain.repository.NotificationRepository,
    private val conversationMemoryManager: com.whatsup.automation.domain.util.ConversationMemoryManager
) {
    suspend fun execute(message: IncomingMessage): ProcessResult {
        if (whatsAppEngine.isAutomationPaused.value) {
            return ProcessResult.Ignored("الأتمتة متوقفة مؤقتاً")
        }

        if (message.isGroupMessage || 
            message.senderPhone.contains("@newsletter") || 
            message.senderPhone.contains("@broadcast") ||
            message.senderPhone.contains("newsletter") ||
            message.senderPhone.isBlank()) {
            return ProcessResult.Ignored("تجاهل — رسالة مجموعة أو قناة إخبارية")
        }

        val targetPhone = (message.realPhone ?: message.senderPhone).takeIf { it.isNotBlank() && it != "null" } ?: ""
        val cleanPhone = targetPhone.replace("[^0-9]".toRegex(), "")
        val phoneToLookup = if (cleanPhone.length in 7..15 && !message.isLid) cleanPhone else message.realPhone?.replace("[^0-9]".toRegex(), "") ?: cleanPhone

        // 0. درع الكتم والنفي الصريح
        if (com.whatsup.automation.domain.util.StrictNegationAnalyzer.isNegated(message.text)) {
            return ProcessResult.Ignored("أمر كتم صريح من المستخدم")
        }

        // 0.1 درع السرد والكلام المنقول
        if (com.whatsup.automation.domain.util.QuotedSpeechDetector.isQuotedOrIndirect(message.text)) {
            return ProcessResult.Ignored("كلام منقول أو سرد غير مباشر - تجاهل")
        }

        // فحص هل الرقم مسجل مسبقاً في دفتر هاتف الجهاز
        val existingContactName = if (targetPhone.isNotBlank()) {
            deviceContactsManager.getContactDisplayName(targetPhone)
                ?: if (phoneToLookup.isNotBlank()) deviceContactsManager.getContactDisplayName(phoneToLookup) else null
        } else null
        val isAlreadyRegistered = !existingContactName.isNullOrBlank()

        val formattingSettings = try {
            contactFormattingRepository.getSettings().first()
        } catch (_: Exception) {
            ContactFormattingSettings()
        }

        val contactKeywords = formattingSettings.triggerKeywords.split(',', '،', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val intentResult = com.whatsup.automation.domain.util.SemanticIntentClassifier.classify(
            text = message.text,
            isAlreadyRegistered = isAlreadyRegistered,
            existingName = existingContactName,
            customKeywords = contactKeywords
        )

        // معالجة النوايا المصنفة
        when (intentResult.intent) {
            com.whatsup.automation.domain.util.SemanticIntent.NEGATED_COMMAND -> {
                return ProcessResult.Ignored("أمر كتم صريح من المستخدم")
            }
            com.whatsup.automation.domain.util.SemanticIntent.QUOTED_OR_NARRATIVE -> {
                return ProcessResult.Ignored("كلام منقول أو سرد غير مباشر - تجاهل")
            }
            com.whatsup.automation.domain.util.SemanticIntent.REGISTER_CONTACT,
            com.whatsup.automation.domain.util.SemanticIntent.UPDATE_CONTACT -> {
                if (isAlreadyRegistered) {
                    // مسجل مسبقاً -> تجاهل صامت تام كما هو مطلوب
                    return ProcessResult.Ignored("الجهة مسجلة مسبقاً باسم $existingContactName - تجاهل صامت")
                } else if (formattingSettings.isEnabled && !message.id.startsWith("NOTIF_")) {
                    val extractedName = intentResult.extractedRawName
                    if (!extractedName.isNullOrBlank() && com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName(extractedName)) {
                        return executeMasterContactSave(formattingSettings, message, extractedName)
                    }
                }
            }
            else -> {
                // استمرار لفحص القواعد العادية
            }
        }

        // 2. فحص القواعد والردود التلقائية العادية
        val rules = matchRuleUseCase.getEnabledRulesSorted()

        for (rule in rules) {
            if (matchRuleUseCase.matches(rule, message.text)) {
                return executeRuleActions(rule, message)
            }
        }

        val isQuestion = intentResult.intent == com.whatsup.automation.domain.util.SemanticIntent.QUESTION_OR_INQUIRY ||
                conversationMemoryManager.isQuestionOrConversationalQuery(message.text)

        // تسجيل المحادثة في الذاكرة السياقية للمحافظة على تتابع الحوار
        if (targetPhone.isNotBlank()) {
            conversationMemoryManager.recordTurn(
                phone = targetPhone,
                incomingText = message.text,
                replySent = null,
                intent = if (isQuestion) com.whatsup.automation.domain.util.ConversationIntent.QUESTION_OR_INQUIRY else com.whatsup.automation.domain.util.ConversationIntent.GENERAL_CHAT,
                extractedName = null
            )
        }

        // تجاهل صامت تماماً بدون أي كتابة في قاعدة البيانات إذا لم تطابق أي قاعدة
        return ProcessResult.NoMatch
    }

    private suspend fun executeMasterContactSave(
        formattingSettings: ContactFormattingSettings,
        message: IncomingMessage,
        explicitExtractedName: String? = null
    ): ProcessResult {
        val targetPhone = (message.realPhone ?: message.senderPhone).takeIf { it.isNotBlank() && it != "null" } ?: ""
        val cleanPhone = targetPhone.replace("[^0-9]".toRegex(), "")
        val isSenderLid = message.isLid || deviceContactsManager.isLid(targetPhone) || cleanPhone.length > 15
        val results = mutableListOf<ActionResult>()

        // استخراج الاسم بذكاء مع دعم كافة الرموز والإيموجي واللهجات
        val customKeywords = formattingSettings.triggerKeywords.split(',', '،', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val finalExtractedName = explicitExtractedName ?: com.whatsup.automation.domain.util.NameExtractorHelper.extractName(
            text = message.text,
            customKeywords = customKeywords,
            fallbackSenderName = message.senderName
        )

        // إذا فشل استخراج اسم بشري حقيقي صالح، نرفض العملية ولا نسجل جملة كاسم أبداً
        if (finalExtractedName.isBlank() || !com.whatsup.automation.domain.util.NameExtractorHelper.isValidHumanName(finalExtractedName)) {
            return ProcessResult.Ignored("تجاهل — لم يتم العثور على اسم بشري صالح في النص")
        }

        // 1. حماية معرفات LID بدون رقم هاتف حقيقي
        if (isSenderLid && message.realPhone.isNullOrBlank()) {
            val replyMsg = "مرحباً بك! يرجى إرسال رقم هاتفك لتسجيلك بنجاح ✅"
            results.add(ActionResult.ReplySent(replyMsg))
            val actionDesc = results.joinToString(" + ") { it.description }
            logRepository.insertLog(
                ActivityLog(
                    senderPhone = targetPhone,
                    messageText = message.text,
                    matchedRule = "درع منع التكرار — حماية LID",
                    actionExecuted = actionDesc,
                    status = LogStatus.SUCCESS,
                    extractedName = finalExtractedName
                )
            )
            return ProcessResult.Executed("درع منع التكرار — حماية LID", results)
        }

        val phoneToSave = if (cleanPhone.length in 7..15 && !isSenderLid) cleanPhone else message.realPhone?.replace("[^0-9]".toRegex(), "") ?: cleanPhone
        val phonebookFormattedName = formattingSettings.formatForPhonebook(finalExtractedName)

        val existingName = deviceContactsManager.getContactDisplayName(targetPhone)
            ?: deviceContactsManager.getContactDisplayName(phoneToSave)

        if (existingName != null) {
            // مسجل مسبقاً في جهات الاتصال -> تجاهل صامت تام بدون رد وبدون تعديل
            return ProcessResult.Ignored("الجهة مسجلة مسبقاً باسم $existingName - تجاهل صامت")
        } else {
            // تسجيل جديد لأول مرة
            val saved = if (phoneToSave.replace("[^0-9]".toRegex(), "").length >= 7) {
                deviceContactsManager.saveOrUpdateContact(phonebookFormattedName, phoneToSave)
            } else {
                false
            }

            if (!saved) {
                return ProcessResult.Ignored("فشل حفظ جهة الاتصال في دفتر الهاتف أو رقم غير صالح")
            }

            val replyTemplate = formattingSettings.replyMessage.ifBlank { "تم حفظك باسم {name} بنجاح ✅" }
            val finalReply = com.whatsup.automation.domain.util.SpintaxEngine.process(
                template = replyTemplate,
                recipientName = finalExtractedName,
                recipientPhone = targetPhone
            )
            results.add(ActionResult.ReplySent(finalReply))

            notificationRepository.postNotification(
                title = "حفظ جهة اتصال جديدة ✅",
                message = "تم حفظ: $phonebookFormattedName ($phoneToSave)",
                type = com.whatsup.automation.domain.model.NotificationType.SUCCESS
            )

            val actionDesc = results.joinToString(" + ") { it.description }
            val hasError = results.any { it is ActionResult.Error }
            val logStatus = if (hasError) LogStatus.FAILURE else LogStatus.SUCCESS

            logRepository.insertLog(
                ActivityLog(
                    senderPhone = targetPhone,
                    messageText = message.text,
                    matchedRule = "تسجيل جهات الاتصال (ثابتة)",
                    actionExecuted = actionDesc,
                    status = logStatus,
                    extractedName = finalExtractedName
                )
            )

            val replyText = results.filterIsInstance<ActionResult.ReplySent>().firstOrNull()?.message
            conversationMemoryManager.recordTurn(
                phone = targetPhone,
                incomingText = message.text,
                replySent = replyText,
                intent = com.whatsup.automation.domain.util.ConversationIntent.CONTACT_JUST_SAVED,
                extractedName = finalExtractedName
            )

            return ProcessResult.Executed("التسجيل التلقائي الذكي", results)
        }
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
                    val selectedTemplate = if (replyOptions.isNotEmpty()) {
                        replyOptions.random()
                    } else {
                        action.message
                    }
                    val finalReply = com.whatsup.automation.domain.util.SpintaxEngine.process(
                        template = selectedTemplate,
                        recipientName = message.senderName,
                        recipientPhone = targetPhone
                    )
                    results.add(ActionResult.ReplySent(finalReply))
                }

                is RuleAction.SaveContactAndReply -> {
                    // حظر حفظ جهات الاتصال عبر مسار الإشعارات — الحفظ يتم حصراً عبر محرك Baileys JID
                    if (message.id.startsWith("NOTIF_")) {
                        continue
                    }

                    // استخراج الاسم بذكاء مع دعم كافة الرموز والإيموجي واللهجات
                    val customKeywords = rule.patternValue.split(',', '،', '\n')
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    val finalExtractedName = com.whatsup.automation.domain.util.NameExtractorHelper.extractName(
                        text = message.text,
                        customKeywords = customKeywords,
                        fallbackSenderName = message.senderName
                    )

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

                        if (existingName != null) {
                            // مسجل مسبقاً -> نحافظ على اسمه المسجل مسبقاً وممنوع الكتابة فوقه إطلاقاً
                            val replyTemplate = action.replyMessage.ifBlank { "أنت مسجل لدينا بالفعل باسم {name} ✅" }
                            val finalReply = com.whatsup.automation.domain.util.SpintaxEngine.process(
                                template = replyTemplate,
                                recipientName = existingName,
                                recipientPhone = targetPhone
                            )
                            results.add(ActionResult.ReplySent(finalReply))
                            notificationRepository.postNotification(
                                title = "جهة اتصال مسجلة مسبقاً ℹ️",
                                message = "المرسل مسجل مسبقاً باسم: $existingName ($targetPhone)",
                                type = com.whatsup.automation.domain.model.NotificationType.INFO
                            )
                        } else {
                            val saved = deviceContactsManager.saveOrUpdateContact(phonebookFormattedName, targetPhone)
                            if (saved) {
                                val replyTemplate = action.replyMessage.ifBlank { "تم حفظك باسم {name} بنجاح ✅" }
                                val finalReply = com.whatsup.automation.domain.util.SpintaxEngine.process(
                                    template = replyTemplate,
                                    recipientName = finalExtractedName,
                                    recipientPhone = targetPhone
                                )
                                results.add(ActionResult.ReplySent(finalReply))
                                notificationRepository.postNotification(
                                    title = "حفظ جهة اتصال جديدة ✅",
                                    message = "تم حفظ: $phonebookFormattedName ($targetPhone)",
                                    type = com.whatsup.automation.domain.model.NotificationType.SUCCESS
                                )
                            } else {
                                val replyMsg = "عذراً، تعذر حفظ جهة الاتصال حالياً ⚠️"
                                results.add(ActionResult.ReplySent(replyMsg))
                                notificationRepository.postNotification(
                                    title = "فشل حفظ جهة الاتصال ❌",
                                    message = "تعذر حفظ: $phonebookFormattedName ($targetPhone) في دفتر الهاتف",
                                    type = com.whatsup.automation.domain.model.NotificationType.ERROR
                                )
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

        val replyText = results.filterIsInstance<ActionResult.ReplySent>().firstOrNull()?.message
        conversationMemoryManager.recordTurn(
            phone = targetPhone,
            incomingText = message.text,
            replySent = replyText,
            intent = if (extractedContactName != null) com.whatsup.automation.domain.util.ConversationIntent.CONTACT_JUST_SAVED else com.whatsup.automation.domain.util.ConversationIntent.GENERAL_CHAT,
            extractedName = contactName
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
            // فحص درع النفي وسياق نقل الكلام أولاً (استبعاد القواعد عندما ينفي المستخدم أو يسرد قصة)
            if (rule.patternType != PatternType.REGEX && isNegatedOrQuotedContext(pattern, text)) {
                return@any false
            }

            when (rule.patternType) {
                PatternType.CONTAINS -> {
                    text.contains(pattern, ignoreCase = true) || com.whatsup.automation.domain.util.ArabicMorphologyHelper.isFuzzyMatch(pattern, text)
                }
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

    /**
     * محرك كشف النفي وسياق نقل الكلام (Smart Negation & Anti-Context Engine):
     * يمنع الردود التلقائية الغبية أو الخاطئة عندما يرد المستخدم بأسلوب نفي بكافة اللهجات العربية
     * (مثل: "لا سلام ولا كلام", "بدون سلام", "ما في سلام", "مش عايزك تسجلني", "مانشتيش تسجل", "قلت له سلام").
     */
    fun isNegatedOrQuotedContext(pattern: String, fullText: String): Boolean {
        val analysis = com.whatsup.automation.domain.nlp.ArabicDialectalNegationEngine.analyzeNegationScope(pattern, fullText)
        if (analysis.isNegated || analysis.isHearsay) {
            return true
        }

        val cleanPattern = pattern.trim().lowercase()
        val cleanText = fullText.trim().lowercase()
        if (cleanPattern.isBlank() || cleanText.isBlank()) return false
        if (cleanText == cleanPattern) return false

        // 1. صيغ "لا ... ولا ..." الشائعة (مثل "لا سلام ولا كلام"، "لا تسجلني ولا تحاكيني")
        val neitherNorRegex = Regex("""\bلا\s+.*$cleanPattern.*ولا\b|\bلا\s+$cleanPattern\s+ولا\b""", RegexOption.IGNORE_CASE)
        if (neitherNorRegex.containsMatchIn(cleanText)) return true

        // 2. أدوات النفي المباشرة قبل الكلمة
        val negationWords = listOf(
            "لا", "بلا", "بدون", "مش", "مو", "ما", "مافي", "ما في", "ليس", "غير", "بلاش", "ممنوع"
        )
        for (neg in negationWords) {
            val negRegex = Regex("""\b$neg\s+(?:داعي\s+|عايز\s+|بدي\s+|تريد\s+|ودنا\s+)?$cleanPattern\b""", RegexOption.IGNORE_CASE)
            if (negRegex.containsMatchIn(cleanText)) return true
        }

        // 3. أفعال نقل الكلام وسرد الحكايات
        val quotedVerbs = listOf(
            "قلت له", "قلتله", "قلت لها", "قلتلها", "قلت", "قال لي", "قالي", "قال", "قالت", "قالوا",
            "حكيت له", "حكيتله", "حكالي", "يحكي", "يقول", "تقول", "سمعت"
        )
        for (verb in quotedVerbs) {
            val verbRegex = Regex("""\b$verb\s+(?:إنه\s+|انه\s+|له\s+|لها\s+)?(?:['"«])?.*$cleanPattern""", RegexOption.IGNORE_CASE)
            if (verbRegex.containsMatchIn(cleanText)) return true
        }

        return false
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
