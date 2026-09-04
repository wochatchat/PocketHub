package com.pockethub.ui.repo

import android.widget.Toast
import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockethub.ui.markdown.RepoTabTarget
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.togetherWith
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    onNavigateToIssue: (Int) -> Unit,
    onNavigateToPR: (Int) -> Unit = { _ -> },
    onNavigateToCommit: (String) -> Unit = { _ -> },
    onNavigateToCreateIssue: (String, String) -> Unit = { _, _ -> },
    onNavigateToRepo: (String, String) -> Unit = { _, _ -> },
    /** 站内链接跳转:仓库指定 tab / 仓库内文件查看。 */
    onNavigateToRepoTab: (String, String, String?) -> Unit = { o, r, tab -> onNavigateToRepo(o, r) },
    onNavigateToFile: (String, String, String, String?) -> Unit = { o, r, _, _ -> onNavigateToRepo(o, r) },
    onNavigateToUser: (String) -> Unit = {},
    // Cross-repo issue/PR links (README 引用其他仓库的 issue 等)。
    // AppNavigation 必须传全局路由;默认降级为同仓库导航。
    onNavigateToIssueFull: (String, String, Int) -> Unit = { o, r, n ->
        if (o == owner && r == repo) onNavigateToIssue(n) else onNavigateToRepo(o, r)
    },
    onNavigateToPRFull: (String, String, Int) -> Unit = { o, r, n ->
        if (o == owner && r == repo) onNavigateToPR(n) else onNavigateToRepo(o, r)
    },
    onNavigateToSearch: (String) -> Unit = {},
    /** 站内跳转携带的目标 tab("code"/"issues"/…),由路由参数决定。 */
    initialTab: String? = null,
    onNavigateToDownloads: (tab: String) -> Unit = { _ -> },
    onNavigateToWorkflowRun: (Long) -> Unit = {},
    onBack: () -> Unit,
    vm: RepoDetailViewModel = hiltViewModel(),
    downloadVm: com.pockethub.ui.download.DownloadViewModel = hiltViewModel(),
    // Shared with CodeTab below: hoisted here so the branch selected in the
    // Code tab is observable even while another tab is on screen.
    codeBrowserVm: CodeBrowserViewModel = hiltViewModel(),
) {
    val repoData by vm.repo.collectAsState()
    val issues by vm.issues.collectAsState()
    val pulls by vm.pulls.collectAsState()
    val releases by vm.releases.collectAsState()
    val workflowRuns by vm.workflowRuns.collectAsState()
    val readme by vm.readme.collectAsState()
    val isLoadingReadme by vm.isLoadingReadme.collectAsState()
    val readmeMissing by vm.readmeMissing.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val isStarred by vm.isStarred.collectAsState()
    val isPinned by vm.isPinned.collectAsState()
    val isForking by vm.isForking.collectAsState()
    val forkMessage by vm.forkMessage.collectAsState()
    val canDelete by vm.canDelete.collectAsState()
    val isDeleting by vm.isDeleting.collectAsState()
    val deleteMessage by vm.deleteMessage.collectAsState()
    val deleteSuccess by vm.deleteSuccess.collectAsState()
    val isTogglingVisibility by vm.isTogglingVisibility.collectAsState()
    val visibilityMessage by vm.visibilityMessage.collectAsState()
    val canManageReleases by vm.canManageReleases.collectAsState()
    val isDeletingRelease by vm.isDeletingRelease.collectAsState()
    val releaseDeleteMessage by vm.releaseDeleteMessage.collectAsState()
    val error by vm.error.collectAsState()
    val tab by vm.currentTab.collectAsState()
    val workflows by vm.workflows.collectAsState()
    val isLoadingWorkflows by vm.isLoadingWorkflows.collectAsState()
    val isLoadingWorkflowRuns by vm.isLoadingWorkflowRuns.collectAsState()
    val workflowFilterId by vm.workflowFilterId.collectAsState()
    val workflowFilterBranch by vm.workflowFilterBranch.collectAsState()
    val commitsRefreshTick by vm.commitsRefreshTick.collectAsState()
    // Branch picked in the Code tab. Falls back to the repo default branch so
    // the Commits tab follows the Code tab's selection; null until loaded.
    val codeBrowserState by codeBrowserVm.state.collectAsState()
    // Derive the live ref from codeBrowserState so Compose recomputes when the
    // branch changes (plain val would only be evaluated once per recomposition).
    val codeBrowserRef = codeBrowserState.ref ?: repoData?.defaultBranch
    val isDispatching by vm.isDispatching.collectAsState()
    val dispatchMessage by vm.dispatchMessage.collectAsState()
    val branches by vm.branches.collectAsState()
    val isLoadingBranches by vm.isLoadingBranches.collectAsState()
    val translatedReadme by vm.translatedReadme.collectAsState()
    val showTranslated by vm.showTranslated.collectAsState()
    val isTranslating by vm.isTranslating.collectAsState()
    val translateTarget by vm.translateTarget.collectAsState()
    val translateMessage by vm.translateMessage.collectAsState()
    val issueStateFilter by vm.issueStateFilter.collectAsState()
    val prStateFilter by vm.prStateFilter.collectAsState()
    val isLoadingPulls by vm.isLoadingPulls.collectAsState()
    val isLoadingMorePulls by vm.isLoadingMorePulls.collectAsState()
    val isLoadingMoreIssues by vm.isLoadingMoreIssues.collectAsState()
    val isLoadingIssues by vm.isLoadingIssues.collectAsState()
    val isLoadingReleases by vm.isLoadingReleases.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showForkDialog by remember { mutableStateOf(false) }
    var showDispatchDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showVisibilityDialog by remember { mutableStateOf(false) }
    var showActionsMenu by remember { mutableStateOf(false) }
    var deleteInput by remember { mutableStateOf("") }

    LaunchedEffect(owner, repo) {
        vm.loadRepo(owner, repo)
        // NOTE: don't clear workflow branch/selections here. loadRepo() already
        // resets them — but only when the owner/repo actually changed. Clearing
        // unconditionally on every re-entry (e.g. returning from a workflow run
        // detail) blanked the branch chip row until loadBranches refetched, which
        // read as the filter chips flashing between one and two rows.
        // Deep-link / in-app link routing: open the repo on the tab the URL
        // asked for (e.g. github.com/o/r/issues → Issues tab).
        initialTab?.let { requested ->
            RepoTabTarget.fromWire(requested)?.let { target ->
                RepoTab.entries.firstOrNull { it.name.equals(target.name, ignoreCase = true) }
                    ?.let { vm.currentTab.value = it }
            }
        }
    }
    // When the Code tab changes branch, mirror it to the workflows tab so the
    // workflow run list & dispatch dialog follow the current branch automatically.
    // CodeBrowserViewModel itself doesn't need resetting here — its ref is scoped
    // to the repo and gets cleared on navigation via the Compose nav graph.
    LaunchedEffect(owner, repo, codeBrowserRef) {
        if (repoData != null) vm.onBranchChanged(owner, repo, codeBrowserRef)
    }
    // Prefetch the data tabs once the repo is in — tab switches then render
    // instantly instead of skeleton-first. Loaders are cache-aware, so this
    // is a no-op for anything already fetched.
    LaunchedEffect(repoData, owner, repo) {
        if (repoData != null) vm.preloadTabs(owner, repo)
    }
    LaunchedEffect(owner, repo, tab) {
        if (tab == RepoTab.ISSUES) vm.loadIssues(owner, repo)
        if (tab == RepoTab.PRS) vm.loadPulls(owner, repo)
        if (tab == RepoTab.RELEASES) vm.loadReleases(owner, repo)
        // Run list intentionally ignores the Code tab branch — show ALL workflow
        // runs regardless of which branch is being browsed. (The dispatch dialog
        // still uses the branch for choosing where to run a workflow.)
        if (tab == RepoTab.WORKFLOWS) {
            vm.loadWorkflowRuns(owner, repo)
            // Chip-row sources: workflow definitions + branch names. Loaded
            // lazily on first tab visit; a no-op when already present.
            if (workflows.isEmpty()) vm.loadWorkflows(owner, repo)
            if (branches.isEmpty()) vm.loadBranches(owner, repo)
        }
    }
    LaunchedEffect(forkMessage) {
        forkMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearForkMessage()
        }
    }

    LaunchedEffect(deleteMessage) {
        deleteMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearDeleteMessage()
        }
    }

    LaunchedEffect(visibilityMessage) {
        visibilityMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearVisibilityMessage()
        }
    }

    LaunchedEffect(releaseDeleteMessage) {
        releaseDeleteMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearReleaseDeleteMessage()
        }
    }

    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            Toast.makeText(context, "Repo deleted", Toast.LENGTH_SHORT).show()
            vm.consumeDeleteSuccess()
            onBack()
        }
    }

    LaunchedEffect(translateMessage) {
        translateMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearTranslateMessage()
        }
    }

    // Load workflow list when the dispatch dialog opens (lazy load). Sync
    // with the current workflow branch (mirrored from the Code tab by default).
    // React to branch changes even while the dialog is open so the user sees the
    // updated workflow list without having to close and reopen the dialog.
    LaunchedEffect(showDispatchDialog, owner, repo, vm.workflowBranch.value) {
        if (showDispatchDialog) {
            val branch = vm.workflowBranch.value ?: repoData?.defaultBranch ?: "main"
            vm.loadWorkflows(owner, repo, branch)
            vm.loadBranches(owner, repo)
        }
    }

    // Surface dispatch results via Snackbar. On success, close the dialog and refresh runs.
    LaunchedEffect(dispatchMessage, isDispatching) {
        if (!isDispatching) {
            dispatchMessage?.let {
                snackbarHostState.showSnackbar(it)
                vm.clearDispatchMessage()
                if (it.startsWith("Triggered")) {
                    showDispatchDialog = false
                    // Refresh run list after a short delay so the newly dispatched run appears.
                    kotlinx.coroutines.delay(2000)
                    if (tab == RepoTab.WORKFLOWS) vm.loadWorkflowRuns(owner, repo)
                }
            }
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
                    val watchState by vm.watchState.collectAsState()
                    IconButton(onClick = { vm.togglePin() }) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) stringResource(R.string.cd_unpin) else stringResource(R.string.cd_pin),
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { showActionsMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_repo_actions))
                        }
                        DropdownMenu(expanded = showActionsMenu, onDismissRequest = { showActionsMenu = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (watchState == WatchState.WATCHING) R.string.cd_unwatch else R.string.cd_watch,
                                        ),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (watchState == WatchState.WATCHING) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff,
                                        null,
                                    )
                                },
                                onClick = { showActionsMenu = false; vm.toggleWatch(owner, repo) },
                                enabled = watchState != WatchState.UNKNOWN,
                            )
                            if (canDelete) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cd_toggle_visibility)) },
                                    leadingIcon = { Icon(if (repoData?.private == true) Icons.Outlined.Lock else Icons.Outlined.LockOpen, null) },
                                    onClick = { showActionsMenu = false; showVisibilityDialog = true },
                                    enabled = !isTogglingVisibility && !isDeleting,
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete_repo_action), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showActionsMenu = false
                                        deleteInput = "$owner/$repo"
                                        showDeleteDialog = true
                                    },
                                    enabled = !isDeleting,
                                )
                            }
                        }
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
                RepoTab.RELEASES -> FloatingActionButton(
                    onClick = { onNavigateToDownloads("done") },
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.cd_open_download))
                }
                RepoTab.WORKFLOWS -> FloatingActionButton(
                    onClick = { showDispatchDialog = true },
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = stringResource(R.string.action_dispatch_workflow))
                }
                else -> {}
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // Stats row — star/fork chips are tappable to toggle star / open fork dialog.
            repoData?.let { data ->
                StatsRow(
                    data,
                    onNavigateToUser = onNavigateToUser,
                    isStarred = isStarred,
                    isForking = isForking,
                    onToggleStar = { vm.toggleStar(owner, repo) },
                    onFork = { showForkDialog = true },
                )
            }

            val tabs = RepoTab.entries
            // Double-tap detection: two taps on the same tab within 400ms open
            // that tab's GitHub web page. The first tap of the pair still
            // performs the normal tab switch, so a double-tap on an inactive
            // tab lands on the tab AND opens the web view.
            var lastTapTab by remember { mutableStateOf<RepoTab?>(null) }
            var lastTapAt by remember { mutableStateOf(0L) }
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
                        onClick = {
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (lastTapTab == current && now - lastTapAt < 400) {
                                lastTapTab = null
                                val url = repoTabWebUrl(
                                    current, owner, repo,
                                    repoData?.defaultBranch,
                                    vm.issueStateFilter.value,
                                    vm.prStateFilter.value,
                                )
                                runCatching {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }
                            } else {
                                lastTapTab = current
                                lastTapAt = now
                                vm.currentTab.value = current
                            }
                        },
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

            com.pockethub.ui.components.RefreshContainer(
                isRefreshing = isRefreshing || isLoading,
                onRefresh = { vm.refreshCurrentTab(owner, repo) },
                modifier = Modifier.weight(1f),
            ) {
            androidx.compose.animation.AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) +
                        androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) {
                            if (forward) it / 8 else -it / 8
                        })
                        .togetherWith(
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))
                        )
                },
                label = "repo_tab_content",
            ) { tab ->
            when (tab) {
                RepoTab.OVERVIEW -> OverviewTab(
                    owner,
                    repo,
                    repoData,
                    readme,
                    isLoading,
                    isLoadingReadme = isLoadingReadme,
                    readmeMissing = readmeMissing,
                    translatedReadme = translatedReadme,
                    showTranslated = showTranslated,
                    isTranslating = isTranslating,
                    translateTarget = translateTarget,
                    onToggleTranslation = { vm.toggleTranslation() },
                    onTopicClick = { topic -> onNavigateToSearch(topic) },
                    onNavigateToRepo = onNavigateToRepo,
                    onLinkClick = rememberMarkdownLinkHandler(owner, repo, onNavigateToRepo, onNavigateToRepoTab, onNavigateToFile, onNavigateToUser, onNavigateToIssue, onNavigateToIssueFull, onNavigateToPRFull, onNavigateToCommit, onNavigateToWorkflowRun, onNavigateToCreateIssue, downloadVm = downloadVm, onNavigateToDownloads = onNavigateToDownloads, onSameRepoTab = { target ->
                        RepoTab.entries.firstOrNull { it.name.equals(target.name, ignoreCase = true) }?.let { vm.currentTab.value = it }
                    }),
                )
                RepoTab.CODE -> CodeTab(
                    owner = owner,
                    repo = repo,
                    defaultBranch = repoData?.defaultBranch,
                    vm = codeBrowserVm,
                    onOpenInBrowser = {
                        val url = "https://github.com/$owner/$repo/tree/${repoData?.defaultBranch ?: "main"}"
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                    },
                    downloadVm = downloadVm,
                    onNavigateToDownloads = onNavigateToDownloads,
                )
                RepoTab.ISSUES -> IssuesTab(
                    issues,
                    stateFilter = issueStateFilter,
                    isLoading = isLoadingIssues,
                    isLoadingMore = isLoadingMoreIssues,
                    onSelectFilter = { filter -> vm.setIssueStateFilter(owner, repo, filter) },
                    onLoadMore = { vm.loadMoreIssues(owner, repo) },
                    onClick = onNavigateToIssue,
                    onNavigateToUser = onNavigateToUser,
                )
                RepoTab.PRS -> PullsTab(
                    pulls,
                    stateFilter = prStateFilter,
                    isLoading = isLoadingPulls,
                    isLoadingMore = isLoadingMorePulls,
                    onSelectFilter = { filter -> vm.setPrStateFilter(owner, repo, filter) },
                    onLoadMore = { vm.loadMorePulls(owner, repo) },
                    onClick = onNavigateToPR,
                    onNavigateToUser = onNavigateToUser,
                )
                RepoTab.RELEASES -> ReleasesTab(
                    releases,
                    repoContext = "$owner/$repo",
                    defaultBranch = repoData?.defaultBranch,
                    canDelete = canManageReleases,
                    isDeletingRelease = isDeletingRelease,
                    isLoading = isLoadingReleases,
                    onLinkClick = rememberMarkdownLinkHandler(owner, repo, onNavigateToRepo, onNavigateToRepoTab, onNavigateToFile, onNavigateToUser, onNavigateToIssue, onNavigateToIssueFull, onNavigateToPRFull, onNavigateToCommit, onNavigateToWorkflowRun, onNavigateToCreateIssue, downloadVm = downloadVm, onNavigateToDownloads = onNavigateToDownloads, onSameRepoTab = { target ->
                        RepoTab.entries.firstOrNull { it.name.equals(target.name, ignoreCase = true) }?.let { vm.currentTab.value = it }
                    }),
                    onNavigateToUser = onNavigateToUser,
                    onDownloadAsset = { asset ->
                        downloadVm.enqueue(
                            com.pockethub.data.download.DownloadManager.EnqueueRequest(
                                url = asset.browserDownloadUrl,
                                fileName = asset.name,
                                contentType = guessAssetMime(asset.name),
                                sizeBytes = asset.size,
                                repoKey = "$owner/$repo",
                                releaseTag = "",
                            )
                        )
                        onNavigateToDownloads("active")
                    },
                    onDeleteRelease = { releaseId -> vm.deleteRelease(owner, repo, releaseId) },
                )
                RepoTab.COMMITS -> CommitsTab(
                    owner = owner,
                    repo = repo,
                    refreshTick = commitsRefreshTick,
                    // Follow the branch selected in the Code tab; null = default.
                    ref = codeBrowserRef,
                    onNavigateToUser = onNavigateToUser,
                    onCommitClick = onNavigateToCommit,
                )
                RepoTab.WORKFLOWS -> WorkflowsTab(
                    workflowRuns,
                    isLoading = isLoadingWorkflowRuns,
                    workflows = workflows,
                    branches = branches,
                    selectedWorkflowId = workflowFilterId,
                    selectedBranch = workflowFilterBranch,
                    onFilterChange = { wfId, branch -> vm.setWorkflowFilter(owner, repo, wfId, branch) },
                    onNavigateToUser = onNavigateToUser,
                    onNavigateToWorkflowRun = onNavigateToWorkflowRun,
                )
            }
            }
            }
        }
    }

    if (showForkDialog) {
        // Pre-fill with the source repo name — GitHub forks default to the same
        // name, and the user can edit it to rename the fork at creation time.
        var forkName by remember { mutableStateOf(repo) }
        AlertDialog(
            onDismissRequest = { if (!isForking) showForkDialog = false },
            title = { Text(stringResource(R.string.fork_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.fork_dialog_message, "$owner/$repo"))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = forkName,
                        onValueChange = { forkName = it },
                        singleLine = true,
                        enabled = !isForking,
                        label = { Text(stringResource(R.string.fork_dialog_name_label)) },
                        isError = forkName.trim().isEmpty(),
                        supportingText = {
                            if (forkName.trim().isEmpty()) {
                                Text(stringResource(R.string.fork_dialog_name_required))
                            }
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForkDialog = false
                        vm.fork(owner, repo, newName = forkName)
                    },
                    enabled = !isForking && forkName.trim().isNotEmpty(),
                ) {
                    if (isForking) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                    } else {
                        Text(stringResource(R.string.action_fork))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForkDialog = false }, enabled = !isForking) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showVisibilityDialog) {
        val goingPrivate = repoData?.private != true
        AlertDialog(
            onDismissRequest = { if (!isTogglingVisibility) showVisibilityDialog = false },
            title = { Text(stringResource(R.string.visibility_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        if (goingPrivate) R.string.visibility_to_private_warning
                        else R.string.visibility_to_public_warning,
                        "$owner/$repo",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVisibilityDialog = false
                        vm.toggleVisibility(owner, repo)
                    },
                    enabled = !isTogglingVisibility,
                ) {
                    Text(
                        if (goingPrivate) stringResource(R.string.visibility_action_private)
                        else stringResource(R.string.visibility_action_public),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showVisibilityDialog = false }, enabled = !isTogglingVisibility) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        val expected = "$owner/$repo"
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_repo_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.delete_repo_warning, expected),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(12.dp))
                    // Verification field — pre-filled with the repo full name so the
                    // user only needs to tap DELETE (no typing required).
                    OutlinedTextField(
                        value = deleteInput,
                        onValueChange = { deleteInput = it },
                        label = { Text(stringResource(R.string.delete_repo_confirm_label, expected)) },
                        singleLine = true,
                        enabled = !isDeleting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        vm.deleteRepository(owner, repo)
                    },
                    enabled = deleteInput.trim() == expected && !isDeleting,
                ) { Text(stringResource(R.string.delete_repo_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeleting) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDispatchDialog) {
        WorkflowDispatchDialog(
            workflows = workflows,
            branches = branches,
            isLoading = isLoadingWorkflows,
            isLoadingBranches = isLoadingBranches,
            defaultBranch = repoData?.defaultBranch,
            isDispatching = isDispatching,
            currentBranch = vm.workflowBranch.value,
            onDismiss = { if (!isDispatching) showDispatchDialog = false },
            onDispatch = { workflowId, ref ->
                vm.dispatchWorkflow(owner, repo, workflowId, ref)
            },
        )
    }
}


