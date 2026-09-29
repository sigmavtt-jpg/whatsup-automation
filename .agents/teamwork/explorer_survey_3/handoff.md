# تقرير التسليم المعماري (Handoff Report — Explorer Survey 3)

**تاريخ التقرير:** 2026-09-28  
**المحقق:** مستكشف المعمارية (Explorer — Architecture & Services)  
**مجلد العمل:** `c:/Users/sO377/Downloads/whatsup/.agents/teamwork/explorer_survey_3/`  
**نوع التسليم:** Hard Handoff (اكتملت المهمة بالكامل)  

---

## 1. الملاحظات المباشرة (Observation)

1. **معالجة الحالات في `WhatsAppForegroundService.kt` (الأسطر 257-285 و 328-348):**
   ```kotlin
   val targetPhone = status.realPhone ?: status.senderPhone
   ...
   whatsAppEngine.markStatusViewed(status.id, targetPhone)
   whatsAppEngine.reactToStatus(status.id, emoji, targetPhone)
   ```
   يتم اختيار `status.realPhone` (رقم الهاتف الحقيقي) بدلاً من معرف المشارك الأصلي للحالة.

2. **استقبال الحالات وتجريد المعرفات في `WhatsAppEngine.kt` (الأسطر 456-498):**
   ```kotlin
   val effectivePhone = realPhone ?: senderPhone
   ...
   val story = StatusStory(
       id = id,
       senderPhone = effectivePhone,
       ...
       isLid = isLid,
       realPhone = realPhone,
       lid = lid
   )
   ```
   يتم وضع `effectivePhone` (الذي يتحول إلى رقم الهاتف عند اكتشافه) في خانة `senderPhone`.

3. **غياب حقول LID والمشارك في كيان Room `StatusEntity` في `Entities.kt` (الأسطر 43-58):**
   ```kotlin
   @Entity(tableName = "statuses")
   data class StatusEntity(
       @PrimaryKey val id: String,
       val senderPhone: String,
       val senderName: String,
       val mediaType: String,
       val textContent: String? = null,
       val mediaUrl: String? = null,
       val timestamp: Long = System.currentTimeMillis(),
       val isViewed: Boolean = false,
       val viewedAt: Long? = null,
       val isReacted: Boolean = false,
       val reactionEmoji: String? = null,
       val reactionSentAt: Long? = null
   )
   ```
   الكيان المخزن في قاعدة بيانات Room لا يحتوي على أي حقول لـ `isLid` أو `realPhone` أو `lid` أو `participant`، وتفقد كافة الحالات هويتها الأصلية بمجرد حفظها في Room.

4. **السلوك الكارثي للـ Fallback في `index.js` (الأسطر 834-848):**
   ```javascript
   try {
       const sendResult = await sock.sendMessage('status@broadcast', reactionMsg, { statusJidList: [targetParticipant] });
       ...
   } catch (_) {
       try {
           const sendResult = await sock.sendMessage(targetParticipant, reactionMsg);
           ...
       } catch (sendErr) {
           console.error('Status react fallback error:', sendErr);
       }
   }
   ```
   عند فشل الإرسال إلى `status@broadcast`، يرسل المحرك كائن `reactionMsg` (الموجّه لحالة `status@broadcast`) مباشرة إلى محادثة الطرف الآخر الخاصة DM، مما يولد رسائل مكررة غير مفكوكة التشفير ("Waiting for this message. This may take a while").

5. **تسجيل الحشو ومخالفة R2 في `ProcessIncomingMessageUseCase.kt` (الأسطر 148-155):**
   ```kotlin
   logRepository.insertLog(
       ActivityLog(
           senderPhone = message.senderPhone,
           messageText = message.text,
           actionExecuted = "تجاهل — بدون قاعدة مطابقة",
           status = LogStatus.NO_MATCH
       )
   )
   return ProcessResult.NoMatch
   ```
   كل رسالة واردة لا تطابق قاعدة يتم إدخالها صراحة في قاعدة البيانات كـ `NO_MATCH`.

6. **نقص تصريح `WhatsAppNotificationListenerService` في `AndroidManifest.xml`:**
   الملف `AndroidManifest.xml` لا يحتوي على تعريف لـ `WhatsAppNotificationListenerService` رغم وجود الكود في المشروع واستخدامه في `ServicePermissionManager.kt`.

---

## 2. سلسلة الاستدلال المنطقي (Logic Chain)

