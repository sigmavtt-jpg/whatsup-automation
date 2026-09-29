---
name: whatsapp-automation-dev
description: Guidelines, best practices, and code recipes for developing and extending the WhatsApp Automation Android project.
---

# مهارة تطوير وهندسة نظام WhatsUp Automation

تُستخدم هذه المهارة لإرشاد المطورين والوكلاء البرمجيين عند إضافة ميزات جديدة، قواعد أتمتة، أو صيانة وتحسين الخدمات الخلفية لتطبيق WhatsUp Automation.

## 1. كيفية إضافة قاعدة أتمتة جديدة
عند إضافة إجراء أو شرط جديد لمحرك القواعد:
1. قم بتحديث نموذج الإجراء في `domain/model/Models.kt` داخل `RuleAction` (إن كان إجراءً جديداً).
2. أضف منطق التحقق والتنفيذ في حالة الاستخدام `ProcessIncomingMessageUseCase.kt`.
3. احرص على توثيق نتيجة التنفيذ في مستودع سجل الأنشطة `ActivityLogRepository`.
4. تأكد من أن التنفيذ يتم بنمط الأوفلاين ولا يتطلب اتصالاً سحابياً.

## 2. معايير التعامل مع الخدمات الخلفية (Background Services)
- خدمة `WhatsAppForegroundService` يجب أن تظل نشطة دائماً.
- عند إرسال رد تلقائي، استخدم مسار `RemoteInput` المدمج في إشعار واتساب عبر `WhatsAppNotificationListenerService`.
- تحقق دائماً من الذاكرة المؤقتة لمنع الرد المزدوج على الإشعار نفسه:
  ```kotlin
  val cacheKey = "$pkg|$title|$text|${sbn.postTime}"
  if (processedMessageCache.contains(cacheKey)) return
  ```

## 3. التعامل مع أرقام الهواتف ومحددات الدول
- استخدم دائماً مكون `PhoneInputWithCountrySelector` في أي واجهة إدخال لرقم الهاتف.
- الرمز الافتراضي هو دائماً اليمن (+967) مع العلم 🇾🇪.
- التخطيط LTR إجباري لمنع تشوه الأرقام والرموز.

## 4. التعافي الذاتي والأمان
- استخدم `SupervisorJob()` مع الكوروتينز لعزل الأخطاء.
- خزّن أي بيانات حساسة أو رموز عبر `SessionKeystore`.
- لا ترسل أي بيانات لخوادم خارجية مطلقاً.
