package com.whatsup.automation.data.local.contacts

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير حفظ وتحديث جهات الاتصال في دفتر أسماء هاتف أندرويد الفعلي (ContactsContract).
 * يعتمد سياسة Device-First Contact Saving: الحفظ والتحقق الفعلي أولاً في النظام.
 */
@Singleton
open class DeviceContactsManager {
    private val context: Context?

    @Inject
    constructor(@ApplicationContext context: Context) {
        this.context = context
        initObserver()
    }

    constructor() {
        this.context = null
    }

    private val _contactsUpdateTrigger = kotlinx.coroutines.flow.MutableStateFlow(System.currentTimeMillis())
    val contactsUpdateTrigger: kotlinx.coroutines.flow.StateFlow<Long> = _contactsUpdateTrigger

    // ذاكرة حية لجهات اتصال واتساب المحفوظة سحابياً في واتساب
    private val whatsAppSavedContacts = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun updateWhatsAppContacts(contacts: List<Pair<String, String>>) {
        for ((phone, name) in contacts) {
            val clean = phone.replace("[^0-9]".toRegex(), "")
            if (clean.length >= 7 && isValidDisplayName(name)) {
                whatsAppSavedContacts[clean] = name.trim()
                val normalized = normalizePhoneNumber(phone).replace("+", "")
                whatsAppSavedContacts[normalized] = name.trim()
            }
        }
        notifyContactsChanged()
    }

    fun updateSingleWhatsAppContact(phone: String, name: String) {
        val clean = phone.replace("[^0-9]".toRegex(), "")
        if (clean.length >= 7 && isValidDisplayName(name)) {
            whatsAppSavedContacts[clean] = name.trim()
            val normalized = normalizePhoneNumber(phone).replace("+", "")
            whatsAppSavedContacts[normalized] = name.trim()
            notifyContactsChanged()
        }
    }

    fun getWhatsAppSavedName(phone: String): String? {
        val clean = phone.replace("[^0-9]".toRegex(), "")
        if (clean.length < 7) return null
        val direct = whatsAppSavedContacts[clean]
        if (direct != null) return direct
        val target = if (clean.length >= 9) clean.takeLast(9) else clean
        for ((k, v) in whatsAppSavedContacts) {
            if (k.endsWith(target) || clean.endsWith(k.takeLast(7))) return v
        }
        return null
    }

    // ذاكرة كاش مفهرسة فائقة السرعة في الذاكرة (In-Memory Resolution Cache) لتفادي استعلامات IPC المتكررة
    private val contactResolutionCache = java.util.concurrent.ConcurrentHashMap<String, ContactResolution>()

    fun notifyContactsChanged() {
        contactResolutionCache.clear()
        _contactsUpdateTrigger.value = System.currentTimeMillis()
    }

