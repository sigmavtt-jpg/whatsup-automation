# تقرير المسح المعماري الشامل — طبقة كوتلن والخدمات وتكامل الجسر المحلي
# Comprehensive Architectural Survey: Kotlin Architecture, Services & Bridge Integration

**التاريخ:** 2026-09-28  
**المحقق:** مستكشف المعمارية (Explorer — Architecture & Services)  
**مسار العمل:** `c:/Users/sO377/Downloads/whatsup/.agents/teamwork/explorer_survey_3/`  
**المرجع الأساسي:** `c:/Users/sO377/Downloads/whatsup/.agents/teamwork/ORIGINAL_REQUEST.md` و `AGENTS.md`  

---

## 1. الملخص التنفيذي (Executive Summary)

تم إجراء مسح شامل ودقيق لكافة مكونات طبقة Kotlin في مشروع **WhatsUp Automation** بما يشمل:
1. **طبقة الخدمات الخلفية (Service Layer):** `WhatsAppForegroundService`, `WhatsAppNotificationListenerService`, `WhatsAppAccessibilityService`, `BootReceiver`.
2. **تكامل الجسر المحلي (Bridge Integration):** آلية التواصل ثنائية الاتجاه بين كوتلن ومحرك Baileys المدمج عبر `BaileysBridgeManager` و `WhatsAppEngine`.
3. **طبقة العرض والواجهات (Presentation Layer):** شاشات ونماذج العرض `LogsScreen`/`LogsViewModel`, `StatusScreen`/`StatusesViewModel`, `RulesScreen`/`RulesViewModel`, `DashboardScreen`/`DashboardViewModel`.
4. **طبقة البيانات ومنطق الأعمال (Domain & Data Layer):** `ProcessIncomingMessageUseCase`, `ActivityLogDao`, `StatusDao`, `RepositoriesImpl`, ونماذج Room والـ Domain.
5. **التقييم المعماري وفحص Clean Architecture:** فحص فئات God Classes، وتسريب منطق الأعمال، والالتزام بالقواعد الصارمة لدستور المشروع في `AGENTS.md`.

### أبرز الاكتشافات الحرجة (Critical Findings):
1. **جذر مشكلة تفاعلات الحالات ورسائل الانتظار (R1 Root Cause):**
   - عند معالجة الحالات الصادرة من حسابات شخصية (LID)، يتم استبدال معرف المشارك الأصلي (`msg.key.participant` وهو بصيغة `<lid>@lid`) برقم الهاتف الحقيقي فور التعرف عليه في كوتلن (`effectivePhone = realPhone ?: senderPhone`).
   - عند طلب التفاعل (`REACT_STATUS`) أو المشاهدة (`VIEW_STATUS`)، يُرسل رقم الهاتف بدلاً من معرف الـ LID الأصلي، مما يؤدي إلى فشل التفاعل في Baileys عند استهدافه عبر `statusJidList`.
   - والأسوأ من ذلك: داخل `index.js` (الأسطر 840-848)، يوجد كود احتياطي (Fallback) كارثي يقوم عند فشل الإرسال إلى `status@broadcast` بإرسال كائن التفاعل مباشرة كرسالة خاصة للناشر في الـ DM (`sock.sendMessage(targetParticipant, reactionMsg)`)! هذا هو السبب الرئيسي القطعي لظهور رسائل غير مشفرة ومعلقة ("Waiting for this message. This may take a while") ورسائل مكررة في الدردشات الخاصة.
   - كيان Room لقواعد البيانات `StatusEntity` **لا يحفظ** أي من حقول `isLid`, `realPhone`, `lid`, أو `participantJid`؛ وبالتالي عند إعادة قراءة الحالات بعد إغلاق التطبيق أو عبر `processExistingUnviewedStatuses`، تفقد الحالات هويتها تماماً.
