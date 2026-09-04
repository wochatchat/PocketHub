package com.pockethub.ui.history

import com.pockethub.R

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput

import com.pockethub.ui.components.PhAsyncImage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToRepo: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: HistoryViewModel = hiltViewModel(),
) {
    val history by vm.history.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.browse_history), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { vm.clear() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            com.pockethub.ui.components.EmptyStateV2(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_empty),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                state = com.pockethub.ui.components.rememberRestorableListState(contentReady = history.isNotEmpty()),
                Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(history, key = { "${it.owner}/${it.repo}@${it.visitedAt}" }) { entry ->
                    SwipeDismissHistoryItem(
                        onDelete = { vm.remove(entry.owner, entry.repo) },
                        modifier = Modifier.animateItem(),
                    ) {
                    com.pockethub.ui.components.PhCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToRepo(entry.owner, entry.repo) },
                        
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            // Header: avatar + owner — mirrors RepositoryRow on the repos tab.
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PhAsyncImage(
                                    model = entry.avatarUrl ?: "https://github.com/${entry.owner}.png?size=80",
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp).clip(CircleShape),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = entry.owner,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = entry.repo,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            entry.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    lineHeight = 18.sp,
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Meta: language dot · stars · forks · last visit
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                entry.language?.let { lang ->
                                    val color = com.pockethub.ui.components.parseColorHex(
                                        com.pockethub.ui.components.languageColorHex(lang),
                                    ) ?: MaterialTheme.colorScheme.outline
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                                    Text(
                                        lang,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Star, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(4.dp))
                                    Text(com.pockethub.util.formatCount(entry.stars ?: 0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CallSplit, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(4.dp))
                                    Text(com.pockethub.util.formatCount(entry.forks ?: 0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Outlined.History, null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    formatAgo(entry.visitedAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * Swipe left → a red delete affordance slides out on the right; tapping it
 * removes the entry. Classic reveal pattern: the swipe stays open (no
 * auto-delete) so the tap is always deliberate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDismissHistoryItem(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Custom anchored drag instead of SwipeToDismissBox: the row is HARD-CAPPED
    // at 25% of the screen width, and settling uses a medium spring — lively
    // release, no visible bounce.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val maxOffsetPx = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx() * 0.25f
    }
    var offsetX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    fun settleTo(target: Float) {
        scope.launch {
            androidx.compose.animation.core.Animatable(offsetX).animateTo(
                target,
                androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                ),
            ) { offsetX = value }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // Delete affordance behind the card, right-aligned; reveal intensity
        // (fade + scale) follows the drag distance for a lively feel.
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFD1242F)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val reveal = (-offsetX / maxOffsetPx).coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(end = 20.dp)
                    .graphicsLayer {
                        alpha = 0.3f + 0.7f * reveal
                        scaleX = 0.75f + 0.25f * reveal
                        scaleY = 0.75f + 0.25f * reveal
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = open) {
                        onDelete()
                        open = false
                        settleTo(0f)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.action_delete),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }
        }

        // Foreground card: horizontal drag only, offset hard-capped with a
        // rubber-band feel (resistance grows as you push past the cap).
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(maxOffsetPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val target = if (-offsetX > maxOffsetPx * 0.55f) -maxOffsetPx else 0f
                            open = target != 0f
                            settleTo(target)
                        },
                        onDragCancel = {
                            open = false
                            settleTo(0f)
                        },
                    ) { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: Float ->
                        change.consume()
                        val raw = offsetX + dragAmount
                        offsetX = when {
                            raw > 0f -> 0f                          // no rightward swipe
                            raw < -maxOffsetPx -> -maxOffsetPx - ((-raw - maxOffsetPx) * 0.15f) // rubber band
                            else -> raw
                        }
                    }
                },
        ) {
            content()
        }
    }
}

private fun formatAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}
