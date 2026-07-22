package com.pockethub.ui.explore

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.model.FeedEvent
import com.pockethub.data.remote.feed.CommunitySignal
import com.pockethub.data.remote.feed.DiscoverItem
import com.pockethub.data.remote.feed.FeedSourceOption

/** Trending language filter chips. */
private val LANGUAGES = listOf("All", "Kotlin", "TypeScript", "Python", "Rust", "Go", "Swift", "Java", "C++")
private val TIME_RANGES = listOf("Daily", "Weekly", "Monthly")

@Composable
private fun rangeLabel(range: String): String = when (range) {
    "Weekly"  -> stringResource(R.string.time_range_weekly)
    "Monthly" -> stringResource(R.string.time_range_monthly)
    else      -> stringResource(R.string.time_range_daily)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    modifier: Modifier = Modifier,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToFeedSources: () -> Unit = {},
    vm: ExploreViewModel = hiltViewModel(),
) {
    val section by vm.section.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val error by vm.error.collectAsState()
    val trending by vm.trending.collectAsState()
    val featured by vm.featured.collectAsState()
    val feed by vm.feed.collectAsState()
    val feedAvailable by vm.feedAvailable.collectAsState()
    val selectedLang by vm.trendingLang.collectAsState()
    val selectedRange by vm.trendingRange.collectAsState()
    val trendingSource by vm.trendingSourceOption.collectAsState()
    val featuredSource by vm.featuredSourceOption.collectAsState()
    val followingSource by vm.followingSourceOption.collectAsState()
    val pullState = rememberPullToRefreshState()

    // Bring up trending data on first composition; later filter changes are driven
    // by the chips via vm.setTrendingFilters(...).
    LaunchedEffect(Unit) { vm.load() }

    val currentSource = when (section) {
        ExploreSection.TRENDING  -> trendingSource
        ExploreSection.FEATURED -> featuredSource
        ExploreSection.FOLLOWING -> followingSource
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { vm.refresh() },
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Section switcher (Trending / Featured / Following)
            item {
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    val sections = listOf(
                        ExploreSection.TRENDING to stringResource(R.string.section_trending),
                        ExploreSection.FEATURED to stringResource(R.string.section_featured),
                        ExploreSection.FOLLOWING to stringResource(R.string.section_following),
                    )
                    sections.forEachIndexed { idx, (value, label) ->
                        SegmentedButton(
                            selected = section == value,
                            onClick = { vm.switchSection(value) },
                            shape = SegmentedButtonDefaults.itemShape(idx, sections.size),
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (value) {
                                        ExploreSection.TRENDING  -> Icons.AutoMirrored.Outlined.TrendingUp
                                        ExploreSection.FEATURED  -> Icons.Outlined.PushPin
                                        ExploreSection.FOLLOWING -> Icons.Outlined.RssFeed
                                    }
                                    Icon(icon, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(label, style = MaterialTheme.typography.labelLarge)
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Wall-of-text source badge — tells you what is powering the current tab.
            // Tap to drill into the feed-source settings screen.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clickable { onNavigateToFeedSources() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Public,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.feed_source_label, sourceDisplayName(currentSource)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.feed_source_change),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            when (section) {
                ExploreSection.TRENDING -> {
                    // Language filter chips
                    item {
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(LANGUAGES) { lang ->
                                FilterChip(
                                    selected = selectedLang == lang,
                                    onClick = { vm.setTrendingFilters(lang, selectedRange) },
                                    label = { Text(if (lang == "All") stringResource(R.string.trending_language_all) else lang, style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                )
                            }
                        }
                    }
                    // Time range chips
                    item {
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(TIME_RANGES) { range ->
                                FilterChip(
                                    selected = selectedRange == range,
                                    onClick = { vm.setTrendingFilters(selectedLang, range) },
                                    label = { Text(rangeLabel(range), style = MaterialTheme.typography.labelMedium) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    repoItems(trending, isLoading, error, { vm.load() }, onNavigateToRepo, onNavigateToUser)
                }

                ExploreSection.FEATURED -> {
                    repoItems(featured, isLoading, error, { vm.load() }, onNavigateToRepo, onNavigateToUser)
                }

                ExploreSection.FOLLOWING -> {
                    if (isLoading && feed.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (!feedAvailable) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Outlined.Group, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                                Text(stringResource(R.string.following_feed_unavailable_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    stringResource(R.string.following_feed_unavailable_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else if (error != null && feed.isEmpty()) {
                        item { ErrorState(message = error ?: "", onRetry = { vm.load() }) }
                    } else if (feed.isEmpty()) {
                        item { EmptyState(stringResource(R.string.feed_empty_title), stringResource(R.string.feed_empty_subtitle)) }
                    } else {
                        items(feed, key = { it.id }) { ev -> FeedEventCard(ev, onNavigateToRepo = onNavigateToRepo, onNavigateToUser = onNavigateToUser) }
                    }
                    if (isLoading && feed.isNotEmpty()) {
                        item { LoadingFooter() }
                    }
                }
            }

            // Global error toast at the bottom — preferred over wiping content.
            error?.let {
                item {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Footer hint when pull-to-refresh isn't obvious — small nudge on first load.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.Storage,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.feed_pull_to_refresh_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun sourceDisplayName(source: FeedSourceOption): String = when (source) {
    FeedSourceOption.GITHUB_SEARCH         -> stringResource(R.string.source_name_github_search)
    FeedSourceOption.GITHUB_TRENDING_API   -> stringResource(R.string.source_name_github_trending_api)
    FeedSourceOption.OSS_INSIGHT          -> stringResource(R.string.source_name_oss_insight)
    FeedSourceOption.HACKER_NEWS_SHOWHN   -> stringResource(R.string.source_name_hn_showhn)
    FeedSourceOption.REDDIT_TOP           -> stringResource(R.string.source_name_reddit_top)
    FeedSourceOption.GITHUB_EVENTS        -> stringResource(R.string.source_name_github_events)
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.error_couldnt_load), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 4)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

/** LazyColumn section listing a [DiscoverItem] collection with loading / error / empty states. */
private fun androidx.compose.foundation.lazy.LazyListScope.repoItems(
    repos: List<DiscoverItem>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
    when {
        isLoading && repos.isEmpty() -> item {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        error != null && repos.isEmpty() -> item {
            ErrorState(message = error, onRetry = onRetry)
        }
        repos.isEmpty() && !isLoading -> item {
            EmptyState(stringResource(R.string.no_repositories_found), stringResource(R.string.no_discover_items_subtitle))
        }
        else -> {
            items(repos, key = { it.id }) { item ->
                DiscoverItemCard(
                    item = item,
                    onClick = { onNavigateToRepo(item.owner, item.repo) },
                    onNavigateToUser = onNavigateToUser,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    if (isLoading && repos.isNotEmpty()) { item { LoadingFooter() } }
}

@Composable
private fun FeedEventCard(
    ev: FeedEvent,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit,
) {
    val repoName = ev.repo?.name
    val ownerLogin = repoName?.substringBefore('/', "")?.ifEmpty { null }
    val repoShort = repoName?.substringAfter('/', "")?.ifEmpty { null } ?: repoName

    val (verb, secondary) = describeFeedEvent(ev)

    val base = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    val modifier = if (ownerLogin != null && repoShort != null) {
        base.clickable { onNavigateToRepo(ownerLogin, repoShort) }
    } else base

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ev.actor?.avatarUrl?.let { url ->
                    AsyncImage(
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

@Composable
private fun describeFeedEvent(ev: FeedEvent): Pair<String, String> {
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

private fun formatTimeAgo(resources: android.content.res.Resources, iso: String): String {
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


@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.Article, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun LoadingFooter() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun DiscoverItemCard(
    item: DiscoverItem,
    onClick: () -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatar = item.ownerAvatarUrl
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
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
                    modifier = Modifier.clickable { onNavigateToUser(item.owner) },
                )
                Text(" / ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = item.repo,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
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
                    Icon(Icons.Outlined.ForkRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(formatCount(item.forks), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.topics.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        item.topics.take(2).joinToString(" · ", prefix = ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunitySignalRow(sig: CommunitySignal) {
    val platformLabel = when (sig.platform) {
        CommunitySignal.Platform.HACKER_NEWS -> stringResource(R.string.feed_signal_hn)
        CommunitySignal.Platform.REDDIT     -> stringResource(R.string.feed_signal_reddit, sig.subreddit.orEmpty())
    }
    val line = buildString {
        append(platformLabel)
        if (sig.score > 0) append("  ·  ").append(stringResource(R.string.feed_signal_score, sig.score))
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
private fun LangDot(language: String) {
    val color = languageColorHex(language)?.let { parseColor(it) } ?: MaterialTheme.colorScheme.outline
    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fk".format(n / 1_000.0)
    else -> n.toString()
}

private fun parseColor(hex: String): androidx.compose.ui.graphics.Color {
    val v = hex.removePrefix("#").toLong(16)
    return androidx.compose.ui.graphics.Color(v or 0xFF000000L)
}

/** GitHub's official language colors (octicons.lang-colors). Subset for the filter chips we offer. */
private fun languageColorHex(language: String): String? = when (language.lowercase()) {
    "kotlin" -> "#A97BFF"
    "typescript" -> "#3178C6"
    "python" -> "#3572A5"
    "rust" -> "#DEA584"
    "go" -> "#00ADD8"
    "swift" -> "#F05138"
    "java" -> "#B07219"
    "c++" -> "#F34B7D"
    "c" -> "#555555"
    "c#" -> "#178600"
    "javascript" -> "#F1E05A"
    "html" -> "#E34C26"
    "css" -> "#563D7C"
    "php" -> "#4F5D95"
    "ruby" -> "#701516"
    "dart" -> "#00B4AB"
    "shell" -> "#89E051"
    else -> null
}
