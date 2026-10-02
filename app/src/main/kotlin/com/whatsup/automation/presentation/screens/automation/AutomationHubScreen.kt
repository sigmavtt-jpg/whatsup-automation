package com.whatsup.automation.presentation.screens.automation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsup.automation.presentation.screens.groups.GroupsScreen
import com.whatsup.automation.presentation.screens.groups.GroupsViewModel
import com.whatsup.automation.presentation.screens.rules.RulesScreen
import com.whatsup.automation.presentation.screens.rules.RulesViewModel
import com.whatsup.automation.presentation.screens.statuses.StatusesScreen
import com.whatsup.automation.presentation.screens.statuses.StatusesViewModel
import com.whatsup.automation.presentation.theme.*

enum class AutomationTab(val title: String, val icon: ImageVector) {
    RULES("القواعد والردود", Icons.Filled.Bolt),
    STATUSES("أتمتة الحالات", Icons.Filled.AutoStories),
    GROUPS("المجموعات", Icons.Filled.Groups)
}

@Composable
fun AutomationHubScreen(
    rulesViewModel: RulesViewModel,
    statusesViewModel: StatusesViewModel,
    groupsViewModel: GroupsViewModel,
    initialTab: AutomationTab = AutomationTab.RULES,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(initialTab) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBgPrimary)
    ) {
        // شريط التبويبات الفرعية العلوي بتصميم Glassmorphism فخم
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AutomationTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val bgBrush = if (isSelected) {
                        Brush.horizontalGradient(listOf(WhatsAppGreenDark, WhatsAppGreen))
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bgBrush)
                            .clickable {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTab = tab
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else TextSecondary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // محتوى التبويب المختار مع أنيميشن انتقال ناعم
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
            },
            label = "AutomationHubContent",
            modifier = Modifier.fillMaxSize()
        ) { tab ->
            when (tab) {
                AutomationTab.RULES -> RulesScreen(viewModel = rulesViewModel)
                AutomationTab.STATUSES -> StatusesScreen(viewModel = statusesViewModel)
                AutomationTab.GROUPS -> GroupsScreen(viewModel = groupsViewModel)
            }
        }
    }
}
