package com.pockethub.ui.explore

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockethub.data.model.FeedEvent
import com.pockethub.data.remote.feed.CommunitySignal
import com.pockethub.data.remote.feed.DiscoverItem
import com.pockethub.ui.components.languageColorHex
import com.pockethub.ui.components.parseColorHex
import androidx.compose.foundation.layout.widthIn
import com.pockethub.ui.components.PhAsyncImage

@Composable
internal fun FeedEventCard(
    ev: FeedEvent,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repoName = ev.repo?.name
    val ownerLogin = repoName?.substringBefore('/', "")?.ifEmpty { null }
    val repoShort = repoName?.substringAfter('/', "")?.ifEmpty { null } ?: repoName

    val (verb, secondary) = describeFeedEvent(ev)

    val base = modifier.fillMaxWidth().padding(horizontal = 16.dp)
    val mod = if (ownerLogin != null && repoShort != null) {
        base.clickable { onNavigateToRepo(ownerLogin, repoShort) }
    } else base

    com.pockethub.ui.components.EnhancedCard(
        modifier = mod,
        onClick = if (ownerLogin != null && repoShort != null) {
            { onNavigateToRepo(ownerLogin, repoShort) }
        } else null,
        elevation = 2.dp,
        gradientIntensity = 0.05f,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ev.actor?.avatarUrl?.let { url ->
                    PhAsyncImage(
                        model = url,
                        contentDescription = ev.actor.login,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable {
                                ev.actor?.login?.let { login -> onNavigateToUser(login) }
                            },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = ev.actor?.displayLogin ?: ev.actor?.login ?: stringResource(R.string.feed_someone),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable {
                        ev.actor?.login?.let { login -> onNavigateToUser(login) }
                    },
                )
                Text(" $verb", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (repoShort != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeveloperMode, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(repoName ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (secondary.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            ev.createdAt?.let { ts ->
                Spacer(Modifier.height(6.dp))
                Text(formatTimeAgo(LocalContext.current.resources, ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Compact pinned-repo card for the horizontal scroller at the top of Explore.
 * Sized so ~2.2 cards fit the content width (the 0.2 peeks from the edge to
 * signal scrollability) while matching the lists' 16dp horizontal padding.
 * Name-only — no icon, no owner line.
 */
@Composable
internal fun PinnedRepoCard(
    slug: String,
    onClick: () -> Unit,
) {
    val repo = slug.substringAfter('/', slug)
    val owner = slug.substringBefore('/', "")
    // Two-plus cards per screen width: avatar + repo name over the owner
    // handle. Sized against the lists' 32dp padding + one 8dp gap.
    val screenW = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val cardWidth = ((screenW - 40) / 2.2f).dp
    Row(
        modifier = Modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhAsyncImage(
            model = "https://github.com/$owner.png?size=80",
            contentDescription = null,
            modifier = Modifier.size(34.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                repo,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                owner,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun describeFeedEvent(ev: FeedEvent): Pair<String, String> {
    return when (ev.type) {
        "PushEvent" -> {
            val count = ev.payload?.size ?: ev.payload?.commits?.size ?: 0
            val cMsgs = ev.payload?.commits?.mapNotNull { it.message }
                ?.take(3)?.joinToString("\n") { "· " + it.substringBefore("\n").take(80) }
                .orEmpty()
            stringResource(R.string.feed_push_verb, count) to cMsgs
        }
        "WatchEvent" -> stringResource(R.string.feed_starred_verb) to ""
        "ForkEvent" -> {
            val f = ev.payload?.forkee?.fullName
            stringResource(R.string.feed_forked_verb) to (f?.let { stringResource(R.string.feed_forked_as, it) } ?: "")
        }
        "CreateEvent" -> {
            when (ev.payload?.refType) {
                "repository" -> stringResource(R.string.feed_created_repository)
                "branch" -> stringResource(R.string.feed_created_branch, ev.payload?.ref ?: "")
                "tag"    -> stringResource(R.string.feed_created_tag, ev.payload?.ref ?: "")
                else     -> stringResource(R.string.feed_created_default, ev.payload?.refType ?: "ref")
            } to ""
        }
        "DeleteEvent" -> {
            when (ev.payload?.refType) {
                "repository" -> stringResource(R.string.feed_deleted_repository)
                "branch" -> stringResource(R.string.feed_deleted_branch, ev.payload?.ref ?: "")
                "tag"    -> stringResource(R.string.feed_deleted_tag, ev.payload?.ref ?: "")
                else     -> stringResource(R.string.feed_deleted_default, ev.payload?.refType ?: "ref")
            } to ""
        }
        "PublicEvent" -> stringResource(R.string.feed_public) to ""
        "ReleaseEvent" -> stringResource(R.string.feed_released) to ""
        else -> {
            val pretty = ev.type.removeSuffix("Event")
                .replace("(?=[A-Z])".toRegex(), " ")
                .trim().lowercase()
                .replaceFirstChar { it.uppercase() }
            stringResource(R.string.feed_unknown, pretty) to ""
        }
    }
}

internal fun formatTimeAgo(resources: android.content.res.Resources, iso: String): String {
    return try {
        val v = iso.trim().replace("Z", "+00:00")
        val ts = java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli()
        val diff = (System.currentTimeMillis() - ts).coerceAtLeast(0)
        val mins = diff / 60_000
        when {
            mins < 1L    -> resources.getString(R.string.time_ago_just_now)
            mins < 60L   -> resources.getString(R.string.time_ago_minutes, mins)
            mins < 1440L -> resources.getString(R.string.time_ago_hours, mins / 60)
            else         -> resources.getString(R.string.time_ago_days, mins / 1440)
        }
    } catch (_: Exception) { iso.take(10) }
}


@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DiscoverItemCard(
    item: DiscoverItem,
    onClick: () -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    com.pockethub.ui.components.EnhancedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick,
        elevation = 2.dp,
        gradientIntensity = 0.06f,
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatar = item.ownerAvatarUrl
                if (!avatar.isNullOrBlank()) {
                    PhAsyncImage(
                        model = avatar,
                        contentDescription = item.owner,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onNavigateToUser(item.owner) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = item.owner,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clickable { onNavigateToUser(item.owner) }
                        .weight(1f, fill = false),
                )
                Text(" / ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = item.repo,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!item.description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Momentum strip — OSS Insight total_score, GitHub Trending API currentPeriodStars.
            if (item.starDelta != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Outlined.TrendingUp,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            R.string.feed_item_star_delta,
                            formatCount(item.starDelta.delta),
                            item.starDelta.periodLabel,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Community signal strip — HN / Reddit only.
            item.communitySignal?.let { sig ->
                Spacer(Modifier.height(6.dp))
                CommunitySignalRow(sig)
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.language != null) {
                    LangDot(item.language)
                    Spacer(Modifier.width(4.dp))
                    Text(item.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                }
                if (item.stars > 0) {
                    Icon(Icons.Outlined.Star, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(formatCount(item.stars), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                }
                if (item.forks > 0) {
                    Icon(Icons.Outlined.CallSplit, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(formatCount(item.forks), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (item.topics.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item.topics.take(4).forEach { topic ->
                        Text(
                            "#$topic",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.topics.size > 4) {
                        Text(
                            "+${item.topics.size - 4}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
}

@Composable
internal fun CommunitySignalRow(sig: CommunitySignal) {
    val platformLabel = when (sig.platform) {
        CommunitySignal.Platform.HACKER_NEWS -> stringResource(R.string.feed_signal_hn)
        CommunitySignal.Platform.REDDIT -> stringResource(R.string.feed_signal_reddit, sig.subreddit.orEmpty())
        CommunitySignal.Platform.NPM -> stringResource(R.string.feed_signal_npm)
        CommunitySignal.Platform.LOBSTERS -> stringResource(R.string.feed_signal_lobsters)
    }
    val line = buildString {
        append(platformLabel)
        if (sig.platform == CommunitySignal.Platform.NPM && sig.score > 0) {
            append("  ·  ").append(stringResource(R.string.feed_signal_downloads, formatCount(sig.score)))
        } else if (sig.score > 0) {
            append("  ·  ").append(stringResource(R.string.feed_signal_score, formatCount(sig.score)))
        }
        if (!sig.author.isNullOrBlank()) append("  ·  ").append(stringResource(R.string.feed_signal_by, sig.author))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Public, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(
            text = line,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tiny colored dot used as the language indicator. Looks up a known color table. */
@Composable
internal fun LangDot(language: String) {
    val color = parseColorHex(languageColorHex(language)) ?: MaterialTheme.colorScheme.outline
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

internal fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

/**
 * Compact card for a pinned repo entry. The slug is "owner/repo"; we split it for
 * visual hierarchy and route to the repo detail screen on tap.
 */
