package com.whatsup.automation.presentation.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.whatsup.automation.domain.model.PatternType
import com.whatsup.automation.domain.model.Rule
import com.whatsup.automation.domain.model.RuleAction
import com.whatsup.automation.presentation.components.GlassmorphicCard
import com.whatsup.automation.presentation.components.GradientButton
import com.whatsup.automation.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<Rule?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Rule?>(null) }
    val haptic = LocalHapticFeedback.current
    val pullToRefreshState = rememberPullToRefreshState()

    // تأكيد الحذف
    showDeleteConfirmDialog?.let { targetRule ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("حذف القاعدة", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("هل أنت متأكد من رغبتك في حذف قاعدة \"${targetRule.name}\"؟", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteRule(targetRule.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) {
                    Text("حذف نهائي", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("إلغاء", color = TextSecondary)
                }
            },
            containerColor = DarkBgSecondary
        )
    }

    // نافذة إضافة أو تعديل قاعدة
    if (showAddDialog || ruleToEdit != null) {
        RuleFormDialog(
            initialRule = ruleToEdit,
            onDismiss = {
                showAddDialog = false
                ruleToEdit = null
            },
            onSave = { name, desc, patternType, patternVal, reply, isSaveContact, priority ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val target = ruleToEdit
                if (target != null) {
                    viewModel.editRule(target.id, name, desc, patternType, patternVal, reply, isSaveContact, priority)
                } else {
                    viewModel.addRule(name, desc, patternType, patternVal, reply, isSaveContact, priority)
                }
                showAddDialog = false
                ruleToEdit = null
            }
        )
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.refreshRules()
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
        ) {
            // 1. شريط الرأس الرئيسي
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "محرك القواعد والردود",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "الردود التلقائية وقواعد حفظ جهات الاتصال المخصصة",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(WhatsAppGreen.copy(alpha = 0.15f))
                                .border(1.dp, WhatsAppGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${uiState.rules.count { it.isEnabled }} نشطة",
                                color = WhatsAppGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                ruleToEdit = null
                                showAddDialog = true
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(WhatsAppGreen)
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "إضافة قاعدة جديدة",
                                tint = DarkBgPrimary
                            )
                        }
                    }
                }
            }

            // 2. بطاقة تخصيص وتوسيم أسماء جهات الاتصال (بدون إيموجي إجباري)
            item {
                val fmt = uiState.formattingSettings

                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    borderBrush = Brush.linearGradient(
                        listOf(NeonPurple.copy(alpha = 0.5f), CyberCyan.copy(alpha = 0.3f))
                    )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Badge,
                                    contentDescription = null,
                                    tint = NeonPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "تنسيق حفظ جهات الاتصال في الهاتف",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "إضافة وسم نصي اختياري لأسماء العملاء في دفتر الهاتف",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Switch(
                                checked = fmt.isEnabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.updateFormattingSettings(fmt.copy(isEnabled = it))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = WhatsAppGreen,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = DarkBgSecondary
                                )
                            )
                        }

                        if (fmt.isEnabled) {
                            HorizontalDivider(color = Color(0x15FFFFFF))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = fmt.customPrefix,
                                    onValueChange = { viewModel.updateFormattingSettings(fmt.copy(customPrefix = it)) },
                                    label = { Text("بادئة نصية (اختياري)") },
                                    placeholder = { Text("مثال: عميل / ") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonPurple,
                                        unfocusedBorderColor = Color(0x22FFFFFF),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )

                                OutlinedTextField(
                                    value = fmt.customTag,
                                    onValueChange = { viewModel.updateFormattingSettings(fmt.copy(customTag = it)) },
                                    label = { Text("وسم لاحق (اختياري)") },
                                    placeholder = { Text("مثال: [واتساب]") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyberCyan,
                                        unfocusedBorderColor = Color(0x22FFFFFF),
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                            }

                            // معاينة مباشرة للاسم المحفوظ في الهاتف
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0x15FFFFFF))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                val previewName = fmt.formatForPhonebook("(الاسم)")
                                Text(
                                    text = "معاينة الحفظ في الهاتف: $previewName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // 3. قائمة القواعد المسجلة
            if (uiState.rules.isEmpty()) {
                item {
                    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "لا توجد قواعد مضافة حالياً",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "اضغط على زر (+) لإضافة قاعدة رد تلقائي أو قاعدة حفظ جهات الاتصال",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            GradientButton(
                                text = "إضافة قاعدة جديدة الآن",
                                onClick = {
                                    ruleToEdit = null
                                    showAddDialog = true
                                },
                                icon = Icons.Filled.Add
                            )
                        }
                    }
                }
            } else {
                items(uiState.rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onToggle = { isEnabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleRule(rule.id, isEnabled)
                        },
                        onEdit = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ruleToEdit = rule
                        },
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDeleteConfirmDialog = rule
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RuleCard(
    rule: Rule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isSaveContactRule = rule.actions.any { it is RuleAction.SaveContactAndReply }

    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // رأس البطاقة (الاسم، شارة النوع، التبديل)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (rule.isEnabled) WhatsAppGreen.copy(alpha = 0.2f) else DarkBgSecondary)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "#${rule.priority}",
                            color = if (rule.isEnabled) WhatsAppGreen else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = rule.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rule.isEnabled) TextPrimary else TextMuted
                    )

                    // شارة نوع الإجراء (حفظ جهة اتصال vs رد تلقائي)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isSaveContactRule) Color(0x3306B6D4) else Color(0x2225D366)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSaveContactRule) "👤 حفظ جهة اتصال + رد" else "💬 رد فقط",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSaveContactRule) CyberCyan else WhatsAppGreen
                        )
                    }
                }

                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = WhatsAppGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkBgSecondary
                    )
                )
            }

            if (rule.description.isNotBlank()) {
                Text(
                    text = rule.description,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // شرط المطابقة
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = when (rule.patternType) {
                        PatternType.CONTAINS -> "يحتوي على:"
                        PatternType.STARTS_WITH -> "يبدأ بـ:"
                        PatternType.EXACT_MATCH -> "يطابق تماماً:"
                        PatternType.REGEX -> "تعبير نمطي:"
                    },
                    fontSize = 11.sp,
                    color = CyberCyan,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = rule.patternValue,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // نص الرد المخصص
            val replyText = rule.actions.firstNotNullOfOrNull {
                when (it) {
                    is RuleAction.SendReply -> it.message
                    is RuleAction.SaveContactAndReply -> it.replyMessage
                }
            } ?: ""

            if (replyText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = if (isSaveContactRule) CyberCyan else WhatsAppGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = replyText,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // أزرار التحكم (تعديل وحذف)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "تعديل", tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تعديل", fontSize = 12.sp, color = CyberCyan)
                }

                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = CoralError, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف", fontSize = 12.sp, color = CoralError)
                }
            }
        }
    }
}

