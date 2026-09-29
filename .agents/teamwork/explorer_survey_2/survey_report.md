# تقرير المسح المعماري والتحليلي: نظام ActivityLog ومعالجة الرسائل في WhatsUp Automation

## 1. الملخص التنفيذي
تم إجراء مسح شامل ودقيق لكافة طبقات الكود المتعلقة بنظام سجل النشاط (`ActivityLog`)، وقاعدة بيانات Room المحلية (`AppDatabase`)، ومنطق معالجة وتدفق الرسائل الواردة (`ProcessIncomingMessageUseCase`، `WhatsAppForegroundService`، `WhatsAppNotificationListenerService`، و `WhatsAppEngine`).

تم التوصل إلى تحديد قطعي للنقاط التالية:
1. **مصدر حشو السجلات بالرسائل غير المطابقة (`NO_MATCH`):** محصور في سطر واحد وموقع وحيد داخل `ProcessIncomingMessageUseCase.kt` (الأسطر 148-156).
2. **الأنواع الثلاثة المصرح بها حصرياً لسجل النشاط:**
   - **الردود التلقائية الناجحة (Automated Replies):** مع اسم جهة الاتصال، وشرط المطابقة (Trigger Pattern)، ونص الرد.
   - **تسجيل وتحديث جهات الاتصال (Contact Registrations & Updates):** حفظ جديد، تحديث اسم، أو تأكيد التسجيل السابق.
   - **مشاهدات وتفاعلات الحالات (Status Views & Reactions):** توثيق مشاهدة الحالات والتفاعل بالإيموجي مع اسم الناشر.
3. **خطة التطهير الفوري للبيانات القديمة (Purge Strategy):** عبر خط دفاع مزدوج يجمع بين استدعاء تنظيف تلقائي عند فتح قاعدة البيانات (`RoomDatabase.Callback.onOpen`) واستعلام تنظيف مباشر في `LogDao`.

---

## 2. تحليل النموذج وقاعدة البيانات (ActivityLog Data Model & Room DB Schema)

### أ. الكيان في قاعدة البيانات (`LogEntity`)
- **الملف:** `app/src/main/kotlin/com/whatsup/automation/data/local/entity/Entities.kt` (الأسطر 27-38)
```kotlin
@Entity(tableName = "activity_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val senderPhone: String,
    val messageText: String,
    val matchedRule: String? = null,
    val actionExecuted: String,
    val status: String, // SUCCESS, FAILURE, NO_MATCH
    val extractedName: String? = null
)
```

### ب. نموذج الدومين (`ActivityLog`)
- **الملف:** `app/src/main/kotlin/com/whatsup/automation/domain/model/Models.kt` (الأسطر 65-83)
```kotlin
data class ActivityLog(
    val id: Long = 0,
    val timestamp: Instant = Instant.now(),
    val senderPhone: String,
    val messageText: String,
    val matchedRule: String? = null,
    val actionExecuted: String,
    val status: LogStatus,
    val extractedName: String? = null
)

enum class LogStatus {
    SUCCESS,
    FAILURE,
    NO_MATCH
}
```

### ج. واجهة الوصول للبيانات (`LogDao`)
- **الملف:** `app/src/main/kotlin/com/whatsup/automation/data/local/dao/Daos.kt` (الأسطر 36-58)
```kotlin
@Dao
interface LogDao {
    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity): Long

    @Query("DELETE FROM activity_logs")
    suspend fun clearAllLogs()

    @Query("SELECT COUNT(*) FROM activity_logs")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM activity_logs WHERE status = 'SUCCESS'")
    suspend fun getRepliesSentCount(): Int

    @Query("SELECT COUNT(*) FROM activity_logs WHERE status = 'NO_MATCH'")
    suspend fun getNoMatchCount(): Int
}
```

### د. تكوين قاعدة البيانات (`AppDatabase` & `DatabaseModule`)
- **الملفات:** 
  - `app/src/main/kotlin/com/whatsup/automation/data/local/AppDatabase.kt` (الإصدار الحالي: 4).
  - `app/src/main/kotlin/com/whatsup/automation/di/DatabaseModule.kt`: تم تكوين Room مع `.addCallback(AppDatabase.createCallback())` و `.fallbackToDestructiveMigration()`.

---

## 3. مسار معالجة الرسائل ورصد موضع تسريب `NO_MATCH`

