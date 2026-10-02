package com.whatsup.automation.domain.util

import android.content.Context
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.Phonenumber
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * فئة مساعدة شاملة للتعامل مع أرقام الهواتف الدولية والمحلية عبر Google libphonenumber.
 */
@Singleton
class PhoneNumberHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val phoneUtil: PhoneNumberUtil by lazy {
        PhoneNumberUtil.createInstance(context)
    }

    /**
     * تحويل الرقم إلى الصيغة الدولية المعيارية E.164 (مثال: +967770000000).
     * إذا فشل التحليل، يعود بالرقم المنظف من الرموز.
     */
    fun formatToE164(rawPhone: String, defaultRegion: String = "YE"): String {
        val trimmed = rawPhone.trim()
        if (trimmed.isBlank()) return ""

        return try {
            val proto: Phonenumber.PhoneNumber = phoneUtil.parse(trimmed, defaultRegion)
            if (phoneUtil.isValidNumber(proto)) {
                phoneUtil.format(proto, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                // محاولة تنظيف الأرقام فقط
                val clean = trimmed.replace("[^0-9+]".toRegex(), "")
                if (!clean.startsWith("+") && clean.length in 9..10) "+967$clean" else clean
            }
        } catch (_: Exception) {
            val clean = trimmed.replace("[^0-9+]".toRegex(), "")
            if (!clean.startsWith("+") && clean.length in 9..10) "+967$clean" else clean
        }
    }

    /**
     * استخراج الأرقام الصافية بدون رمز الزائد (مثال: 967770000000) للتعامل مع Baileys JID.
     */
    fun extractCleanDigits(rawPhone: String, defaultRegion: String = "YE"): String {
        val e164 = formatToE164(rawPhone, defaultRegion)
        return e164.replace("[^0-9]".toRegex(), "")
    }

    /**
     * التحقق مما إذا كان الرقم صالحاً ومكتملاً حسب معايير الدولة.
     */
    fun isValidPhoneNumber(rawPhone: String, defaultRegion: String = "YE"): Boolean {
        val trimmed = rawPhone.trim()
        if (trimmed.length < 6) return false

        return try {
            val proto = phoneUtil.parse(trimmed, defaultRegion)
            phoneUtil.isValidNumber(proto)
        } catch (_: Exception) {
            trimmed.replace("[^0-9]".toRegex(), "").length in 8..15
        }
    }
}
