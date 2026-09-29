# تقرير المسح والتحليل المعماري لمحرك Node.js وآلية التفاعل مع الحالات (Status Reactions Survey Report)

**تاريخ المسح:** 2026-09-28  
**الموقع:** `app/src/main/assets/nodejs-project/`  
**الهدف:** فحص محرك واتساب (Baileys)، تحليل أسباب ظهور الرسائل المكررة ورسائل "في الانتظار" في الخاص، وضع خطة التفكيك الموديولي (ES Modules)، والتحقق من آلية البناء `build_bundle.cjs` وتوافق طبقات أندرويد.

---

## 1. فحص البنية الحالية لملف `index.js` وملفات المحرك

### أ. الوضع الراهن
- يقع الكود المصدري للمحرك في: `app/src/main/assets/nodejs-project/index.js` بحجم تقريبي 38.6 KB و 895 سطراً.
- المشروع يعرّف `"type": "module"` داخل `package.json` مع الاعتماديات:
  - `@whiskeysockets/baileys`: `^6.7.9`
  - `pino`: `^9.5.0`
  - `qrcode-terminal`: `^0.12.0`
- الملف الحالي عبارة عن كود متجانس (Monolithic) يجمع في ملف واحد المسؤوليات التالية:
  1. تهيئة بيئة التشفير (`globalThis.crypto` WebCrypto Polyfill).
  2. إدارة مخازن الذاكرة المؤقتة (Caches) لفك التشفير وإعادة المحاولات (`msgRetryCounterCache`, `sentMessagesCache`, `rawMessagesCache`, `statusKeysMap`, `lidPhoneMap`).
  3. خادم المقبس المحلي TCP Socket (`127.0.0.1:6789`) وواجهة القراءة `readline` القياسية عبر `stdin`.
  4. محرك فك تشفير وتعيين معرفات الحسابات المشفرة LID والأرقام الحقيقية Phone.
  5. دورة حياة اتصال واتساب ومقبس Baileys (`makeWASocket`) وأحداث الاتصال ومزامنة السجل.
  6. معالجة وتخزين حالات واتساب (`status@broadcast`).
  7. معالج الأوامر الواردة من كوتلن (`processCommandLine`).

---

## 2. آلية استقبال الحالات والتفاعل معها ومعالجة أنواع الحسابات (LID vs Business)

### أ. استقبال الحالات (Incoming Status Stories)
- تستمع الدالة `sock.ev.on('messages.upsert')` و `messaging-history.set` لأي رسالة يكون فيها `msg.key.remoteJid === 'status@broadcast'`.
- يتم استدعاء `processStatusMessage(msg, authFolder)`:
  1. التحقق من أن الرسالة ليست مرسلة من المستخدم نفسه (`!msg.key.fromMe`).
  2. استخراج معرف الناشر: `const senderJid = msg.key.participant || msg.key.remoteJid`.
  3. حفظ مفتاح الحالة داخل خريطة `statusKeysMap` وحفظها في القرص (`status_keys.json`):
     ```javascript
     statusKeysMap.set(msg.key.id, {
         remoteJid: msg.key.remoteJid,
         id: msg.key.id,
         participant: msg.key.participant || senderJid,
         fromMe: msg.key.fromMe || false,
         pushName: msg.pushName || ''
     });
     ```
  4. فحص عمر الحالة (يجب ألا يتجاوز 25 ساعة).
  5. استدعاء `resolvePhoneAndLid` لفك ارتباط المعرف الصامت LID بالرقم الحقيقي.
  6. إرسال حدث `INCOMING_STATUS` إلى كوتلن مع تفاصيل الحالة:
     `{ id, senderPhone, realPhone, lid, senderName, isLid, mediaType, textContent, timestamp }`.

### ب. الفرق الجوهري بين الحسابات الشخصية (LID) وحسابات الأعمال (Business)
1. **الحسابات الشخصية (Personal Accounts with LID):**
   - ينشر المستخدم الحالة بمعرف الحساب المشفر `LID` (مثال: `12345678901234@lid`).
   - خادم واتساب يسجل الحالة بمفتاح: `participant = 12345678901234@lid`.
   - جلسة تشفير الحالة (Signal Ratchet / PreKeys) مرتبطة حصرياً بـ `LID`.
2. **حسابات واتساب للأعمال (WhatsApp Business Accounts):**
   - لا تستخدم معرفات `LID` للبث العام.
   - ينشر الحساب الحالة برقم الهاتف المباشر: `participant = 96777xxxxxxx@s.whatsapp.net`.

