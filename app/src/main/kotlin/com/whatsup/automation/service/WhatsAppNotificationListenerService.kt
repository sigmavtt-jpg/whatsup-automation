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

        // استخراج رقم الهاتف الحقيقي (إذا كان العنوان اسم شخص، نبحث عنه في جهات اتصال الهاتف)
        val cleanDigits = title.replace("[^0-9]".toRegex(), "")
        val senderPhone = if (cleanDigits.length >= 7) {
            deviceContactsManager.normalizePhoneNumber(title)
        } else {
            val phoneFromName = deviceContactsManager.findPhoneByName(title)
            if (phoneFromName != null) deviceContactsManager.normalizePhoneNumber(phoneFromName) else title
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
                                    if (whatsAppEngine.isMasterEngineConnected()) {
                                        sent = whatsAppEngine.sendMessage(incomingMessage.senderPhone, actionResult.message)
                                    }
                                    if (!sent && quickReply != null) {
                                        sent = sendActualReply(applicationContext, quickReply.first, quickReply.second, actionResult.message)
                                    }
                                    if (!sent && !whatsAppEngine.isMasterEngineConnected()) {
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
