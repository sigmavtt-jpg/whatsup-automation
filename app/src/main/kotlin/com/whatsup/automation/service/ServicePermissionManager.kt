package com.whatsup.automation.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * مدير الصلاحيات وخدمات النظام الخاصة بالأتمتة المباشرة على أندرويد.
 * يفحص حالة الخدمات ويفتح صفحات الإعدادات الخاصة بها بضغطة زر واحدة.
 */
object ServicePermissionManager {

    /**
     * فحص هل تم تفعيل خدمة الاستماع للإشعارات (Notification Listener).
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat != null && flat.contains(packageName)
    }

    /**
     * فحص هل تم تفعيل خدمة إمكانية الوصول (Accessibility Service).
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(
            context,
            WhatsAppAccessibilityService::class.java
        ).flattenToString()

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val accessibilityEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )

        return accessibilityEnabled == 1 && enabledServices.contains(expectedComponentName)
    }

    /**
     * فحص هل تم استثناء التطبيق من قيود توفير الطاقة (Battery Optimization).
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    /**
     * فحص صلاحيات جهات الاتصال (قراءة وكتابة).
     */
    fun hasContactsPermissions(context: Context): Boolean {
        return hasReadContactsPermission(context) && hasWriteContactsPermission(context)
    }

    /**
     * فحص إذن كتابة جهات الاتصال (WRITE_CONTACTS).
     */
    fun hasWriteContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * فحص إذن قراءة جهات الاتصال (READ_CONTACTS).
     */
    fun hasReadContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * فتح صفحة إعدادات الوصول إلى الإشعارات في أندرويد.
     */
    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    val componentName = ComponentName(context, WhatsAppNotificationListenerService::class.java)
                    putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName.flattenToString())
                }
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {}
        }
    }

    /**
     * فتح صفحة إعدادات إمكانية الوصول في أندرويد.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * فتح نافذة استثناء تحسين استهلاك البطارية.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * فتح إعدادات التطبيق العامة.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * التحقق هل الخدمات الحيوية للأتمتة متصلة ونشطة بالكامل.
     */
    fun isAllCriticalServicesActive(context: Context): Boolean {
        return isNotificationListenerEnabled(context) &&
                isBatteryOptimizationIgnored(context) &&
                hasContactsPermissions(context)
    }
}
