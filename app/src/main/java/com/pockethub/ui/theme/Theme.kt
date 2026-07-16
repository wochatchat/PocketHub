package com.pockethub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * App-wide theme mode choice. The [Default] (Linear-inspired dark) is the default; users can
 * override to light / system in settings.
 */
enum class ThemeMode { System, Dark, Light }

/**
 * The Linear-inspired dark palette — calm accent, compact, focused on information density.
 *
 * Channels deliberately calm: a single accent violet is used for key actions; everything else
 * stays in neutral greys.
 */
private val LinearDarkColors = darkColorScheme(
    primary = Color(0xFF7C8BFF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2A3140),
    onPrimaryContainer = Color(0xFFD6DEFF),
    secondary = Color(0xFF8FAEFF),
    onSecondary = Color(0xFF0E1116),
    secondaryContainer = Color(0xFF1E2230),
    onSecondaryContainer = Color(0xFFC0C9FF),
    tertiary = Color(0xFF5BC8A8),
    onTertiary = Color(0xFF001A14),
    tertiaryContainer = Color(0xFF1B2D2A),
    onTertiaryContainer = Color(0xFF9AEFDE),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE7EAEE),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFE7EAEE),
    surfaceVariant = Color(0xFF1A1D26),
    onSurfaceVariant = Color(0xFFB3B8C3),
    surfaceTint = Color(0xFF7C8BFF),
    inverseSurface = Color(0xFFE7EAEE),
    inverseOnSurface = Color(0xFF0B0E14),
    error = Color(0xFFE75B5B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF2A1518),
    onErrorContainer = Color(0xFFFFD0D0),
    outline = Color(0xFF4A4F5A),
    outlineVariant = Color(0xFF2A2D38),
    scrim = Color(0xFF000000),
)

/**
 * The GitHub Primer-inspired light palette — airy white cards, warm ink, accent indigo
 * inherited from github.com.
 */
private val PrimerLightColors = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDEEFF),
    onPrimaryContainer = Color(0xFF07418A),
    secondary = Color(0xFF57606A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAEEF2),
    onSecondaryContainer = Color(0xFF2F363D),
    tertiary = Color(0xFF1A7F37),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6F3DE),
    onTertiaryContainer = Color(0xFF0B5A26),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEFF1F4),
    onSurfaceVariant = Color(0xFF57606A),
    surfaceTint = Color(0xFF0969DA),
    inverseSurface = Color(0xFF1F2328),
    inverseOnSurface = Color(0xFFE7EAEE),
    error = Color(0xFFCF222E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE3E3),
    onErrorContainer = Color(0xFF6E0E16),
    outline = Color(0xFF8C959F),
    outlineVariant = Color(0xFFD0D7DE),
    scrim = Color(0xFF000000),
)

/**
 * English-style typography suite shared between the two themes — tight heading sizes, slightly
 * condensed body, and one monospace slot used by code viewers.
 */
val PocketHubTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp, letterSpacing = (-0.25).sp),
    displaySmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    headlineSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

/** Color used for the status / navigation bars. Default dark — matches Linear dark theme. */
private val LocalSystemBarsDark = compositionLocalOf { true }

@Composable
fun PocketHubTheme(
    mode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val colors = if (isDark) LinearDarkColors else PrimerLightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }
    CompositionLocalProvider(LocalSystemBarsDark provides isDark) {
        MaterialTheme(
            colorScheme = colors,
            typography = PocketHubTypography,
            content = content,
        )
    }
}
