package com.whatsup.automation.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.whatsup.automation.domain.model.CountryCode
import com.whatsup.automation.domain.model.CountryCodeRepository
import com.whatsup.automation.presentation.theme.*

/**
 * حقل إدخال رقم الجوال مع محدد رمز الدولة في جهة اليسار (LTR Layout).
 * يتيح اختيار رمز الدولة والبحث السريع، مع إدخال الرقم الوطني مجاوراً له.
 */
@Composable
fun PhoneInputWithCountrySelector(
    selectedCountry: CountryCode,
    nationalNumber: String,
    onCountrySelected: (CountryCode) -> Unit,
    onNationalNumberChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showCountryPicker by remember { mutableStateOf(false) }

    // فرض اتجاه من اليسار لليمين LTR لضمان تموضع رمز الدولة في أقصى اليسار وعدم تشوه الأرقام
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // زر اختيار رمز الدولة (في جهة اليسار)
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { showCountryPicker = true },
                color = DarkSurface,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = selectedCountry.flagEmoji,
                        fontSize = 20.sp
                    )
                    Text(
                        text = selectedCountry.dialCode,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Select country code",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // حقل إدخال رقم الهاتف (الرقم المحلي)
            OutlinedTextField(
                value = nationalNumber,
                onValueChange = { input ->
                    // قبول الأرقام فقط
                    val filtered = input.filter { it.isDigit() }
                    onNationalNumberChanged(filtered)
                },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                placeholder = {
                    Text(
                        text = selectedCountry.exampleNumber,
                        color = TextMuted,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                trailingIcon = {
                    if (nationalNumber.isNotEmpty()) {
                        IconButton(onClick = { onNationalNumberChanged("") }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WhatsAppGreen,
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = WhatsAppGreen
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }

    // نافذة اختيار الدولة المنبثقة
    if (showCountryPicker) {
        CountryPickerDialog(
            selectedCountry = selectedCountry,
            onDismissRequest = { showCountryPicker = false },
            onSelect = { country ->
                onCountrySelected(country)
                showCountryPicker = false
            }
        )
    }
}

/**
 * نافذة حوارية أنيقة لاختيار الدولة مع شريط بحث باللغة العربية والإنجليزية ورمز الاتصال.
 */
@Composable
fun CountryPickerDialog(
    selectedCountry: CountryCode,
    onDismissRequest: () -> Unit,
    onSelect: (CountryCode) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            CountryCodeRepository.allCountries
        } else {
            val query = searchQuery.trim().lowercase()
            CountryCodeRepository.allCountries.filter { country ->
                country.nameAr.contains(query, ignoreCase = true) ||
                        country.nameEn.lowercase().contains(query) ||
                        country.dialCode.contains(query) ||
                        country.isoCode.lowercase().contains(query)
            }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(20.dp),
            color = DarkBgSecondary,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // شريط الرأس
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختر الدولة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // شريط البحث
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "ابحث بالاسم أو الرمز (مثال: اليمن، +967)...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = CyberCyan
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0x22FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyberCyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // قائمة الدول
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredCountries, key = { it.isoCode + it.dialCode }) { country ->
                        val isSelected = country.dialCode == selectedCountry.dialCode &&
                                country.isoCode == selectedCountry.isoCode

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelect(country) },
                            color = if (isSelected) WhatsAppGreen.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = country.flagEmoji,
                                        fontSize = 22.sp
                                    )
                                    Column {
                                        Text(
                                            text = country.nameAr,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) WhatsAppGreen else TextPrimary
                                        )
                                        Text(
                                            text = country.nameEn,
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Text(
                                    text = country.dialCode,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) WhatsAppGreen else CyberCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
