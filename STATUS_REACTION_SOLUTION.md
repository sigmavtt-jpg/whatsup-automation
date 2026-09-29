# توثيق حل مشكلة التفاعل مع حالات واتساب (WhatsApp Status Like / Reaction Solution)

تم حل مشكلة عدم ظهور التفاعل (القلب الأخضر 💚 أو الإيموجي) على حالات واتساب عند الناشرين (سواء على أجهزة **iOS (iPhone)**، **Android**، أو **Windows Desktop**) وتثبيت البروتوكول الصحيح المعتمد في مكتبة **Baileys 6.7.9** ومحرك **WhatsUp Automation**.

---

## 1. التشخيص الجذري للمشكلة (Root-Cause Analysis)

### أسباب الفشل السابقة:
1. **تداخل معرفات الحسابات (LID vs Phone Number JID Mismatch):**
   - في واتساب الحديث، الحسابات الشخصية تنشر الحالات باستخدام معرّف الهوية المشفر (`<lid>@lid`)، بينما حسابات الأعمال (Business) تنشر برقم الهاتف المباشر (`<phone>@s.whatsapp.net`).
   - عند محاولة إرسال التفاعل بدمج المعرفين معاً داخل قائمة `statusJidList: [lid, phone]`، يتسبب ذلك في:
     - فشل تشفير جلسات Signal (جلسة التشفير `PreKey` و `Session Ratchet` تُربط بمعرّف واحد فقط).
     - ظهور أخطاء `Bad MAC` و `Decryption Error`.
     - توليد رسائل معلقة "في انتظار هذه الرسالة" وتلف جلسة الاقتران.

2. **تعديل كائن المفتاح الأصلي (`reaction.key` Modification):**
   - كان يتم استبدال `msg.key.participant` أو `msg.key.remoteJid` برقم الهاتف المحلول بدلاً من الحفاظ على المفتاح الدقيق كما ورد من خادم واتساب في رسالة الحالة الأصلية.

3. **تزامن المشاهدة مع التفاعل (Missing Receipt Sequencing):**
   - خوادم واتساب وتطبيقات الهواتف (خاصة عميل iOS الذي يعتمد على قاعدة بيانات `ChatStorage.sqlite`) تتطلب تسجيل إشعار قراءة/مشاهدة (`read receipt`) قبل أو بالتزامن مع قيد التفاعل لربطه بالحالة في الواجهة الرسومية لصاحب الحالة.

---

## 2. قواعد الحل الهندسي المعتمد (Strict Engineering Solution)

تم تطبيق القواعد الصارمة التالية وفق الدستور المعماري للمشروع (`AGENTS.md` - القسم 8):

### القاعدة الأولى: عزل القرارات عن المحرك (Dumb Bridge Architecture)
- طبقة Node.js (`index.js`) هي مجرد جسر تنفيذي لتمرير الأوامر واستقبال الأحداث.
- يتم إطلاق أمر التفاعل حصراً وبشكل صريح من طبقة كوتلن (`WhatsAppForegroundService.kt` / `WhatsAppEngine.kt`).

### القاعدة الثانية: التوجيه الحصري لمعرف الحالة الأصلي (Strict Single-JID Routing)
- يُرسل التفاعل دائماً إلى الوجهة `status@broadcast`.
- تحتوي مصفوفة `statusJidList` على **معرف الناشر الأصلي فقط** (`[originalKey.participant]`) دون أي دمج بين LID ورقم الهاتف.
- إذا كانت الحالة منشورة بـ `LID` ⬅️ يُرسل التفاعل للـ `LID` فقط.
- إذا كانت منشورة برقم هاتف ⬅️ يُرسل التفاعل لرقم الهاتف فقط.

### القاعدة الثالثة: منع التأثيرات الجانبية في الخاص (Zero DM Side-Effects)
- التفاعل موجه حصرياً لقناة الحالات `status@broadcast` مع خيار `statusJidList`، ويُحظر إرسال أي رسائل عادية للدردشة الخاصة للناشر أثناء معالجة الحالة.

