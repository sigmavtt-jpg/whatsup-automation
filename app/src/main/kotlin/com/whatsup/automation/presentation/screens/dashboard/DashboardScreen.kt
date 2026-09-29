package com.whatsup.automation.presentation.screens.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            // شريط الرأس (Header)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "محرك الأتمتة",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "واتساب أوتوميشن فائق السرعة",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    ConnectionStatusBadge(connectionState = uiState.connectionState)
                }
            }

            // تنبيه فوري عند فقدان إذن الكتابة في جهات الاتصال (R2)
            if (!uiState.isWritePermissionGranted) {
                item {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { _ ->
                        viewModel.refreshDashboard()
                    }

                    GlassmorphicCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.WRITE_CONTACTS,
                                        Manifest.permission.READ_CONTACTS
                                    )
                                )
                            },
                        cornerRadius = 16.dp,
                        borderBrush = Brush.linearGradient(listOf(RoseError, AmberWarning))
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
                                    .background(RoseError.copy(alpha = 0.2f)),
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
                                    text = "تنبيه: إذن كتابة جهات الاتصال مفقود! ⚠️",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoseError
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "مطلوب صلاحية WRITE_CONTACTS لتمكين حفظ الأسماء في دليل الهاتف والواتساب.",
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
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
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

            // بطاقة التحكم السريع بالمحرك
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 20.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (uiState.connectionState is ConnectionState.Connected) "المحرك الأساسي متصل ويعمل" else "المحرك الأساسي متوقف",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "الاعتماد الكامل على الربط لجلب كل شيء والأتمتة",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            GradientButton(
                                text = if (uiState.connectionState is ConnectionState.Connected) "إيقاف" else "تشغيل",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleService()
                                },
                                icon = if (uiState.connectionState is ConnectionState.Connected) Icons.Filled.Stop else Icons.Filled.PlayArrow
                            )
                        }

                        // شريط حالة المحركين (الأساسي + المساعد)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // مؤشر المحرك الأساسي
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = if (uiState.connectionState is ConnectionState.Connected) WhatsAppGreen.copy(alpha = 0.15f) else AmberWarning.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.connectionState is ConnectionState.Connected) WhatsAppGreen.copy(alpha = 0.4f) else AmberWarning.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(7.dp).clip(CircleShape).background(if (uiState.connectionState is ConnectionState.Connected) WhatsAppGreen else AmberWarning)
                                    )
                                    Text(
                                        text = if (uiState.connectionState is ConnectionState.Connected) "الأساسي: متصل 👑" else "الأساسي: بانتظار الربط",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.connectionState is ConnectionState.Connected) WhatsAppGreenLight else AmberWarning
                                    )
                                }
                            }

                            // مؤشر المساعد
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = if (uiState.isAssistantActive) CyberCyan.copy(alpha = 0.15f) else Color(0x1AFFFFFF),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (uiState.isAssistantActive) CyberCyan.copy(alpha = 0.4f) else Color(0x22FFFFFF))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(7.dp).clip(CircleShape).background(if (uiState.isAssistantActive) CyberCyan else TextMuted)
                                    )
                                    Text(
                                        text = if (uiState.isAssistantActive) "المساعد: جاهز كدرع 🛡️" else "المساعد: بانتظار التفعيل",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.isAssistantActive) CyberCyan else TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

        // شبكة الإحصائيات (Stats Grid)
        item {
            Text(
                text = "الإحصائيات المباشرة",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
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
                    title = "جهات الاتصال",
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

        // سجل المحاكاة المباشر (Terminal)
        item {
            TerminalConsole(
                lines = uiState.terminalLines,
                modifier = Modifier.fillMaxWidth()
            )
        }

    }
}
}
