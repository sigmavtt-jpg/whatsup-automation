package com.whatsup.automation.domain.model

import java.time.Instant

/**
 * حالة الاتصال بواتساب — تمثل جميع الحالات الممكنة للجلسة.
 */
sealed class ConnectionState {
    /** متصل ونشط — الجلسة تعمل بشكل طبيعي */
    data object Connected : ConnectionState()

    /** غير متصل — الجلسة منتهية أو مرفوضة */
    data object Disconnected : ConnectionState()

    /** يعيد الاتصال — محاولة إعادة ربط الجلسة */
    data class Reconnecting(val attempt: Int = 1) : ConnectionState()

    /** في انتظار الاقتران — QR أو Pairing Code */
    data object AwaitingPairing : ConnectionState()

    /** خطأ — حدث خطأ غير متوقع */
    data class Error(val message: String) : ConnectionState()
}

/**
 * قاعدة أتمتة — تحدد شرط المطابقة والإجراء المطلوب تنفيذه.
 */
data class Rule(
    val id: Long = 0,
    val name: String,
    val description: String,
    val patternType: PatternType,
    val patternValue: String,
    val actions: List<RuleAction>,
    val priority: Int,
    val isEnabled: Boolean = true,
    val createdAt: Instant = Instant.now()
)

/**
 * أنواع الأنماط المدعومة في القواعد.
 */
enum class PatternType {
    /** النص يحتوي على الكلمة المفتاحية */
    CONTAINS,
    /** النص يبدأ بالعبارة المحددة */
    STARTS_WITH,
    /** النص يطابق تماماً */
    EXACT_MATCH,
    /** النص يطابق تعبيراً نمطياً (Regex) */
    REGEX
}

/**
 * إجراء يُنفَّذ عند تطابق القاعدة.
 */
sealed class RuleAction {
    /** إرسال رد نصي فقط دون حفظ جهة اتصال */
    data class SendReply(val message: String) : RuleAction()

    /** استخراج الاسم وحفظ جهة الاتصال في الهاتف ثم إرسال الرد التلقائي */
    data class SaveContactAndReply(val replyMessage: String) : RuleAction()
}

/**
 * سجل نشاط — يوثق كل عملية تمر بالمحرك.
 */
data class ActivityLog(
    val id: Long = 0,
    val timestamp: Instant = Instant.now(),
    val senderPhone: String,
    val messageText: String,
    val matchedRule: String? = null,
    val actionExecuted: String,
    val status: LogStatus,
    val extractedName: String? = null
)

/**
 * حالة تنفيذ العملية.
 */
enum class LogStatus {
    SUCCESS,
    FAILURE,
    NO_MATCH
}

/**
 * رسالة واردة من واتساب.
 */
data class IncomingMessage(
    val id: String,
    val senderPhone: String,
    val text: String,
    val timestamp: Instant = Instant.now(),
    val isGroupMessage: Boolean = false,
    val senderName: String = "",
    val isLid: Boolean = false,
    val realPhone: String? = null,
    val lid: String? = null
)

/**
 * إحصائيات لوحة التحكم.
 */
data class DashboardStats(
    val totalProcessed: Int = 0,
    val totalSavedContacts: Int = 0,
    val totalRepliesSent: Int = 0,
    val totalIgnored: Int = 0,
    val totalNoMatch: Int = totalIgnored
)

/**
 * قصة حالة واتساب (Status Story) ممسوحة من مسار status@broadcast.
 */
data class StatusStory(
    val id: String,
    val senderPhone: String,
    val senderName: String,
    val mediaType: StatusMediaType = StatusMediaType.TEXT,
    val textContent: String? = null,
    val mediaUrl: String? = null,
    val timestamp: Instant = Instant.now(),
    val isViewed: Boolean = false,
    val viewedAt: Instant? = null,
    val isReacted: Boolean = false,
    val reactionEmoji: String? = null,
    val reactionSentAt: Instant? = null,
    val isLid: Boolean = false,
    val realPhone: String? = null,
    val lid: String? = null,
    val participant: String? = null
)

/**
 * نوع وسائط الحالة.
 */
enum class StatusMediaType {
    TEXT,
    IMAGE,
    VIDEO
}

