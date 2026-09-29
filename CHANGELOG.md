# سجل التغييرات والتحديثات (Changelog) — WhatsUp Automation

جميع التحديثات والإصدارات الخاصة بالمشروع يتم توثيقها هنا مع رقم الإصدار، واسم التحديث، وتاريخه، وتفاصيل التعديلات.

---

## [v7.4.1] — 2026-09-29 — *Status Reaction Protocol & Forensic Shield* 💚

### 🏷️ معرّف التحديث (Release Name):
`v7.4.1-status-reaction-fix`

### 🛠️ نوع التحديث:
**Critical Fix & Protocol Stabilization** (إصلاح حرج وتثبيت بروتوكول التفاعل)

### 📋 أبرز التغييرات والتحسينات (What's Changed):
- **إصلاح التفاعل مع الحالات (Status Reaction):**
  - اعتماد التوجيه الأحادي الحصري (`Strict Single-JID Routing`) لناشر الحالة الأصلي (`[originalKey.participant]`) في `statusJidList`.
  - منع دمج معرف `LID` مع رقم الهاتف في نفس التفاعل لتفادي كسر تشفير Signal وجلسات الـ `Ratchet`.
  - الحفاظ على كائن المفتاح الأصلي للحالة (`reactionKey`) دون تعديل على `remoteJid` أو `participant`.
- **تسلسل المشاهدة والتفاعل الزمني:**
  - إرسال إشعار قراءة المشاهدة (`sendReceipt`) أولاً ثم تأخير بروتوكولي مقداره `600ms` قبل إرسال التفاعل لضمان تسجيل المشاهدة على أجهزة iOS و Android و Windows Desktop.
- **نظام التتبع الجنائي الشامل (Forensic E2E Tracer):**
  - إضافة موديول `tracer.js` لتوليد `traceId` فريد لكل حالة وتتبعها عبر 13 مرحلة زمنية حتى تأكيد السيرفر.
- **التوثيق الهندسي:**
  - إضافة وثيقة الحل الشاملة `STATUS_REACTION_SOLUTION.md`.

---

## [v7.4.0] — 2026-09-28 — *Modular Architecture & Performance* 🚀

### 🏷️ معرّف التحديث (Release Name):
`v7.4.0-modular-bridge`

### 📋 أبرز التغييرات:
- إعادة هيكلة محرك Node.js المحلي وتقسيمه إلى وحدات مستقلة (`status-manager.js`, `lid-resolver.js`, `command-dispatcher.js`, `config.js`, `bridge.js`).
- تحسين سرعة الاتصال ومعالجة الرسائل غير المتزامنة.

---

## [v1.0.0] — 2026-09-20 — *Initial Release* 🌟

### 🏷️ معرّف التحديث (Release Name):
`v1.0.0-initial-release`

### 📋 أبرز التغييرات:
- الإطلاق الأولي لمشروع WhatsUp Automation مع معمارية Clean Architecture، ودعم Room Database، وJetpack Compose UI، ومحرك Baileys المحلي.