@Composable
fun RuleFormDialog(
    initialRule: Rule?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        description: String,
        patternType: PatternType,
        patternValue: String,
        replyMessage: String,
        isSaveContact: Boolean,
        priority: Int
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var description by remember { mutableStateOf(initialRule?.description ?: "") }
    var patternType by remember { mutableStateOf(initialRule?.patternType ?: PatternType.CONTAINS) }
    var patternValue by remember { mutableStateOf(initialRule?.patternValue ?: "") }
    var priorityText by remember { mutableStateOf((initialRule?.priority ?: 1).toString()) }

    val initialIsSaveContact = remember(initialRule) {
        initialRule?.actions?.any { it is RuleAction.SaveContactAndReply } ?: false
    }
    var isSaveContact by remember { mutableStateOf(initialIsSaveContact) }

    val initialReply = remember(initialRule) {
        initialRule?.actions?.firstNotNullOfOrNull {
            when (it) {
                is RuleAction.SendReply -> it.message
                is RuleAction.SaveContactAndReply -> it.replyMessage
            }
        } ?: ""
    }
    var replyMessage by remember { mutableStateOf(initialReply) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassmorphicCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp),
                cornerRadius = 24.dp
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (initialRule == null) "إضافة قاعدة جديدة" else "تعديل القاعدة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // محدد نوع القاعدة (رد تلقائي vs حفظ جهة اتصال + رد)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBgPrimary)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isSaveContact) Color(0x3325D366) else Color.Transparent)
                                .clickable { isSaveContact = false }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "💬 رد تلقائي فقط",
                                fontSize = 12.sp,
                                fontWeight = if (!isSaveContact) FontWeight.Bold else FontWeight.Normal,
                                color = if (!isSaveContact) WhatsAppGreen else TextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSaveContact) Color(0x3306B6D4) else Color.Transparent)
                                .clickable { isSaveContact = true }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "👤 حفظ جهة اتصال + رد",
                                fontSize = 12.sp,
                                fontWeight = if (isSaveContact) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSaveContact) CyberCyan else TextMuted
                            )
                        }
                    }

                    if (isSaveContact) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1A06B6D4))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "ℹ️ عند تطابق هذه القاعدة، سيتم استخراج اسم الشخص من الرسالة وحفظه مباشرة في دفتر الهاتف بدون أي إيموجي مميز، ثم إرسال الرد.",
                                fontSize = 11.sp,
                                color = CyberCyan
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم القاعدة *") },
                        placeholder = { Text(if (isSaveContact) "مثال: تسجيل العملاء الجدد" else "مثال: الرد على التحية") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WhatsAppGreen,
                            unfocusedBorderColor = Color(0x26FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("الوصف (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0x26FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    // نوع المطابقة
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            PatternType.CONTAINS to "يحتوي",
                            PatternType.STARTS_WITH to "يبدأ بـ",
                            PatternType.EXACT_MATCH to "مطابقة تامة",
                            PatternType.REGEX to "نمط Regex"
                        ).forEach { (type, label) ->
                            val isSelected = patternType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0x3325D366) else DarkBgPrimary)
                                    .border(1.dp, if (isSelected) WhatsAppGreen else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { patternType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) WhatsAppGreen else TextMuted
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = patternValue,
                        onValueChange = { patternValue = it },
                        label = { Text("الكلمات المفتاحية / النمط *") },
                        placeholder = { Text(if (isSaveContact) "سجلني، احفظ رقمي، اسمي" else "سلام، مرحبا، هلا") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WhatsAppGreen,
                            unfocusedBorderColor = Color(0x26FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = replyMessage,
                        onValueChange = { replyMessage = it },
                        label = { Text("نص الرد التلقائي *") },
                        placeholder = { Text(if (isSaveContact) "تم حفظك باسم {name} بنجاح ✅" else "وعليكم السلام ورحمة الله...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WhatsAppGreen,
                            unfocusedBorderColor = Color(0x26FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = priorityText,
                        onValueChange = { priorityText = it },
                        label = { Text("الأولوية (رقم 1 هو الأعلى)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0x26FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("إلغاء", color = TextMuted)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        GradientButton(
                            text = if (initialRule == null) "حفظ القاعدة" else "تحديث القاعدة",
                            onClick = {
                                val prio = priorityText.toIntOrNull() ?: 1
                                onSave(name, description, patternType, patternValue, replyMessage, isSaveContact, prio)
                            },
                            enabled = name.isNotBlank() && patternValue.isNotBlank(),
                            icon = Icons.Filled.Check
                        )
                    }
                }
            }
        }
    }
}
