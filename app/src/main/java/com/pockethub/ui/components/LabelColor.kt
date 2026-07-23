package com.pockethub.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Pick black or white as the foreground color given a [background] color, so
 * that label/badge chips stay legible regardless of the underlying label hex.
 *
 * Threshold follows the standard WCAG-style perceived-luminance heuristic —
 * used by GitHub.com for rendering issue labels.
 */
@Composable
fun rememberContrastColor(background: Color): Color {
    val onDark = MaterialTheme.colorScheme.onPrimary
    val onLight = MaterialTheme.colorScheme.onSurface
    return remember(background) {
        val r = background.red
        val g = background.green
        val b = background.blue
        // sRGB-relative luminance approximation; Compose Color components are 0f..1f.
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        if (luminance > 0.55f) onLight else onDark
    }
}
