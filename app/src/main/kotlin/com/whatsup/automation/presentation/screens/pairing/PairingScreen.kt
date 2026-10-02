package com.whatsup.automation.presentation.screens.pairing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsup.automation.domain.model.ConnectionState
import com.whatsup.automation.presentation.components.BentoCard
import com.whatsup.automation.presentation.components.ConnectionStatusBadge
import com.whatsup.automation.presentation.components.GlassmorphicCard
import com.whatsup.automation.presentation.components.GradientButton
import com.whatsup.automation.presentation.components.PhoneInputWithCountrySelector
import com.whatsup.automation.presentation.components.SegmentedPillSelector
import com.whatsup.automation.presentation.theme.*

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.checkAllPermissions(context)
    }

    LaunchedEffect(uiState.pairingCode) {
        val code = uiState.pairingCode
        if (!code.isNullOrBlank()) {
            val cleanCode = code.replace("-", "").trim()
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Pairing Code", cleanCode)
            clipboard.setPrimaryClip(clip)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            android.widget.Toast.makeText(context, "تم نسخ كود الاقتران تلقائياً: $code", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var showRestrictedSettingsDialog by remember { mutableStateOf(false) }

    // نافذة إرشاد فك حظر الإعدادات المقيدة (Restricted Settings)
    if (showRestrictedSettingsDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showRestrictedSettingsDialog = false }
        ) {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                cornerRadius = 20.dp,
                borderBrush = Brush.linearGradient(listOf(NeonPurple.copy(alpha = 0.6f), CyberCyan.copy(alpha = 0.4f)))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(24.dp))
                        Text(
                            text = "فك حظر الأمان (الإعدادات المقيدة)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "في أندرويد 13 و 14 فما فوق، يظهر النظام رسالة «محظور بسبب الأمان / Restricted Setting» لمنع تفعيل إمكانية الوصول للتطبيقات المثبتة يدوياً. اتبع 4 خطوات لفك الحظر:",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "1️⃣ اضغط على «فتح معلومات التطبيق» أدناه.", fontSize = 12.sp, color = TextPrimary)
                            Text(text = "2️⃣ اضغط على القائمة (الثلاث نقاط ⋮) في أعلى الزاوية.", fontSize = 12.sp, color = TextPrimary)
                            Text(text = "3️⃣ اختر «السماح بالإعدادات المقيدة» (Allow restricted settings).", fontSize = 12.sp, color = WhatsAppGreenLight, fontWeight = FontWeight.Bold)
                            Text(text = "4️⃣ أكّد بصمتك/رمزك، ثم ارجع وفعل الخدمة مباشرة.", fontSize = 12.sp, color = TextPrimary)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.openAppSettings(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1. فتح معلومات التطبيق (App Info)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.openAccessibilitySettings(context)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan)
                        ) {
                            Icon(Icons.Filled.AccessibilityNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("2. الانتقال لصفحة إمكانية الوصول", fontSize = 13.sp)
                        }

                        TextButton(
                            onClick = { showRestrictedSettingsDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تم / إغلاق النافذة", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // شريط الرأس
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ربط الحساب والأتمتة",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "اختر طريقة الربط المناسبة لجهازك",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            ConnectionStatusBadge(connectionState = uiState.connectionState)
        }

        // إذا كان الحساب متصلاً بالفعل
        if (uiState.isPairedSuccess) {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                borderBrush = Brush.linearGradient(listOf(WhatsAppGreen, CyberCyan))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(WhatsAppGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Text(
                        text = "الحساب متصل ويعمل بنجاح! ✅",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "محرك الأتمتة والتقاط الرسائل نشط الآن 24/7. يتم فحص كل رسالة واردة والرد عليها وحفظ الأسماء فوراً.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseError.copy(alpha = 0.2f))
                    ) {
                        Text("إلغاء الربط / تسجيل الخروج", color = RoseError, fontSize = 12.sp)
                    }
                }
            }
        }

        // محدد طرق الربط الكبسولي فائق السلاسة
        SegmentedPillSelector(
            options = listOf(
                PairingMethod.PAIRING_CODE to "كود الرقم 👑",
                PairingMethod.QR_CODE to "رمز QR 📷",
                PairingMethod.DIRECT_SYSTEM to "مساعد النظام 🛡️"
            ),
            selectedOption = uiState.selectedMethod,
            onOptionSelected = { viewModel.selectMethod(it) },
            activeColor = WhatsAppGreen
        )

        // محتوى التبويب المختار
        when (uiState.selectedMethod) {
            PairingMethod.PAIRING_CODE -> {
                // واجهة كود الاقتران عبر رقم الهاتف
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "أدخل رقم هاتفك لتوليد كود الاقتران المكون من 8 خانات:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )

                        // حقل إدخال رقم الهاتف مع محدد رمز الدولة باليسار وافتراض اليمن
                        PhoneInputWithCountrySelector(
                            selectedCountry = uiState.selectedCountry,
                            nationalNumber = uiState.nationalPhoneNumber,
                            onCountrySelected = { viewModel.onCountrySelected(it) },
                            onNationalNumberChanged = { viewModel.onNationalPhoneNumberChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        )

                        if (uiState.nationalPhoneNumber.isNotBlank()) {
                            val cleanDigits = uiState.nationalPhoneNumber.filter { it.isDigit() }.trimStart('0')
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = WhatsAppGreen.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhoneAndroid,
                                        contentDescription = null,
                                        tint = WhatsAppGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "الرقم الدولي الذي سيتم ربطه: ${uiState.selectedCountry.dialCode} $cleanDigits",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = WhatsAppGreenLight
                                    )
                                }
                            }
                        }

                        // دليل الخطوات السريع للربط
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, Color(0x22FFFFFF))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "💡 خطوات ربط الحساب على هذا الهاتف:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WhatsAppGreenLight
                                )
                                Text(
                                    text = "1. اضغط \"طلب كود الاقتران\".",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "2. انسخ الكود أو اضغط \"فتح واتساب لإدخال الكود\".",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "3. في واتساب، اختر: (الأجهزة المرتبطة) ➔ (ربط جهاز) ➔ (الربط باستخدام رقم الهاتف) والصق الكود.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        GradientButton(
                            text = if (uiState.isLoading) "جارٍ طلب الكود من واتساب..." else "طلب كود الاقتران (8 أرقام)",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.requestPairingCode()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading,
                            icon = Icons.Filled.Key
                        )

                        // عرض رسالة الخطأ إن وجدت
                        uiState.errorMessage?.let { error ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = RoseError.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, RoseError.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = RoseError,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = error,
                                        color = RoseError,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // عرض الكود عند توليده مع المؤقت التنازلي والأزرار المباشرة
                        uiState.pairingCode?.let { code ->
                            val formattedCode = if (code.length == 8 && !code.contains("-")) {
                                "${code.take(4)}-${code.drop(4)}"
                            } else {
                                code
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF070E1A))
                                    .border(1.dp, WhatsAppGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(vertical = 16.dp, horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "كود الاقتران الصادر:",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )

                                        if (uiState.codeCountdownSeconds > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (uiState.codeCountdownSeconds <= 15) RoseError.copy(alpha = 0.2f) else AmberWarning.copy(alpha = 0.15f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (uiState.codeCountdownSeconds <= 15) RoseError.copy(alpha = 0.4f) else AmberWarning.copy(alpha = 0.4f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Timer,
                                                        contentDescription = null,
                                                        tint = if (uiState.codeCountdownSeconds <= 15) RoseError else AmberWarning,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = "صالح لمدة: ${uiState.codeCountdownSeconds} ث",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (uiState.codeCountdownSeconds <= 15) RoseError else AmberWarning
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = formattedCode,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberCyan,
                                        letterSpacing = 4.sp
                                    )

                                    // زر فتح واتساب وزر نسخ الكود
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.openWhatsApp(context)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.OpenInNew,
                                                contentDescription = null,
                                                tint = DarkBgPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "فتح واتساب لإدخال الكود",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkBgPrimary
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("Pairing Code", code.replace("-", "").trim())
                                                clipboard.setPrimaryClip(clip)
                                                android.widget.Toast.makeText(context, "تم نسخ الكود: $code", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(vertical = 10.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("نسخ كود الاقتران", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.confirmSimulatedSuccess()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("تأكيد اكتمال الاقتران بنجاح ✅", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            PairingMethod.QR_CODE -> {
                // واجهة مسح رمز QR الحقيقي الصادر من خوادم واتساب
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "امسح رمز QR الحقيقي من واتساب بهاتفك:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )

                        // صندوق رمز QR الحقيقي القابل للمسح عبر كاميرا واتساب الرسمية
                        com.whatsup.automation.presentation.components.RealQrCodeView(
                            payload = uiState.qrCodePayload,
                            size = 210.dp
                        )

                        Text(
                            text = "افتح واتساب > الأجهزة المرتبطة > ربط جهاز > وجّه الكاميرا",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.refreshQr() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحديث", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.shareQrCode(context) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مشاركة الرمز", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { viewModel.confirmSimulatedSuccess() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("تأكيد", color = DarkBgPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            PairingMethod.DIRECT_SYSTEM -> {
                // واجهة الصلاحيات وخدمات النظام المباشرة
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ServiceStatusCard(
                        title = "خدمة قراءة إشعارات واتساب (أساسي)",
                        description = "تمكن التطبيق من التقاط الرسائل الواردة والرد الفوري عليها عبر RemoteInput",
                        isActive = uiState.isNotificationListenerActive,
                        icon = Icons.Filled.NotificationsActive,
                        activeColor = WhatsAppGreen,
                        onActionClick = { viewModel.openNotificationSettings(context) },
                        actionButtonText = if (uiState.isNotificationListenerActive) "مفعلة ✅" else "تفعيل الآن ⚙️"
                    )

                    ServiceStatusCard(
                        title = "حصانة الخلفية 24/7 (توفير البطارية)",
                        description = "استثناء التطبيق من وضع Doze لضمان استمرار عمل الأتمتة دون إيقاف المعالج",
                        isActive = uiState.isBatteryOptimizationIgnored,
                        icon = Icons.Filled.BatteryChargingFull,
                        activeColor = CyberCyan,
                        onActionClick = { viewModel.openBatterySettings(context) },
                        actionButtonText = if (uiState.isBatteryOptimizationIgnored) "مستثنى ✅" else "منح الحصانة ⚡"
                    )

                    ServiceStatusCard(
                        title = "أذونات دفتر جهات الاتصال",
                        description = "تتيح حفظ الأسماء المستخرجة (مثل: سجلني باسم...) تلقائياً في دفتر الهاتف",
                        isActive = uiState.hasContactsPermission,
                        icon = Icons.Filled.Contacts,
                        activeColor = ElectricBlue,
                        onActionClick = { viewModel.openAppSettings(context) },
                        actionButtonText = if (uiState.hasContactsPermission) "ممنوحة ✅" else "فتح الأذونات 👥"
                    )

                    ServiceStatusCard(
                        title = "خدمة الوصول المساعدة (مسار هجين)",
                        description = "مسار احتياطي إضافي لرصد نوافذ وحالات واتساب والتأكد من استلام الرسائل",
                        isActive = uiState.isAccessibilityActive,
                        icon = Icons.Filled.AccessibilityNew,
                        activeColor = NeonPurple,
                        onActionClick = { viewModel.openAccessibilitySettings(context) },
                        actionButtonText = if (uiState.isAccessibilityActive) "مفعلة ✅" else "تفعيل المسار ♿"
                    )

                    // زر المساعدة لفك حظر الإعدادات المقيدة (Restricted Settings)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, NeonPurple.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable { showRestrictedSettingsDialog = true },
                        color = NeonPurple.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.HelpOutline, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        text = "محظور بسبب الأمان؟ (إعدادات مقيدة)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "اضغط هنا لمعرفة طريقة فك الحظر في 4 خطوات",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Text(
                                text = "طريقة الحل 🔓",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonPurple
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.checkAllPermissions(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceHover)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فحص وتحديث حالة الخدمات", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * بطاقة عرض حالة كل خدمة من خدمات النظام مع زر الإجراء السريع.
 */
@Composable
fun ServiceStatusCard(
    title: String,
    description: String,
    isActive: Boolean,
    icon: ImageVector,
    activeColor: Color,
    onActionClick: () -> Unit,
    actionButtonText: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (isActive) activeColor.copy(alpha = 0.3f) else Color(0x22FFFFFF),
                RoundedCornerShape(16.dp)
            ),
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isActive) activeColor.copy(alpha = 0.2f) else Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) activeColor else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isActive) WhatsAppGreen else AmberWarning)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = description,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) activeColor.copy(alpha = 0.15f) else WhatsAppGreen.copy(alpha = 0.2f)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = actionButtonText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) activeColor else WhatsAppGreen
                )
            }
        }
    }
}
