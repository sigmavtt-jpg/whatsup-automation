package com.whatsup.automation.data.engine

import com.whatsup.automation.domain.model.ActivityLog
import com.whatsup.automation.domain.model.AntiBanSettings
import com.whatsup.automation.domain.model.LogStatus
import com.whatsup.automation.domain.repository.AntiBanRepository
import com.whatsup.automation.domain.repository.LogRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * نتيجة فحص درع الحماية ومحاكي السلوك البشري قبل الإرسال.
 */
sealed class ShieldCheckResult {
    data class Allowed(
        val readDelayMs: Long,
        val typingDelayMs: Long,
        val settings: AntiBanSettings
    ) : ShieldCheckResult()

    data class Throttled(
        val reason: String,
        val remainingCooldownSeconds: Long
    ) : ShieldCheckResult()
}

/**
 * مدير درع الحماية ومكافحة الحظر (Anti-Ban Shield & Humanizer Manager).
 * يحمي الحساب من الاكتشاف الآلي، يمنع الحلقات المفرغة مع البوتات الأخرى،
 * ويحاكي السلوك البشري الطبيعي أثناء القراءة والكتابة.
 */
@Singleton
class AntiBanShieldManager @Inject constructor(
    private val antiBanRepository: AntiBanRepository,
    private val logRepository: LogRepository
) {
    // سجل زمني لآخر الردود لكل رقم هاتف: Phone -> List of Epoch Milliseconds
    private val contactReplyHistory = ConcurrentHashMap<String, MutableList<Long>>()
    // آخر رد أُرسل للرقم لمنع الحلقات مع البوتات: Phone -> Last Sent Text
    private val lastSentTexts = ConcurrentHashMap<String, String>()

    /**
     * التحقق مما إذا كان مسموحاً بالرد على هذا الرقم أم يجب كتمه لحماية الحساب.
     */
    suspend fun evaluateMessageReply(
        senderPhone: String,
        incomingText: String,
        replyText: String
    ): ShieldCheckResult {
        val settings = antiBanRepository.getSettings().first()
        val cleanPhone = senderPhone.filter { it.isDigit() }.ifBlank { senderPhone }

        if (!settings.isEnabled) {
            return ShieldCheckResult.Allowed(
                readDelayMs = 0L,
                typingDelayMs = 1200L,
                settings = settings
            )
        }

        val now = System.currentTimeMillis()
        val history = contactReplyHistory.computeIfAbsent(cleanPhone) { mutableListOf() }

        // تنظيف السجلات الأقدم من نافذة الفحص
        val windowMs = settings.windowMinutes * 60 * 1000L
        synchronized(history) {
            history.removeAll { now - it > windowMs }
        }

        // 1. فحص فترة التهدئة بين الردود المتتالية (Cooldown)
        val lastReplyTime = synchronized(history) { history.lastOrNull() }
        if (lastReplyTime != null) {
            val elapsedSeconds = (now - lastReplyTime) / 1000
            val cooldown = settings.contactCooldownSeconds
            if (elapsedSeconds < cooldown) {
                val remaining = cooldown - elapsedSeconds
                val reason = "تجاوز سرعة التهدئة: آخر رد أُرسل قبل $elapsedSeconds ثانية (المطلوب $cooldown ثانية)"
                logShieldAction(cleanPhone, incomingText, reason)
                return ShieldCheckResult.Throttled(reason, remaining)
            }
        }

        // 2. فحص سقف الردود خلال النافذة الزمنية (Rate Limiting)
        val repliesInWindow = synchronized(history) { history.size }
        if (repliesInWindow >= settings.maxRepliesPerWindow) {
            val reason = "تجاوز السقف الأقصى للردود ($repliesInWindow من أصل ${settings.maxRepliesPerWindow}) خلال ${settings.windowMinutes} دقائق لمنع الحلقات"
            logShieldAction(cleanPhone, incomingText, reason)
            return ShieldCheckResult.Throttled(reason, (windowMs / 1000))
        }

        // 3. فحص الحلقات اللانهائية مع البوتات الأخرى (Anti-Loop Detection)
        val lastSent = lastSentTexts[cleanPhone]
        if (lastSent != null && incomingText.trim().equals(lastSent.trim(), ignoreCase = true)) {
            val reason = "كشف تكرار حلقة بوت متطابقة مع آخر رد مرسل (Anti-Loop)"
            logShieldAction(cleanPhone, incomingText, reason)
            return ShieldCheckResult.Throttled(reason, 60)
        }

        // تسجيل الرد في الذاكرة
        synchronized(history) {
            history.add(now)
        }
        lastSentTexts[cleanPhone] = replyText

        // حساب التأخير البشري التلقائي
        val readDelay = settings.calculateReadDelayMs()
        val typingDelay = settings.calculateTypingDelayMs(replyText)

        return ShieldCheckResult.Allowed(
            readDelayMs = readDelay,
            typingDelayMs = typingDelay,
            settings = settings
        )
    }

    private suspend fun logShieldAction(phone: String, incomingText: String, reason: String) {
        try {
            logRepository.insertLog(
                ActivityLog(
                    timestamp = Instant.now(),
                    senderPhone = phone,
                    messageText = incomingText.take(50),
                    matchedRule = "درع الحماية (Anti-Ban Shield)",
                    actionExecuted = "🛡️ كتم الرد التلقائي: $reason",
                    status = LogStatus.NO_MATCH
                )
            )
        } catch (_: Exception) {}
    }

    /**
     * إعادة تعيين سجلات التهدئة لرقم معين (مثلاً عند التفاعل اليدوي).
     */
    fun resetContactCooldown(phone: String) {
        val clean = phone.filter { it.isDigit() }
        contactReplyHistory.remove(clean)
        lastSentTexts.remove(clean)
    }
}
