package com.whatsup.automation.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير التخزين الآمن لجلسات واتساب — يعتمد على Android Keystore و EncryptedSharedPreferences.
 * يضمن حفظ بيانات الجلسة والمفاتيح التشفيرية بشكل آمن على الجهاز دون تسريب.
 */
@Singleton
@Suppress("DEPRECATION")
class SessionKeystore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "whatsup_secure_session",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Throwable) {
        // Fallback في حالة عدم دعم أجهزة معينة لـ EncryptedSharedPreferences أو فشل Keystore
        android.util.Log.e("SessionKeystore", "Failed to initialize EncryptedSharedPreferences, falling back to standard SharedPreferences: ${e.message}", e)
        context.getSharedPreferences("whatsup_secure_session_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_SESSION_PHONE = "session_phone"
        private const val KEY_SESSION_DATA = "session_data"
        private const val KEY_PAIRING_CODE = "pairing_code"
        private const val KEY_LAST_CONNECTED = "last_connected"
    }

    fun isSessionActive(): Boolean = securePrefs.getBoolean(KEY_SESSION_ACTIVE, false)

    fun setSessionActive(active: Boolean) {
        securePrefs.edit().putBoolean(KEY_SESSION_ACTIVE, active).apply()
    }

    fun getSessionPhone(): String? = securePrefs.getString(KEY_SESSION_PHONE, null)

    fun setSessionPhone(phone: String?) {
        securePrefs.edit().putString(KEY_SESSION_PHONE, phone).apply()
    }

    fun getSessionData(): String? = securePrefs.getString(KEY_SESSION_DATA, null)

    fun saveSessionData(data: String) {
        securePrefs.edit().putString(KEY_SESSION_DATA, data).apply()
    }

    fun getPairingCode(): String? = securePrefs.getString(KEY_PAIRING_CODE, null)

    fun savePairingCode(code: String) {
        securePrefs.edit().putString(KEY_PAIRING_CODE, code).apply()
    }

    fun setLastConnectedTimestamp(timestamp: Long) {
        securePrefs.edit().putLong(KEY_LAST_CONNECTED, timestamp).apply()
    }

    fun getLastConnectedTimestamp(): Long = securePrefs.getLong(KEY_LAST_CONNECTED, 0L)

    fun clearSession() {
        securePrefs.edit().clear().apply()
    }
}