/**
 * إعدادات أتمتة مشاهدة والتفاعل مع الحالات.
 */
data class StatusAutomationSettings(
    val autoViewEnabled: Boolean = true,
    val autoReactEnabled: Boolean = true,
    val defaultEmoji: String = "💚",
    val autoDownloadMedia: Boolean = false
)

/**
 * مجموعة رسائل جماعية للأنشطة والبث المباشر.
 */
data class Group(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Instant = Instant.now(),
    val memberCount: Int = 0
)

/**
 * عضو في مجموعة البث.
 */
data class GroupMember(
    val id: Long = 0,
    val groupId: Long = 0,
    val contactName: String,
    val phone: String,
    val addedAt: Instant = Instant.now()
)

/**
 * حالة سجل البث للمجموعة.
 */
enum class GroupLogStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * سجل إرسال رسالة بث لعضو داخل مجموعة.
 */
data class GroupMessageLog(
    val id: Long = 0,
    val groupId: Long,
    val messageText: String,
    val recipientPhone: String,
    val recipientName: String,
    val status: GroupLogStatus,
    val sentAt: Instant? = null,
    val errorMessage: String? = null
)

/**
 * كائن يجمع بين المجموعة وأعضائها.
 */
data class GroupWithMembers(
    val group: Group,
    val members: List<GroupMember>
)

/**
 * إعدادات تنسيق وتوسيم أسماء جهات الاتصال التلقائية عند الحفظ في دفتر الهاتف.
 */
data class ContactFormattingSettings(
    val isEnabled: Boolean = true,
    val customPrefix: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val customTag: String = "",
    val customEmoji: String = "",
    val triggerKeywords: String = "سجلني، احفظني، سجل اسمي، احفظ رقمي، اسمي",
    val replyMessage: String = "تم حفظك باسم {name} بنجاح ✅"
) {
    /**
     * تشكيل وتنسيق الاسم ليتم حفظه في دفتر جهات اتصال الهاتف الفعلي (ContactsContract).
     */
    fun formatForPhonebook(rawName: String): String {
        if (!isEnabled) return rawName.trim()
        val cp = customPrefix.trim()
        val p = prefix.trim()
        val s = suffix.trim()
        val tag = customTag.trim()
        val emoji = customEmoji.trim()

        val parts = mutableListOf<String>()
        if (cp.isNotEmpty()) parts.add(cp)
        if (p.isNotEmpty()) parts.add(p)
        parts.add(rawName.trim())
        if (tag.isNotEmpty()) parts.add(tag)
        if (s.isNotEmpty()) parts.add(s)
        if (emoji.isNotEmpty()) parts.add(emoji)

        return parts.joinToString(" ").trim().ifBlank { rawName.trim() }
    }
}

/**
 * إعدادات درع الحماية ومحاكي السلوك البشري لمنع الحظر.
 */
data class AntiBanSettings(
    val isEnabled: Boolean = true,
    val simulateReading: Boolean = true,
    val readDelayMinMs: Long = 800L,
    val readDelayMaxMs: Long = 2000L,
    val dynamicTypingSpeed: Boolean = true,
    val typingSpeedCharMs: Long = 35L,
    val contactCooldownSeconds: Int = 12,
    val maxRepliesPerWindow: Int = 4,
    val windowMinutes: Int = 10
) {
    /**
     * حساب التأخير البشري المطلوب لمحاكاة كتابة النص بناءً على طوله.
     */
    fun calculateTypingDelayMs(text: String): Long {
        if (!isEnabled) return 0L
        val baseDelay = if (dynamicTypingSpeed) {
            val length = text.length.coerceIn(5, 400)
            (length * typingSpeedCharMs).coerceIn(1200L, 7000L)
        } else {
            1500L
        }
        val jitter = (Math.random() * 800).toLong()
        return baseDelay + jitter
    }

    /**
     * حساب وقت استيعاب وقراءة الرسالة قبل البدء بالكتابة.
     */
    fun calculateReadDelayMs(): Long {
        if (!isEnabled || !simulateReading) return 0L
        val diff = (readDelayMaxMs - readDelayMinMs).coerceAtLeast(100L)
        return readDelayMinMs + (Math.random() * diff).toLong()
    }
}
