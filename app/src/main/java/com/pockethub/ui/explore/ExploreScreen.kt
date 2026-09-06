package com.pockethub.ui.explore

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.data.remote.feed.DiscoverItem
import com.pockethub.data.remote.feed.FeedSourceOption
import com.pockethub.ui.components.EmptyState
import com.pockethub.ui.components.ErrorState
import com.pockethub.ui.components.LoadingFooter

/** Trending language filter chips. */
internal val LANGUAGES = listOf("All", "Kotlin", "TypeScript", "Python", "Rust", "Go", "Swift", "Java", "C++")
internal val TIME_RANGES = listOf("Daily", "Weekly", "Monthly")

/** Komi top charts filter values — kept in sync with FeedSourceConfig. */
internal val KOMI_CATEGORIES = listOf("trending", "new-releases", "most-popular")
internal val KOMI_PLATFORMS = listOf("android", "windows", "macos", "linux")

/** Discovery feed adds an explicit "all platforms" bucket (backend default). */
internal val KOMI_DISCOVER_PLATFORMS = listOf("all") + KOMI_PLATFORMS

@Composable
internal fun komiCategoryLabel(category: String): String = when (category) {
    "new-releases" -> stringResource(R.string.komi_cat_new)
    "most-popular" -> stringResource(R.string.komi_cat_popular)
    else -> stringResource(R.string.komi_cat_trending)
}