    private fun initObserver() {
        val ctx = context ?: return
        try {
            val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    contactResolutionCache.clear()
                    _contactsUpdateTrigger.value = System.currentTimeMillis()
                    logContactDiagnostic("[Contacts] Phonebook modified: Live 3-Way Sync Triggered")
                }
            }
            ctx.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer
            )
            ctx.contentResolver.registerContentObserver(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                true,
                observer
            )
        } catch (_: Throwable) {}
    }

    /**
     * مسجل الأحداث التشخيصية لعرضها في شاشات التطبيق وTerminal.
     */
    var diagnosticLogger: ((String) -> Unit)? = null

    /**
     * فحص توفر إذن الكتابة في جهات اتصال الهاتف.
     */
    open fun hasWritePermission(): Boolean {
        val ctx = context ?: return false
        return try {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.WRITE_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * فحص توفر إذن القراءة من جهات اتصال الهاتف.
     */
    open fun hasReadPermission(): Boolean {
        val ctx = context ?: return false
        return try {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun logContactDiagnostic(message: String) {
        try {
            android.util.Log.i("DeviceContactsManager", message)
        } catch (_: Throwable) {}
        try {
            println(message)
        } catch (_: Throwable) {}
        try {
            diagnosticLogger?.invoke(message)
        } catch (_: Throwable) {}
    }

    /**
     * توحيد وتنسيق أرقام الهواتف إلى الصيغة الدولية المعيارية E.164.
     * يدعم الأرقام اليمنية والدولية بكافة حالاتها (مع أو بدون +، أرقام محلية تبدأ بـ 0 أو 7، وتنظيف أحرف التوجيه).
     */
    open fun normalizePhoneNumber(phoneNumber: String): String {
        val sanitized = phoneNumber.trim().replace("\u200E", "").replace("\u200F", "")
        val cleanDigits = sanitized.replace("[^0-9]".toRegex(), "")
        if (cleanDigits.length < 7) return sanitized

        return when {
            sanitized.startsWith("+") -> "+$cleanDigits"
            cleanDigits.startsWith("00") -> "+${cleanDigits.substring(2)}"
            cleanDigits.startsWith("0") && cleanDigits.length == 10 && cleanDigits[1] == '7' -> "+967${cleanDigits.substring(1)}"
            cleanDigits.length == 9 && cleanDigits.startsWith("7") -> "+967$cleanDigits"
            else -> "+$cleanDigits"
        }
    }

    /**
     * التحقق من أن الاسم المسترجع من دليل الهاتف هو اسم حقيقي لشخص وليس مجرد رقم هاتف أو معرّف تقني.
     */
    open fun isValidDisplayName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val trimmed = name.trim()
        if (trimmed.length < 2) return false
        if (trimmed.contains("@") || trimmed.contains("lid", ignoreCase = true)) return false
        if (trimmed == "جهة اتصال غير مسجلة") return false
        val clean = trimmed.replace("[^0-9]".toRegex(), "")
        if (trimmed.all { it.isDigit() || it == '+' || it == ' ' || it == '-' || it == '(' || it == ')' } && clean.length >= 5) {
            return false
        }
        return trimmed.any { it.isLetter() }
    }

    /**
     * فحص ما إذا كان الرقم مسجلاً مسبقاً وله اسم معتمد في:
     * 1. دفتر جهات اتصال الهاتف المحلي (Device Local / SIM)
     * 2. حساب جيميل (Google Contacts)
     * 3. جهات اتصال واتساب المحفوظة داخلياً (WhatsApp Saved Contacts)
     * إذا كان الشخص مسجلاً: ممنوع تعديل اسمه أو إعادة حفظه برقم عشوائي حتى لو أرسل رسالة "سجلني ...".
     */
    open fun isContactAlreadyRegistered(phoneNumber: String): Boolean {
        val cleanDigits = phoneNumber.replace("[^0-9]".toRegex(), "")
        if (cleanDigits.length < 7) return false

        // 1. فحص جهات اتصال واتساب المحفوظة
        val waName = getWhatsAppSavedName(phoneNumber)
        if (isValidDisplayName(waName)) return true

        // 2. فحص دليل هاتف أندرويد وحساب Google
        if (!hasReadPermission()) return false
        val formatted = normalizePhoneNumber(phoneNumber)
        val name1 = getContactDisplayName(formatted)
        if (isValidDisplayName(name1)) return true
        val name2 = getContactDisplayName(cleanDigits)
        if (isValidDisplayName(name2)) return true
        val name3 = getContactDisplayName(phoneNumber)
        if (isValidDisplayName(name3)) return true
        return false
    }

    /**
     * التحقق مما إذا كان المعرف أو الرقم عبارة عن معرف مشفر (LID) وليس رقم هاتف حقيقي.
     */
    open fun isLid(phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        if (phoneNumber.contains("lid", ignoreCase = true)) return true
        val cleanDigits = phoneNumber.replace("[^0-9]".toRegex(), "")
        return cleanDigits == "97517970702387"
    }

    /**
     * حفظ جهة اتصال جديدة في سجل الهاتف الفعلي.
     * R1: يتم الحفظ أولاً في ContactsContract والتحقق من نجاح الكتابة في النظام.
     * R2: تسجيل تشخيصي واضح: [Contacts] Write permission: GRANTED / DENIED, Saved to Phone: SUCCESS / FAILED
     * يمنع منعاً باتاً تعديل أو الكتابة فوق أي جهة اتصال مسجلة مسبقاً ولها اسم، ويمنع حفظ معرّفات الـ LID.
     *
     * @param name اسم الشخص المستخرج من الرسالة
     * @param phoneNumber رقم الهاتف
     * @return true إذا نجحت عملية الكتابة والتحقق الفعلي في دليل الهاتف
     */
    open fun saveContactToDevice(name: String, phoneNumber: String): Boolean {
        if (isLid(phoneNumber)) {
            logContactDiagnostic("[Contacts] Anti-Duplication Shield: Blocked saving raw LID ($phoneNumber) to device.")
            return false
        }

        var permStatus = "DENIED"
        return try {
            val writeGranted = hasWritePermission()
            permStatus = if (writeGranted) "GRANTED" else "DENIED"

            // 1. التحقق من إذن الكتابة أولاً
            if (!writeGranted) {
                val msg = "[Contacts] Write permission: $permStatus, Saved to Phone: FAILED"
                logContactDiagnostic(msg)
                return false
            }

            // 2. التحقق من صلاحية الاسم والرقم وتنظيفهما
            val cleanName = name.trim()
            val cleanDigits = phoneNumber.replace("[^0-9]".toRegex(), "")
            if (cleanName.isBlank() || cleanName.none { it.isLetterOrDigit() } || cleanDigits.length < 7) {
                val msg = "[Contacts] Write permission: $permStatus, Saved to Phone: FAILED"
                logContactDiagnostic(msg)
                return false
            }

            // 3. التحقق من إذن القراءة للتحقق وتفادي التكرار
            if (!hasReadPermission()) {
                val msg = "[Contacts] Write permission: $permStatus, Saved to Phone: FAILED"
                logContactDiagnostic(msg)
                return false
            }

            val formattedPhone = normalizePhoneNumber(phoneNumber)

            // فحص ما إذا كان الرقم مسجلاً مسبقاً وله اسم حقيقي في الهاتف — ممنوع تعديل اسمه أو الكتابة فوقه
            val existingName = getContactDisplayName(formattedPhone)
                ?: getContactDisplayName(cleanDigits)
                ?: getContactDisplayName(phoneNumber)

            if (existingName != null && isValidDisplayName(existingName)) {
                logContactDiagnostic("[Contacts] Contact already registered as \"$existingName\" for $formattedPhone. Overwrite prohibited.")
                return false
            }

            val writeSuccess = try {
                val existingRawContactId = findRawContactIdByPhone(formattedPhone)
                    ?: findRawContactIdByPhone(cleanDigits)

                if (existingRawContactId != null) {
                    // السجل موجود برقم بدون اسم صالح -> تحديث الاسم فقط
                    updateContactName(existingRawContactId, cleanName)
                } else {
                    // إنشاء جهة اتصال جديدة
                    createNewContact(cleanName, formattedPhone)
                }
            } catch (e: Throwable) {
                try {
                    android.util.Log.e("DeviceContactsManager", "Failed to save contact to device: ${e.message}", e)
                } catch (_: Throwable) {}
                false
            }

            // 4. التحقق الفعلي من نجاح الكتابة في دليل هاتف أندرويد
            val verified = writeSuccess && try {
                isPhoneAlreadySaved(formattedPhone) || 
                isPhoneAlreadySaved(cleanDigits) || 
                getContactDisplayName(formattedPhone) != null ||
                getContactDisplayName(cleanDigits) != null
            } catch (_: Throwable) {
                false
            }

            val saveStatus = if (verified) "SUCCESS" else "FAILED"
            val logMessage = "[Contacts] Write permission: $permStatus, Saved to Phone: $saveStatus"
            logContactDiagnostic(logMessage)

            verified
        } catch (e: Throwable) {
            try {
                android.util.Log.e("DeviceContactsManager", "Unexpected error in saveContactToDevice: ${e.message}", e)
            } catch (_: Throwable) {}
            val logMessage = "[Contacts] Write permission: $permStatus, Saved to Phone: FAILED"
            logContactDiagnostic(logMessage)
            false
        }
    }

    /**
     * درع منع تكرار وانتحال جهات الاتصال (Anti-Duplication Shield):
     * إذا كان الرقم أو الحساب مسجلاً مسبقاً، يتم تحديث الاسم في سجل الهاتف بدلاً من ملء دفتر الهاتف بجهات اتصال مكررة.
     * إذا كان جديداً، يتم إنشاؤه لأول مرة.
     */
    open fun saveOrUpdateContact(name: String, phoneNumber: String): Boolean {
        if (isLid(phoneNumber)) {
            logContactDiagnostic("[Contacts] Anti-Duplication Shield: Blocked saving raw LID ($phoneNumber) as contact.")
            return false
        }

        val writeGranted = hasWritePermission()
        if (!writeGranted || !hasReadPermission()) {
            logContactDiagnostic("[Contacts] Write permission: ${if (writeGranted) "GRANTED" else "DENIED"}, Saved to Phone: FAILED")
            return false
        }

        val cleanName = name.trim()
        val cleanDigits = phoneNumber.replace("[^0-9]".toRegex(), "")
        if (cleanName.isBlank() || cleanName.none { it.isLetterOrDigit() } || cleanDigits.length < 7) {
            return false
        }

        val formattedPhone = normalizePhoneNumber(phoneNumber)

        val existingRawContactId = findRawContactIdByPhone(formattedPhone)
            ?: findRawContactIdByPhone(cleanDigits)

        return if (existingRawContactId != null) {
            val updated = updateContactName(existingRawContactId, cleanName)
            if (updated) {
                whatsAppSavedContacts[cleanDigits] = cleanName
                whatsAppSavedContacts[formattedPhone.replace("+", "")] = cleanName
            }
            logContactDiagnostic("[Contacts] Anti-Duplication Shield: Updated existing contact #$existingRawContactId name to \"$cleanName\" for $formattedPhone (Success: $updated)")
            notifyContactsChanged()
            updated
        } else {
            val created = createNewContact(cleanName, formattedPhone)
            if (created) {
                whatsAppSavedContacts[cleanDigits] = cleanName
                whatsAppSavedContacts[formattedPhone.replace("+", "")] = cleanName
            }
            logContactDiagnostic("[Contacts] Anti-Duplication Shield: Created new contact \"$cleanName\" for $formattedPhone (Success: $created)")
            notifyContactsChanged()
            created
        }
    }

    /**
     * تنظيف وحذف أي جهات اتصال تم حفظها سابقاً كمعرفات LID مشفرة في دفتر الهاتف (مثل +97517970702387).
     */
    open fun cleanupLidContactsFromDevice(): Int {
        val ctx = context ?: return 0
        if (!hasWritePermission() || !hasReadPermission()) return 0

        var deletedCount = 0
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            val rawIdsToDelete = mutableSetOf<Long>()

            ctx.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val rawId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""

                    if (rawId > 0 && (number.contains("lid", ignoreCase = true) || number.replace("[^0-9]".toRegex(), "") == "97517970702387" || name.contains("lid", ignoreCase = true))) {
                        rawIdsToDelete.add(rawId)
                    }
                }
            }

            if (rawIdsToDelete.isNotEmpty()) {
                for (rawId in rawIdsToDelete) {
                    try {
                        val deleteUri = ContactsContract.RawContacts.CONTENT_URI
                        val deleted = ctx.contentResolver.delete(
                            deleteUri,
                            "${ContactsContract.RawContacts._ID} = ?",
                            arrayOf(rawId.toString())
                        )
                        if (deleted > 0) deletedCount++
                    } catch (_: Exception) {}
                }
                logContactDiagnostic("[Contacts] Cleanup: Removed $deletedCount legacy LID contacts from device phonebook.")
                notifyContactsChanged()
            }
        } catch (e: Exception) {
            logContactDiagnostic("[Contacts] Cleanup error: ${e.message}")
        }
        return deletedCount
    }

    /**
     * استعلام واسترجاع الاسم المسجل وفق الترتيب الصارم المطلوب:
     * 1. أولاً: جهات اتصال الهاتف المحلية (Local Device / SIM Phone Storage)
     * 2. ثانياً: حساب جيميل (Google / Gmail Contacts)
     * 3. ثالثاً: جهات اتصال واتساب المحفوظة داخلياً (WhatsApp Saved Contacts)
     * 4. إذا لم يوجد: يعود بـ null ليعرض الرقم الدولي الحقيقي الصريح دون تخمين.
     */
    open fun resolveContactNameWithPriority(phoneNumber: String, pushName: String? = null): ContactResolution? {
        val cleanDigits = phoneNumber.replace("[^0-9]".toRegex(), "")
        if (cleanDigits.length < 7) return null

        val cacheKey = cleanDigits
        val cached = contactResolutionCache[cacheKey]
        if (cached != null) {
            return cached
        }

        val internationalPhone = normalizePhoneNumber(phoneNumber)
        val targetDigits = if (cleanDigits.length >= 9) cleanDigits.takeLast(9) else cleanDigits

        var deviceLocalCandidate: String? = null
        var gmailCandidate: String? = null

        val ctx = context
        if (ctx != null && hasReadPermission()) {
            try {
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.RawContacts.ACCOUNT_TYPE
                )
                val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
                val selectionArgs = arrayOf("%$targetDigits")

                ctx.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val accTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                    while (cursor.moveToNext()) {
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        val rowPhone = if (phoneIdx >= 0) cursor.getString(phoneIdx) else null
                        val accountType = if (accTypeIdx >= 0) cursor.getString(accTypeIdx) else null

                        if (!isValidDisplayName(name)) continue
                        val rowClean = rowPhone?.replace("[^0-9]".toRegex(), "") ?: ""
                        if (rowClean.length >= 7 && !rowClean.endsWith(targetDigits) && !cleanDigits.endsWith(rowClean.takeLast(7))) {
                            continue
                        }

                        val accLower = accountType?.lowercase() ?: ""

                        // استبعاد حسابات المزامنة القديمة لتطبيقات المراسلة
                        if (accLower.contains("whatsapp") || accLower.contains("telegram") || accLower.contains("signal") || accLower.contains("viber") || accLower.contains("facebook")) {
                            continue
                        }

                        when {
                            accLower.contains("google") || accLower.contains("gmail") -> {
                                if (gmailCandidate == null) gmailCandidate = name!!.trim()
                            }
                            accountType == null || accLower.isEmpty() || accLower.contains("phone") || accLower.contains("sim") || accLower.contains("local") || accLower.contains("sec.contact") -> {
                                // حسابات الهاتف المحلية (ذاكرة الجهاز والشريحة)
                                if (deviceLocalCandidate == null) deviceLocalCandidate = name!!.trim()
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // التحقق الإضافي عبر PhoneLookup للهاتف
            if (deviceLocalCandidate == null && gmailCandidate == null) {
                try {
                    val lookupUri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(internationalPhone)
                    )
                    val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    ctx.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val name = cursor.getString(0)
                            if (isValidDisplayName(name)) {
                                deviceLocalCandidate = name.trim()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // فحص جهات اتصال واتساب المحفوظة داخلياً
        val waSavedName = getWhatsAppSavedName(phoneNumber)

        // إرجاع النتيجة بالأولوية الصارمة (الهاتف ثم جيميل ثم جهات اتصال واتساب المحفوظة)
        val resolution = when {
            deviceLocalCandidate != null -> ContactResolution(deviceLocalCandidate!!, ContactSourcePriority.DEVICE_LOCAL)
            gmailCandidate != null -> ContactResolution(gmailCandidate!!, ContactSourcePriority.GMAIL)
            waSavedName != null -> ContactResolution(waSavedName, ContactSourcePriority.WHATSAPP)
            else -> null
        }
        if (resolution != null) {
            contactResolutionCache[cacheKey] = resolution
        }
        return resolution
    }

    private fun resolvePushNameOnly(pushName: String?): ContactResolution? {
        return null // حظر استخدام pushName كجهة اتصال مسجلة — فقط الهاتف وجيميل
    }

    /**
     * استعلام واسترجاع الاسم المسجل في دفتر جهات اتصال جهاز أندرويد الفعلي باستخدام رقم الهاتف.
     * R3: مصدر الاسم المعتمد لشاشات الحالات والسجل والداشبورد وفق الأولويات الثلاث.
     */
    open fun getContactDisplayName(phoneNumber: String, pushName: String? = null): String? {
        return resolveContactNameWithPriority(phoneNumber, pushName)?.name
    }

    private fun getPrimaryAccountInfo(): Pair<String?, String?>? {
        val ctx = context ?: return null
        try {
            val accountManager = android.accounts.AccountManager.get(ctx)
            val googleAccounts = accountManager.getAccountsByType("com.google")
            if (googleAccounts.isNotEmpty()) {
                val primaryName = googleAccounts[0].name
                if (!primaryName.isNullOrBlank()) {
                    return Pair("com.google", primaryName)
                }
            }
        } catch (_: Throwable) {}

        return try {
            val uri = ContactsContract.RawContacts.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.RawContacts.ACCOUNT_NAME
            )
            val selection = "${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NOT NULL AND ${ContactsContract.RawContacts.ACCOUNT_NAME} IS NOT NULL"
            ctx.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                var fallbackAccount: Pair<String?, String?>? = null
                while (cursor.moveToNext()) {
                    val type = cursor.getString(0)
                    val name = cursor.getString(1)
                    if (!type.isNullOrBlank() && !name.isNullOrBlank()) {
                        val lowerType = type.lowercase()
                        if (lowerType.contains("whatsapp") || lowerType.contains("telegram") || lowerType.contains("signal")) {
                            continue // تجنب حسابات المزامنة الخاصة ببرامج المراسلة
                        }
                        if (lowerType == "com.google") {
                            return Pair(type, name) // إعطاء الأولوية لحساب Google لضمان المزامنة السحابية والفهرسة الفورية
                        }
                        if (fallbackAccount == null) {
                            fallbackAccount = Pair(type, name)
                        }
                    }
                }
                fallbackAccount
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun updateContactName(rawContactId: Long, newName: String): Boolean {
        val ctx = context ?: return false
        return try {
            val hasNameRow = try {
                ctx.contentResolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID),
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(
                        rawContactId.toString(),
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    ),
                    null
                )?.use { it.moveToFirst() } ?: false
            } catch (_: Exception) {
                false
            }

            val ops = ArrayList<ContentProviderOperation>()
            if (hasNameRow) {
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(
                                rawContactId.toString(),
                                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                            )
                        )
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, newName)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, null)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, null)
                        .withYieldAllowed(true)
                        .build()
                )
            } else {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, newName)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, null)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, null)
                        .withYieldAllowed(true)
                        .build()
                )
            }
            val results = ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val success = results.isNotEmpty()
            try {
                android.util.Log.d("DeviceContactsManager", "Updated contact $rawContactId name to $newName, success=$success")
            } catch (_: Throwable) {}
            success
        } catch (e: Throwable) {
            try {
                android.util.Log.e("DeviceContactsManager", "Failed to update contact name: ${e.message}", e)
            } catch (_: Throwable) {}
            false
        }
    }

    private fun createNewContact(name: String, phoneNumber: String): Boolean {
        val ctx = context ?: return false
        val formattedPhone = normalizePhoneNumber(phoneNumber)
        return try {
            val ops = ArrayList<ContentProviderOperation>()
            val accountInfo = getPrimaryAccountInfo()

            // 1. إنشاء RawContact جديد
            val rawContactInsertIndex = ops.size
            val insertBuilder = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withYieldAllowed(true)

            if (accountInfo != null) {
                insertBuilder
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountInfo.first)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountInfo.second)
            } else {
                insertBuilder
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            }
            ops.add(insertBuilder.build())

            // 2. ربط الاسم الكامل (DISPLAY_NAME و GIVEN_NAME لضمان فهرسة الواتساب الفورية)
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, name)
                    .withYieldAllowed(true)
                    .build()
            )

            // 3. ربط رقم الهاتف مع تحديد النوع محمول (MOBILE)
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, formattedPhone)
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .withYieldAllowed(true)
                    .build()
            )

            val results = ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val success = results.isNotEmpty()
            try {
                android.util.Log.d("DeviceContactsManager", "Created contact $name with phone $formattedPhone, success=$success")
            } catch (_: Throwable) {}
            success
        } catch (e: Throwable) {
            try {
                android.util.Log.e("DeviceContactsManager", "Failed to create new contact: ${e.message}", e)
            } catch (_: Throwable) {}
            false
        }
    }

    /**
     * البحث عن معرّف RawContactId باستخدام رقم الهاتف.
     */
    open fun findRawContactIdByPhone(phoneNumber: String): Long? {
        val ctx = context ?: return null
        if (!hasReadPermission()) return null

        val cleanPhone = phoneNumber.replace("[^0-9]".toRegex(), "")
        if (cleanPhone.length < 7) return null

        val formattedPhone = normalizePhoneNumber(phoneNumber)

        // 1. PhoneLookup
        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(formattedPhone)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup._ID)
            ctx.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getLong(0)
                    val rawUri = ContactsContract.RawContacts.CONTENT_URI
                    val rawProj = arrayOf(ContactsContract.RawContacts._ID)
                    val rawSelection = "${ContactsContract.RawContacts.CONTACT_ID} = ?"
                    val rawArgs = arrayOf(contactId.toString())
                    ctx.contentResolver.query(rawUri, rawProj, rawSelection, rawArgs, null)?.use { rawCursor ->
                        if (rawCursor.moveToFirst()) {
                            return rawCursor.getLong(0)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. CONTENT_FILTER_URI
        try {
            val filterUri = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(cleanPhone)
            )
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
            ctx.contentResolver.query(filterUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0)
                }
            }
        } catch (_: Exception) {}

        // 3. CONTENT_FILTER_URI بآخر 9 أرقام
        val targetDigits = if (cleanPhone.length >= 9) cleanPhone.takeLast(9) else cleanPhone
        if (targetDigits.length >= 7) {
            try {
                val filterUri = Uri.withAppendedPath(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                    Uri.encode(targetDigits)
                )
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                ctx.contentResolver.query(filterUri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getLong(0)
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. NUMBER LIKE (أحدث 9 أرقام لتغطية الرقم المحلي كاملاً مع مفتاح المشغل وتفادي المطابقة العشوائية)
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
        val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val selectionArgs = arrayOf("%$targetDigits")

        return try {
            ctx.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * البحث عن رقم الهاتف باستخدام اسم جهة الاتصال المسجلة في دفتر هاتف أندرويد.
     */
    open fun findPhoneByName(name: String): String? {
        val ctx = context ?: return null
        val trimmedName = name.trim()
        if (trimmedName.isBlank() || !hasReadPermission()) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? OR ${ContactsContract.Data.DISPLAY_NAME_PRIMARY} = ?"
        val selectionArgs = arrayOf(trimmedName, trimmedName)

        return try {
            ctx.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * التحقق مما إذا كان رقم الهاتف مسجلاً مسبقاً في دفتر الأسماء.
     */
    open fun isPhoneAlreadySaved(phoneNumber: String): Boolean {
        return findRawContactIdByPhone(phoneNumber) != null
    }

    /**
     * جلب كافة جهات الاتصال المسجلة في دفتر أسماء هاتف أندرويد.
     */
    open fun getAllDeviceContacts(): List<DeviceContactItem> {
        val ctx = context ?: return emptyList()
        if (!hasReadPermission()) return emptyList()

        val result = mutableListOf<DeviceContactItem>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"

        try {
            ctx.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val accTypeIndex = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                val seenPhones = mutableSetOf<String>()

                while (cursor.moveToNext()) {
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "" else ""
                    val rawPhone = if (phoneIndex >= 0) cursor.getString(phoneIndex) ?: "" else ""
                    val accType = if (accTypeIndex >= 0) cursor.getString(accTypeIndex) else null
                    val accLower = accType?.lowercase() ?: ""

                    // تجاهل حسابات واتساب وتطبيقات المراسلة كلياً
                    if (accLower.contains("whatsapp") || accLower.contains("telegram") || accLower.contains("signal") || accLower.contains("viber") || accLower.contains("facebook")) {
                        continue
                    }

                    val isGmail = accLower.contains("google") || accLower.contains("gmail")
                    val isDeviceLocal = accType == null || accLower.isEmpty() || accLower.contains("phone") || accLower.contains("sim") || accLower.contains("local") || accLower.contains("sec.contact")
                    if (!isGmail && !isDeviceLocal) continue

                    val normalizedPhone = normalizePhoneNumber(rawPhone)
                    val cleanDigits = rawPhone.replace("[^0-9]".toRegex(), "")

                    if (isValidDisplayName(name) && cleanDigits.length >= 7 && seenPhones.add(normalizedPhone)) {
                        result.add(DeviceContactItem(name = name.trim(), phone = normalizedPhone))
                    }
                }
            }
        } catch (_: Exception) {}

        return result
    }
}

data class DeviceContactItem(
    val name: String,
    val phone: String
)

enum class ContactSourcePriority {
    DEVICE_LOCAL,  // ذاكرة الهاتف والشريحة
    WHATSAPP,      // جهات اتصال ومزامنة واتساب
    GMAIL,         // حساب جوجل
    UNKNOWN
}

data class ContactResolution(
    val name: String,
    val source: ContactSourcePriority,
    val rawAccountType: String? = null
)
