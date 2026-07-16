package com.pockethub.ui.repo

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.text.DateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    onNavigateToIssue: (Int) -> Unit,
    onNavigateToCreateIssue: (String, String) -> Unit = { _, _ -> },
    onNavigateToRepo: (String, String) -> Unit = { _, _ -> },
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToSearch: (String) -> Unit = {},
    onBack: () -> Unit,
    vm: RepoDetailViewModel = hiltViewModel(),
) {
    val repoData by vm.repo.collectAsState()
    val issues by vm.issues.collectAsState()
    val pulls by vm.pulls.collectAsState()
    val releases by vm.releases.collectAsState()
    val workflowRuns by vm.workflowRuns.collectAsState()
    val readme by vm.readme.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isStarred by vm.isStarred.collectAsState()
    val isForking by vm.isForking.collectAsState()
    val forkMessage by vm.forkMessage.collectAsState()
    val error by vm.error.collectAsState()
    val tab by vm.currentTab.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showForkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(owner, repo) { vm.loadRepo(owner, repo) }
    LaunchedEffect(owner, repo, tab) {
        if (tab == RepoTab.ISSUES) vm.loadIssues(owner, repo, state = "open")
        if (tab == RepoTab.PRS) vm.loadPulls(owner, repo, state = "open")
        if (tab == RepoTab.RELEASES) vm.loadReleases(owner, repo)
        if (tab == RepoTab.WORKFLOWS) vm.loadWorkflowRuns(owner, repo)
    }
    LaunchedEffect(forkMessage) {
        forkMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearForkMessage()
        }
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = { showForkDialog = true }, enabled = !isForking) {
                        Icon(
                            Icons.Outlined.ForkRight,
                            contentDescription = stringResource(R.string.action_fork),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.toggleStar(owner, repo) }) {
                        Icon(
                            if (isStarred) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isStarred) stringResource(R.string.cd_unstar) else stringResource(R.string.cd_star),
                            tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            when (tab) {
                RepoTab.OVERVIEW -> FloatingActionButton(
                    onClick = {
                        val url = repoData?.htmlUrl ?: "https://github.com/$owner/$repo"
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(intent, context.getString(R.string.action_share)),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share))
                }
                RepoTab.ISSUES -> FloatingActionButton(
                    onClick = { onNavigateToCreateIssue(owner, repo) },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_new_issue))
                }
                else -> {}
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Stats row
            repoData?.let { data -> StatsRow(data, onNavigateToUser = onNavigateToUser) }

            val tabs = RepoTab.entries
            ScrollableTabRow(selectedTabIndex = tabs.indexOf(tab), edgePadding = 0.dp) {
                tabs.forEach { current ->
                    val label = when (current) {
                        RepoTab.OVERVIEW -> stringResource(R.string.tab_overview)
                        RepoTab.CODE -> stringResource(R.string.tab_code)
                        RepoTab.ISSUES -> stringResource(R.string.tab_issues)
                        RepoTab.PRS -> stringResource(R.string.tab_prs)
                        RepoTab.RELEASES -> stringResource(R.string.tab_releases)
                        RepoTab.COMMITS -> stringResource(R.string.tab_commits)
                        RepoTab.WORKFLOWS -> stringResource(R.string.tab_workflows)
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
                RepoTab.OVERVIEW -> OverviewTab(
                    owner,
                    repo,
                    repoData,
                    readme,
                    isLoading,
                    onTopicClick = { topic -> onNavigateToSearch(topic) },
                    onLinkClick = rememberMarkdownLinkHandler(owner, repo, onNavigateToRepo, onNavigateToUser, onNavigateToIssue),
                )
                RepoTab.CODE -> CodeTab(owner, repo)
                RepoTab.ISSUES -> IssuesTab(issues, onClick = onNavigateToIssue, onNavigateToUser = onNavigateToUser)
                RepoTab.PRS -> PullsTab(pulls, onClick = onNavigateToIssue, onNavigateToUser = onNavigateToUser)
                RepoTab.RELEASES -> ReleasesTab(
                    releases,
                    repoContext = "$owner/$repo",
                    onLinkClick = rememberMarkdownLinkHandler(owner, repo, onNavigateToRepo, onNavigateToUser, onNavigateToIssue),
                    onNavigateToUser = onNavigateToUser,
                )
                RepoTab.COMMITS -> CommitsTab(owner, repo, onNavigateToUser = onNavigateToUser)
                RepoTab.WORKFLOWS -> WorkflowsTab(
                    workflowRuns,
                    onNavigateToUser = onNavigateToUser,
                )
            }
        }
    }

    if (showForkDialog) {
        AlertDialog(
            onDismissRequest = { showForkDialog = false },
            title = { Text(stringResource(R.string.fork_dialog_title)) },
            text = { Text(stringResource(R.string.fork_dialog_message, "$owner/$repo")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForkDialog = false
                        vm.fork(owner, repo)
                    },
                ) { Text(stringResource(R.string.action_fork)) }
            },
            dismissButton = {
                TextButton(onClick = { showForkDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun StatsRow(
    data: Repository,
    onNavigateToUser: (String) -> Unit = {},
) {
    val userClickModifier = Modifier.clickable { onNavigateToUser(data.owner.login) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = data.owner.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(18.dp).clip(CircleShape).then(userClickModifier),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.stats_by, data.owner.login),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = userClickModifier,
        )
        Spacer(Modifier.weight(1f))
        StatChip(star = true, count = data.stars)
        Spacer(Modifier.width(8.dp))
        StatChip(star = false, count = data.forks, label = stringResource(R.string.stat_forks))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.repo_issues_header, data.openIssues), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun OverviewTab(
    owner: String,
    repo: String,
    repoData: Repository?,
    readme: String?,
    isLoading: Boolean,
    onTopicClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit,
) {
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
                            onClick = { onTopicClick(it) },
                            label = { Text(it, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        )
                    }
                }
            }
            HorizontalDivider()
            // README
            Text(stringResource(R.string.readme_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (readme != null) {
                MarkdownText(
                    markdown = readme,
                    modifier = Modifier.fillMaxWidth(),
                    repoContext = "$owner/$repo",
                    onLinkClick = onLinkClick,
                )
            } else if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.readme_loading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    stringResource(R.string.readme_unavailable, "$owner/$repo"),
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
private fun IssuesTab(
    issues: List<Issue>,
    onClick: (Int) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
    if (issues.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_open_issues), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val user = issue.user
                        if (user != null) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clip(CircleShape)
                                    .clickable { onNavigateToUser(user.login) },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                user.login,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onNavigateToUser(user.login) },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            stringResource(R.string.issue_meta, issue.number, issue.comments),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
private fun PullsTab(
    pulls: List<Issue>,
    onClick: (Int) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
    if (pulls.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_open_pull_requests), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val user = pr.user
                        if (user != null) {
                            AsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clip(CircleShape)
                                    .clickable { onNavigateToUser(user.login) },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                user.login,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onNavigateToUser(user.login) },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            stringResource(R.string.issue_meta, pr.number, pr.comments),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleasesTab(
    releases: List<GitHubApi.Release>,
    repoContext: String,
    onLinkClick: (String) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
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
                Text(stringResource(R.string.no_releases_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            label = { Text(stringResource(R.string.pre_release), style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        )
                    }
                }
                release.name?.let { if (it != release.tagName) Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                release.publishedAt?.let {
                    Text(
                        stringResource(R.string.released_at, formatDate(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (release.author != null) {
                    val author = release.author
                    val authorClick = Modifier.clickable { onNavigateToUser(author.login) }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = author.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).clip(CircleShape).then(authorClick),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.by_author, author.login),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = authorClick,
                        )
                    }
                }
                if (!release.body.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    MarkdownText(
                        markdown = release.body.take(2000),
                        modifier = Modifier.fillMaxWidth(),
                        repoContext = repoContext,
                        onLinkClick = onLinkClick,
                    )
                }
                if (release.assets.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    release.assets.forEach { asset ->
                        Text(
                            stringResource(R.string.asset_download, asset.name, humanReadableSize(asset.size), asset.downloadCount),
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

@Composable
private fun WorkflowsTab(
    runs: List<GitHubApi.WorkflowRun>,
    onNavigateToUser: (String) -> Unit = {},
) {
    if (runs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_workflow_runs), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(runs, key = { it.id }) { run ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Status dot
                val statusColor = when (run.conclusion) {
                    "success" -> androidx.compose.ui.graphics.Color(0xFF2EA043)
                    "failure", "cancelled" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        run.name.ifBlank { run.event ?: "" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.workflow_run_status, run.runNumber, run.headBranch ?: "—", run.status ?: "—"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val actor = run.actor
                        if (actor != null) {
                            AsyncImage(
                                model = actor.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clip(CircleShape)
                                    .clickable { onNavigateToUser(actor.login) },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                actor.login,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onNavigateToUser(actor.login) },
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        run.createdAt?.let {
                            Text(
                                formatDate(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun rememberMarkdownLinkHandler(
    owner: String,
    repo: String,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit,
    onNavigateToIssue: (Int) -> Unit,
): (String) -> Unit {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    return link@{ url ->
        // Issues in current repo
        Regex("^https://github\\.com/[^/]+/[^/]+/issues/(\\d+)$").matchEntire(url)?.let {
            it.groupValues[1].toIntOrNull()?.let { n -> onNavigateToIssue(n) }
            return@link
        }
        // Repo URLs
        Regex("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/?.*$").matchEntire(url)?.let {
            onNavigateToRepo(it.groupValues[1], it.groupValues[2])
            return@link
        }
        // User/profile URLs
        Regex("^https://github\\.com/([A-Za-z0-9_.-]+)$").matchEntire(url)?.let {
            onNavigateToUser(it.groupValues[1])
            return@link
        }
        // External links
        runCatching { uriHandler.openUri(url) }
    }
}

private fun formatDate(s: String): String = try {
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(java.time.OffsetDateTime.parse(s))
} catch (_: Exception) { s.take(10) }
private fun humanReadableSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
