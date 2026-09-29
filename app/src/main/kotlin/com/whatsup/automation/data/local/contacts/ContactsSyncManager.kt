package com.whatsup.automation.data.local.contacts

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import com.whatsup.automation.data.local.entity.SyncedContactDao
import com.whatsup.automation.data.local.entity.SyncedContactEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncedContactDao: SyncedContactDao
) {
    companion object {
        private const val TAG = "ContactsSyncManager"
    }

    suspend fun syncContacts() = withContext(Dispatchers.IO) {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.READ_CONTACTS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Cannot sync contacts: READ_CONTACTS permission not granted yet.")
                return@withContext
            }

            Log.d(TAG, "Starting contacts sync for Device Local and Google/Gmail accounts only...")
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE
            )
            
            val cursor: Cursor? = context.contentResolver.query(
                uri, projection, null, null, null
            )

            // phone -> Pair(displayName, priorityRank)
            // Rank 1: Device Local / SIM (highest)
            // Rank 2: Gmail / Google
            val contactsMap = mutableMapOf<String, Pair<String, Int>>()

            cursor?.use {
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val accTypeIndex = it.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                while (it.moveToNext()) {
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else null
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else null
                    val accType = if (accTypeIndex >= 0) it.getString(accTypeIndex) else null

                    if (!number.isNullOrBlank() && !name.isNullOrBlank()) {
                        val trimmedName = name.trim()
                        if (trimmedName.length >= 2 && !trimmedName.contains("@") && !trimmedName.contains("lid")) {
                            val accLower = accType?.lowercase() ?: ""

                            // استبعاد حسابات واتساب وتطبيقات المراسلة كلياً
                            if (accLower.contains("whatsapp") || accLower.contains("telegram") || accLower.contains("signal") || accLower.contains("viber") || accLower.contains("facebook")) {
                                continue
                            }

                            val isGmail = accLower.contains("google") || accLower.contains("gmail")
                            val isDeviceLocal = accType == null || accLower.isEmpty() || accLower.contains("phone") || accLower.contains("sim") || accLower.contains("local") || accLower.contains("sec.contact")

                            if (!isGmail && !isDeviceLocal) continue

                            val normalized = normalizePhoneNumber(number)
                            val rank = if (isDeviceLocal) 1 else 2

                            val existing = contactsMap[normalized]
                            if (existing == null || rank < existing.second) {
                                contactsMap[normalized] = Pair(trimmedName, rank)
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "Fetched ${contactsMap.size} unique prioritized contacts. Saving to Room...")

            val entities = contactsMap.map { (phone, pair) ->
                SyncedContactEntity(normalizedPhone = phone, displayName = pair.first)
            }

            syncedContactDao.deleteAll()
            entities.chunked(999).forEach { chunk ->
                syncedContactDao.insertAll(chunk)
            }

            Log.d(TAG, "Contacts sync completed successfully.")

        } catch (e: Exception) {
            Log.e(TAG, "Error syncing contacts: ${e.message}", e)
        }
    }

    /**
     * توحيد صيغة الأرقام لتتطابق مع الواتساب.
     */
    fun normalizePhoneNumber(rawNumber: String): String {
        val digitsOnly = rawNumber.replace("[^0-9+]".toRegex(), "")
        if (digitsOnly.isBlank()) return rawNumber
        
        var normalized = digitsOnly
        
        // إذا كان يحتوي على أصفار بالبداية
        if (normalized.startsWith("00")) {
            normalized = "+" + normalized.substring(2)
        } else if (normalized.startsWith("0")) {
            // إضافة مفتاح اليمن الافتراضي إذا بدأ بـ 0
            normalized = "+967" + normalized.substring(1)
        } else if (normalized.startsWith("7")) {
            // إضافة مفتاح اليمن الافتراضي إذا بدأ بـ 7 مباشرة
            normalized = "+967$normalized"
        } else if (!normalized.startsWith("+")) {
            // إضافة + إذا لم تكن موجودة ورقم دولي
            normalized = "+$normalized"
        }

        // إزالة علامة الزائد ليتطابق مع تخزيننا في Room (حيث يخزن 9677...)
        return normalized.replace("+", "")
    }
}