2. **مخالفة R2 الصريحة في تسجيل الرسائل غير المطابقة (Clutter Violation):**
   - في `ProcessIncomingMessageUseCase.kt` (السطور 148-155)، يتم صراحة حفظ كل رسالة واردة لا تطابق أي قاعدة داخل قاعدة البيانات بصفة `LogStatus.NO_MATCH`، مما يتسبب في ملء قاعدة البيانات بحشو المحادثات غير المرغوبة.
   - في الردود المؤتمتة الناجحة، لا يتم حفظ اسم جهة الاتصال أو تفاصيل شرط التطابق بوضوح في سجل النشاط.
3. **فئة God Class جزئية في `WhatsAppEngine.kt`:**
   - يجمع `WhatsAppEngine` بين إدارة العمليات المحلية، كاش إزالة التكرار والحجز الذري، تنظيف دليل الهاتف، واستعلامات أولوية الأسماء، وتسجيل أحداث `ActivityLog` مباشرة متجاوزاً طبقة الدومين.
4. **ثغرة حرجة في `AndroidManifest.xml`:**
   - خدمة `WhatsAppNotificationListenerService` المنفذة بالكامل غير مسجلة في `AndroidManifest.xml`، مما يعطل تفعيلها التلقائي من نظام أندرويد.

---

## 2. المسح التفصيلي لطبقة الخدمات (Service Layer Deep Dive)

### أ. الخدمة الأمامية الدائمة (`WhatsAppForegroundService.kt` — 521 سطر)
- **دورها:** حجر الزاوية للتشغيل الدائم 24/7 دون توقف (`START_STICKY`) مع نوع خدمة `FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING`.
- **التعافي الذاتي وحصانة النظام:**
  - تحوز `PowerManager.PARTIAL_WAKE_LOCK` مع مهلة أمان 12 ساعة لتفادي نوم المعالج أثناء فحص الرسائل.
  - تستخدم `AlarmManager.RTC_WAKEUP` في دالة `onTaskRemoved` لإعادة تشغيل الخدمة خلال 1000ms فور قيام المستخدم بمسح التطبيق من قائمة المهام الأخيرة (Swipe-to-dismiss).
  - تراقب الشبكة عبر `ConnectivityManager.NetworkCallback` لإعادة ربط المحرك فور عودة الاتصال بالإنترنت.
  - تفحص أذونات `WRITE_CONTACTS` و `READ_CONTACTS` دورياً كل 30 ثانية وتُصدر إشعار تنبيه مرتفع الأهمية للمستخدم في حال فقدان الصلاحية.
- **معالجة الرسائل الواردة (`observeIncomingMessages`):**
  - تستثني رسائل الإشعارات (`NOTIF_`) لمنع الازدواجية مع خدمة الإشعارات.
  - تستثني القنوات الإخبارية والمجموعات (`@newsletter`, `@broadcast`, `120363`).
  - تحجز الرسالة ذرياً عبر `whatsAppEngine.tryClaimMessageProcessing` بنافذة 60 ثانية.
  - تستدعي `processIncomingMessageUseCase.execute(message)` وترسل الردود عبر `whatsAppEngine.sendMessage`.
- **معالجة الحالات الواردة والسابقة (`observeIncomingStatuses` و `processExistingUnviewedStatuses`):**
  - تستقبل تدفق الحالات وتخزنها في `statusRepository`.
  - تطبق المشاهدة التلقائية والتفاعل التلقائي بحسب إعدادات `StatusAutomationSettings`.
  - **الخلل المرصود:** تستخدم `val targetPhone = status.realPhone ?: status.senderPhone` لتمريره إلى `markStatusViewed` و `reactToStatus`. إذا كان الحساب LID وتم فك رقمه الحقيقي، يتم إرسال رقم الهاتف بدلاً من معرف الـ LID الأصلي، مما يكسر التفاعل في Baileys!

