package com.pockethub.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * PocketHub motion tokens. One place to keep every animation consistent:
 * springs for interactions (press / selection), tweens for entrances.
 */
object Motion {
    /** Interactive press / toggle springs — snappy with a slight bounce. */
    fun press() = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    fun settle() = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)

    /** Screen / element entrance tweens. */
    fun enter(millis: Int = 260) = tween<Float>(millis, easing = FastOutSlowInEasing)

    /** Stagger step between list items, ms. */
    const val STAGGER_STEP_MS = 45
    /** Cap for the per-item entrance delay so long lists stay responsive. */
    const val MAX_STAGGER_MS = 360
    /** Only the first N list items get the staggered entrance — items composed
     *  later (scroll/fling) render immediately: animating every newly composed
     *  item costs frames and delays content exactly where scrolling hurts. */
    const val STAGGER_MAX_ITEMS = 12
}

/**
 * Press feedback: the content scales down slightly while pressed and springs
 * back on release. Apply to any clickable element for a tactile feel.
 *
 * Also emits a light selection tick on press-down — pressable surfaces get
 * tactile confirmation for free, no call-site wiring.
 */
@Composable
fun Modifier.pressScale(
    pressedScale: Float = 0.97f,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(pressed) { if (pressed && enabled) Haptics.tick(view) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Motion.press(),
        label = "press_scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The signature card of the redesigned UI: a softly-lit surface with a hairline
 * border, a whisper of vertical light and spring press feedback. Replaces
 * flat/boxed list items everywhere.
 *
 * Defaults that keep every card coherent:
 *  - [container] lifts one tone above the page background (surfaceContainerLow)
 *    so cards float instead of dissolving into it;
 *  - [cornerRadius] follows the active style's extraLarge shape (Paper gets its
 *    tight 12dp, Neon its sharp 0dp, Lavender its ballooned 32dp) — pass an
 *    explicit value only for genuine outliers.
 */
@Composable
fun PhCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(pressed) { if (pressed && onClick != null) Haptics.tick(view) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = Motion.press(),
        label = "ph_card_press",
    )
    val shape = cornerRadius?.let { RoundedCornerShape(it) } ?: MaterialTheme.shapes.extraLarge
    val border by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(120),
        label = "ph_card_border",
    )
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = shape,
        color = container,
        shadowElevation = if (pressed) 0.dp else 0.dp,
    ) {
        Box(
            Modifier
                .clip(shape)
                .background(
                    // Remembered: PhCard sits in every list item; allocating the
                    // gradient on each recomposition (image loads, state ticks)
                    // added up across a whole screen of cards.
                    remember(onSurfaceColor) {
                        Brush.verticalGradient(
                            0f to onSurfaceColor.copy(alpha = 0.02f),
                            1f to Color.Transparent,
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            borderColor.copy(alpha = 0.9f + 0.1f * border),
                            borderColor.copy(alpha = 0.35f),
                        )
                    ),
                    shape = shape,
                )
                .let { m ->
                    if (onClick != null) m.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ) else m
                },
        ) {
            content()
        }
    }
}

/** Soft circular icon plate used in cards, empty states and headers. */

/**
 * Staggered entrance: fades + slides an item up as it first composes. Give each
 * list item an [index] and the whole list animates in a gentle cascade.
 */
@Composable
fun StaggeredAppear(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Late items (fling / scrolled-down) skip the entrance entirely.
    if (index >= Motion.STAGGER_MAX_ITEMS) {
        androidx.compose.foundation.layout.Box(modifier) { content() }
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val delay = (index * Motion.STAGGER_STEP_MS).coerceAtMost(Motion.MAX_STAGGER_MS)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220, delayMillis = delay)) +
            slideInVertically(tween(260, delayMillis = delay, easing = FastOutSlowInEasing)) { it / 6 },
    ) {
        content()
    }
}

// ── Skeleton loading ─────────────────────────────────────────────────────────

