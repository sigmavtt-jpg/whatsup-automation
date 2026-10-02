package com.whatsup.automation.presentation.screens.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
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
import com.whatsup.automation.presentation.components.BentoCard
import com.whatsup.automation.presentation.components.GlassmorphicCard
import com.whatsup.automation.presentation.components.GradientButton
import com.whatsup.automation.presentation.components.LiquidGradientButton
import com.whatsup.automation.presentation.components.SegmentedPillSelector
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
            onSave = { name, patternVal, reply, priority ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val target = ruleToEdit
                if (target != null) {
                    viewModel.editRule(target.id, name, "", PatternType.CONTAINS, patternVal, reply, false, priority)
                } else {
                    viewModel.addRule(name, "", PatternType.CONTAINS, patternVal, reply, false, priority)
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

            // 2. القاعدة الثابتة الأولى: تسجيل وحفظ جهات الاتصال في دفتر الهاتف
            item {
                val fmt = uiState.formattingSettings

                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderBrush = ObsidianGlowBorder.Purple
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(NeonPurple.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Badge,
                                        contentDescription = null,
                                        tint = NeonPurple,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "قاعدة تسجيل جهات الاتصال",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(NeonPurple.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("ثابتة 📌", color = NeonPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        text = "التقاط اسم العميل وحفظه في دفتر الهاتف والرد عليه فوراً",
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

                            // حقول تخصيص الاسم: قبل الاسم وبعد الاسم
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = fmt.customPrefix,
                                    onValueChange = { viewModel.updateFormattingSettings(fmt.copy(customPrefix = it)) },
                                    label = { Text("قبل الاسم (بادئة/رمز)") },
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
                                    label = { Text("بعد الاسم (وسم/رمز)") },
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x15FFFFFF))
                                    .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                val previewName = fmt.formatForPhonebook("(الاسم)")
                                Text(
                                    text = "معاينة الحفظ في الهاتف: $previewName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondary
                                )
                            }

                            // حقل الكلمات المفتاحية للتسجيل
                            OutlinedTextField(
                                value = fmt.triggerKeywords,
                                onValueChange = { viewModel.updateFormattingSettings(fmt.copy(triggerKeywords = it)) },
                                label = { Text("الكلمات المفتاحية للتسجيل والحفظ *") },
                                placeholder = { Text("سجلني، احفظني، سجل اسمي، احفظ رقمي، اسمي") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = WhatsAppGreen,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            // حقل نص الرد التلقائي عند الحفظ
                            OutlinedTextField(
                                value = fmt.replyMessage,
                                onValueChange = { viewModel.updateFormattingSettings(fmt.copy(replyMessage = it)) },
                                label = { Text("نص الرد التلقائي عند اكتمال الحفظ * (يدعم Spintax)") },
                                placeholder = { Text("تم حفظك باسم {name} بنجاح ✅") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            // شريط إدراج متغيرات الرد
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val replyVars = listOf(
                                    "{name}" to "الاسم كامل",
                                    "{first_name}" to "الاسم الأول",
                                    "{time_greeting}" to "التحية بالوقت",
                                    "{{أهلاً|مرحباً|حياك الله}}" to "Spintax ترحيب",
                                    "{{تم حفظك|سجلتك عندي}}" to "Spintax تأكيد"
                                )
                                replyVars.forEach { (tag, label) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x1800E5FF))
                                            .clickable {
                                                viewModel.updateFormattingSettings(fmt.copy(replyMessage = fmt.replyMessage + " $tag"))
                                            }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "+ $label",
                                            color = CyberCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // معاينة عينة من رد التأكيد المولد
                            if (fmt.replyMessage.isNotBlank()) {
                                val previewReply = remember(fmt.replyMessage) {
                                    com.whatsup.automation.domain.util.SpintaxEngine.process(
                                        template = fmt.replyMessage,
                                        recipientName = "محمد أحمد",
                                        recipientPhone = "+967770000000"
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x1025D366))
                                        .padding(8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "🎲 عينة حية لرسالة التأكيد التي ستصل للشخص:",
                                            fontSize = 10.sp,
                                            color = WhatsAppGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = previewReply,
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // فاصل وعنوان قسم الردود التلقائية
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "الردود التلقائية المخصصة",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "قواعد الرد على الكلمات والرسائل الخاصة",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ruleToEdit = null
                            showAddDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة رد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 3. قائمة القواعد المسجلة
            if (uiState.rules.isEmpty()) {
                item {
                    BentoCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            LiquidGradientButton(
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
    BentoCard(
        modifier = Modifier.fillMaxWidth(),
        borderBrush = if (rule.isEnabled) ObsidianGlowBorder.Emerald else ObsidianGlowBorder.None
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // رأس البطاقة (الاسم، الأولوية، التبديل)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (rule.isEnabled) WhatsAppGreen.copy(alpha = 0.2f) else DarkBgSecondary)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "#${rule.priority}",
                            color = if (rule.isEnabled) WhatsAppGreen else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = rule.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rule.isEnabled) TextPrimary else TextMuted
                    )
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

            // الكلمات المفتاحية
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "الكلمات المفتاحية:",
                    fontSize = 13.sp,
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = rule.patternValue,
                    fontSize = 14.sp,
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = replyText,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp
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
                    Icon(Icons.Filled.Edit, contentDescription = "تعديل", tint = CyberCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تعديل", fontSize = 13.sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = CoralError, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف", fontSize = 13.sp, color = CoralError, fontWeight = FontWeight.Bold)
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
        patternValue: String,
        replyMessage: String,
        priority: Int
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var patternValue by remember { mutableStateOf(initialRule?.patternValue ?: "") }
    var priorityText by remember { mutableStateOf((initialRule?.priority ?: 1).toString()) }

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
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            BentoCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                borderBrush = ObsidianGlowBorder.Emerald
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (initialRule == null) "إضافة قاعدة رد تلقائي" else "تعديل قاعدة الرد",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم القاعدة *") },
                        placeholder = { Text("مثال: الرد على التحية") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WhatsAppGreen,
                            unfocusedBorderColor = Color(0x26FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = patternValue,
                        onValueChange = { patternValue = it },
                        label = { Text("الكلمات المفتاحية المطلوبة *") },
                        placeholder = { Text("مثال: سلام، مرحبا، هلا") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0x26FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = replyMessage,
                        onValueChange = { replyMessage = it },
                        label = { Text("نص الرد التلقائي *") },
                        placeholder = { Text("وعليكم السلام ورحمة الله وبركاته...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp),
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
                        label = { Text("ترتيب الأولوية") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = Color(0x26FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("إلغاء", color = TextMuted, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        LiquidGradientButton(
                            text = if (initialRule == null) "حفظ القاعدة" else "تحديث القاعدة",
                            onClick = {
                                val prio = priorityText.toIntOrNull() ?: 1
                                onSave(name, patternValue, replyMessage, prio)
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