### ب. خدمة الاستماع للإشعارات الرسمية (`WhatsAppNotificationListenerService.kt` — 213 سطر)
- **دورها:** التقاط إشعارات واتساب الرسمية (WhatsApp العادي `com.whatsapp` و WhatsApp Business `com.whatsapp.w4b`).
- **المعالجة والرد الفوري:**
  - تستخرج المرسل والنص مع كاش فريد لآخر 200 إشعار (`processedMessageCache`) لمنع التكرار.
  - تحجز الرسالة ذرياً في `whatsAppEngine` للتنسيق مع محرك Baileys.
  - تستخرج كائن `RemoteInput` المدمج في الإشعار الرسمي وترد مباشرة عبر `sendActualReply` بواسطة `actionIntent.send()` في حال كان محرك Baileys غير متصل.
- **الثغرة المكتشفة:** الخدمة غير معلنة في `AndroidManifest.xml` كـ `NotificationListenerService`، مما يمنع نظام أندرويد من ربطها أو تشغيلها فعلياً!

### ج. خدمة إمكانية الوصول (`WhatsAppAccessibilityService.kt` — 58 سطر)
- خدمة احتياطية هجينة خفيفة الوزن محجوزة للرصد الهجين، لا تستهلك أي معالجة ولا تتدخل في الردود حالياً لتوفير البطارية.

### د. مستقبل الإقلاع (`BootReceiver.kt` — 30 سطر)
- مسجل لأحداث `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `QUICKBOOT_POWERON` ويقوم فوراً باستدعاء `WhatsAppForegroundService.start(context)`.

---

## 3. تكامل الجسر المحلي (Bridge Integration: Kotlin <-> Node.js)

### أ. قناة الاتصال وبروتوكول IPC
1. **التشغيل:** يتم تشغيل محرك Node.js 18 المدمج محلياً داخل الهاتف بدون أي سيرفر خارجي عبر `NodeRunner` (JNI) مشغلاً `bundle.cjs` من دليل التطبيق الداخلي.
2. **بروتوكول الأوامر:**
   - **من Kotlin إلى Node.js:** عبر `Local Socket` إلى `127.0.0.1:6789` (أو `stdin` كمسار بديل) بصيغة JSON مفصولة بسطر جديد (`\n`).
   - **من Node.js إلى Kotlin:** عبر أحداث قياسية مبدوءة بـ `[WHATSAPP_EVENT]{"event":"...", "data":{...}}`.
3. **الأوامر المدعومة في الجسر:**
   - `PAIR_CODE`: طلب كود الاقتران.
   - `SEND_MESSAGE`: إرسال رسالة نصية.
   - `REACT_MESSAGE`: تفاعل مع رسالة خاصة.
   - `VIEW_STATUS`: تسجيل مشاهدة حالة.
   - `REACT_STATUS`: إرسال تفاعل إيموجي على الحالة.
   - `FETCH_STATUSES`: جلب الحالات النشطة.
   - `LOGOUT`: تسجيل الخروج ومسح الجلسة.

### ب. التحليل الجذري لفشل تفاعلات الحالات ورسائل الانتظار (R1 Root Cause Analysis)

من خلال فحص `WhatsAppEngine.kt` و `BaileysBridgeManager.kt` ومطابقتها مع كود `index.js`:
```
[WhatsApp Status Event] (msg.key.remoteJid = 'status@broadcast', msg.key.participant = '<lid>@lid')
        │
        ▼ (index.js: processStatusMessage)
resolvePhoneAndLid() ──> resolvedPhone = '96777xxxxxx' (Real Phone)
        │
        ▼ (emitToAndroid: INCOMING_STATUS)
{ id, senderPhone: '96777xxxxxx', realPhone: '96777xxxxxx', lid: '<lid>' }
        │
        ▼ (WhatsAppEngine.kt: lines 467-496)
val effectivePhone = realPhone ?: senderPhone  // أصبحت '96777xxxxxx'
val story = StatusStory(..., senderPhone = effectivePhone, ...)
        │
        ▼ (Room Database: StatusDao.insertStatus)
