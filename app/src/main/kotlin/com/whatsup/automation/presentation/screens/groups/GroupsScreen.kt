package com.whatsup.automation.presentation.screens.groups

import androidx.compose.animation.*
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsup.automation.data.local.contacts.DeviceContactItem
import com.whatsup.automation.domain.model.Group
import com.whatsup.automation.domain.model.GroupLogStatus
import com.whatsup.automation.domain.model.GroupMessageLog
import com.whatsup.automation.presentation.components.GlassmorphicCard
import com.whatsup.automation.presentation.components.GradientButton
import com.whatsup.automation.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupsScreen(
    viewModel: GroupsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.selectedGroup != null) {
        GroupDetailsScreen(
            group = uiState.selectedGroup!!,
            uiState = uiState,
            onBackClick = { viewModel.closeGroupDetails() },
            onTabSelect = { viewModel.setDetailsTab(it) },
            onBroadcastTextChange = { viewModel.onBroadcastTextChange(uiState.selectedGroup!!.id, it) },
            onSendBroadcast = { viewModel.sendBroadcast(uiState.selectedGroup!!.id) }
        )
    } else {
        GroupsListScreen(
            uiState = uiState,
            viewModel = viewModel,
            modifier = modifier
        )
    }

    if (uiState.isCreateModalOpen) {
        CreateGroupDialog(
            uiState = uiState,
            onDismiss = { viewModel.closeCreateModal() },
            onGroupNameChange = { viewModel.onNewGroupNameChange(it) },
            onGroupDescChange = { viewModel.onNewGroupDescriptionChange(it) },
            onContactSearchChange = { viewModel.onContactSearchQueryChange(it) },
            onToggleContact = { viewModel.toggleContactSelection(it) },
            onSelectAll = { viewModel.selectAllContacts() },
            onClearAll = { viewModel.clearContactSelection() },
            onCreateConfirm = { viewModel.createGroup() }
        )
    }
}

@Composable
fun GroupsListScreen(
    uiState: GroupsUiState,
    viewModel: GroupsViewModel,
    modifier: Modifier = Modifier
) {
    val filteredGroups = remember(uiState.groups, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.groups
        } else {
            uiState.groups.filter {
                it.name.contains(uiState.searchQuery, ignoreCase = true) ||
                it.description.contains(uiState.searchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBgPrimary)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 100.dp)
    ) {
        // شريط العنوان العلوي + زر إضافة مجموعة
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "المجموعات للبث",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "أنشئ مجموعات وأرسل رسائل بث لجميع الأعضاء",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                IconButton(
                    onClick = { viewModel.openCreateModal() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(listOf(WhatsAppGreen, CyberCyan))
                        )
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "إنشاء مجموعة",
                        tint = DarkBgPrimary
                    )
                }
            }
        }

        // حقل البحث عن مجموعة
        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                label = { Text("ابحث عن مجموعة...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = Color(0x26FFFFFF),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            )
        }

        // قائمة بطاقات المجموعات
        if (filteredGroups.isEmpty()) {
            item {
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Groups,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "لا توجد مجموعات حالياً",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "اضغط على زر (+) بالأعلى لإنشاء مجموعة جديدة واختيار أعضائها من دفتر الهاتف",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        GradientButton(
                            text = "إنشاء مجموعة جديدة الآن",
                            onClick = { viewModel.openCreateModal() },
                            modifier = Modifier.padding(top = 8.dp),
                            icon = Icons.Filled.Add
                        )
                    }
                }
            }
        } else {
            items(filteredGroups, key = { it.id }) { group ->
                GroupCardItem(
                    group = group,
                    broadcastText = uiState.broadcastTexts[group.id] ?: "",
                    isBroadcasting = uiState.isBroadcasting,
                    onBroadcastTextChange = { viewModel.onBroadcastTextChange(group.id, it) },
                    onSendBroadcast = { viewModel.sendBroadcast(group.id) },
                    onOpenDetails = { viewModel.selectGroupForDetails(group) },
                    onDeleteGroup = { viewModel.deleteGroup(group.id) }
                )
            }
        }
    }
}

