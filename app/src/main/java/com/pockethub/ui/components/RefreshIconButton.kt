package com.pockethub.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pockethub.R

/**
 * Refresh action for a [androidx.compose.material3.TopAppBar]. Shows a static
 * [Icons.Outlined.Refresh] glyph that spins clockwise while [refreshing] is true,
 * then freezes again once the bound VM reports the refresh done.
 *
 * Tapping the button calls [onClick]; while [refreshing] is true the button is
 * disabled so the user can't queue a second refresh behind the first one. Pass
 * [compact] true when the host top-bar has constrained touch-targets (e.g.
 * embedded inside a section header rather than at the screen top bar), so the
 * glyph shrinks from the 24dp IconButton default down to 18dp.
 */
@Composable
fun RefreshIconButton(
    onClick: () -> Unit,
    refreshing: Boolean = false,
    contentDescriptionRes: Int = R.string.action_refresh,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    // Animated rotation for the spinning state; the in-progress transition keeps the
    // glyph visibly moving and stops the moment refreshing flips to false.
    val transition = rememberInfiniteTransition(label = "refresh-icon-spin")
    val spinAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin-angle",
    )

    IconButton(onClick = onClick, enabled = enabled && !refreshing) {
        Box(modifier = Modifier.size(if (compact) 18.dp else 24.dp)) {
            // Same glyph shape either way — layout stays stable so the row doesn't
            // jolt when the refresh starts/stops, only the rotation changes.
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(contentDescriptionRes),
                modifier = Modifier
                    .size(if (compact) 18.dp else 24.dp)
                    .rotate(if (refreshing) spinAngle else 0f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
