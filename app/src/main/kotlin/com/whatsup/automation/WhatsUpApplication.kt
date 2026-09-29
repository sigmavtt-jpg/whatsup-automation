package com.whatsup.automation

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * نقطة الدخول الرئيسية للتطبيق — مزودة بـ Hilt للحقن التلقائي.
 * تهيئ جميع الوحدات المطلوبة عند بدء التطبيق.
 */
@HiltAndroidApp
class WhatsUpApplication : Application()
