package com.whatsup.automation.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// لوحة ألوان واتساب أوتوميشن المتقدمة (Cyber-Emerald Premium)
val WhatsAppGreen = Color(0xFF25D366)
val WhatsAppGreenLight = Color(0xFF34D399)
val WhatsAppGreenDark = Color(0xFF059669)
val WhatsAppTeal = Color(0xFF128C7E)
val WhatsAppDarkTeal = Color(0xFF075E54)

val CyberCyan = Color(0xFF06B6D4)
val ElectricBlue = Color(0xFF3B82F6)
val NeonPurple = Color(0xFF8B5CF6)
val AmberWarning = Color(0xFFF59E0B)
val RoseError = Color(0xFFEF4444)
val CoralError = Color(0xFFF43F5E)

// خلفيات الوضع الداكن فائق الجودة
val DarkBgPrimary = Color(0xFF0A0F1D)
val DarkBgSecondary = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceHover = Color(0xFF334155)

// زجاج وأغشية (Glassmorphism)
val GlassFill = Color(0x1AFFFFFF)
val GlassBorder = Color(0x33FFFFFF)
val GlassFillSubtle = Color(0x0DFFFFFF)
val GlassBorderHighlight = Color(0x4D34D399)

// نصوص
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// تدرجات لونية ممتازة
val EmeraldGradient = Brush.horizontalGradient(
    colors = listOf(WhatsAppGreen, CyberCyan)
)

val BackgroundGradient = Brush.verticalGradient(
    colors = listOf(DarkBgPrimary, DarkBgSecondary, Color(0xFF050811))
)

val GlassCardGradient = Brush.verticalGradient(
    colors = listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF))
)
