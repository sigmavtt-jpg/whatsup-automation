package com.whatsup.automation.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.whatsup.automation.presentation.MainActivity

/**
 * مساعد إرسال إشعارات النظام الحقيقية لشريط الحالة (Status Bar) عند حدوث أخطاء أو تنبيهات مهمة.
 */
object SystemNotificationHelper {

    private const val CHANNEL_ID = "whatsup_system_alerts_channel"
    private const val CHANNEL_NAME = "تنبيهات النظام وأخطاء الأتمتة"

    fun sendSystemNotification(
        context: Context,
        title: String,
        message: String,
        isError: Boolean = true
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = if (isError) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                    description = "إشعارات تنبيهية بالأخطاء والعمليات الحيوية"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val icon = android.R.drawable.stat_notify_error
            val color = if (isError) 0xFFFF3366.toInt() else 0xFF25D366.toInt()

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setColor(color)
                .setAutoCancel(true)
                .setPriority(if (isError) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
        } catch (_: Throwable) {}
    }
}
