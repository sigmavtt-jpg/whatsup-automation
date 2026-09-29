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
import androidx.compose.ui.text.font.FontFamily
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
            onSave = { name, desc, patternType, patternVal, reply, priority ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val target = ruleToEdit
                if (target != null) {
                    viewModel.editRule(target.id, name, desc, patternType, patternVal, reply, priority)
                } else {
                    viewModel.addRule(name, desc, patternType, patternVal, reply, priority)
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
                            text = "فرز الرسائل، استخراج الأسماء، وتنفيذ الردود آلياً",
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

            // 2. بطاقة الاختبار المباشر للفحص الفوري
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    borderBrush = Brush.linearGradient(
                        listOf(CyberCyan.copy(alpha = 0.5f), WhatsAppGreen.copy(alpha = 0.3f))
                    )
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(20.dp))
                            Text(
                                text = "اختبار مطابقة القواعد فورياً 🧪",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                        }

                        Text(
                            text = "اكتب أي نص رسالة تجريبية لتجربة القواعد واستخراج الأسماء مباشرة:",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        OutlinedTextField(
                            value = uiState.testInput,
                            onValueChange = { viewModel.onTestInputChange(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("مثال: سجلني سماح الماس أو السلام عليكم", fontSize = 12.sp, color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (uiState.testInput.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onTestInputChange("") }) {
                                        Icon(Icons.Filled.Close, contentDescription = "مسح", tint = TextMuted)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WhatsAppGreen,
                                unfocusedBorderColor = Color(0x33FFFFFF),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        if (uiState.testInput.isNotBlank()) {
                            val result = uiState.testResult
                            if (result != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(WhatsAppGreen.copy(alpha = 0.12f))
                                        .border(1.dp, WhatsAppGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "مطابقة ناجحة مع القاعدة: #${result.matchedRule.priority} ${result.matchedRule.name} ✅",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WhatsAppGreenLight
                                    )

                                    if (result.extractedName != null) {
                                        Text(
                                            text = "👤 الاسم المستخرج: \"${result.extractedName}\"",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CyberCyan
                                        )
                                    }

                                    if (result.resultingReply.isNotBlank()) {
                                        Text(
                                            text = "🤖 الرد التلقائي: ${result.resultingReply}",
                                            fontSize = 12.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AmberWarning.copy(alpha = 0.12f))
                                        .border(1.dp, AmberWarning.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "⚠️ لم تتطابق أي قاعدة مفعّلة مع هذا النص — سيتم تجاهل الرسالة",
                                        fontSize = 11.sp,
                                        color = AmberWarning
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. تنبيه تسلسل الأولوية
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "تُفحص القواعد تصاعدياً حسب الأولوية (#1 أولاً). أول مطابقة تُنفَّذ ثم يتوقف الفحص فوراً.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 4. حالة القائمة الفارغة
            if (uiState.rules.isEmpty() && !uiState.isLoading) {
                item {
                    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.List,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "لا توجد قواعد مسجلة بعد",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "انقر على زر (+) لإنشاء أول قاعدة أتمتة.",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // 5. قائمة القواعد المنسقة
            items(uiState.rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { enabled ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.toggleRule(rule.id, enabled)
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

@Composable
fun RuleCard(
    rule: Rule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassmorphicCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        borderBrush = Brush.linearGradient(
            if (rule.isEnabled) listOf(WhatsAppGreen.copy(alpha = 0.35f), Color(0x10FFFFFF))
            else listOf(Color(0x22FFFFFF), Color(0x08FFFFFF))
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (rule.isEnabled) CyberCyan.copy(alpha = 0.2f) else DarkSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${rule.priority}",
                            color = if (rule.isEnabled) CyberCyan else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = rule.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rule.isEnabled) TextPrimary else TextMuted
                        )
                        if (rule.description.isNotBlank()) {
                            Text(
                                text = rule.description,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = WhatsAppGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurface
                        )
                    )

                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "تعديل القاعدة",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "حذف القاعدة",
                            tint = RoseError.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0x14FFFFFF), thickness = 1.dp)

            // تفاصيل الشرط والكلمات المفتاحية
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val patternLabel = when (rule.patternType) {
                    PatternType.CONTAINS -> "يحتوي على"
                    PatternType.STARTS_WITH -> "يبدأ بـ"
                    PatternType.EXACT_MATCH -> "مطابقة تامة"
                    PatternType.REGEX -> "تعبير نمطي (Regex)"
                }
                Text(
                    text = "نوع الشرط: $patternLabel",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "\"${rule.patternValue}\"",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = WhatsAppGreenLight
                    )
                }
            }

            // تفاصيل الإجراءات المقترنة
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rule.actions.forEach { action ->
                    when (action) {
                        is RuleAction.SendReply -> {
                            val replyLines = action.message.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val isRandom = replyLines.size > 1
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(15.dp))
                                Text(
                                    text = if (isRandom) "قرعة ردود (${replyLines.size} ردود عشوائية 🎲)" else "رد فوري: \"${action.message}\"",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleFormDialog(
    initialRule: Rule?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        desc: String,
        patternType: PatternType,
        patternVal: String,
        reply: String?,
        priority: Int
    ) -> Unit
) {
    val isEditMode = initialRule != null
    val existingReplyAction = initialRule?.actions?.filterIsInstance<RuleAction.SendReply>()?.firstOrNull()
    
    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var description by remember { mutableStateOf(initialRule?.description ?: "") }
    var selectedPatternType by remember { mutableStateOf(initialRule?.patternType ?: PatternType.CONTAINS) }
    var patternValue by remember { mutableStateOf(initialRule?.patternValue ?: "") }
    var replyMessage by remember { mutableStateOf(existingReplyAction?.message ?: "") }
        var priorityText by remember { mutableStateOf((initialRule?.priority ?: 1).toString()) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkBgSecondary)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // رأس النافذة
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) "تعديل قاعدة الأتمتة" else "إنشاء قاعدة أتمتة جديدة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = TextMuted)
                    }
                }

                HorizontalDivider(color = Color(0x1AFFFFFF))

                // اسم القاعدة
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم القاعدة (مثال: تسجيل جهة اتصال)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WhatsAppGreen,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedLabelColor = WhatsAppGreen,
                        unfocusedLabelColor = TextMuted
                    )
                )

                // وصف القاعدة
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف التوضيحي (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WhatsAppGreen,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedLabelColor = WhatsAppGreen,
                        unfocusedLabelColor = TextMuted
                    )
                )

                // الأولوية
                OutlinedTextField(
                    value = priorityText,
                    onValueChange = { priorityText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("الأولوية (رقم 1 هو الأعلى أسبقية)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WhatsAppGreen,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedLabelColor = WhatsAppGreen,
                        unfocusedLabelColor = TextMuted
                    )
                )

                // نوع الشرط
                Text(
                    text = "نوع شرط المطابقة:",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val patterns = listOf(
                        PatternType.CONTAINS to "يحتوي",
                        PatternType.STARTS_WITH to "يبدأ بـ",
                        PatternType.EXACT_MATCH to "مطابقة",
                        PatternType.REGEX to "Regex"
                    )
                    patterns.forEach { (type, label) ->
                        val isSelected = selectedPatternType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) WhatsAppGreen.copy(alpha = 0.2f) else DarkSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) WhatsAppGreen else Color(0x22FFFFFF),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedPatternType = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) WhatsAppGreenLight else TextMuted
                            )
                        }
                    }
                }

                // الكلمة أو العبارة المفتاحية
                OutlinedTextField(
                    value = patternValue,
                    onValueChange = { patternValue = it },
                    label = { Text("الكلمات المفتاحية أو النمط") },
                    placeholder = { Text("مثال: سجلني, اسمي, سجل, انا, معاك, معك") },
                    supportingText = { Text("يدعم عدة كلمات مفصولة بفواصل (، أو ,) أو أسطر جديدة", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = TextMuted
                    )
                )

                // الرد التلقائي
                OutlinedTextField(
                    value = replyMessage,
                    onValueChange = { replyMessage = it },
                    label = { Text("نص الرد التلقائي (أو قرعة الردود 🎲)") },
                    placeholder = { Text("وعليكم السلام ورحمة الله وبركاته\nأهلاً وسهلاً بك، تفضل كيف أقدر أساعدك؟\nحياك الله أخي الكريم") },
                    supportingText = { Text("قرعة الردود: اكتب كل رد في سطر منفصل لاختيار رد عشوائي لكل مرسل 🎲", color = WhatsAppGreenLight, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WhatsAppGreen,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedLabelColor = WhatsAppGreen,
                        unfocusedLabelColor = TextMuted
                    )
                )

                

                // أزرار التحكم
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("إلغاء", color = TextSecondary)
                    }

                    GradientButton(
                        text = if (isEditMode) "تحديث القاعدة" else "حفظ وتفعيل",
                        onClick = {
                            if (name.isNotBlank() && patternValue.isNotBlank()) {
                                onSave(
                                    name,
                                    description.ifBlank { "قاعدة $name" },
                                    selectedPatternType,
                                    patternValue,
                                    replyMessage.ifBlank { null },
                                    priorityText.toIntOrNull() ?: 1
                                )
                            }
                        },
                        enabled = name.isNotBlank() && patternValue.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
