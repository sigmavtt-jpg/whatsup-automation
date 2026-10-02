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
import com.whatsup.automation.presentation.components.BentoCard
import com.whatsup.automation.presentation.components.PulseReactor
import com.whatsup.automation.presentation.components.StoryAvatarRing
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.whatsup.automation.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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
    val pullToRefreshState = rememberPullToRefreshState()

    // نافذة إدخال إيموجي مخصص
    if (showCustomEmojiDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                showCustomEmojiDialog = false
                customEmojiInput = ""
            }
        ) {
            BentoCard(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                borderBrush = ObsidianGlowBorder.Cyan
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

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.refreshStatuses()
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

        // شريط القصص الدائري الحقيقي (يظهر فقط عند وجود حالات فعلية مرصودة)
        if (uiState.statuses.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "الحالات المرصودة مؤخراً",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.statuses.take(15), key = { it.id }) { story ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                StoryAvatarRing(
                                    initials = story.senderName.ifBlank { story.senderPhone }.take(2),
                                    isViewed = story.isViewed,
                                    size = 56.dp,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                )

                                Text(
                                    text = story.senderName.ifBlank { story.senderPhone },
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // بطاقة الحالة النشطة والمؤشرات
        item {
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                borderBrush = ObsidianGlowBorder.Emerald
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
                            PulseReactor(
                                isActive = true,
                                activeColor = WhatsAppGreen
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "محرك الحالات التلقائي",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "مشاهدة وتفاعل فوري 24/7",
                                fontSize = 12.sp,
                                color = WhatsAppGreenLight
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 1.dp)

                    // إحصائيات سريعة بنمط Bento
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "الحالات المعالجة", fontSize = 12.sp, color = TextMuted)
                            Text(
                                text = "${uiState.totalViewedCount}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Column {
                            Text(text = "الأشخاص المتفاعل معهم", fontSize = 12.sp, color = TextMuted)
                            Text(
                                text = "${uiState.viewedUniquePersonsCount}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                        }

                        Column {
                            Text(text = "رمز التفاعل الحالي", fontSize = 12.sp, color = TextMuted)
                            Text(
                                text = uiState.settings.defaultEmoji.ifBlank { "💚" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // بطاقة تخصيص إيموجي التفاعل التلقائي
        item {
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                borderBrush = ObsidianGlowBorder.None
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
                            fontSize = 14.sp,
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
    }
}
}
