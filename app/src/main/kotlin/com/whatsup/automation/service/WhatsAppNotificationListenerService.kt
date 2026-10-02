package com.whatsup.automation.service

import com.whatsup.automation.data.local.contacts.DeviceContactsManager

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.whatsup.automation.data.engine.WhatsAppEngine
import com.whatsup.automation.domain.model.IncomingMessage
import com.whatsup.automation.domain.usecase.ActionResult
import com.whatsup.automation.domain.usecase.ProcessIncomingMessageUseCase
import com.whatsup.automation.domain.usecase.ProcessResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * خدمة الاستماع لإشعارات واتساب الرسمية (WhatsApp & WhatsApp Business).
 * تمثل محرك الأتمتة الحقيقي (Real Automation Engine) على نظام أندرويد بدون وسيط أو خوادم خارجية:
 * 1. تلتقط رسائل واتساب الحقيقية لحظة وصولها.
 * 2. تفحص القواعد وتستخرج الأسماء لحفظها في دفتر الهاتف عبر DeviceContactsManager.
 * 3. ترسل الردود الحقيقية الفورية عبر آلية RemoteInput الرسمية المدمجة في إشعارات واتساب.
 */
@AndroidEntryPoint
class WhatsAppNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var processIncomingMessageUseCase: ProcessIncomingMessageUseCase

    @Inject
    lateinit var whatsAppEngine: WhatsAppEngine

    @Inject
    lateinit var deviceContactsManager: DeviceContactsManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val WHATSAPP_PKG = "com.whatsapp"
        private const val WHATSAPP_BIZ_PKG = "com.whatsapp.w4b"
        
        // ذاكرة مؤقتة لتفادي الرد المتكرر على نفس الإشعار
        private val processedMessageCache = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        whatsAppEngine.activateDirectAutomation()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (whatsAppEngine.isAutomationPaused.value) return

        val pkg = sbn.packageName ?: return
        if (pkg != WHATSAPP_PKG && pkg != WHATSAPP_BIZ_PKG) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // تجاهل إشعارات النظام الداخلية أو إشعارات النسخ الاحتياطي أو إشعارات التلخيص المجمعة
        val category = notification.category
        if (category == Notification.CATEGORY_PROGRESS || category == Notification.CATEGORY_SERVICE) {
            return
        }
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }

        // استخراج اسم المرسل ونص الرسالة
        val title = extras.getString(Notification.EXTRA_TITLE)?.trim() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() 
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: "").trim()

        if (title.isBlank() || text.isBlank()) return

        // التحقق هل هي رسالة مجموعة
        val isGroup = extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false) ||
                title.contains(":") || title.contains("@")

        // معرّف فريد للرسالة لمنع المعالجة المزدوجة لنفس الإشعار
        val cacheKey = "$pkg|$title|$text|${sbn.postTime}"
        if (processedMessageCache.contains(cacheKey)) return
        
        // الاحتفاظ بآخر 200 رسالة في الكاش
        if (processedMessageCache.size > 200) {
            val iterator = processedMessageCache.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        processedMessageCache.add(cacheKey)

        // استخراج رقم الهاتف الحقيقي بكل الطرق الممكنة لمنع وصول أرقام فارغة
        val senderPhone = extractSenderPhone(sbn, notification, extras, title, text)
        val phoneDigits = senderPhone.replace("[^0-9]".toRegex(), "")

        // السيادة المطلقة لمحرك Baileys JID:
        // إذا كان محرك Baileys متصلاً أو كان الإشعار يحمل اسماً فقط بدون أرقام (أقل من 7 خانات)،
        // يُحظر حجز الرسالة عبر الإشعارات وتُترك كلياً لمحرك Baileys JID الذي يملك الرقم الفعلي الموثوق 100%.
        if (whatsAppEngine.isMasterEngineConnected() || phoneDigits.length < 7) {
            return
        }

        val incomingMessage = IncomingMessage(
            id = "NOTIF_${UUID.randomUUID().toString().take(8)}",
            senderPhone = senderPhone,
            text = text,
            timestamp = Instant.ofEpochMilli(sbn.postTime),
            isGroupMessage = isGroup,
            senderName = title
        )

        serviceScope.launch {
            try {
                // حجز المعالجة ذرياً بشكل قطعي لمنع أي تنافس أو ردود مكررة
                val claimed = whatsAppEngine.tryClaimMessageProcessing(incomingMessage.senderPhone, incomingMessage.text)
                if (!claimed) {
                    return@launch
                }

                if (title != incomingMessage.senderPhone) {
                    whatsAppEngine.markMessageHandled(title, incomingMessage.text)
                }

                // معالجة الرسالة عبر القواعد
                val result = processIncomingMessageUseCase.execute(incomingMessage)

                if (result is ProcessResult.Executed) {
                    // البحث عن إجراء الرد السريع في الإشعار
                    val quickReply = extractQuickReplyAction(notification)

                    for (actionResult in result.actions) {
                        when (actionResult) {
                            is ActionResult.ReplySent -> {
                                if (actionResult.message.isNotBlank()) {
                                    var sent = false
                                    if (quickReply != null) {
                                        sent = sendActualReply(applicationContext, quickReply.first, quickReply.second, actionResult.message)
                                    }
                                    if (!sent) {
                                        whatsAppEngine.sendMessage(incomingMessage.senderPhone, actionResult.message)
                                    }
                                }
                            }
                            is ActionResult.Error -> {}
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("WhatsAppNotifListener", "Error processing notification in Assistant: ${e.message}", e)
            }
        }
    }

    /**
     * استخراج كائن RemoteInput المدمج في إشعار واتساب للرد السريع.
     */
    private fun extractQuickReplyAction(notification: Notification): Pair<Notification.Action, RemoteInput>? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (input in remoteInputs) {
                if (input.allowFreeFormInput) {
                    return Pair(action, input)
                }
            }
        }
        return null
    }

    /**
     * إرسال الرد الفعلي الحقيقي عبر PendingIntent المدمج في واتساب.
     */
    private fun sendActualReply(
        context: Context,
        action: Notification.Action,
        remoteInput: RemoteInput,
        replyText: String
    ): Boolean {
        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            action.actionIntent.send(context, 0, intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * استخراج رقم الهاتف الحقيقي بكل الطرق الممكنة من وسائط الإشعار المختلفة.
     */
    private fun extractSenderPhone(
        sbn: StatusBarNotification,
        notification: Notification,
        extras: Bundle,
        title: String,
        text: String
    ): String {
        // 1. فحص العنوان أولاً إذا كان رقماً صريحاً
        val titleDigits = title.replace("[^0-9]".toRegex(), "")
        if (titleDigits.length >= 7) {
            return deviceContactsManager.normalizePhoneNumber(title)
        }

        // 2. فحص tag الخاص بإشعار واتساب (يحتوي غالباً على JID أو رقم الهاتف)
        val tag = sbn.tag ?: ""
        val tagDigits = tag.replace("[^0-9]".toRegex(), "")
        if (tagDigits.length >= 7 && !tagDigits.startsWith("97517970702387")) {
            return deviceContactsManager.normalizePhoneNumber(tagDigits)
        }

        // 3. فحص مفتاح الإشعار (sbn.key)
        val key = sbn.key ?: ""
        val keyDigits = key.split('|', ':', ';', '_')
            .map { it.replace("[^0-9]".toRegex(), "") }
            .firstOrNull { it.length in 8..15 && !it.startsWith("97517970702387") }
        if (keyDigits != null) {
            return deviceContactsManager.normalizePhoneNumber(keyDigits)
        }

        // 4. فحص روابط tel: في كائنات Person المدمجة بالإشعار
        try {
            val peopleList = extras.get("android.people.list")
            if (peopleList is List<*>) {
                for (p in peopleList) {
                    val pStr = p.toString()
                    if (pStr.contains("tel:")) {
                        val tel = pStr.substringAfter("tel:").substringBefore('&').substringBefore(';')
                        val clean = tel.replace("[^0-9]".toRegex(), "")
                        if (clean.length >= 7) {
                            return deviceContactsManager.normalizePhoneNumber(clean)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 5. البحث بالاسم في دفتر الهاتف
        val phoneFromName = deviceContactsManager.findPhoneByName(title)
        if (phoneFromName != null && phoneFromName.replace("[^0-9]".toRegex(), "").length >= 7) {
            return deviceContactsManager.normalizePhoneNumber(phoneFromName)
        }

        // 6. استخراج رقم هاتف من نص الرسالة إن وجد (مثل "سجلني 771234567")
        val phoneInTextRegex = Regex("""\b(?:\+?967|00967|0)?[713][0-9]{7,8}\b|\b\+[0-9]{8,15}\b""")
        val matchInText = phoneInTextRegex.find(text)
        if (matchInText != null) {
            val extracted = matchInText.value
            return deviceContactsManager.normalizePhoneNumber(extracted)
        }

        return title
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