@Composable
fun GroupCardItem(
    group: Group,
    broadcastText: String,
    isBroadcasting: Boolean,
    onBroadcastTextChange: (String) -> Unit,
    onSendBroadcast: () -> Unit,
    onOpenDetails: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // رأس البطاقة
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x3325D366)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Groups,
                            contentDescription = null,
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = group.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${group.memberCount} عضو مسجل",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row {
                    IconButton(onClick = onOpenDetails) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "التفاصيل والسجل", tint = CyberCyan)
                    }
                    IconButton(onClick = onDeleteGroup) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف المجموعة", tint = CoralError)
                    }
                }
            }

            if (group.description.isNotBlank()) {
                Text(
                    text = group.description,
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }

            HorizontalDivider(color = Color(0x1AFFFFFF))

            // حقل إدخال الرسالة للبث وحذف
            Text(
                text = "رسالة بث جديدة لجميع الأعضاء:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = broadcastText,
                    onValueChange = onBroadcastTextChange,
                    placeholder = { Text("اكتب نص الرسالة هنا...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WhatsAppGreen,
                        unfocusedBorderColor = Color(0x26FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                IconButton(
                    onClick = onSendBroadcast,
                    enabled = broadcastText.isNotBlank() && !isBroadcasting,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (broadcastText.isNotBlank()) WhatsAppGreen else DarkSurface
                        )
                        .size(48.dp)
                ) {
                    if (isBroadcasting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DarkBgPrimary)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "إرسال للبث",
                            tint = if (broadcastText.isNotBlank()) DarkBgPrimary else TextMuted
                        )
                    }
                }
            }

            // زر فتح السجل والتفاصيل
            TextButton(
                onClick = onOpenDetails,
                modifier = Modifier.align(Alignment.End)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "فتح سجل إرسال المجموعة", fontSize = 13.sp, color = CyberCyan)
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun GroupDetailsScreen(
    group: Group,
    uiState: GroupsUiState,
    onBackClick: () -> Unit,
    onTabSelect: (Int) -> Unit,
    onBroadcastTextChange: (String) -> Unit,
    onSendBroadcast: () -> Unit
) {
    val broadcastText = uiState.broadcastTexts[group.id] ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBgPrimary)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // شريط علوي مع زر الرجوع
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(DarkSurface)
                    .size(40.dp)
            ) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع", tint = TextPrimary)
            }

            Column {
                Text(
                    text = group.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${uiState.selectedGroupMembers.size} عضو في هذه المجموعة",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // حقل إدخال الرسالة السريعة داخل التفاصيل
        GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "إرسال بث جديد للمجموعة:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = broadcastText,
                        onValueChange = onBroadcastTextChange,
                        placeholder = { Text("اكتب الرسالة للبث المباشر...", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WhatsAppGreen,
                            unfocusedBorderColor = Color(0x26FFFFFF),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    GradientButton(
                        text = "إرسال",
                        onClick = onSendBroadcast,
                        enabled = broadcastText.isNotBlank() && !uiState.isBroadcasting,
                        icon = Icons.Filled.Send
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // شريط التبويبين الصارم (سجل المرسل وسجل المنتهي)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .padding(4.dp)
        ) {
            TabButton(
                title = "سجل المرسل (الجاري)",
                count = uiState.selectedGroupInsiteLogs.size,
                isSelected = uiState.activeDetailsTab == 0,
                onClick = { onTabSelect(0) },
                modifier = Modifier.weight(1f)
            )

            TabButton(
                title = "سجل المنتهي",
                count = uiState.selectedGroupCompletedLogs.size,
                isSelected = uiState.activeDetailsTab == 1,
                onClick = { onTabSelect(1) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // قائمة السجلات حسب التبويب النشط
        val currentLogs = if (uiState.activeDetailsTab == 0) {
            uiState.selectedGroupInsiteLogs
        } else {
            uiState.selectedGroupCompletedLogs
        }

        if (currentLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                    Text(
                        text = if (uiState.activeDetailsTab == 0) "لا توجد رسائل قيد الإرسال حالياً" else "لا يوجد سجل رسائل منتهية بعد",
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(currentLogs, key = { it.id }) { log ->
                    GroupLogCardItem(log = log)
                }
            }
        }
    }
}

@Composable
fun TabButton(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0x3325D366) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) WhatsAppGreen else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) WhatsAppGreen else TextMuted
            )

            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) WhatsAppGreen else TextMuted)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$count",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkBgPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun GroupLogCardItem(log: GroupMessageLog) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale.getDefault()) }
    val formattedTime = log.sentAt?.let { dateFormat.format(Date(it.toEpochMilli())) } ?: "الآن"

    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${log.recipientName} (${log.recipientPhone})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Text(
                    text = when (log.status) {
                        GroupLogStatus.IN_PROGRESS -> "جاري الإرسال ⏳"
                        GroupLogStatus.COMPLETED -> "تم الإرسال بنجاح ✅"
                        GroupLogStatus.FAILED -> "فشل الإرسال ❌"
                    },
                    fontSize = 11.sp,
                    color = when (log.status) {
                        GroupLogStatus.IN_PROGRESS -> AmberWarning
                        GroupLogStatus.COMPLETED -> WhatsAppGreen
                        GroupLogStatus.FAILED -> CoralError
                    }
                )
            }

            Text(
                text = "\"${log.messageText}\"",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formattedTime, fontSize = 11.sp, color = TextMuted)
                if (!log.errorMessage.isNullOrBlank()) {
                    Text(text = log.errorMessage, fontSize = 11.sp, color = CoralError)
                }
            }
        }
    }
}

