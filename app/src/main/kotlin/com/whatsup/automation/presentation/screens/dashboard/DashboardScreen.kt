package com.whatsup.automation.presentation.screens.dashboard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsup.automation.domain.model.ConnectionState
import com.whatsup.automation.presentation.components.*
import com.whatsup.automation.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadNotificationsCount.collectAsState()
    var showNotificationsSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.refreshDashboard()
        },
        state = pullToRefreshState,
        modifier = modifier
            .fillMaxSize()
            .background(DarkBgPrimary)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // شريط الرأس (Header)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Sigma AutoFlow",
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "محرك أتمتة واتساب الفائق ⚡",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NotificationBellButton(
                            unreadCount = unreadCount,
                            onClick = {
                                showNotificationsSheet = true
                                viewModel.markNotificationsAsRead()
                            }
                        )
                        ConnectionStatusBadge(connectionState = uiState.connectionState)
                    }
                }
            }

            // تنبيه فوري عند فقدان إذن الكتابة في جهات الاتصال
            if (!uiState.isWritePermissionGranted) {
                item {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { _ ->
                        viewModel.refreshDashboard()
                    }

                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderBrush = Brush.linearGradient(listOf(RoseError.copy(alpha = 0.6f), AmberWarning.copy(alpha = 0.4f))),
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.WRITE_CONTACTS,
                                    Manifest.permission.READ_CONTACTS
                                )
                            )
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(RoseError.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = RoseError,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "إذن جهات الاتصال مفقود! ⚠️",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoseError
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "مطلوب صلاحية WRITE_CONTACTS لتمكين حفظ الأسماء في دفتر الهاتف.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Button(
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.WRITE_CONTACTS,
                                            Manifest.permission.READ_CONTACTS
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "منح الإذن",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // بطاقة التحكم الرئيسية بالمفاعل (Reactor Hero Bento)
            item {
                val isConnected = uiState.connectionState is ConnectionState.Connected
                val isPaused = uiState.isAutomationPaused
                val isFullyActive = isConnected && !isPaused

                val borderBrush = when {
                    isFullyActive -> ObsidianGlowBorder.Emerald
                    isConnected && isPaused -> ObsidianGlowBorder.Cyan
                    else -> Brush.linearGradient(listOf(AmberWarning.copy(alpha = 0.35f), Color(0x10FFFFFF)))
                }

                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    borderBrush = borderBrush
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PulseReactor(
                                        isActive = isConnected,
                                        activeColor = if (isPaused) AmberWarning else WhatsAppGreen,
                                        inactiveColor = TextMuted
                                    )
                                    Text(
                                        text = when {
                                            isFullyActive -> "الأتمتة نشطة وتعمل 24/7 ✅"
                                            isConnected && isPaused -> "الأتمتة متوقفة مؤقتاً ⏸️"
                                            else -> "المحرك الأساسي متوقف"
                                        },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isPaused && isConnected) {
                                        "الاتصال محفوظ بأمان — تم كتم الردود والحفظ مؤقتاً 🔒"
                                    } else {
                                        "معالجة فورية محلية 100% بدون أي خوادم سحابية 🔒"
                                    },
                                    fontSize = 11.sp,
                                    color = if (isPaused && isConnected) AmberWarning else TextMuted
                                )
                            }

                            LiquidGradientButton(
                                text = when {
                                    isFullyActive -> "إيقاف مؤقت"
                                    isConnected && isPaused -> "استئناف"
                                    else -> "تشغيل"
                                },
                                onClick = {
                                    viewModel.toggleService()
                                },
                                icon = when {
                                    isFullyActive -> Icons.Filled.Pause
                                    else -> Icons.Filled.PlayArrow
                                },
                                gradient = when {
                                    isFullyActive -> Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFFB45309)))
                                    else -> LiquidEmeraldGradient
                                }
                            )
                        }

                        // شريط حالة المحركين (الأساسي + المساعد)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // مؤشر المحرك الأساسي
                            val engineStatusBg = when {
                                isFullyActive -> WhatsAppGreen.copy(alpha = 0.12f)
                                isConnected && isPaused -> AmberWarning.copy(alpha = 0.12f)
                                else -> DarkBgSecondary
                            }
                            val engineStatusBorder = when {
                                isFullyActive -> WhatsAppGreen.copy(alpha = 0.35f)
                                isConnected && isPaused -> AmberWarning.copy(alpha = 0.35f)
                                else -> Color(0x20FFFFFF)
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = engineStatusBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, engineStatusBorder)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isFullyActive -> WhatsAppGreen
                                                    isConnected && isPaused -> AmberWarning
                                                    else -> TextMuted
                                                }
                                            )
                                    )
                                    Text(
                                        text = when {
                                            isFullyActive -> "الأساسي: نشط 👑"
                                            isConnected && isPaused -> "الأساسي: متصل (خامل)"
                                            else -> "الأساسي: بانتظار الربط"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isFullyActive -> WhatsAppGreenLight
                                            isConnected && isPaused -> AmberWarning
                                            else -> TextMuted
                                        }
                                    )
                                }
                            }

                            // مؤشر المساعد
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (uiState.isAssistantActive) CyberCyan.copy(alpha = 0.12f) else Color(0x14FFFFFF),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.isAssistantActive) CyberCyan.copy(alpha = 0.35f) else Color(0x20FFFFFF))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (uiState.isAssistantActive) CyberCyan else TextMuted)
                                    )
                                    Text(
                                        text = if (uiState.isAssistantActive) "المساعد: جاهز كدرع 🛡️" else "المساعد: بانتظار التفعيل",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.isAssistantActive) CyberCyan else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // بطاقة حارس الخدمة ومراقب الأداء الحي (Live Watchdog & Health Card)
            item {
                ServiceHealthCard(snapshot = healthState)
            }

            // شبكة الإحصائيات الذكية (Bento Grid)
            item {
                Text(
                    text = "الإحصائيات المباشرة",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "الرسائل المعالجة",
                        value = uiState.stats.totalProcessed.toString(),
                        icon = Icons.Filled.Message,
                        accentColor = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )

                    StatCard(
                        title = "جهات الاتصال المحفوظة",
                        value = uiState.stats.totalSavedContacts.toString(),
                        icon = Icons.Filled.PersonAdd,
                        accentColor = WhatsAppGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "الردود التلقائية",
                        value = uiState.stats.totalRepliesSent.toString(),
                        icon = Icons.Filled.Reply,
                        accentColor = ElectricBlue,
                        modifier = Modifier.weight(1f)
                    )

                    StatCard(
                        title = "العمليات الناجحة",
                        value = uiState.stats.totalProcessed.toString(),
                        icon = Icons.Filled.CheckCircle,
                        accentColor = NeonPurple,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // قسم سجل الأحداث المباشر التفاعلي والسلس (Scrollable Live Activity Stream)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "سجل الأحداث والنشاط المباشر",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "${uiState.recentLogs.size} عملية مسجلة",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            if (uiState.recentLogs.isEmpty()) {
                item {
                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderBrush = Brush.linearGradient(listOf(Color(0x20FFFFFF), Color(0x10FFFFFF)))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sensors,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "بانتظار أول رسالة أو حدث وارد...",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "اسحب للأسفل للتحديث الفوري ⚡",
                                fontSize = 11.sp,
                                color = CyberCyan
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.recentLogs,
                    key = { it.id }
                ) { log ->
                    val isContactSave = log.extractedName != null || log.actionExecuted.contains("حفظ", ignoreCase = true)
                    val isReply = log.actionExecuted.contains("رد", ignoreCase = true) || log.actionExecuted.contains("Reply", ignoreCase = true)
                    val isStatus = log.actionExecuted.contains("حالة", ignoreCase = true) || log.actionExecuted.contains("Status", ignoreCase = true)

                    val actionColor = when {
                        isContactSave -> WhatsAppGreen
                        isReply -> CyberCyan
                        isStatus -> NeonPurple
                        else -> ElectricBlue
                    }

                    val actionIcon = when {
                        isContactSave -> Icons.Filled.PersonAdd
                        isReply -> Icons.Filled.Reply
                        isStatus -> Icons.Filled.AutoStories
                        else -> Icons.Filled.Bolt
                    }

                    BentoCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderBrush = Brush.horizontalGradient(
                            listOf(actionColor.copy(alpha = 0.35f), Color(0x10FFFFFF))
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(actionColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = null,
                                    tint = actionColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = log.extractedName?.let { "تم حفظ: $it" } ?: log.actionExecuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )

                                    Text(
                                        text = log.senderPhone,
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (log.messageText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "\"${log.messageText}\"",
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // سجل المحاكاة المباشر (Terminal Console)
            item {
                TerminalConsole(
                    lines = uiState.terminalLines,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    NotificationCenterSheet(
        isOpen = showNotificationsSheet,
        notifications = notifications,
        onDismiss = { showNotificationsSheet = false },
        onClearAll = { viewModel.clearNotifications() }
    )
}