/** A single shimmering block. Compose your own skeletons from these. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    shape: Shape? = null,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton_shift",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val shapeOrDefault = shape ?: RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .clip(shapeOrDefault)
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = androidx.compose.ui.geometry.Offset(shift * 600f, 0f),
                    end = androidx.compose.ui.geometry.Offset((shift + 1f) * 600f, 250f),
                )
            )
    )
}

/** One skeleton list row that mirrors the redesigned card layout. */
@Composable
private fun SkeletonCardRow(modifier: Modifier = Modifier) {
    PhCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(44.dp), shape = CircleShape)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(Modifier.fillMaxWidth(0.55f).height(15.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.85f).height(11.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.35f).height(11.dp))
            }
        }
    }
}

/**
 * Full-screen skeleton list — the default "loading" look of the app.
 * [horizontalPadding] and [spacing] should mirror the real list's layout so
 * the skeleton occupies exactly the same width/gaps as the loaded content.
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 8,
    topPadding: Dp = 8.dp,
    horizontalPadding: Dp = 16.dp,
    spacing: Dp = 8.dp,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = topPadding, start = horizontalPadding, end = horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        repeat(rows) { SkeletonCardRow() }
    }
}

/**
 * Skeleton shaped like a code document — ragged line widths, tight leading.
 * The loading state for every file/patch viewer, so content "takes the
 * shape" of code instead of a spinner (same principle as SkeletonList).
 */
@Composable
fun SkeletonCodeLines(
    modifier: Modifier = Modifier,
    lines: Int = 14,
) {
    val widths = listOf(
        0.35f, 0.62f, 0.80f, 0.48f, 0.90f, 0.42f, 0.72f, 0.86f,
        0.38f, 0.66f, 0.76f, 0.52f, 0.92f, 0.50f,
    )
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        repeat(lines.coerceAtMost(widths.size)) { i ->
            SkeletonBox(Modifier.fillMaxWidth(widths[i]).height(12.dp), cornerRadius = 4.dp)
        }
    }
}

// ── Small functional atoms ───────────────────────────────────────────────────

/** Section header with an accent tick — consistent section titles app-wide. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/**
 * Redesigned empty state: icon on a soft plate, scale-in, friendly copy.
 */
@Composable
fun EmptyStateV2(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "empty_scale",
    )
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = scale
            }
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/**
 * [rememberScrollState] with a belt-and-suspenders restore for the
 * navigate-away-and-back case: mirrors the live scroll position into
 * [rememberSaveable] ints and re-applies via [ScrollState.scrollTo] once
 * [contentReady] turns true — covers the case where the built-in restore was
 * clamped to the top because the container was briefly empty (data still
 * loading) at restore time. When the built-in restore already worked, the
 * re-apply is a no-op.
 */
@Composable
fun rememberRestorableScrollState(contentReady: Boolean): ScrollState {
    val scrollState = rememberScrollState()
    var savedValue by rememberSaveable { mutableIntStateOf(0) }
    // True while we're still pinning the position back to [savedValue]. Plain
    // remember: a fresh return session restarts the window.
    var restoring by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Restore FIRST — declared before the mirror so the same-frame effect
    // order can't overwrite the target with the freshly restored (possibly
    // clamped) state.
    LaunchedEffect(contentReady) {
        if (contentReady && savedValue > 0 && scrollState.value < savedValue) {
            restoring = true
            scrollState.scrollTo(savedValue.coerceAtMost(scrollState.maxValue))
        }
    }
    // Mirror the live position — but never while re-applying it, or the
    // intermediate clamped offsets would overwrite the restore target.
    LaunchedEffect(scrollState.value) {
        if (!restoring) savedValue = scrollState.value
    }
    if (restoring) {
        // Content height keeps growing after the restore (markdown parse,
        // image loads, readme fetch). Re-pin on every growth step until the
        // saved offset is reachable, then hand control back.
        LaunchedEffect(scrollState.maxValue) {
            if (scrollState.value < savedValue) {
                scrollState.scrollTo(savedValue.coerceAtMost(scrollState.maxValue))
            }
            if (scrollState.maxValue >= savedValue) restoring = false
        }
        // Watchdog: content that never reaches the target (data trimmed on
        // refresh) must not block position mirroring forever.
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3000)
            restoring = false
        }
        // A user drag cancels the pin immediately.
        LaunchedEffect(scrollState.interactionSource) {
            scrollState.interactionSource.interactions.collect { interaction ->
                if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) {
                    restoring = false
                }
            }
        }
    }
    return scrollState
}

