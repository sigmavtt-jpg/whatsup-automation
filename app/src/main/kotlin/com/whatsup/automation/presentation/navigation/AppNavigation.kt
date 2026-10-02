package com.whatsup.automation.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.whatsup.automation.presentation.theme.*

/**
 * وجهات التطبيق الرئيسية بتصميم 4 محاور موحدة وأنيقة.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : Screen(
        route = "dashboard",
        title = "الرئيسية",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )

    data object Automation : Screen(
        route = "automation",
        title = "الأتمتة",
        selectedIcon = Icons.Filled.Bolt,
        unselectedIcon = Icons.Outlined.Bolt
    )

    data object ChatsHub : Screen(
        route = "chats_hub",
        title = "المحادثات",
        selectedIcon = Icons.Filled.Forum,
        unselectedIcon = Icons.Outlined.Forum
    )

    data object System : Screen(
        route = "system",
        title = "النظام والربط",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune
    )

    // المسارات الفرعية للتوافق والتوجيه المباشر
    data object Groups : Screen("groups", "المجموعات", Icons.Filled.Groups, Icons.Outlined.Groups)
    data object Rules : Screen("rules", "القواعد", Icons.Filled.Bolt, Icons.Outlined.Bolt)
    data object Statuses : Screen("statuses", "الحالات", Icons.Filled.AutoStories, Icons.Outlined.AutoStories)
    data object Pairing : Screen("pairing", "الربط", Icons.Filled.QrCode2, Icons.Outlined.QrCode2)
    data object Logs : Screen("logs", "السجلات", Icons.Filled.Tune, Icons.Outlined.Tune)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Automation,
    Screen.ChatsHub,
    Screen.System
)

/**
 * شريط تنقل سفلي عائم بتصميم Dynamic Glass Island 2.0 ومؤشرات نيون حية.
 */
@Composable
fun ModernBottomNavBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF8090F1C))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0x3500F2FE),
                            Color(0x5510B981),
                            Color(0x358B5CF6)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { screen ->
                val isSelected = currentRoute == screen.route
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.05f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "tabScale"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) WhatsAppGreenLight else TextMuted,
                    animationSpec = tween(250),
                    label = "iconTint"
                )
                val bgPillColor by animateColorAsState(
                    targetValue = if (isSelected) WhatsAppGreen.copy(alpha = 0.18f) else Color.Transparent,
                    animationSpec = tween(250),
                    label = "bgPill"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .scale(scale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgPillColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isSelected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                            contentDescription = screen.title,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )

                        Text(
                            text = screen.title,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = iconTint,
                            maxLines = 1
                        )

                        // نقطة نيون سفلية نشطة
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 4.dp else 0.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) CyberCyan else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}
