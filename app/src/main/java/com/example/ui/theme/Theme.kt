package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val JrsDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = Color(0xFF003640),
    onPrimaryContainer = NeonCyan,
    secondary = CyberEmerald,
    onSecondary = ObsidianVoid,
    secondaryContainer = Color(0xFF00391D),
    onSecondaryContainer = CyberEmerald,
    tertiary = ElectricBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF172554),
    onTertiaryContainer = Color(0xFFBFDBFE),
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFFB4C8),
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    outline = GlassStroke,
    outlineVariant = Color(0x16FFFFFF)
)

private val JrsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JrsAiGuardTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = JrsDarkColorScheme,
        typography = JrsTypography,
        shapes = JrsShapes,
        content = content
    )
}