/**
 * [rememberLazyListState] with a belt-and-suspenders restore for the
 * navigate-away-and-back case: the built-in saveable state can be clamped to
 * the top when the list is (briefly) empty at restore time — e.g. a cold
 * screen whose data is still arriving. The index/offset are mirrored into
 * plain [rememberSaveable] ints and re-applied via [LazyListState.scrollToItem]
 * once [contentReady] turns true, so the scroll position survives even that
 * window. When the built-in restore already worked, the re-apply is a no-op.
 */
@Composable
fun rememberRestorableListState(contentReady: Boolean): LazyListState {
    val listState = rememberLazyListState()
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }
    // Same growth-tracking restore as [rememberRestorableScrollState]: the
    // first re-apply can still clamp while late content (markdown, images,
    // readme) inflates item heights, so keep re-pinning until reachable.
    var restoring by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Restore FIRST (before the mirror) — same same-frame ordering argument
    // as [rememberRestorableScrollState].
    LaunchedEffect(contentReady) {
        if (contentReady && savedIndex > 0 && listState.firstVisibleItemIndex < savedIndex) {
            restoring = true
            listState.scrollToItem(savedIndex, savedOffset)
        }
    }
    // Mirror the live position continuously — never while re-applying it.
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (!restoring) {
            savedIndex = listState.firstVisibleItemIndex
            savedOffset = listState.firstVisibleItemScrollOffset
        }
    }
    if (restoring) {
        // Watchdog: content that never reaches the target (data trimmed on
        // refresh) must not block position mirroring forever.
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3000)
            restoring = false
        }
        LaunchedEffect(listState.layoutInfo.totalItemsCount) {
            val total = listState.layoutInfo.totalItemsCount
            if (total == 0) return@LaunchedEffect
            if (total > savedIndex) {
                listState.scrollToItem(savedIndex, savedOffset)
                restoring = false
            } else {
                // Items still streaming in (comments, diffs) — hold at the
                // deepest reached position until the target index exists.
                listState.scrollToItem(total - 1)
            }
        }
        // A user drag cancels the pin immediately.
        LaunchedEffect(listState.interactionSource) {
            listState.interactionSource.interactions.collect { interaction ->
                if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) {
                    restoring = false
                }
            }
        }
    }
    return listState
}

/** Grid twin of [rememberRestorableListState] for [LazyVerticalGrid] screens. */
@Composable
fun rememberRestorableGridState(contentReady: Boolean): LazyGridState {
    val gridState = rememberLazyGridState()
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedOffset by rememberSaveable { mutableIntStateOf(0) }
    var restoring by remember { androidx.compose.runtime.mutableStateOf(false) }
    // Restore FIRST (before the mirror) — same same-frame ordering argument
    // as [rememberRestorableScrollState].
    LaunchedEffect(contentReady) {
        if (contentReady && savedIndex > 0 && gridState.firstVisibleItemIndex < savedIndex) {
            restoring = true
            gridState.scrollToItem(savedIndex, savedOffset)
        }
    }
    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        if (!restoring) {
            savedIndex = gridState.firstVisibleItemIndex
            savedOffset = gridState.firstVisibleItemScrollOffset
        }
    }
    if (restoring) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3000)
            restoring = false
        }
        LaunchedEffect(gridState.layoutInfo.totalItemsCount) {
            val total = gridState.layoutInfo.totalItemsCount
            if (total == 0) return@LaunchedEffect
            if (total > savedIndex) {
                gridState.scrollToItem(savedIndex, savedOffset)
                restoring = false
            } else {
                gridState.scrollToItem(total - 1)
            }
        }
        LaunchedEffect(gridState.interactionSource) {
            gridState.interactionSource.interactions.collect { interaction ->
                if (interaction is androidx.compose.foundation.interaction.DragInteraction.Start) {
                    restoring = false
                }
            }
        }
    }
    return gridState
}