### القاعدة الرابعة: تسلسل العمليات الزمني (Protocol Sequencing & Delay)
1. إرسال تأكيد قراءة الحالة أولاً عبر `sock.sendReceipt('status@broadcast', participant, [statusId], 'read')`.
2. تحديث قراءة الحالة في الحالة المحلية للمحرك عبر `sock.readMessages([reactionKey])`.
3. تطبيق تأخير زمني بروتوكولي مقداره **600ms** لمنح خادم واتساب فرصة لتسجيل المشاهدة قبل معالجة التفاعل.
4. إرسال حزمة الـ Reaction عبر `sock.sendMessage('status@broadcast', reactionMsg, { statusJidList: [participant] })`.

---

## 3. الشفرة البرمجية المعتمدة (Implementation Code)

الملف: `app/src/main/assets/nodejs-project/status-manager.js`

```javascript
export async function reactStatus(sock, statusId, emoji = '💚', senderPhone = '', participantInput = '', authFolder = CONFIG.DEFAULT_AUTH_FOLDER) {
    if (!sock || !statusId) return;

    try {
        const originalKey = statusKeysMap.get(statusId);
        const participant = originalKey?.participant || (participantInput && participantInput.includes('@') ? participantInput : null);

        if (!participant) {
            emitToAndroid('ERROR', { error: `لم يتم العثور على مفتاح الحالة ${statusId} لإرسال التفاعل` });
            return;
        }

        // 1. مفتاح الحالة الأصلي تماماً كما ورد من السيرفر
        const reactionKey = {
            remoteJid: originalKey?.remoteJid || 'status@broadcast',
            id: statusId,
            participant: participant,
            fromMe: false
        };

        const reactionMsg = {
            react: {
                text: emoji || '💚',
                key: reactionKey
            }
        };

        // 2. توجيه أحادي حصري لناشر الحالة الأصلي فقط
        const statusJidList = [participant];

        // 3. إرسال إشعار قراءة المشاهدة أولاً
        let receiptSuccess = false;
        try {
            await sock.sendReceipt('status@broadcast', participant, [statusId], 'read');
            receiptSuccess = true;
        } catch (receiptErr) {
            console.error(`[reactStatus] sendReceipt failed: ${receiptErr?.message || receiptErr}`);
        }

        try {
            await sock.readMessages([reactionKey]);
        } catch (_) {}

        // 4. تأخير بروتوكولي 600ms
        await new Promise(r => setTimeout(r, 600));

        // 5. إرسال التفاعل إلى status@broadcast مع حصر statusJidList
        let sendResult = null;
        try {
            sendResult = await sock.sendMessage('status@broadcast', reactionMsg, { statusJidList });
        } catch (sendErr) {
            console.error(`[reactStatus] sendMessage failed: ${sendErr?.message || sendErr}`);
            emitToAndroid('ERROR', { error: `فشل إرسال التفاعل: ${sendErr?.message || sendErr}` });
        }

        if (sendResult?.key?.id && sendResult?.message) {
            sentMessagesCache.set(sendResult.key.id, sendResult.message);
        }
        sentMessagesCache.set(statusId, reactionMsg);

        const resolved = await resolvePhoneAndLid(participant || senderPhone, null, sock);
        const pushName = originalKey?.pushName || getContactName(resolved.phone) || '';

        emitToAndroid('STATUS_REACTED', {
            id: statusId,
            senderPhone: resolved.phone,
            realPhone: resolved.realPhone,
            lid: resolved.lid,
            senderName: pushName,
            participant: participant,
            emoji: emoji || '💚',
            success: !!sendResult,
            receiptSuccess: receiptSuccess
        });
    } catch (err) {
        emitToAndroid('ERROR', { error: `فشل التفاعل مع الحالة: ${err.message || err}` });
    }
}
```

---

## 4. نظام التتبع والتدقيق الجنائي (Forensic E2E Tracer)

تم إضافة وحدة `tracer.js` لمراقبة دورة حياة التفاعل وتسجيلها بدقة:

```text
STATUS_RECEIVED ──► STATUS_STORED ──► KOTLIN_COMMAND ──► DISPATCHER 
       │
       ▼
REACTION_BUILD ──► READ_RECEIPT (600ms) ──► BAILEYS_SEND ──► WHATSAPP_ACK (PASS)
```

---

## 5. خطوات التجميع والبناء (Build & Bundle)

عند تعديل أي ملف في كود Node.js المحلي:
```bash
# 1. تجميع كود الجافاسكربت إلى bundle.cjs
node build_bundle.cjs

# 2. بناء وتثبيت التطبيق على هاتف الأندرويد
.\gradlew.bat installDebug
```