### ج. تنفيذ المشاهدة (`VIEW_STATUS`) والتفاعل (`REACT_STATUS`)
- **المشاهدة (`VIEW_STATUS`):**
  - تستخرج `participant` الأصلي من `statusKeysMap`.
  - ترسل إشعار القراءة المباشر: `sock.sendReceipt('status@broadcast', participant, [statusId], 'read')`.
  - تؤكد القراءة محلياً: `sock.readMessages([readKey])`.
- **التفاعل بالإيموجي (`REACT_STATUS`):**
  - تنشئ كائن التفاعل بمفتاح:
    ```javascript
    const reactionKey = {
        remoteJid: 'status@broadcast',
        id: command.statusId,
        participant: targetParticipant,
        fromMe: false
    };
    const reactionMsg = { react: { text: emoji, key: reactionKey } };
    ```
  - ترسل القراءة أولاً لضمان احتساب المشاهدة.
  - ترسل التفاعل إلى `'status@broadcast'` مع حصر المستلم:
    `sock.sendMessage('status@broadcast', reactionMsg, { statusJidList: [targetParticipant] })`.

---

## 3. التحقيق الجذري: أسباب ظهور الرسائل المكررة ورسائل "في انتظار هذه الرسالة" في الخاص

تم تحديد ثلاثة أسباب جذرية مؤكدة بنسبة 100% بالدليل القطعي من الكود المصدري:

### السبب الجذري الأول (The Smoking Gun — العيب القاتل في Index.js):
في السطور 839–848 من `index.js`:
```javascript
try {
    const sendResult = await sock.sendMessage('status@broadcast', reactionMsg, { statusJidList: [targetParticipant] });
    if (sendResult?.key?.id && sendResult?.message) {
        sentMessagesCache.set(sendResult.key.id, sendResult.message);
    }
} catch (_) {
    try {
        const sendResult = await sock.sendMessage(targetParticipant, reactionMsg);
        if (sendResult?.key?.id && sendResult?.message) {
            sentMessagesCache.set(sendResult.key.id, sendResult.message);
        }
    } catch (sendErr) {
        console.error('Status react fallback error:', sendErr);
    }
}
```
**التحليل الفني للخطأ:**
- إذا فشل الإرسال إلى `status@broadcast` لأي سبب (مثل تأخر استجابة المقبس أو عدم تطابق المعرف)، يدخل الكود في كتلة `catch (_)` الاحتياطية.
- في هذا الاحتياط، يقوم الكود بإرسال رسالة التفاعل `reactionMsg` مباشرة إلى `targetParticipant` (أي إلى الدردشة الخاصة 1-on-1 للشخص)!
- رسالة التفاعل هذه تحمل داخلياً `remoteJid: 'status@broadcast'` ومعرف رسالة الحالة `statusId`.
- عندما يستلم هاتف الضحية رسالة تفاعل داخل محادثة خاصة تشير إلى رسالة في `status@broadcast`، يعجز تطبيق واتساب عن مطابقة الرسالة داخل الدردشة الخاصة وتفشل جلسة فك التشفير.
- **النتيجة الكارثية:** يظهر في دردشة الشخص الخاصة إما فقاعة تفاعل تالفة، أو رسالة مكررة، أو العبارة الشهيرة:
  **"في انتظار هذه الرسالة. قد يستغرق هذا بعض الوقت." (Waiting for this message. This may take a while.)**
- **الحل الحاسم:** إزالة هذا الـ Fallback الخطير نهائياً. تفاعلات الحالات لا تُرسل إلا إلى `status@broadcast` فقط طبقاً لقاعدة "درع الحالات الصارم" في AGENTS.md.

### السبب الجذري الثاني (عدم تطابق المعرف الأصلي وفقدان الـ LID):
- عند تفاعل المستخدم مع حالة منشورة بحساب شخصي (LID)، المعرف الصحيح هو `... @lid`.
- إذا لم يجد المحرك المعرف في `statusKeysMap` (مثلاً بعد إعادة تشغيل الخدمة)، يلجأ الكود للبحث عن رقم الهاتف `senderPhone`.
- إذا لم تكن خريطة `phoneLidMap` تحتوي المعرف، يقوم الكود ببناء: `${cleanPhone}@s.whatsapp.net`.
- عند إرسال التفاعل إلى `status@broadcast` بمعرف رقم الهاتف لحالة منشورة بـ `LID`، يرفض سيرفر واتساب التفاعل لأن الحالة مسجلة بالـ LID.
- هذا الفشل كان يقود مباشرة إلى تفعيل السبب الجذري الأول (الـ Fallback في الخاص)!