### أ. مسار تدفق الرسائل الواردة
تدخل الرسائل إلى التطبيق عبر مسارين متوازيين:
1. **مسار المحرك المباشر (`WhatsAppForegroundService`):** يستقبل أحداث `whatsAppEngine.incomingMessages` المستخرجة من جلسة Baileys، ويصفي القنوات الإخبارية والمجموعات، ثم يستدعي `processIncomingMessageUseCase.execute(message)`.
2. **مسار الإشعارات الرسمية (`WhatsAppNotificationListenerService`):** يلتقط إشعارات واتساب الرسمية عبر `onNotificationPosted`، ويصفي الإشعارات المكررة بنظام الكاش، ثم يستدعي `processIncomingMessageUseCase.execute(incomingMessage)`.

### ب. النقطة الدقيقة لكتابة `NO_MATCH` في قاعدة البيانات
داخل `app/src/main/kotlin/com/whatsup/automation/domain/usecase/UseCases.kt`:
- **الأسطر 140-146:** يتم فحص القواعد المفعّلة:
```kotlin
val rules = matchRuleUseCase.getEnabledRulesSorted()
for (rule in rules) {
    if (matchRuleUseCase.matches(rule, message.text)) {
        return executeRuleActions(rule, message)
    }
}
```
- **الأسطر 148-156 (الموضع المسبب للمشكلة):**
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
**النتيجة:** كل رسالة خاصة واردة من أي شخص لا تطابق قاعدة أتمتة يتم حفظ نصها ورقم مرسلها في جدول `activity_logs` كـ `NO_MATCH`، مما يتسبب في:
- انتهاك الخصوصية بتخزين محادثات المستخدمين العادية في قاعدة البيانات.
- تراكم السجلات غير المفيدة واستهلاك مساحة التخزين وعمليات الكتابة في القرص.
- تشويه واجهة السجلات (Logs Screen) بآلاف الرسائل غير ذات الصلة.

---

## 4. خطة الحذف النظيف للتسجيل الصامت (Silent Ignore Architecture)

### التعديل المطلوب في `ProcessIncomingMessageUseCase.kt`:
حذف استدعاء `logRepository.insertLog` عند عدم وجود قاعدة مطابقة، واستبداله بالإرجاع المباشر لـ `ProcessResult.NoMatch`:

```kotlin
// قبل التعديل:
logRepository.insertLog(
    ActivityLog(
        senderPhone = message.senderPhone,
        messageText = message.text,
        actionExecuted = "تجاهل — بدون قاعدة مطابقة",
        status = LogStatus.NO_MATCH
    )
)
return ProcessResult.NoMatch

// بعد التعديل (تجاهل صامت تماماً بدون كتابة في الداتابيس):
return ProcessResult.NoMatch
```

### الأثر على الخدمات المستهلكة:
كلا الخدمتين (`WhatsAppForegroundService` سطر 236 و `WhatsAppNotificationListenerService` سطر 141) تتحققان صراحة من:
```kotlin
if (result is ProcessResult.Executed) { ... }
```
وبالتالي عندما تعيد الدالة `ProcessResult.NoMatch`، تتوقف المعالجة فوراً دون أي استجابة أو عمليات I/O.

---

## 5. المتطلبات التفصيلية للأنواع الثلاثة المصرح بها حصرياً

وفقاً للمتطلب **R2**، يجب حصر السجلات حصرياً في 3 أنواع واضحة وموثقة:

