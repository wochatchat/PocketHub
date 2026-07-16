package com.pockethub.ui.repo

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.ui.markdown.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    onBack: () -> Unit,
    vm: RepoDetailViewModel = hiltViewModel(),
) {
    val repoData by vm.repo.collectAsState()
    val issues by vm.issues.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val tab by vm.currentTab.collectAsState()

    LaunchedEffect(owner, repo) { vm.loadRepo(owner, repo) }
    LaunchedEffect(owner, repo, tab) {
        if (tab == RepoTab.ISSUES || tab == RepoTab.PRS)
            vm.loadIssues(owner, repo, state = if (tab == RepoTab.ISSUES) "open" else "open")
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
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Stats row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                repoData?.owner?.avatarUrl?.let {
                    AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(16.dp).clip(CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                Text("by ${repoData?.owner?.login ?: owner}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Outlined.Star, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" ${repoData?.stars ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text("Forks ${repoData?.forks ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text("Issues ${repoData?.openIssues ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val tabs = RepoTab.entries
            ScrollableTabRow(selectedTabIndex = tabs.indexOf(tab), edgePadding = 0.dp) {
                tabs.forEach { current ->
                    val label = when (current) {
                        RepoTab.OVERVIEW -> "Overview"
                        RepoTab.CODE     -> "Code"
                        RepoTab.ISSUES   -> "Issues"
                        RepoTab.PRS      -> "PRs"
                        RepoTab.RELEASES -> "Releases"
                        RepoTab.ACTIONS  -> "Actions"
                        RepoTab.WIKI     -> "Wiki"
                        RepoTab.PROJECTS -> "Projects"
                    }
                    Tab(
                        selected = tab == current,
                        onClick = { vm.currentTab.value = current },
                        text = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            when (tab) {
                RepoTab.OVERVIEW -> OverviewTab(owner, repo, repoData, isLoading)
                RepoTab.CODE -> Text("File browser (V1)", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                RepoTab.ISSUES -> IssuesTab(issues) { number -> /* navigate to issue detail */ }
                RepoTab.PRS -> IssuesTab(issues.filter { it.pullRequest != null }) { number -> /* navigate to PR detail */ }
                RepoTab.RELEASES -> PlaceholderTab("Releases")
                RepoTab.ACTIONS -> PlaceholderTab("Actions")
                RepoTab.WIKI -> PlaceholderTab("Wiki")
                RepoTab.PROJECTS -> PlaceholderTab("Projects")
            }
        }
    }
}

@Composable
private fun OverviewTab(owner: String, repo: String, repoData: Repository?, isLoading: Boolean) {
    if (isLoading && repoData == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    repoData?.let { data ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!data.description.isNullOrBlank()) {
                Text(data.description!!, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            if (!data.homepage.isNullOrBlank()) {
                Text(data.homepage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            data.topics.forEach {
                Text("#$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            // README
            Text("README", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            // TODO: fetch and render actual README content
            MarkdownText(
                markdown = "Loading README for **$owner/$repo**…",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IssuesTab(issues: List<Issue>, onClick: (Int) -> Unit) {
    if (issues.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No issues", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(issues, key = { it.id }) { issue ->
            Row(Modifier.fillMaxWidth().clickable { onClick(issue.number) }.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.size(8.dp).clip(CircleShape))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(issue.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("#${issue.number} opened by ${issue.user?.login ?: "unknown"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlaceholderTab(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Coming soon", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
