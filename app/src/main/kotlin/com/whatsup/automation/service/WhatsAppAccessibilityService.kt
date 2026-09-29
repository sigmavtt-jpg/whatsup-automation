package com.whatsup.automation.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.whatsup.automation.data.engine.WhatsAppEngine
import com.whatsup.automation.domain.model.IncomingMessage
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
 * خدمة إمكانية الوصول المساعدة (Hybrid Mode Accessibility Service).
 * تعمل كمسار رصد هجين واحتياطي لقراءة إشعارات ونوافذ واتساب (com.whatsapp و com.whatsapp.w4b)
 * للتحقق من وصول الرسائل ومعالجتها حتى أثناء إعادة اتصال المحرك المحلي.
 */
@AndroidEntryPoint
class WhatsAppAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var whatsAppEngine: WhatsAppEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: return
        if (pkgName != "com.whatsapp" && pkgName != "com.whatsapp.w4b") return

        when (event.eventType) {
            // معالجة الإشعارات تم إسنادها بالكامل وبشكل رسمي لـ WhatsAppNotificationListenerService
            // لتوفير موارد البطارية والمعالج ومنع أي معالجة مكررة
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Reserved: No duplicate dispatch needed
            }
            // أحداث النوافذ محجوزة للاستخدام المستقبلي
        }
    }



    override fun onInterrupt() {
        // يتم استدعاؤها عند مقاطعة خدمة الوصول من النظام
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