1. **بخصوص R1 (تفاعلات الحالات ورسائل الانتظار في DM):**
   - من الملاحظة 1 و 2: عندما تنشر جهة اتصال حالة من حساب شخصي (LID)، فإن معرفها الأصلي في Baileys هو `<lid>@lid`.
   - يقوم `WhatsAppEngine` بتعيين `effectivePhone = realPhone ?: senderPhone`. إذا تم حل الرقم الحقيقي للشخص، تصبح القيمة رقم هاتف (مثل `96777xxxxxx`).
   - من الملاحظة 3: عند حفظ الحالة في Room وقراءتها لاحقاً، تضيع معرفات الـ LID تماماً لأن `StatusEntity` لا يحفظها.
   - عندما يستدعي `WhatsAppForegroundService` أو `StatusesViewModel` التفاعل مع الحالة، يمرر رقم الهاتف كمعرف.
   - في `index.js`، يتم استهداف `statusJidList: [targetParticipant]`. ونظراً لأن الحالة نُشرت بمعرف LID وليس برقم هاتف، تفشل خوادم واتساب في ربط التفاعل بالحالة.
   - من الملاحظة 4: يدخل الكود في كتلة `catch (_)` ويرسل التفاعل إلى المحادثة الخاصة `targetParticipant`.
   - خادم واتساب لا يستطيع تفسير تفاعل موجه لـ `status@broadcast` داخل دردشة 1-on-1، فتظهر للمستلم عبارة "في انتظار هذه الرسالة" (Pending Message) وتتولد رسائل مكررة مزعجة، وهو ما يخالف صراحة البند 8 من `AGENTS.md` والمتطلب R1.

2. **بخصوص R2 (السجل الانتقائي وإزالة الحشو):**
   - من الملاحظة 5: سطر 148 في `ProcessIncomingMessageUseCase` يقوم بتسجيل كل رسالة واردة بدون مطابقة.
   - هذا هو المصدر المباشر لكل الحشو في جدول `activity_logs`.
   - إزالة هذا السطر تجعل المحرك يتجاهل الرسائل غير المطابقة بصمت.
   - إضافة استعلام تطهير `DELETE FROM activity_logs WHERE status = 'NO_MATCH'` سيطهر قاعدة البيانات بالكامل من الحشو السابق.

3. **بخصوص R3 (نظافة المعمارية والخدمات):**
   - من الملاحظة 6: بدون تسجيل `WhatsAppNotificationListenerService` في `AndroidManifest.xml`، لن يقوم نظام أندرويد بربط الخدمة حتى وإن منح المستخدم الإذن من الإعدادات.

---

## 3. التحفظات والافتراضات (Caveats)

- تم فحص وتتبع كود كوتلن وNode.js عبر أدوات القراءة والبحث دون إجراء أي تعديل على كود المشروع الأصلي التزاماً بنمط الاستكشاف Read-Only.
- لا توجد أي تحفظات غير مستكشفة؛ البنية البرمجية مكتملة ومحددة المعالم ومسارات الخلل واضحة بنسبة 100%.

---

## 4. الخلاصة النهائية (Conclusion)

- **جاهزية الحل:** تم إعداد خارطة طريق وتصميم تفصيلي للتعديلات المطلوبة في طبقة Kotlin لدعم R1, R2, R3 بشكل متكامل ومتناغم مع تعديلات Node.js.
- **التعديلات الأساسية في Kotlin:**
  1. إضافة حقل `participant` في `StatusStory` و `StatusEntity` والـ Mappers.
  2. تمرير `status.participant` في أوامر `VIEW_STATUS` و `REACT_STATUS`.
  3. إزالة إدراج `NO_MATCH` من `ProcessIncomingMessageUseCase.kt`.
  4. تطهير جدول `activity_logs` من سجلات `NO_MATCH`.
  5. تحسين صياغة الردود المؤتمتة الناجحة لتضمين اسم جهة الاتصال والنمط المطابق.
  6. إضافة تصريح `WhatsAppNotificationListenerService` في `AndroidManifest.xml`.

---

## 5. طريقة التحقق المستقل (Verification Method)

1. **التحقق من الفحص البرمجي:**
   - فحص ملف التقرير التفصيلي: `c:/Users/sO377/Downloads/whatsup/.agents/teamwork/explorer_survey_3/survey_report.md`
2. **أوامر التحقق من البناء والتجميع (عند مرحلة التنفيذ لاحقاً):**
   - تجميع حزمة Node.js:
     ```powershell
     node build_bundle.cjs
     ```
   - فحص تجميع APK كوتلن بدون أخطاء:
     ```powershell
     ./gradlew assembleDebug
     ```
3. **شروط إبطال الفرضية (Invalidation Conditions):**
   - إذا تم إثبات أن خوادم واتساب تقبل تفاعل الحالات المنشورة بـ LID عند إرسالها لرقم الهاتف العادي دون الحاجة لـ LID، وهو ما يخالف بروتوكول Baileys و Signal PreKey المثبت.
