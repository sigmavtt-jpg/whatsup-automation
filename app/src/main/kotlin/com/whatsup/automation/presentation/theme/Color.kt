package com.whatsup.automation.presentation.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// لوحة ألوان واتساب أوتوميشن المتقدمة — الجيل الثاني (Obsidian Emerald & Cyber Luxury 2.0)
val WhatsAppGreen = Color(0xFF10B981) // زمردي ناصع
val WhatsAppGreenLight = Color(0xFF34D399)
val WhatsAppGreenDark = Color(0xFF059669)
val WhatsAppTeal = Color(0xFF0D9488)
val WhatsAppDarkTeal = Color(0xFF042F2E)
val WhatsAppNeon = Color(0xFF00E676) // نيون واتساب فاقع

val CyberCyan = Color(0xFF00F2FE)
val CyanGlow = Color(0xFF38BDF8)
val ElectricBlue = Color(0xFF3B82F6)
val NeonPurple = Color(0xFF8B5CF6)
val VioletNebula = Color(0xFFA855F7)
val AmberWarning = Color(0xFFF59E0B)
val RoseError = Color(0xFFF43F5E)
val CoralError = Color(0xFFFB7185)

// خلفيات أوبسيديان العميقة الفاخرة (Deep Obsidian & Glass)
val DarkBgPrimary = Color(0xFF060911) // أسود كحلي أوبسيديان عميق
val DarkBgSecondary = Color(0xFF0B111E)
val DarkBgTertiary = Color(0xFF0F172A)
val DarkSurface = Color(0xFF131C2E)
val DarkSurfaceHover = Color(0xFF1E293B)
val DarkSurfaceElevated = Color(0xFF1A2438)

// زجاج وأغشية (Next-Gen Glassmorphism)
val GlassFill = Color(0x18FFFFFF)
val GlassBorder = Color(0x2EFFFFFF)
val GlassFillSubtle = Color(0x0CFFFFFF)
val GlassBorderHighlight = Color(0x4D10B981)

// نصوص عالية التباين
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val TextHint = Color(0xFF475569)

// تدرجات لونية سائلة ومضيئة (Liquid Gradients)
val EmeraldGradient = Brush.horizontalGradient(
    colors = listOf(WhatsAppNeon, CyberCyan)
)

val LiquidEmeraldGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF059669), Color(0xFF10B981), Color(0xFF00F2FE))
)

val NeonPurpleGradient = Brush.linearGradient(
    colors = listOf(NeonPurple, CyberCyan)
)

val BackgroundGradient = Brush.verticalGradient(
    colors = listOf(DarkBgPrimary, DarkBgSecondary, Color(0xFF03050A))
)

val GlassCardGradient = Brush.verticalGradient(
    colors = listOf(Color(0x28FFFFFF), Color(0x08FFFFFF))
)

object ObsidianGlowBorder {
    val Emerald: Brush = Brush.linearGradient(listOf(Color(0x4010B981), Color(0x2000F2FE), Color(0x10FFFFFF)))
    val Cyan: Brush = Brush.linearGradient(listOf(Color(0x4000F2FE), Color(0x2038BDF8), Color(0x10FFFFFF)))
    val Purple: Brush = Brush.linearGradient(listOf(Color(0x408B5CF6), Color(0x20A855F7), Color(0x10FFFFFF)))
    val Amber: Brush = Brush.linearGradient(listOf(Color(0x40F59E0B), Color(0x20FBBF24), Color(0x10FFFFFF)))
    val Rose: Brush = Brush.linearGradient(listOf(Color(0x40F43F5E), Color(0x20FB7185), Color(0x10FFFFFF)))
    val None: Brush = Brush.linearGradient(listOf(Color(0x18FFFFFF), Color(0x0AFFFFFF)))
}
