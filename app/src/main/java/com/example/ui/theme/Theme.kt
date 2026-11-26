package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.security.AppThemeMode

@Immutable
data class JrsPalette(
    val isDark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val surface: Color,
    val navBar: Color,
    val cardTop: Color,
    val cardBottom: Color,
    val bubbleElevated: Color,
    val glassStroke: Color,
    val glassStrokeBright: Color,
    val cyan: Color,
    val emerald: Color,
    val amber: Color,
    val crimson: Color,
    val violet: Color,
    val blue: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val onAccentButton: Color,
    val trackMuted: Color
)

val DarkJrsPalette = JrsPalette(
    isDark = true,
    bgTop = ObsidianVoid,
    bgBottom = ObsidianDeep,
    surface = ObsidianSurface,
    navBar = ObsidianDeep.copy(alpha = 0.96f),
    cardTop = ObsidianCardElevated,
    cardBottom = ObsidianCard,
    bubbleElevated = ObsidianCardElevated,
    glassStroke = GlassStroke,
    glassStrokeBright = GlassStrokeBright,
    cyan = NeonCyan,
    emerald = CyberEmerald,
    amber = AmberPulse,
    crimson = AlertCrimson,
    violet = VioletNeural,
    blue = ElectricBlue,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    onAccentButton = ObsidianVoid,
    trackMuted = Color.White.copy(alpha = 0.10f)
)

val LightJrsPalette = JrsPalette(
    isDark = false,
    bgTop = FrostVoid,
    bgBottom = FrostDeep,
    surface = FrostSurface,
    navBar = FrostSurface.copy(alpha = 0.97f),
    cardTop = FrostSurface,
    cardBottom = FrostCardElevated,
    bubbleElevated = Color(0xFFEFF6FF),
    glassStroke = LightGlassStroke,
    glassStrokeBright = LightGlassStrokeBright,
    cyan = LightTealAccent,
    emerald = LightEmeraldAccent,
    amber = LightAmberAccent,
    crimson = LightCrimsonAccent,
    violet = LightVioletAccent,
    blue = ElectricBlue,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    onAccentButton = Color.White,
    trackMuted = Color(0xFF0F172A).copy(alpha = 0.10f)
)

val LocalJrsPalette = staticCompositionLocalOf { DarkJrsPalette }

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

private val JrsLightColorScheme = lightColorScheme(
    primary = LightTealAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = LightEmeraldAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF047857),
    tertiary = ElectricBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1D4ED8),
    error = LightCrimsonAccent,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFB91C1C),
    background = FrostVoid,
    onBackground = LightTextPrimary,
    surface = FrostSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = FrostCardElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightGlassStroke,
    outlineVariant = Color(0x160F172A)
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
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> systemDark
    }
    val palette = if (useDark) DarkJrsPalette else LightJrsPalette
    val colorScheme = if (useDark) JrsDarkColorScheme else JrsLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDark
                controller.isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalJrsPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JrsTypography,
            shapes = JrsShapes,
            content = content
        )
    }
}