@Composable
fun CreateGroupDialog(
    uiState: GroupsUiState,
    onDismiss: () -> Unit,
    onGroupNameChange: (String) -> Unit,
    onGroupDescChange: (String) -> Unit,
    onContactSearchChange: (String) -> Unit,
    onToggleContact: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onCreateConfirm: () -> Unit
) {
    val filteredContacts = remember(uiState.deviceContacts, uiState.contactSearchQuery) {
        if (uiState.contactSearchQuery.isBlank()) {
            uiState.deviceContacts
        } else {
            uiState.deviceContacts.filter {
                it.name.contains(uiState.contactSearchQuery, ignoreCase = true) ||
                it.phone.contains(uiState.contactSearchQuery)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "إنشاء مجموعة بث جديدة",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.newGroupName,
                    onValueChange = onGroupNameChange,
                    label = { Text("اسم المجموعة *") },
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
                    value = uiState.newGroupDescription,
                    onValueChange = onGroupDescChange,
                    label = { Text("وصف المجموعة (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0x26FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                HorizontalDivider(color = Color(0x1AFFFFFF))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختر جهات الاتصال (${uiState.selectedContactPhones.size} محدد):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Row {
                        TextButton(onClick = onSelectAll) {
                            Text("تحديد الكل", fontSize = 11.sp, color = CyberCyan)
                        }
                        TextButton(onClick = onClearAll) {
                            Text("إلغاء", fontSize = 11.sp, color = CoralError)
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.contactSearchQuery,
                    onValueChange = onContactSearchChange,
                    placeholder = { Text("ابحث في أسماء الهاتف...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = Color(0x26FFFFFF),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد جهات اتصال مطابقة",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredContacts, key = { it.phone }) { contact ->
                            val isSelected = uiState.selectedContactPhones.contains(contact.phone)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleContact(contact.phone) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleContact(contact.phone) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = WhatsAppGreen,
                                            uncheckedColor = TextMuted
                                        )
                                    )

                                    Column {
                                        Text(text = contact.name, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                                        Text(text = contact.phone, fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            GradientButton(
                text = "إنشاء المجموعة",
                onClick = onCreateConfirm,
                enabled = uiState.newGroupName.isNotBlank() && uiState.selectedContactPhones.isNotEmpty(),
                icon = Icons.Filled.Check
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextMuted)
            }
        }
    )
}