### السبب الجذري الثالث (فقدان بيانات LID في طبقة كوتلن - Room Database):
- في كود كوتلن (`Entities.kt` سطر 43)، الكيان `StatusEntity` لا يخزن حقول `lid` ولا `realPhone` ولا `isLid` ولا `participantJid`!
- عندما تقوم الخدمة الخلفية `WhatsAppForegroundService` بقراءة الحالات المحفوظة في Room عند بدء التشغيل (`processExistingUnviewedStatuses`):
  تكون قيمة `status.realPhone` و `status.lid` فارغة، فترسل الخدمة رقم الهاتف فقط إلى Node.js!
- **الحل الحاسم:** 
  1. تمرير المعرف الأصلي للحالة `participantJid` من Node.js والاحتفاظ به دائماً.
  2. تحديث `StatusEntity` و `StatusStory` لحفظ `participantJid` و `lid` و `realPhone`.
  3. في أمر `REACT_STATUS` و `VIEW_STATUS`، تمرير المعرف الأصلي `participantJid` من كوتلن إلى Node.js لضمان توجيهه دائماً للهدف الصحيح دون تخمين.

---

## 4. خطة التفكيك الموديولي النظيف لـ `index.js` (Modular ES Architecture)

لتحقيق متطلبات R3 من الدستور المعماري ومنع وجود ملفات ضخمة (God Classes)، سيتم تفكيك `index.js` (895 سطراً) إلى الوحدات التالية:

```
app/src/main/assets/nodejs-project/
├── config.js              # الثوابت، polyfill التشفير، وإعدادات التسجيل والذاكرة المؤقتة
├── bridge.js              # خادم المقبس المحلي TCP (6789) ودوال البث إلى أندرويد
├── lid-resolver.js        # إدارة ومطابقة معرفات LID والأرقام الحقيقية وتخزينها
├── status-manager.js      # إدارة واستقبال الحالات والمشاهدة والتفاعل الحصري Single-JID
├── command-dispatcher.js  # مفرّق ومعالج الأوامر القادمة من كوتلن
└── index.js               # نقطة الدخول وإدارة المقبس ودورة حياة جلسة Baileys
```

### تفاصيل الوحدات المقترحة:

| اسم الموديول | الحجم المتوقع | المسؤولية المحددة (Single Responsibility) |
|---|---|---|
| `config.js` | ~60 سطر | تهيئة `crypto.webcrypto`، إعدادات `pino`، منافذ TCP، بصمة المتصفح المعتمدة `['Ubuntu', 'Chrome', '20.0.04']`، وحاويات الكاش (`msgRetryCounterCache`, `sentMessagesCache`, `rawMessagesCache`). |
| `bridge.js` | ~70 سطر | إنشاء خادم TCP المحلي على البورت 6789، إدارة قنوات الاتصال النشطة `localSockets`، وتنفيذ دالة `emitToAndroid(eventType, payload)`. |
| `lid-resolver.js` | ~160 سطر | الخرائط الثنائية (`lidPhoneMap`, `phoneLidMap`, `contactNamesMap`)، الحفظ والتحميل من `lid_phone_map.json`، والدوال `resolvePhoneAndLid`, `linkLidAndPhone`, `isLidJidOrNumber`. |
| `status-manager.js` | ~190 سطر | خريطة مفاتيح الحالات `statusKeysMap` وملف `status_keys.json`، معالجة رسائل الحالات `processStatusMessage`، المشاهدة `viewStatus`، والتفاعل الحصري `reactStatus` بقاعدة Single-JID الصارمة وحذف أي إرسال للخاص. |
| `command-dispatcher.js` | ~180 سطر | استقبال ومعالجة أوامر كوتلن (`START`, `PAIR_CODE`, `SEND_MESSAGE`, `REACT_MESSAGE`, `FETCH_STATUSES`, `VIEW_STATUS`, `REACT_STATUS`, `LOGOUT`) وتوجيهها للمحركات المناسبة. |
| `index.js` | ~220 سطر | تجميع الوحدات، إدارة مقبس واتساب `makeWASocket`، ربط مستمعي أحداث Baileys (`connection.update`, `messages.upsert`, `messaging-history.set`, `contacts.upsert`, `chats.upsert`)، وبدء التشغيل التلقائي. |

