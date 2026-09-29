package com.whatsup.automation.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsup.automation.domain.model.ConnectionState
import com.whatsup.automation.presentation.theme.*

/**
 * بطاقة زجاجية فاخرة (Glassmorphic Card) مع حواف متدرجة وشبه شفافة.
 */
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderBrush: Brush = Brush.linearGradient(
        colors = listOf(Color(0x33FFFFFF), Color(0x0DFFFFFF))
    ),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0x14FFFFFF))
            .border(1.dp, borderBrush, RoundedCornerShape(cornerRadius))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

/**
 * شارة حالة الاتصال مع نقطة نبضية متحركة.
 */
@Composable
fun ConnectionStatusBadge(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor) = when (connectionState) {
        is ConnectionState.Connected -> "متصل ونشط" to WhatsAppGreen
        is ConnectionState.AwaitingPairing -> "في انتظار الاقتران" to AmberWarning
        is ConnectionState.Reconnecting -> "جارٍ إعادة الاتصال" to CyberCyan
        is ConnectionState.Disconnected -> "غير متصل" to TextMuted
        is ConnectionState.Error -> "خطأ بالاتصال" to RoseError
    }

    // تأثير النبض (Pulse Animation)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(statusColor.copy(alpha = 0.15f))
            .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = alpha))
        )
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * بطاقة إحصائية لعرض العدادات الحية في لوحة التحكم.
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    GlassmorphicCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        borderBrush = Brush.linearGradient(
            colors = listOf(accentColor.copy(alpha = 0.4f), Color(0x10FFFFFF))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * زر متدرج أنيق بنمط Cyber Emerald.
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) EmeraldGradient else Brush.linearGradient(listOf(DarkSurface, DarkSurface)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) DarkBgPrimary else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = text,
                color = if (enabled) DarkBgPrimary else TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * طرفية محاكاة Terminal لعرض السجلات والأوامر البرمجية الحية.
 */
@Composable
fun TerminalConsole(
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070B14))
            .border(1.dp, Color(0x2600F2FE), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجل الأتمتة المباشر (Terminal)",
                    fontSize = 12.sp,
                    color = CyberCyan,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(RoseError))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AmberWarning))
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(WhatsAppGreen))
                }
            }

            HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 1.dp)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                lines.takeLast(6).forEach { line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (line.contains("✅") || line.contains("تم")) WhatsAppGreenLight else TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