StatusEntity(id, senderPhone = '96777xxxxxx', ...) // ⚠️ تم إسقاط isLid, realPhone, lid, participant بالكامل!
        │
        ▼ (WhatsAppForegroundService.kt: lines 275-278)
val targetPhone = status.realPhone ?: status.senderPhone // أصبحت '96777xxxxxx'
whatsAppEngine.reactToStatus(status.id, emoji, targetPhone)
        │
        ▼ (BaileysBridgeManager.kt: lines 254-262)
{"action":"REACT_STATUS", "statusId": id, "senderPhone": "96777xxxxxx", "emoji": "💚"}
        │
        ▼ (index.js: lines 790-848)
1. إذا لم يكن statusId في الذاكرة الحية (statusKeysMap):
   rawParticipant = '96777xxxxxx@s.whatsapp.net' (لأنه رقم هاتف وليس LID!)
2. يتم إرسال التفاعل إلى:
   sock.sendMessage('status@broadcast', reactionMsg, { statusJidList: ['96777xxxxxx@s.whatsapp.net'] })
   ❌ تفشل العملية لأن الحالة نشرت بواسطة '<lid>@lid' وليس الهاتف!
3. ⚠️ الكارثة: سطر 841 في index.js:
   catch (_) {
       sock.sendMessage(targetParticipant, reactionMsg); // إرسال التفاعل للخاص DM!
   }
   ❌ يتلقى الطرف الآخر رسالة تفاعل موجهة لـ status@broadcast داخل محادثته الخاصة،
   فتظهر كرسالة غير مشفرة ومعلقة: "Waiting for this message. This may take a while."!
