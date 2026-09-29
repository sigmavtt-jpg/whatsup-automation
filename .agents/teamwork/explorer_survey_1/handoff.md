# تقرير التسليم الهندسي (Handoff Report)

## 1. الملاحظات المباشرة (Observation)
1. **ملف المحرك المتجانس:**
   - المسار: `app/src/main/assets/nodejs-project/index.js`
   - الطول: 895 سطراً، الحجم: 38,611 بايت.
   - يدمج جميع الوظائف (خادم TCP، فك ارتباط LID، مفاتيح الحالات، استماع مقبس Baileys، ومعالجة الأوامر) في ملف واحد.
2. **العيب القاتل المسبب لرسائل الانتظار والرسائل المكررة في الخاص:**
   - في `app/src/main/assets/nodejs-project/index.js` السطور 834-848:
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
     حيث يؤدي فشل الإرسال إلى `status@broadcast` إلى إرسال `reactionMsg` الحامل لـ `remoteJid: 'status@broadcast'` مباشرة إلى الدردشة الخاصة للشخص `targetParticipant`، مسبباً ظهور رسالة تالفة أو "Waiting for this message. This may take a while."
3. **فقدان معرف LID في قاعدة بيانات Room:**
   - في `app/src/main/kotlin/com/whatsup/automation/data/local/entity/Entities.kt` (السطور 43-58)، الكيان `StatusEntity` لا يحتوي على حقول `participantJid` أو `lid` أو `realPhone`.
   - في `app/src/main/kotlin/com/whatsup/automation/data/repository/RepositoriesImpl.kt` (السطور 271-305)، دالتا `toDomain()` و `toEntity()` تسقطان بيانات LID تماماً، مما يضطر الخدمة الخلفية لإرسال رقم الهاتف فقط عند معالجة الحالات غير المشاهدة بعد إعادة التشغيل.
4. **تسجيل رسائل المحادثات العادية كـ NO_MATCH:**
   - في `app/src/main/kotlin/com/whatsup/automation/domain/usecase/UseCases.kt` (السطور 148-155):
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
     هذا الاستدعاء يسجل كل رسالة واردة لا تطابق قاعدة أتمتة، مسبباً حشو قاعدة البيانات وتسريب نصوص المحادثات إلى السجل.
5. **أداة التجميع وبناء الحزمة `build_bundle.cjs`:**
   - المسار: `app/src/main/assets/nodejs-project/build_bundle.cjs`.
   - تنفذ: `npx.cmd esbuild index.js --bundle --platform=node --target=node18 --format=cjs --banner:js="..." --outfile=bundle.cjs`.
   - تم تشغيل الأمر تجريبياً: استغرق 296ms وولد ملف `bundle.cjs` بحجم 6.5 MB بنجاح، ونسخه إلى `dist/bundle.cjs`.
6. **سلامة بيئة بناء أندرويد Gradle:**
   - تم تشغيل أداة Gradle wrapper وتحديث ملفاتها عبر `gradle wrapper`.
   - تم التحقق عبر `.\gradlew.bat -v` (Gradle 8.11.1 / JVM 17).
   - تم فحص مهام البناء عبر `.\gradlew.bat compileDebugKotlin --dry-run` وانتهت بنجاح تام (`BUILD SUCCESSFUL in 13s`).

---

## 2. سلسلة الاستدلال المنطقي (Logic Chain)
1. **من الملاحظة (2):** بما أن رسالة التفاعل تحمل مفتاحاً مرجعياً يشير إلى `remoteJid: status@broadcast`، فإن إرسالها إلى الدردشة الخاصة 1-on-1 للمستلم يجعل تطبيق واتساب لدى المستلم يبحث عن المعرف في المحادثة الفردية، وتفشل جلسة فك التشفير التلقائية ويظهر للمستلم "في انتظار هذه الرسالة".
2. **من الملاحظتين (2 و 3):** عندما تنشر جهة اتصال ذات حساب شخصي حالة عبر `LID`، ويفقد تطبيق أندرويد هذا الـ LID بسبب عدم حفظه في Room، يرسل أندرويد رقم الهاتف العادي. يفشل إرسال التفاعل برقم الهاتف إلى خادم واتساب للحالة المنشورة بـ LID، فينشط الـ Fallback في `index.js` ويرسل التفاعل إلى الخاص، مما يؤكد السبب المباشر للمشكلة.
3. **من الملاحظة (5):** بما أن `esbuild` يدعم تتبع واستيراد ملفات ES Modules (`import ... from './...js'`) تلقائياً، فإن تقسيم `index.js` إلى موديولات (`config.js`, `bridge.js`, `lid-resolver.js`, `status-manager.js`, `command-dispatcher.js`, `index.js`) سيتم تجميعه في ملف `bundle.cjs` واحد دون الحاجة لتغيير أمر البناء في `build_bundle.cjs`.
4. **من الملاحظة (4):** حذف استدعاء `logRepository.insertLog` عند عدم مطابقة القاعدة داخل `ProcessIncomingMessageUseCase.kt` سيمنع تماماً تسجيل الرسائل العادية ويحقق متطلب الخصوصية الصارم R2.

---

## 3. التحفظات والحدود (Caveats)
- لم يتم إجراء أي تعديل مباشر على الملفات المصدرية في المشروع البرمجي خلال هذه الجولة التزاماً بطبيعة مهمة الاستكشاف والمسح كعملية قراءة واستقصاء فقط (Read-only Investigation).
- الاعتماد على `statusKeysMap` بحجم 1000 عنصر كافٍ جداً، لكن حفظ `participantJid` في Room يضمن استقرار التفاعل حتى للحالات القديمة جداً بعد إعادة تشغيل الهاتف.

---

## 4. الاستنتاج النهائي (Conclusion)
1. **حل مشكلة تفاعلات الحالات:**
   - إزالة الـ Fallback المرسل للخاص في `REACT_STATUS` تماماً وتطبيق قاعدة `Strict Single-JID Routing` حصرياً إلى `status@broadcast` مع `statusJidList: [targetParticipant]`.
   - الاحتفاظ بـ `participantJid` وتمريره بين Node.js و Kotlin و Room لتفادي فقدان معرف الحساب (LID أو Business).
2. **التفكيك الموديولي:**
   - تفكيك `index.js` إلى 6 موديولات ES واضحة ومستقلة، وتجميعها عبر `node build_bundle.cjs`.
3. **تطهير السجلات:**
   - حذف سطر تسجيل `NO_MATCH` في `UseCases.kt` وتطهير السجلات القديمة من قاعدة البيانات.

---

## 5. طريقة التحقق المستقلة (Verification Method)
1. **فحص بناء حزمة Node.js:**
   ```powershell
   cd app/src/main/assets/nodejs-project
   node build_bundle.cjs
   ```
   يجب أن ينتهي التجميع دون أخطاء (`Done in ...ms`) ويولد `bundle.cjs`.
2. **فحص بناء أندرويد:**
   ```powershell
   .\gradlew.bat compileDebugKotlin --dry-run
   ```
   يجب أن يعيد `BUILD SUCCESSFUL`.
3. **فحص تقرير المسح الكامل:**
   قراءة التقرير المفصل المكتوب في:
   `.agents/teamwork/explorer_survey_1/survey_report.md`