@Composable
private fun rememberMarkdownLinkHandler(
    owner: String,
    repo: String,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToRepoTab: (String, String, String?) -> Unit,
    onNavigateToFile: (String, String, String, String?) -> Unit,
    onNavigateToUser: (String) -> Unit,
    onNavigateToIssue: (Int) -> Unit,
    onNavigateToIssueFull: (String, String, Int) -> Unit,
    onNavigateToPRFull: (String, String, Int) -> Unit,
    onNavigateToCommit: (String) -> Unit,
    onNavigateToWorkflowRun: (Long) -> Unit,
    onNavigateToCreateIssue: (String, String) -> Unit,
    downloadVm: com.pockethub.ui.download.DownloadViewModel,
    onNavigateToDownloads: (tab: String) -> Unit,
    /** Same-repo tab links switch tabs in place (no nav churn). */
    onSameRepoTab: (com.pockethub.ui.markdown.RepoTabTarget) -> Unit,
): (String, com.pockethub.ui.markdown.LinkKind) -> Unit {
    // Unified GitHub in-app router — README / release notes links resolve to
    // the right screen (issue vs PR vs commit vs workflow run vs repo file),
    // non-GitHub URLs and marketing pages fall through to the system browser.
    // Same-repo tab/file targets switch tabs / open the viewer in place.
    return com.pockethub.ui.markdown.rememberGitHubLinkHandler(
        com.pockethub.ui.markdown.GitHubLinkNav(
            owner = owner,
            repo = repo,
            onRepo = { o, r, tab ->
                val target = tab?.let { RepoTabTarget.fromWire(it) }
                if (o == owner && r == repo && target != null) {
                    onSameRepoTab(target)
                } else {
                    onNavigateToRepoTab(o, r, tab)
                }
            },
            onFile = { o, r, path, ref ->
                if (o == owner && r == repo) onNavigateToFile(o, r, path, ref)
                else onNavigateToRepoTab(o, r, null)
            },
            onIssue = onNavigateToIssueFull,
            onPull = onNavigateToPRFull,
            onCommit = { _, _, sha -> onNavigateToCommit(sha) },
            onUser = onNavigateToUser,
            onWorkflowRun = { runId -> onNavigateToWorkflowRun(runId) },
            onCreateIssue = onNavigateToCreateIssue,
            onDownload = { url, fileName ->
                downloadVm.enqueue(
                    com.pockethub.data.download.DownloadManager.EnqueueRequest(
                        url = url,
                        fileName = fileName,
                        contentType = guessAssetMime(fileName),
                        sizeBytes = 0L,
                        repoKey = "$owner/$repo",
                        releaseTag = "",
                    )
                )
                onNavigateToDownloads("active")
            },
        ),
    )
}


