package com.whatsup.automation.presentation.screens.statuses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.whatsup.automation.presentation.components.GlassmorphicCard
import com.whatsup.automation.presentation.theme.*

@Composable
fun StatusesScreen(
    viewModel: StatusesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val availableEmojis = listOf("💚", "❤️", "⭐", "🔥", "🫀", "😂", "👍", "👏", "🎉", "🤲", "💯", "😍", "🥳")
    var showCustomEmojiDialog by remember { mutableStateOf(false) }
    var customEmojiInput by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    // نافذة إدخال إيموجي مخصص
    if (showCustomEmojiDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showCustomEmojiDialog = false
                customEmojiInput = ""
            }
        ) {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                cornerRadius = 20.dp,
                borderBrush = Brush.linearGradient(listOf(CyberCyan.copy(alpha = 0.5f), WhatsAppGreen.copy(alpha = 0.3f)))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "تحديد إيموجي التفاعل المخصص",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "اكتب أو الصق أي إيموجي تفضله من لوحة المفاتيح",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    OutlinedTextField(
                        value = customEmojiInput,
                        onValueChange = { customEmojiInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("مثال: ⭐ أو 🫀 أو 💯", color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WhatsAppGreen,
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showCustomEmojiDialog = false
                                customEmojiInput = ""
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("إلغاء", color = TextMuted)
                        }

                        Button(
                            onClick = {
                                val trimmed = customEmojiInput.trim()
                                if (trimmed.isNotEmpty()) {
                                    viewModel.setDefaultEmoji(trimmed)
                                    showCustomEmojiDialog = false
                                    customEmojiInput = ""
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("تطبيق", color = DarkBgPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBgPrimary)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(top = 28.dp, bottom = 100.dp)
    ) {
        // شريط العنوان
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "أتمتة الحالات التلقائية",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "مشاهدة وتفاعل ديناميكي فوري لجهات الاتصال المعتمدة 24/7",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }

        // بطاقة الحالة النشطة الديناميكية
        item {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                borderBrush = Brush.linearGradient(
                    listOf(WhatsAppGreen.copy(alpha = 0.6f), CyberCyan.copy(alpha = 0.3f))
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(WhatsAppGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = WhatsAppGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(WhatsAppGreen)
                                )
                                Text(
                                    text = "المحرك الذكي نشط وتلقائي",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = "يتم رصد الحالات والمشاهدة والتفاعل آلياً في الخلفية بدون تدخل يدوي",
                                fontSize = 12.sp,
                                color = TextMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 1.dp)

                    // إحصائيات سريعة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "الحالات المعالجة", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = "${uiState.totalViewedCount}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Column {
                            Text(text = "الأشخاص المتفاعل معهم", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = "${uiState.viewedUniquePersonsCount}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                        }

                        Column {
                            Text(text = "رمز التفاعل الحالي", fontSize = 11.sp, color = TextMuted)
                            Text(
                                text = uiState.settings.defaultEmoji.ifBlank { "💚" },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // بطاقة تخصيص إيموجي التفاعل التلقائي
        item {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                borderBrush = Brush.linearGradient(
                    listOf(Color(0x22FFFFFF), Color(0x08FFFFFF))
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اختيار إيموجي التفاعل التلقائي",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = "الحالي: ${uiState.settings.defaultEmoji.ifBlank { "💚" }}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WhatsAppGreen
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableEmojis) { emoji ->
                            val isSelected = uiState.settings.defaultEmoji == emoji
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) WhatsAppGreen.copy(alpha = 0.25f) else Color(0x10FFFFFF))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) WhatsAppGreen else Color(0x20FFFFFF),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setDefaultEmoji(emoji)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }

                        // زر إيموجي مخصص
                        item {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(CyberCyan.copy(alpha = 0.15f))
                                    .border(1.dp, CyberCyan.copy(alpha = 0.3f), CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showCustomEmojiDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddReaction,
                                    contentDescription = "إيموجي مخصص",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // بطاقة توضيح معايير الخصوصية والأمان الصارمة
        item {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                borderBrush = Brush.linearGradient(
                    listOf(Color(0x15FFFFFF), Color(0x05FFFFFF))
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "قواعد الفلترة والحماية",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = "• يتفاعل النظام فقط مع جهات الاتصال المسجلة في دفتر الهاتف أو حساب Google/Gmail.\n• يتم تجاهل أي حالات من أرقام غير مسجلة أو قنوات إخبارية لحماية خصوصيتك وأمان حسابك.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}
