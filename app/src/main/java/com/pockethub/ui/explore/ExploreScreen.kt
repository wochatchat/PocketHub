package com.pockethub.ui.explore

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
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.model.FeedEvent
import com.pockethub.data.model.Repository

/** Trending language filter chips. */
private val LANGUAGES = listOf("All", "Kotlin", "TypeScript", "Python", "Rust", "Go", "Swift", "Java", "C++")
private val TIME_RANGES = listOf("Daily", "Weekly", "Monthly")

@Composable
fun ExploreScreen(
    modifier: Modifier = Modifier,
    onNavigateToRepo: (String, String) -> Unit,
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

    // Bring up trending data on first composition; later filter changes are driven
    // by the chips via vm.setTrendingFilters(...).
    LaunchedEffect(Unit) { vm.load() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Section switcher (Trending / Featured / Following)
        item {
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                val sections = listOf(
                    ExploreSection.TRENDING to "Trending",
                    ExploreSection.FEATURED to "Featured",
                    ExploreSection.FOLLOWING to "Following",
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
                                label = { Text(lang, style = MaterialTheme.typography.labelMedium) },
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
                                label = { Text(range, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                repoItems(trending, isLoading, error, { vm.load() }, onNavigateToRepo)
            }

            ExploreSection.FEATURED -> {
                repoItems(featured, isLoading, error, { vm.load() }, onNavigateToRepo)
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
                            Text("Following feed unavailable", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "Sign in to see what the people you follow are up to.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                } else if (feed.isEmpty()) {
                    item { EmptyState("Your feed is quiet", "Activity from people you follow will appear here.") }
                } else {
                    items(feed, key = { it.id }) { ev -> FeedEventCard(ev, onNavigateToRepo = onNavigateToRepo) }
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
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        Text("Couldn't load", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 4)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/** LazyColumn section listing a [Repository] collection with loading / error / empty states. */
private fun androidx.compose.foundation.lazy.LazyListScope.repoItems(
    repos: List<Repository>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onNavigateToRepo: (String, String) -> Unit,
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
        repos.isEmpty() && !isLoading -> item { EmptyState("No repositories found", "Try a different language or time window.") }
        else -> {
            items(repos, key = { it.id }) { repo ->
                TrendingRepoCard(repo = repo, onClick = { onNavigateToRepo(repo.owner.login, repo.name) })
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
) {
    val repoName = ev.repo?.name
    val ownerLogin = repoName?.substringBefore('/', "")?.ifEmpty { null }
    val repoShort = repoName?.substringAfter('/', "")?.ifEmpty { null } ?: repoName

    val (verb, secondary) = describe(ev)

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
                        modifier = Modifier.size(20.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = ev.actor?.displayLogin ?: ev.actor?.login ?: "someone",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
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
                Text(formatTimeAgo(ts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun describe(ev: FeedEvent): Pair<String, String> {
    return when (ev.type) {
        "PushEvent" -> {
            val count = ev.payload?.size ?: ev.payload?.commits?.size ?: 0
            val cMsgs = ev.payload?.commits?.mapNotNull { it.message }
                ?.take(3)?.joinToString("\n") { "· " + it.substringBefore("\n").take(80) }
                .orEmpty()
            "pushed $count commit${if (count == 1) "" else "s"} to" to cMsgs
        }
        "WatchEvent" -> "starred" to ""
        "ForkEvent" -> {
            val f = ev.payload?.forkee?.fullName
            "forked" to (f?.let { "as $it" } ?: "")
        }
        "CreateEvent" -> {
            val kind = ev.payload?.refType ?: "ref"
            val ref = ev.payload?.ref ?: ""
            "created a $kind${if (ref.isNotBlank()) " $ref" else ""} in" to ""
        }
        "DeleteEvent" -> {
            val ref = ev.payload?.ref ?: ""
            "deleted ${ev.payload?.refType ?: "ref"}${if (ref.isNotBlank()) " $ref" else ""} from" to ""
        }
        "PublicEvent" -> "made public" to ""
        "ReleaseEvent" -> "released in" to ""
        else -> {
            val pretty = ev.type.removeSuffix("Event")
                .replace("(?=[A-Z])".toRegex(), " ")
                .trim().lowercase()
                .replaceFirstChar { it.uppercase() }
            "$pretty in" to ""
        }
    }
}

private fun formatTimeAgo(iso: String): String {
    return try {
        val v = iso.trim().replace("Z", "+00:00")
        val ts = java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli()
        val diff = (System.currentTimeMillis() - ts).coerceAtLeast(0)
        val mins = diff / 60_000
        when {
            mins < 1L    -> "just now"
            mins < 60L   -> "${mins}m ago"
            mins < 1440L -> "${mins / 60}h ago"
            else         -> "${mins / 1440}d ago"
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
private fun TrendingRepoCard(
    repo: Repository,
    onClick: () -> Unit,
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
                AsyncImage(
                    model = repo.owner.avatarUrl,
                    contentDescription = repo.owner.login,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = repo.owner.login,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(" / ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (repo.language != null) {
                    LangDot(repo.language)
                    Spacer(Modifier.width(4.dp))
                    Text(repo.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                }
                Icon(Icons.Outlined.Star, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(formatCount(repo.stars), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Outlined.ForkRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(formatCount(repo.forks), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                repo.license?.let { lic ->
                    Spacer(Modifier.width(12.dp))
                    val licName = lic.spdxId?.takeIf { it.isNotBlank() && it != "NOASSERTION" } ?: lic.name
                    if (licName.isNotBlank()) Text(licName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
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
