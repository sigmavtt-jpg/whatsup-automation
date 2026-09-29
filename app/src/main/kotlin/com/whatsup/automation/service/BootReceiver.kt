package com.whatsup.automation.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * مستقبل البث عند إعادة تشغيل الجهاز أو تحديث التطبيق.
 * يعيد تشغيل الخدمة الأمامية تلقائياً للتعافي الذاتي (Self-Healing).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val isBootAction = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON"

        if (isBootAction) {
            try {
                // إعادة تشغيل الخدمة الأمامية للتعافي الذاتي فور إقلاع الجهاز
                WhatsAppForegroundService.start(context)
            } catch (e: Exception) {
                android.util.Log.e("BootReceiver", "Failed to start WhatsAppForegroundService on boot: ${e.message}", e)
            }
        }
    }
}