| النوع | الحدث | البيانات المطلوبة في السجل | الحالة الحالية والتحسين المطلوب |
|---|---|---|---|
| **1. الردود التلقائية الناجحة** (`Automated Replies`) | تطابق رسالة واردة مع قاعدة أتمتة مفعّلة وإرسال الرد | - `senderPhone`: الرقم الحقيقي الهدف<br>- `messageText`: نص رسالة العميل<br>- `matchedRule`: اسم القاعدة + شرط المطابقة `[الشرط: ${rule.patternValue}]`<br>- `actionExecuted`: نص الرد المرسل `إرسال رد ("...")`<br>- `extractedName`: اسم جهة الاتصال من دفتر الهاتف أو WhatsApp<br>- `status`: `LogStatus.SUCCESS` | **تحسين:** حالياً `extractedName` يُترك `null` في `executeRuleActions`. يجب تعبئته بـ `deviceContactsManager.getContactDisplayName(targetPhone)`، وتضمين شرط المطابقة داخل `matchedRule`. |
| **2. تسجيل وتحديث الأسماء** (`Contact Registration & Update`) | استخراج الاسم وحفظه أو تحديثه في دفتر هاتف أندرويد | - `senderPhone`: رقم الهاتف<br>- `messageText`: نص طلب التسجيل<br>- `matchedRule`: نوع الإجراء (حفظ جديد / تحديث اسم / تأكيد سابق)<br>- `actionExecuted`: تفاصيل العملية والاسم<br>- `extractedName`: الاسم المستخرج المعالج<br>- `status`: `LogStatus.SUCCESS` أو `FAILURE` | **ممتاز:** مطبق حالياً بدقة متناهية في `UseCases.kt` (الأسطر 63-134). |
| **3. مشاهدات وتفاعلات الحالات** (`Status Views & Reactions`) | مشاهدة قصة حالة أو إرسال تفاعل إيموجي (💚) | - `senderPhone`: رقم ناشر الحالة أو المعرف<br>- `messageText`: نص وصفي للحالة واسم الناشر<br>- `matchedRule`: "مشاهدة حالة" أو "تفاعل مع حالة"<br>- `actionExecuted`: "مشاهدة حالة واتساب ($displayName)" أو "تفاعل ($emoji) مع حالة $displayName"<br>- `extractedName`: اسم ناشر الحالة `displayName`<br>- `status`: `LogStatus.SUCCESS` | **تحسين طفيف:** في `WhatsAppEngine.kt` (الأسطر 521 و 544)، يُسجل الحدث بنجاح ولكن يجب تمرير `extractedName = displayName` و `matchedRule`. |

---

## 6. استراتيجية تطهير السجلات القديمة من قاعدة البيانات (Clutter Purge)

لضمان إزالة كافة الرسائل غير المطابقة المخزنة مسبقاً في هواتف المستخدمين، نوصي بتطبيق استراتيجية **الدفاع المزدوج (Dual-Layer Cleanup)**:

### أ. الطبقة الأولى: التطهير التلقائي عند فتح قاعدة البيانات (`AppDatabase.Callback.onOpen`)
تعديل كائن الـ Callback في `com/whatsup/automation/data/local/AppDatabase.kt`:
```kotlin
fun createCallback(): Callback {
    return object : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // إدراج القواعد الافتراضية...
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // تطهير فوري وشامل لجميع السجلات القديمة غير المطابقة أو المحشوة
            db.execSQL("DELETE FROM activity_logs WHERE status = 'NO_MATCH' OR actionExecuted LIKE '%تجاهل%'")
        }
    }
}
```
**المزايا:**
- يُنفذ تلقائياً بمجرد إقلاع التطبيق وتشغيل Room.
- لا يتطلب رفع رقم إصدار قاعدة البيانات ولا يمس بيانات جهات الاتصال أو القواعد النشطة.
- يضمن نظافة فورية لقاعدة البيانات بدون أي تدخل يدوي.

### ب. الطبقة الثانية: دالة مخصصة في `LogDao` و `LogRepository`
إضافة استعلام في `Daos.kt`:
```kotlin
@Query("DELETE FROM activity_logs WHERE status = 'NO_MATCH' OR actionExecuted LIKE '%تجاهل%'")
suspend fun purgeClutterLogs(): Int
```
وإتاحتها عبر `LogRepository.purgeClutterLogs()` للاستخدام في زر تنظيف السجلات أو في اختبارات التكامل.

---

## 7. تحديثات شاشات العرض وإحصائيات لوحة التحكم

1. **شاشة السجلات (`LogsScreen.kt` & `LogsViewModel.kt`):**
   - إزالة أو تعطيل فلتر `NO_MATCH` ("تجاهل")، حيث لن توجد سجلات بهذا النوع بعد الآن.
   - في بطاقة السجل الفردي (`LogItemCard`)، التأكد من إظهار اسم جهة الاتصال والشرط المطابق ونص الرد بشكل أنيق وواضح.
2. **شاشة لوحة التحكم (`DashboardScreen.kt` & `DashboardViewModel.kt`):**
   - في بطاقات الإحصائيات الأربع (Stat Cards): استبدال البطاقة الرابعة "بدون مطابقة" (التي ستصبح دائماً 0) بإحصائية مفيدة مثل "تفاعلات الحالات" أو "حفظ الأسماء" أو "إجمالي العمليات الناجحة".
