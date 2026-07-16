package com.pockethub.ui.repo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.pockethub.data.remote.GitHubApi
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.ui.markdown.MarkdownText
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    onNavigateToIssue: (Int) -> Unit,
    onBack: () -> Unit,
    vm: RepoDetailViewModel = hiltViewModel(),
) {
    val repoData by vm.repo.collectAsState()
    val issues by vm.issues.collectAsState()
    val pulls by vm.pulls.collectAsState()
    val releases by vm.releases.collectAsState()
    val readme by vm.readme.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isStarred by vm.isStarred.collectAsState()
    val error by vm.error.collectAsState()
    val tab by vm.currentTab.collectAsState()

    LaunchedEffect(owner, repo) { vm.loadRepo(owner, repo) }
    LaunchedEffect(owner, repo, tab) {
        if (tab == RepoTab.ISSUES) vm.loadIssues(owner, repo, state = "open")
        if (tab == RepoTab.PRS) vm.loadPulls(owner, repo, state = "open")
        if (tab == RepoTab.RELEASES) vm.loadReleases(owner, repo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "$owner/$repo",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { vm.toggleStar(owner, repo) }) {
                        Icon(
                            if (isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isStarred) "Unstar" else "Star",
                            tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Stats row
            repoData?.let { data -> StatsRow(data) }

            val tabs = RepoTab.entries
            ScrollableTabRow(selectedTabIndex = tabs.indexOf(tab), edgePadding = 0.dp) {
                tabs.forEach { current ->
                    val label = when (current) {
                        RepoTab.OVERVIEW -> "Overview"
                        RepoTab.CODE -> "Code"
                        RepoTab.ISSUES -> "Issues"
                        RepoTab.PRS -> "PRs"
                        RepoTab.RELEASES -> "Releases"
                    }
                    Tab(
                        selected = tab == current,
                        onClick = { vm.currentTab.value = current },
                        text = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            // Inline error banner shown across all tabs except Overview (which has its own empty state).
            if (error != null && tab != RepoTab.OVERVIEW) {
                Text(
                    text = error!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            when (tab) {
                RepoTab.OVERVIEW -> OverviewTab(owner, repo, repoData, readme, isLoading)
                RepoTab.CODE -> CodeTab(owner, repo)
                RepoTab.ISSUES -> IssuesTab(issues, onClick = onNavigateToIssue)
                RepoTab.PRS -> PullsTab(pulls, onClick = onNavigateToIssue)
                RepoTab.RELEASES -> ReleasesTab(releases)
            }
        }
    }
}

@Composable
private fun StatsRow(data: Repository) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = data.owner.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(18.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "by ${data.owner.login}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        StatChip(star = true, count = data.stars)
        Spacer(Modifier.width(8.dp))
        StatChip(star = false, count = data.forks, label = "forks")
        Spacer(Modifier.width(8.dp))
        Text("${data.openIssues} issues", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatChip(star: Boolean, count: Int, label: String = "") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (star) Icons.Outlined.Star else Icons.AutoMirrored.Outlined.ArrowBack,
            null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            if (label.isEmpty()) "$count" else "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(owner: String, repo: String, repoData: Repository?, readme: String?, isLoading: Boolean) {
    if (isLoading && repoData == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    repoData?.let { data ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!data.description.isNullOrBlank()) {
                Text(data.description, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            if (!data.homepage.isNullOrBlank()) {
                Text(
                    data.homepage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (data.topics.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.topics.forEach {
                        AssistChip(
                            onClick = {},
                            label = { Text(it, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                }
            }
            HorizontalDivider()
            // README
            Text("README", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (readme != null) {
                MarkdownText(markdown = readme, modifier = Modifier.fillMaxWidth())
            } else if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading README…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    "No README available for $owner/$repo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssuesTab(issues: List<Issue>, onClick: (Int) -> Unit) {
    if (issues.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No open issues", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(issues, key = { it.id }) { issue ->
            Row(
                Modifier.fillMaxWidth().clickable { onClick(issue.number) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // State indicator dot — green for open, purple for closed
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .then(Modifier.padding(0.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        issue.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "#${issue.number} opened by ${issue.user?.login ?: "unknown"} · ${issue.comments} comments",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (issue.labels.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            issue.labels.take(5).forEach { label ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(label.name, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PullsTab(pulls: List<Issue>, onClick: (Int) -> Unit) {
    if (pulls.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No open pull requests", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(pulls, key = { it.id }) { pr ->
            Row(
                Modifier.fillMaxWidth().clickable { onClick(pr.number) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Outlined.Campaign, null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pr.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "#${pr.number} opened by ${pr.user?.login ?: "unknown"} · ${pr.comments} comments",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleasesTab(releases: List<GitHubApi.Release>) {
    if (releases.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Campaign,
                    null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("No releases yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(releases, key = { it.id }) { release ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(release.tagName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (release.prerelease) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text("pre-release", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        )
                    }
                }
                release.name?.let { if (it != release.tagName) Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                release.publishedAt?.let {
                    Text(
                        "Released ${formatDate(it)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                release.author?.let { author ->
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = author.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("@${author.login}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!release.body.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    MarkdownText(
                        markdown = release.body.take(2000),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (release.assets.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    release.assets.forEach { asset ->
                        Text(
                            "⬇ ${asset.name} (${humanReadableSize(asset.size)}, ${asset.downloadCount} downloads)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
private fun formatDate(s: String): String = try { dateFmt.format(java.time.OffsetDateTime.parse(s)) } catch (_: Exception) { s.take(10) }
private fun humanReadableSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
