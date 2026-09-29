package com.whatsup.automation.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppGreen,
    onPrimary = DarkBgPrimary,
    primaryContainer = WhatsAppGreenDark,
    onPrimaryContainer = TextPrimary,
    secondary = CyberCyan,
    onSecondary = DarkBgPrimary,
    background = DarkBgPrimary,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkBgSecondary,
    onSurfaceVariant = TextSecondary,
    error = RoseError,
    onError = TextPrimary
)

@Composable
fun WhatsUpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = DarkBgPrimary.toArgb()
                window.navigationBarColor = DarkBgPrimary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