```

---

## 4. مسح طبقة العرض والواجهات (Presentation Layer)

### أ. شاشة ونموذج سجل النشاط (`LogsScreen.kt` & `LogsViewModel.kt`)
- **الهيكل:** 3 تبويبات رئيسية:
  1. `CONVERSATIONS`: تجميع الرسائل في بطاقات محادثات فريدة حسب رقم الهاتف مع كشف الاسم من دفتر الهاتف.
  2. `RAW_LOGS`: سجل النشاطات الفردية مع فلاتر (الكل، ناجح، أخطاء، تجاهل `NO_MATCH`).
  3. `SYSTEM_DIAGNOSTICS`: تقرير حي لأحداث المحرك مع زر نسخ شامل.
- **التوافق مع R2:**
  - التبويب يعرض حالياً فلتر `NO_MATCH`، وعدد الرسائل المهملة يظهر للمستخدم.
  - للامتثال لـ R2، يجب إزالة فلتر وحالة `NO_MATCH` تماماً، ليقتصر السجل على:
    1. الردود التلقائية الناجحة مع اسم جهة الاتصال والنمط المطابق.
    2. تسجيل وتحديث جهات الاتصال.
    3. مشاهدات وتفاعلات الحالات.

### ب. شاشة ونموذج الحالات (`StatusesScreen.kt` & `StatusesViewModel.kt`)
- **الهيكل:** تبويبان فرعيان (جديدة `NEW`، ومكتملة `VIEWED`) مع إحصائيات علوية (إجمالي المشاهدات، الأشخاص الفريدين).
- **التوافق مع R1:**
  - في دوال `markStatusViewed`, `reactToStatus`, `viewAndReact`, `viewAllStatusesAndReact`، يمرر الـ ViewModel قيمة `status.senderPhone`.
  - مع تعديل الكيان ليشمل `participantJid`، ستمرر هذه الدوال المعرف الأصلي مباشرة لضمان سلامة الإرسال.

### ج. شاشة ونموذج القواعد (`RulesScreen.kt` & `RulesViewModel.kt`)
- تدعم إنشاء وتعديل وحذف القواعد بأنواع الأنماط (`CONTAINS`, `STARTS_WITH`, `EXACT_MATCH`, `REGEX`) مع فحص تجريبي حي (`testInput` / `testResult`).
- البنية ممتازة وتتبع نمط MVVM/MVI بدقة.

### د. شاشة ونموذج لوحة التحكم (`DashboardScreen.kt` & `DashboardViewModel.kt`)
- تعرض حالة الاتصال، حالة المساعد، تنبيهات الصلاحيات، ونافذة Terminal تفاعلية حية.
- شبكة الإحصائيات تحوي حالياً بطاقة "بدون مطابقة" (`totalIgnored`) المستندة لـ `LogDao.getNoMatchCount()`.
- للامتثال لـ R2، يمكن تحويل هذه البطاقة إلى "تفاعلات الحالات" أو "إجمالي الأتمتة الناجحة".

---

## 5. تقييم Clean Architecture واكتشاف الانتهاكات

| المكون | التقييم | الملاحظات والانتهاكات المرصودة |
|---|---|---|
| **فصل الطبقات (Layer Separation)** | **جيد جداً مع استثناءات محددة** | طبقة Compose نقية وخالية من منطق الأعمال. الـ ViewModels تدير الـ State عبر `StateFlow`. |
| **`WhatsAppEngine.kt`** | **انتهاك جزئي (God Class)** | يجمع بين 6 مسؤوليات مختلفة (العملية، الربط، كاش التنافس، تنظيف جهات الاتصال، تحليل JSON، وحفظ السجلات في الـ Repository مباشرة). |
| **`ProcessIncomingMessageUseCase.kt`** | **انتهاك لمتطلب R2** | يقوم صراحة بحفظ سجل `NO_MATCH` في قاعدة البيانات بدلاً من التجاهل الصامت. كما يفتقر حفظ الرد الناجح لاسم جهة الاتصال الصريح. |
| **تطابق الـ Entity مع الـ Domain (`StatusStory`)** | **خلل بنيوي حرج** | `StatusEntity` في Room لا يحتوي على `isLid`, `realPhone`, `lid`, أو `participantJid`. البيانات تُفقد بمجرد الحفظ في Room! |
| **تجريد المستودعات (Repository Pattern)** | **ممتاز** | كل مستودع يملك Interface في `domain/repository` وتنفيذ في `data/repository` مع Hilt DI. |
| **بيان التطبيق (`AndroidManifest.xml`)** | **خلل تشغيلي حرج** | غياب تعريف `WhatsAppNotificationListenerService` يمنع تفعيل خدمة الإشعارات بالنظام. |

---

## 6. خطة التعديلات البرمجية لـ Kotlin (Blueprint for R1, R2, R3)

### أولاً: تعديلات متطلب R1 (وثوقية تفاعلات الحالات ومنع رسائل الـ DM)
1. **تحديث نموذج الدومين `StatusStory` في `domain/model/Models.kt`:**
   - إضافة حقل `val participant: String = ""` (يحمل المعرف الأصلي `msg.key.participant` سواء كان `<lid>@lid` أو `<phone>@s.whatsapp.net`).
2. **تحديث كيان Room `StatusEntity` في `data/local/entity/Entities.kt`:**
   - إضافة الأعمدة: `val participant: String = ""`, `val isLid: Boolean = false`, `val realPhone: String? = null`, `val lid: String? = null`.
3. **تحديث دوال التحويل (Mappers) في `RepositoriesImpl.kt`:**
   - تضمين الحقول الجديدة في `StatusEntity.toDomain()` و `StatusStory.toEntity()`.
4. **تحديث استخراج الحالة في `WhatsAppEngine.kt`:**
   - قراءة `participant = data.optString("participant", senderPhone)` وبناء `StatusStory` مع الحفاظ على الـ `participant` الأصلي.
5. **تحديث أوامر الجسر في `BaileysBridgeManager.kt`:**
   - تمرير `participant` الأصلي في حزمة JSON لأوامر `VIEW_STATUS` و `REACT_STATUS`.
6. **تحديث استدعاءات الخدمة والـ ViewModel:**
   - في `WhatsAppForegroundService.kt` و `StatusesViewModel.kt`: استدعاء `whatsAppEngine.reactToStatus(status.id, emoji, status.participant.ifBlank { status.senderPhone })`.

### ثانياً: تعديلات متطلب R2 (السجل الانتقائي الخالي من الحشو والتنظيف الشامل)
1. **تعديل `ProcessIncomingMessageUseCase.kt`:**
   - حذف كود إنشاء وحفظ `ActivityLog` بحالة `NO_MATCH` نهائياً (السطور 148-155)؛ الاكتفاء بإرجاع `ProcessResult.NoMatch`.
   - عند تنفيذ رد تلقائي ناجح (`executeRuleActions`):
     - استخراج اسم جهة الاتصال عبر `deviceContactsManager.getContactDisplayName(message.senderPhone) ?: message.senderName`.
     - تسجيل `matchedRule = "${rule.name} [مطابقة: ${rule.patternValue}]"`.
     - تسجيل `extractedName` باسم الشخص، و `actionExecuted = "رد تلقائي: $finalReply"`.
2. **تنظيف وتطهير قاعدة البيانات من الحشو في `LogDao` و `RepositoriesImpl.kt`:**
   - إضافة دالة في `LogDao`:
     ```kotlin
     @Query("DELETE FROM activity_logs WHERE status = 'NO_MATCH'")
     suspend fun purgeNoMatchLogs(): Int
     ```
   - استدعاء التطهير تلقائياً عند بدء تشغيل `LogRepositoryImpl` أو `WhatsAppEngine` لتنظيف كافة السجلات القديمة غير المطابقة فوراً.
3. **تحديث `LogsViewModel.kt` و `LogsScreen.kt`:**
   - إزالة فلتر `LogStatusFilter.NO_MATCH` وشريحة فلتر "تجاهل" من الشاشة.
   - ضمان عرض الاسم والنمط المطابق والرد بشكل أنيق داخل بطاقات السجل.
4. **تحديث `DashboardStats` و `DashboardScreen.kt`:**
   - تحديث بطاقة "بدون مطابقة" لتعكس "العمليات الناجحة" أو إخفائها لتفادي عرض أرقام غير مطابقة.

### ثالثاً: تعديلات متطلب R3 (تفكيك الكود ودعم المعمارية النظيفة)
1. **إصلاح `AndroidManifest.xml`:**
   - إضافة تصريح خدمة `WhatsAppNotificationListenerService` مع إذن `BIND_NOTIFICATION_LISTENER_SERVICE`.
2. **تفكيك `WhatsAppEngine.kt`:**
   - استخراج إدارة حجز الرسائل ومنع التكرار إلى فئة مستقلة: `MessageDeduplicationManager`.
   - توجيه تسجيل أحداث الحالات عبر حالة استخدام أو من خلال الخدمة بدلاً من حقن `LogRepository` مباشرة داخل المحرك.

---

## 7. خلاصة واستنتاج المسح (Survey Conclusion)

طبقة كوتلن تتمتع بأساس متين جداً من حيث Clean Architecture و Jetpack Compose ونظام Hilt DI. المشاكل التي تواجه المشروع في R1 و R2 محددة بدقة:
- **R1:** ناتج عن استبدال معرف الـ LID برقم الهاتف في كوتلن قبل إعادة إرساله للحالة، مقترناً بوجود كود Fallback خاطئ في Node.js يرسل التفاعل إلى المحادثة الخاصة DM.
- **R2:** ناتج عن وجود سطر صريح يحفظ `NO_MATCH` في `ProcessIncomingMessageUseCase.kt`، وغياب تنظيف السجلات القديمة.
- الخطة أعلاه تقدم الحل الشامل والدقيق والجاهز للتنفيذ دون أي مخاطر معمارية.