internal fun guessAssetMime(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (ext) {
        "zip" -> "application/zip"
        "gz", "tgz" -> "application/gzip"
        "tar" -> "application/x-tar"
        "apk" -> "application/vnd.android.package-archive"
        "txt" -> "text/plain"
        "pdf" -> "application/pdf"
        "json" -> "application/json"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "md" -> "text/markdown"
        else -> "application/octet-stream"
    }
}

/**
 * GitHub web URL for a repo tab, carrying the tab's live filter state so the
 * browser lands on the same view the user is looking at: the Code / Commits
 * tabs follow the current default branch, Issues / PRs carry their open /
 * closed / merged filter as the `q` query param.
 */
internal fun repoTabWebUrl(
    tab: RepoTab,
    owner: String,
    repo: String,
    defaultBranch: String?,
    issueFilter: IssueStateFilter,
    prFilter: PRStateFilter,
): String {
    val base = "https://github.com/$owner/$repo"
    val branch = defaultBranch?.takeIf { it.isNotBlank() } ?: "main"
    return when (tab) {
        RepoTab.OVERVIEW -> base
        RepoTab.CODE -> "$base/tree/$branch"
        RepoTab.ISSUES -> when (issueFilter) {
            IssueStateFilter.OPEN -> "$base/issues?q=is%3Aopen+is%3Aissue"
            IssueStateFilter.CLOSED -> "$base/issues?q=is%3Aclosed+is%3Aissue"
            IssueStateFilter.ALL -> "$base/issues"
        }
        RepoTab.PRS -> when (prFilter) {
            PRStateFilter.OPEN -> "$base/pulls?q=is%3Apr+is%3Aopen"
            PRStateFilter.CLOSED -> "$base/pulls?q=is%3Apr+is%3Aclosed"
            PRStateFilter.MERGED -> "$base/pulls?q=is%3Apr+is%3Amerged"
            PRStateFilter.ALL -> "$base/pulls"
        }
        RepoTab.RELEASES -> "$base/releases"
        RepoTab.COMMITS -> "$base/commits/$branch"
        RepoTab.WORKFLOWS -> "$base/actions"
    }
}
