package com.pockethub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Back-compat theme-mode choice (System/Dark/Light). With multi-style support this now maps
 * to a style pair: Dark→LinearDark, Light→PrimerLight, System→follow system. The full
 * style picker in Settings overrides this via [AppStyle].
 */
enum class ThemeMode { System, Dark, Light }

/** Complete visual definition of one style: palette + typography + shapes + tokens. */
data class AppStyleDef(
    val style: AppStyle,
    val isDark: Boolean,
    val colors: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val tokens: StyleTokens,
)

// ── Typography builders ──────────────────────────────────────────────────────

private fun linearTypography() = Typography(
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

/** Paper: bookish serif-leaning — larger body, generous leading, slightly warm weight. */
private fun paperTypography() = Typography(
    displayLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    displayMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 33.sp),
    displaySmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    headlineLarge = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    headlineSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 25.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp, letterSpacing = 0.6.sp),
)

/** Neon: terminal mono voice — tight, condensed, uppercase-flavored labels, wide tracking. */
private fun neonTypography(mono: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = mono, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontFamily = mono, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = mono, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp, letterSpacing = 0.5.sp),
    headlineLarge = TextStyle(fontFamily = mono, fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp, letterSpacing = 0.5.sp),
    headlineMedium = TextStyle(fontFamily = mono, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontFamily = mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = mono, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontFamily = mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp, letterSpacing = 1.sp),
    titleSmall = TextStyle(fontFamily = mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp, letterSpacing = 1.sp),
    bodyLarge = TextStyle(fontFamily = mono, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontFamily = mono, fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 19.sp, letterSpacing = 0.3.sp),
    bodySmall = TextStyle(fontFamily = mono, fontSize = 11.sp, fontWeight = FontWeight.Normal, lineHeight = 15.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 17.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontFamily = mono, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 15.sp, letterSpacing = 1.5.sp),
    labelSmall = TextStyle(fontFamily = mono, fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp, letterSpacing = 1.5.sp),
)

/** Lavender: rounded friendly — soft weights, slightly larger titles, open tracking. */
private fun lavenderTypography() = Typography(
    displayLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 37.sp, letterSpacing = (-0.2).sp),
    displayMedium = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    displaySmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 27.sp),
    headlineLarge = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp),
    headlineMedium = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp),
    headlineSmall = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp, letterSpacing = 0.2.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, letterSpacing = 0.2.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 23.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp, letterSpacing = 0.5.sp),
)

/** Forest: organic editorial — medium weight titles, airy leading, calm rhythm. */
private fun forestTypography() = Typography(
    displayLarge = TextStyle(fontSize = 29.sp, fontWeight = FontWeight.Medium, lineHeight = 36.sp, letterSpacing = (-0.3).sp),
    displayMedium = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Medium, lineHeight = 31.sp),
    displaySmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium, lineHeight = 27.sp),
    headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 29.sp),
    headlineMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 25.sp),
    headlineSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 23.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp, letterSpacing = 0.3.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, letterSpacing = 0.3.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, letterSpacing = 0.6.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp, letterSpacing = 0.8.sp),
)

// ── Shape systems ────────────────────────────────────────────────────────────

private fun linearShapes() = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)
private fun paperShapes() = Shapes(
    extraSmall = RoundedCornerShape(3.dp), small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
private fun neonShapes() = Shapes(
    extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp), large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)