@Composable
internal fun komiPlatformLabel(platform: String): String = when (platform) {
        "all" -> stringResource(R.string.trending_language_all)
    "windows" -> stringResource(R.string.komi_platform_windows)
    "macos" -> stringResource(R.string.komi_platform_macos)
    "linux" -> stringResource(R.string.komi_platform_linux)
    else -> stringResource(R.string.komi_platform_android)
}

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
    /**
     * Incremented by the host (HomeScreen) when the user double-taps the
     * Explore bottom-nav tab. We react with a LaunchedEffect to force-fetch
     * the active section through [ExploreViewModel.refresh].
     */
    refreshTrigger: Int = 0,
    vm: ExploreViewModel = hiltViewModel(),
) {
    val section by vm.section.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val trending by vm.trending.collectAsState()
    val featured by vm.featured.collectAsState()
    val feed by vm.feed.collectAsState()
    val feedAvailable by vm.feedAvailable.collectAsState()
    val selectedLang by vm.trendingLang.collectAsState()
    val selectedRange by vm.trendingRange.collectAsState()
    val komiCategory by vm.komiCategory.collectAsState()
    val komiPlatform by vm.komiPlatform.collectAsState()
    val trendingSource by vm.trendingSourceOption.collectAsState()
    val featuredSource by vm.featuredSourceOption.collectAsState()
    val followingSource by vm.followingSourceOption.collectAsState()
    val pinnedRepos by vm.pinnedRepos.collectAsState()

    // Bring up trending data on first composition; later filter changes are driven
    // by the chips via vm.setTrendingFilters(...).
    LaunchedEffect(Unit) { vm.load() }

    // Double-tap-the-tab refresh — HomeScreen increments refreshTrigger, we
    // re-fetch whatever section is active. Ignore the initial 0 (screen just
    // entered composition).
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) vm.refresh()
    }

    val currentSource = when (section) {
        ExploreSection.TRENDING  -> trendingSource
        ExploreSection.FEATURED -> featuredSource
        ExploreSection.FOLLOWING -> followingSource
    }

        com.pockethub.ui.components.RefreshContainer(
            isRefreshing = isLoading,
            onRefresh = { vm.refresh() },
            modifier = modifier,
        ) {
        LazyColumn(
            state = com.pockethub.ui.components.rememberRestorableListState(contentReady = trending.isNotEmpty() || featured.isNotEmpty() || feed.isNotEmpty()),
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

            // Pinned repos — compact horizontal scroller; cards stay small so the
            // section reads as a quick-access strip, not a full list. Only rendered
            // when the user has at least one pin so the empty state doesn't take
            // real estate on first launch.
            if (pinnedRepos.isNotEmpty()) {
                item(key = "pinned") {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Icon(
                                Icons.Outlined.PushPin,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.pinned_repos_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(pinnedRepos, key = { it }) { slug ->
                                PinnedRepoCard(
                                    slug = slug,
                                    onClick = {
                                        val parts = slug.split("/", limit = 2)
                                        if (parts.size == 2) onNavigateToRepo(parts[0], parts[1])
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Source badge — one quiet glyph + name + "change" affordance on the far
// edge. Dev jargon ("API", proxy hosts) stays out of the copy; the details
// live in Feed Sources.
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
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        sourceDisplayName(currentSource),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.feed_source_change),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            when (section) {
                ExploreSection.TRENDING -> {
                    // Filter chips are conditionally rendered based on the
                    // Trending tab's configured source: the official GitHub
                    // source gets language + time-range chips; Komi top charts
                    // gets category + platform chips (same visual language).
                    // Sources that respond to neither drop the surface entirely
                    // rather than misleading the user.
                    if (trendingSource == FeedSourceOption.KOMI_DISCOVER) {
                        // Discovery feed: platform chips only (all / android / …).
                        item {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(KOMI_DISCOVER_PLATFORMS, key = { it }) { platform ->
                                    FilterChip(
                                        selected = komiPlatform == platform,
                                        onClick = { vm.setKomiFilters(komiCategory, platform) },
                                        label = { Text(komiPlatformLabel(platform), style = MaterialTheme.typography.labelMedium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    } else if (trendingSource == FeedSourceOption.KOMI_TOP_CHARTS) {
                        // Category chips
                        item {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(KOMI_CATEGORIES, key = { it }) { category ->
                                    FilterChip(
                                        selected = komiCategory == category,
                                        onClick = { vm.setKomiFilters(category, komiPlatform) },
                                        label = { Text(komiCategoryLabel(category), style = MaterialTheme.typography.labelMedium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                    )
                                }
                            }
                        }
                        // Platform chips
                        item {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(KOMI_PLATFORMS, key = { it }) { platform ->
                                    FilterChip(
                                        selected = komiPlatform == platform,
                                        onClick = { vm.setKomiFilters(komiCategory, platform) },
                                        label = { Text(komiPlatformLabel(platform), style = MaterialTheme.typography.labelMedium) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        ),
                                    )
                                }
                            }
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    } else if (trendingSource.supportsTrendingFilters) {
                        // Language filter chips
                        item {
                            LazyRow(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(LANGUAGES, key = { it }) { lang ->
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
                                items(TIME_RANGES, key = { it }) { range ->
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
                    }
                    repoItems(trending, isLoading, error, isRefreshing = isLoading, onRetry = { vm.load() }, onNavigateToRepo = onNavigateToRepo, onNavigateToUser = onNavigateToUser)
                }

                ExploreSection.FEATURED -> {
                    repoItems(featured, isLoading, error, isRefreshing = isLoading, onRetry = { vm.load() }, onNavigateToRepo = onNavigateToRepo, onNavigateToUser = onNavigateToUser)
                }

                ExploreSection.FOLLOWING -> {
                    if (isLoading && feed.isEmpty()) {
                        item {
                            com.pockethub.ui.components.SkeletonList(Modifier.fillMaxWidth(), rows = 6, topPadding = 8.dp)
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
                        item { EmptyState(stringResource(R.string.feed_empty_title), stringResource(R.string.feed_empty_subtitle), icon = Icons.AutoMirrored.Outlined.Article) }
                    } else {
                        items(feed, key = { it.id }) { ev ->
                            FeedEventCard(
                                ev,
                                onNavigateToRepo = onNavigateToRepo,
                                onNavigateToUser = onNavigateToUser,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    // The following feed has no pagination: any non-empty reload is a
                    // pull-to-refresh, whose indicator is already at the top. A footer
                    // spinner here would duplicate it (double-spinner bug).
                    // if (isLoading && feed.isNotEmpty()) { item { LoadingFooter() } }
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

            // Footer hint for the double-tap-tab refresh affordance.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
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
    FeedSourceOption.NPM_REGISTRY          -> stringResource(R.string.source_name_npm_registry)
    FeedSourceOption.LOBSTERS              -> stringResource(R.string.source_name_lobsters)
    FeedSourceOption.REDDIT_TOP           -> stringResource(R.string.source_name_reddit_top)
    FeedSourceOption.GITHUB_EVENTS        -> stringResource(R.string.source_name_github_events)
    FeedSourceOption.KOMI_TOP_CHARTS      -> stringResource(R.string.source_name_komi)
    FeedSourceOption.KOMI_DISCOVER        -> stringResource(R.string.source_name_komi_feed)
}

/** LazyColumn section listing a [DiscoverItem] collection with loading / error / empty states.
 *  [isRefreshing] is the pull-to-refresh indicator state, tracked separately so a
 *  pull-to-refresh never ALSO shows the list footer spinner (double spinner). */
private fun androidx.compose.foundation.lazy.LazyListScope.repoItems(
    repos: List<DiscoverItem>,
    isLoading: Boolean,
    error: String?,
    isRefreshing: Boolean = false,
    onRetry: () -> Unit,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
    when {
        isLoading && repos.isEmpty() -> item {
            com.pockethub.ui.components.SkeletonList(Modifier.fillMaxWidth(), rows = 7, topPadding = 8.dp)
        }
        error != null && repos.isEmpty() -> item {
            ErrorState(message = error, onRetry = onRetry)
        }
        repos.isEmpty() && !isLoading -> item {
            EmptyState(stringResource(R.string.no_repositories_found), stringResource(R.string.no_discover_items_subtitle), icon = Icons.AutoMirrored.Outlined.Article)
        }
        else -> {
            items(repos, key = { it.id }) { item ->
                DiscoverItemCard(
                    item = item,
                    onClick = { onNavigateToRepo(item.owner, item.repo) },
                    onNavigateToUser = onNavigateToUser,
                    modifier = Modifier.animateItem(),
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    // Footer spinner is for *paging* only. During pull-to-refresh the pull
    // indicator at the top is already visible — showing this too was the
    // "two spinners at once" bug.
    if (isLoading && repos.isNotEmpty() && !isRefreshing) { item { LoadingFooter() } }
}
