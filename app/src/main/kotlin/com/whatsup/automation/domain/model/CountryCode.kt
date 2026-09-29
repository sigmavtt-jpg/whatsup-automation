package com.whatsup.automation.domain.model

/**
 * نموذج بيانات رمز الدولة لاختيار مفتاح الاتصال الدولي.
 */
data class CountryCode(
    val isoCode: String,
    val nameAr: String,
    val nameEn: String,
    val dialCode: String,
    val flagEmoji: String,
    val exampleNumber: String = "770123456"
)

object CountryCodeRepository {
    val defaultCountry = CountryCode(
        isoCode = "YE",
        nameAr = "اليمن",
        nameEn = "Yemen",
        dialCode = "+967",
        flagEmoji = "🇾🇪",
        exampleNumber = "770123456"
    )

    val allCountries: List<CountryCode> = listOf(
        defaultCountry,
        CountryCode("SA", "المملكة العربية السعودية", "Saudi Arabia", "+966", "🇸🇦", "501234567"),
        CountryCode("AE", "الإمارات العربية المتحدة", "United Arab Emirates", "+971", "🇦🇪", "501234567"),
        CountryCode("EG", "مصر", "Egypt", "+20", "🇪🇬", "1001234567"),
        CountryCode("OM", "سلطنة عُمان", "Oman", "+968", "🇴🇲", "91234567"),
        CountryCode("KW", "الكويت", "Kuwait", "+965", "🇰🇼", "91234567"),
        CountryCode("QA", "قطر", "Qatar", "+974", "🇶🇦", "33123456"),
        CountryCode("BH", "البحرين", "Bahrain", "+973", "🇧🇭", "36123456"),
        CountryCode("JO", "الأردن", "Jordan", "+962", "🇯🇴", "791234567"),
        CountryCode("IQ", "العراق", "Iraq", "+964", "🇮🇶", "7701234567"),
        CountryCode("PS", "فلسطين", "Palestine", "+970", "🇵🇸", "599123456"),
        CountryCode("SY", "سوريا", "Syria", "+963", "🇸🇾", "944123456"),
        CountryCode("LB", "لبنان", "Lebanon", "+961", "🇱🇧", "71123456"),
        CountryCode("SD", "السودان", "Sudan", "+249", "🇸🇩", "912345678"),
        CountryCode("LY", "ليبيا", "Libya", "+218", "🇱🇾", "911234567"),
        CountryCode("DZ", "الجزائر", "Algeria", "+213", "🇩🇿", "551234567"),
        CountryCode("MA", "المغرب", "Morocco", "+212", "🇲🇦", "612345678"),
        CountryCode("TN", "تونس", "Tunisia", "+216", "🇹🇳", "20123456"),
        CountryCode("MR", "موريتانيا", "Mauritania", "+222", "🇲🇷", "22123456"),
        CountryCode("SO", "الصومال", "Somalia", "+252", "🇸🇴", "615123456"),
        CountryCode("DJ", "جيبوتي", "Djibouti", "+253", "🇩🇯", "77123456"),
        CountryCode("TR", "تركيا", "Turkey", "+90", "🇹🇷", "5321234567"),
        CountryCode("US", "الولايات المتحدة", "United States", "+1", "🇺🇸", "2025550123"),
        CountryCode("GB", "المملكة المتحدة", "United Kingdom", "+44", "🇬🇧", "7911123456"),
        CountryCode("CA", "كندا", "Canada", "+1", "🇨🇦", "4165550123"),
        CountryCode("DE", "ألمانيا", "Germany", "+49", "🇩🇪", "15112345678"),
        CountryCode("FR", "فرنسا", "France", "+33", "🇫🇷", "612345678"),
        CountryCode("IN", "الهند", "India", "+91", "🇮🇳", "9812345678"),
        CountryCode("PK", "باكستان", "Pakistan", "+92", "🇵🇰", "3001234567"),
        CountryCode("MY", "ماليزيا", "Malaysia", "+60", "🇲🇾", "123456789"),
        CountryCode("ID", "إندونيسيا", "Indonesia", "+62", "🇮🇩", "8123456789"),
        CountryCode("RU", "روسيا", "Russia", "+7", "🇷🇺", "9123456789"),
        CountryCode("BR", "البرازيل", "Brazil", "+55", "🇧🇷", "11987654321"),
        CountryCode("CN", "الصين", "China", "+86", "🇨🇳", "13812345678")
    )
}