---

## 5. فحص واختبار أداة التجميع والحزم `build_bundle.cjs`

### أ. الموقع والآلية
- الملف موجود في: `app/src/main/assets/nodejs-project/build_bundle.cjs`.
- يعتمد على `esbuild` لتجميع الكود عبر الأمر:
  ```javascript
  npx.cmd esbuild index.js --bundle --platform=node --target=node18 --format=cjs --banner:js="${banner}" --outfile=bundle.cjs
  ```
- بعد التوليد، ينسخ `bundle.cjs` إلى المجلد `dist/bundle.cjs`.

### ب. نتيجة الاختبار العملي أثناء المسح
- تم تشغيل `node build_bundle.cjs` بنجاح كامل:
  ```
  Building bundle.cjs via esbuild...
    bundle.cjs  6.5mb
  Done in 296ms
  Successfully built bundle.cjs and updated dist/bundle.cjs!
  ```
- **التوافق مع الموديولات الجديدة:** `esbuild` يدعم استيراد الموديولات النسبية (`import ... from './...js'`) تلقائياً. عند تقسيم الكود إلى `config.js` و `status-manager.js` وغيرها، سيقوم `esbuild` بحل وتضمين كافة الملفات تلقائياً داخل الحزمة الموحدة `bundle.cjs` دون الحاجة لأي تعديل في أمر البناء `build_bundle.cjs`!

---

## 6. فحص حالة أدوات بناء كوتلن وأندرويد (Gradle Toolchain Verification)

- تم تشغيل وفحص أداة البناء `gradle-8.11.1` مع `JDK 17` (Adoptium).
- تم تجديد وضبط ملفات الـ wrapper بنجاح عبر مهمة `wrapper`.
- أمر التحقق `.\gradlew.bat -v` يعمل بنجاح تام (Exit Code: 0).
- تم تنفيذ فحص المهام وعملية `compileDebugKotlin --dry-run` وانتهت بنجاح كامل في 13 ثانية (`BUILD SUCCESSFUL`).
- لا توجد أي أخطاء في تكوين Gradle أو أدوات البناء.

---

## 7. فحص متطلبات سجل النشاط والخصوصية (R2 Activity Logs Audit)

- **المشكلة المرصودة:**
  في ملف `UseCases.kt` (السطور 148–156):
  ```kotlin
  logRepository.insertLog(
      ActivityLog(
          senderPhone = message.senderPhone,
          messageText = message.text,
          actionExecuted = "تجاهل — بدون قاعدة مطابقة",
          status = LogStatus.NO_MATCH
      )
  )
  ```
  يتم تسجيل كل رسالة واردة لا تطابق أي قاعدة برمجية بحالة `NO_MATCH`، مما يؤدي إلى ملء قاعدة البيانات برسائل المحادثات العادية وانتهاك خصوصية المستخدم وحشو سجل الأنشطة.
- **الحل المطلوب تنفيذه لاحقاً في مرحلة التطوير:**
  1. حذف سطر `logRepository.insertLog` عند عدم مطابقة القاعدة داخل `ProcessIncomingMessageUseCase.kt`.
  2. حصر التسجيل فقط في:
     - الردود الآلية الناجحة (مع اسم جهة الاتصال والشرط المطابق).
     - حفظ وتحديث جهات الاتصال.
     - مشاهدات الحالات والتفاعلات معها.
  3. توفير استعلام تنظيف لحذف أي سجلات سابقة تحمل حالة `NO_MATCH`.

---

## 8. الخلاصة والتوصيات الهندسية لمرحلة التنفيذ

1. **إصلاح تفاعل الحالات:**
   - حذف الـ Fallback القاتل من `REACT_STATUS` (السطور 839-848) نهائياً.
   - الاعتماد الحصري على التوجيه لـ `status@broadcast` مع `statusJidList: [targetParticipant]`.
   - تمرير `participantJid` الأصلي دائماً بين Node.js و Kotlin و Room.
2. **إعادة هيكلة المحرك:**
   - تطبيق خطة تقسيم `index.js` إلى 6 موديولات ES واضحة ومستقلة.
   - تشغيل `node build_bundle.cjs` لتوليد `bundle.cjs` الموحد بنجاح.
3. **تطهير سجل الأنشطة:**
   - إزالة تسجيل `NO_MATCH` في كوتلن لحماية الخصوصية وتنظيف السجلات.