private fun lavenderShapes() = Shapes(
    extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
private fun forestShapes() = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ── Style registry ───────────────────────────────────────────────────────────

/**
 * Public entry point. Wraps the raw palette with derived surface-container
 * colors so every themed surface (dialogs, sheets, menus) matches the style.
 */
fun styleDef(style: AppStyle): AppStyleDef {
    val def = baseStyleDef(style)
    return def.copy(colors = deriveSurfaceContainers(def.colors, def.isDark))
}

private fun baseStyleDef(style: AppStyle): AppStyleDef = when (style) {
    AppStyle.LinearDark -> AppStyleDef(
        style, isDark = true, colors = LinearDarkColors, typography = linearTypography(),
        shapes = linearShapes(),
        tokens = StyleTokens(1.0f, FontFamily.Monospace, null, Color(0xFF7C8BFF), Color(0xFF7C8BFF)),
    )
    AppStyle.PrimerLight -> AppStyleDef(
        style, isDark = false, colors = PrimerLightColors, typography = linearTypography(),
        shapes = linearShapes(),
        tokens = StyleTokens(1.0f, FontFamily.Monospace, null, Color(0xFF0969DA), Color(0xFF0969DA)),
    )
    AppStyle.Paper -> AppStyleDef(
        style, isDark = false, colors = PaperColors, typography = paperTypography(),
        shapes = paperShapes(),
        tokens = StyleTokens(0.6f, FontFamily.Monospace, FontFamily.Serif, Color(0xFF8A6D3B), Color(0xFFB05C4A)),
    )
    AppStyle.Neon -> AppStyleDef(
        style, isDark = true, colors = NeonColors, typography = neonTypography(FontFamily.Monospace),
        shapes = neonShapes(),
        tokens = StyleTokens(0.0f, FontFamily.Monospace, FontFamily.Monospace, Color(0xFF00E5FF), Color(0xFFFF2FD6)),
    )
    AppStyle.Lavender -> AppStyleDef(
        style, isDark = false, colors = LavenderColors, typography = lavenderTypography(),
        shapes = lavenderShapes(),
        tokens = StyleTokens(2.0f, FontFamily.Monospace, FontFamily.SansSerif, Color(0xFF7B5CE0), Color(0xFFD05CB8)),
    )
    AppStyle.Forest -> AppStyleDef(
        style, isDark = true, colors = ForestColors, typography = forestTypography(),
        shapes = forestShapes(),
        tokens = StyleTokens(1.4f, FontFamily.Monospace, FontFamily.SansSerif, Color(0xFF8FBC7A), Color(0xFFD8B25C)),
    )
}

/** Resolve the active style: explicit override wins, else map from legacy [mode]. */
private fun resolveStyle(styleOverride: AppStyle?, mode: ThemeMode, systemDark: Boolean): AppStyle =
    styleOverride ?: when (mode) {
        ThemeMode.Dark -> AppStyle.LinearDark
        ThemeMode.Light -> AppStyle.PrimerLight
        ThemeMode.System -> if (systemDark) AppStyle.LinearDark else AppStyle.PrimerLight
    }

/**
 * Built-in dark style forced on when "follow system dark mode" is enabled and
 * the system is currently in night mode. [styleOverride] stays untouched — it
 * is the user's chosen baseline and is restored as soon as the system leaves
 * night mode.
 */
private val FORCE_DARK_STYLE = AppStyle.LinearDark

/** Color used for the status / navigation bars. Default dark — matches Linear dark theme. */
private val LocalSystemBarsDark = compositionLocalOf { true }

// ── Semantic status colors ───────────────────────────────────────────────────

/**
 * Status hues that must stay recognizable in all six styles — taken from
 * GitHub's own Primer dark/light scales. UI chrome must never hardcode these:
 * screens read them via [semanticColors], so "green = success" holds in Neon,
 * Forest and Paper alike while every other accent follows the active palette.
 */
@Immutable
data class SemanticColors(
    val success: Color,
    val danger: Color,
    val merged: Color,
    val warning: Color,
    val running: Color,
    /** De-emphasised outcome (skipped / neutral / unknown). */
    val neutral: Color,
)

fun semanticForDark(dark: Boolean): SemanticColors = if (dark) SemanticColors(
    success = Color(0xFF3FB950), danger = Color(0xFFF85149), merged = Color(0xFFA371F7),
    warning = Color(0xFFD29922), running = Color(0xFF539BF5), neutral = Color(0xFF8B949E),
) else SemanticColors(
    success = Color(0xFF1A7F37), danger = Color(0xFFCF222E), merged = Color(0xFF8250DF),
    warning = Color(0xFF9A6700), running = Color(0xFF0969DA), neutral = Color(0xFF57606A),
)

val LocalSemanticColors = compositionLocalOf { semanticForDark(true) }

/** Semantic status colors of the active style. Read these, never `Color(0xFF…)`. */
@Composable
fun semanticColors(): SemanticColors = LocalSemanticColors.current

/**
 * Derive the M3 "surface container" family from the palette's own surface /
 * surfaceVariant colors. The custom palettes only define the base roles, which
 * left surfaceContainer* at the static M3 defaults (neutral gray) — that's why
 * AlertDialogs / bottom sheets / menus looked jarring against every style.
 * Blending from the palette's own tones keeps every themed surface consistent.
 */
private fun deriveSurfaceContainers(s: ColorScheme, isDark: Boolean): ColorScheme {
    fun blend(a: Color, b: Color, f: Float) = androidx.compose.ui.graphics.lerp(a, b, f)
    val surface = s.surface
    val variant = s.surfaceVariant
    val onSurface = s.onSurface
    return s.copy(
        surfaceContainerLowest = blend(surface, if (isDark) Color.Black else Color.White, 0.04f),
        surfaceContainerLow = blend(surface, variant, 0.45f),
        surfaceContainer = blend(surface, variant, 0.75f),
        surfaceContainerHigh = variant,
        surfaceContainerHighest = blend(variant, onSurface, 0.08f),
        surfaceDim = blend(surface, Color.Black, if (isDark) 0.08f else 0.10f),
        surfaceBright = blend(surface, Color.White, if (isDark) 0.06f else 0.05f),
    )
}

@Composable
fun PocketHubTheme(
    mode: ThemeMode = ThemeMode.Dark,
    styleOverride: AppStyle? = null,
    forceDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    // "Follow system" night mode: while the OS is in dark mode the built-in
    // dark style is forced on regardless of the user's chosen style; once the
    // system leaves night mode the chosen style takes effect again. Applied on
    // top of styleOverride so the persisted preference is never mutated.
    val effectiveOverride = if (forceDark && systemDark) FORCE_DARK_STYLE else styleOverride
    val style = resolveStyle(effectiveOverride, mode, systemDark)
    val def = styleDef(style)
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !def.isDark
                WindowCompat.getInsetsController(it, view).isAppearanceLightNavigationBars = !def.isDark
            }
        }
    }
    CompositionLocalProvider(
        LocalSystemBarsDark provides def.isDark,
        LocalSemanticColors provides semanticForDark(def.isDark),
        LocalStyleTokens provides def.tokens,
        LocalAppStyle provides style,
    ) {
        MaterialTheme(
            colorScheme = def.colors,
            typography = def.typography,
            shapes = def.shapes,
            content = content,
        )
    }
}
