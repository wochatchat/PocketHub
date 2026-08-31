package com.pockethub.ui.repo

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.InputChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.ui.components.CommentItem
import com.pockethub.ui.markdown.MarkdownText
import com.pockethub.ui.components.PhAsyncImage
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PullRequestDetailScreen(
    owner: String,
    repo: String,
    prNumber: Int,
    onNavigateToRepo: (String, String) -> Unit = { _, _ -> },
    onNavigateToUser: (String) -> Unit = {},
    /** 仓库文件(blob/文档)打开 app 内查看器。 */
    onNavigateToFile: (String, String, String, String?) -> Unit = { o, r, _, _ -> onNavigateToRepo(o, r) },
    /** 仓库指定 tab(issues/pulls/releases/…)。 */
    onNavigateToRepoTab: (String, String, String?) -> Unit = { o, r, _ -> onNavigateToRepo(o, r) },
    /** GitHub 站内链接跨仓库跳转(AppNavigation 传全局路由)。 */
    onNavigateToIssue: (String, String, Int) -> Unit = { _, _, _ -> },
    onNavigateToPR: (String, String, Int) -> Unit = { _, _, _ -> },
    onNavigateToCommit: (String, String, String) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    vm: PullRequestDetailViewModel = hiltViewModel(),
    downloadVm: com.pockethub.ui.download.DownloadViewModel = hiltViewModel(),
) {
    val pr by vm.pr.collectAsState()
    val files by vm.files.collectAsState()
    val reviews by vm.reviews.collectAsState()
    val comments by vm.comments.collectAsState()
    val reviewComments by vm.reviewComments.collectAsState()
    val filesError by vm.filesError.collectAsState()
    val reviewsError by vm.reviewsError.collectAsState()
    val reviewCommentsError by vm.reviewCommentsError.collectAsState()
    val commentsError by vm.commentsError.collectAsState()
    val events by vm.events.collectAsState()
    val eventsError by vm.eventsError.collectAsState()
    val isSendingLineComment by vm.isSendingLineComment.collectAsState()
    val checkRuns by vm.checkRuns.collectAsState()
    val checkSummary by vm.checkSummary.collectAsState()
    val isRefreshingCheckRuns by vm.isLoadingCheckRuns.collectAsState()
    // Thread resolve state (Map<rootCommentId, ThreadInfo>) surfaced for R3.
    val threadState by vm.threadState.collectAsState()
    val busyReviewComments by vm.busyReviewComments.collectAsState()
    val inlineCommentError by vm.inlineCommentError.collectAsState()
    val viewerLogin by vm.currentLogin.collectAsState()
    // Touch these so comment rows re-compose when viewer reactions arrive async.
    @Suppress("unused")
    val currentLogin by vm.currentLogin.collectAsState()
    @Suppress("unused")
    val viewerReactions by vm.viewerReactions.collectAsState()
    @Suppress("unused")
    val busyComments by vm.busyComments.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val isMerging by vm.isMerging.collectAsState()
    val mergeResult by vm.mergeResult.collectAsState()
    val isSendingReview by vm.isSendingReview.collectAsState()
    val reviewResult by vm.reviewResult.collectAsState()
    val isSendingComment by vm.isSendingComment.collectAsState()
    val commentError by vm.commentError.collectAsState()
    val isTogglingState by vm.isTogglingState.collectAsState()
    val actionMessage by vm.actionMessage.collectAsState()
    val reviewerWorking by vm.reviewerWorking.collectAsState()
    val reviewerError by vm.reviewerError.collectAsState()
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showMergeDialog by remember { mutableStateOf(false) }
    var showMergeWarningDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showAddReviewer by remember { mutableStateOf(false) }
    var reviewEvent by remember { mutableStateOf(ReviewEvent.APPROVE) }
    var editingCommentId by remember { mutableStateOf<Long?>(null) }
    var editingBody by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    // Editing / deleting inline (PR review) comments — kept in the outer composable
    // so dialogs can be rendered outside the scrollable column.
    var editingInlineId by remember { mutableStateOf<Long?>(null) }
    var editingInlineBody by remember { mutableStateOf("") }
    var pendingDeleteInlineId by remember { mutableStateOf<Long?>(null) }

    val onLinkClick: (String, com.pockethub.ui.markdown.LinkKind) -> Unit =
        com.pockethub.ui.markdown.rememberGitHubLinkHandler(
            com.pockethub.ui.markdown.GitHubLinkNav(
                owner = owner,
                repo = repo,
                onRepo = onNavigateToRepoTab,
                onFile = onNavigateToFile,
                onIssue = onNavigateToIssue,
                onPull = onNavigateToPR,
                onCommit = onNavigateToCommit,
                onUser = onNavigateToUser,
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
                },
            ),
        )

    // Add reviewers dialog (multi-input via chip list)
    if (showAddReviewer) {
        AddReviewerSheet(
            working = reviewerWorking,
            error = reviewerError,
            onDismiss = { showAddReviewer = false },
            onSubmit = { vm.requestReviewers(owner, repo, prNumber, it) },
        )
    }
    // Merge dialog
    if (showMergeDialog) {
        MergeDialog(
            prNumber = pr?.number ?: 0,
            merging = isMerging,
            onDismiss = { showMergeDialog = false },
            onMerge = { vm.merge(owner, repo, prNumber, it) },
        )
    }
    // Review submit bottom sheet (R1)
    if (showReviewDialog) {
        ReviewSheet(
            reviewEvent = reviewEvent,
            onReviewEventChange = { reviewEvent = it },
            sending = isSendingReview,
            onDismiss = { showReviewDialog = false },
            onSubmit = { apiValue, body -> vm.submitReview(owner, repo, prNumber, apiValue, body) },
        )
    }
    // Merge warning dialog (R5) — reviews requested changes; user taps "merge anyway"
    if (showMergeWarningDialog) {
        MergeWarningDialog(
            changesRequestedCount = reviews.count { it.state == "CHANGES_REQUESTED" },
            onDismiss = { showMergeWarningDialog = false },
            onMergeAnyway = { showMergeWarningDialog = false; showMergeDialog = true },
        )
    }
    // Snackbar for results
    LaunchedEffect(mergeResult) {
        mergeResult?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearMergeResult()
        }
    }
    LaunchedEffect(reviewResult) {
        reviewResult?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearReviewResult()
        }
    }
    LaunchedEffect(commentError) {
        commentError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearCommentError()
        }
    }
    LaunchedEffect(inlineCommentError) {
        inlineCommentError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearInlineCommentError()
        }
    }
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearActionMessage()
        }
    }

    LaunchedEffect(owner, repo, prNumber) {
        vm.loadPullRequest(owner, repo, prNumber)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PR #$prNumber", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        pr?.htmlUrl?.let { url ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.cd_open_in_browser))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val p = pr
            if (p != null && p.state == "open" && !p.merged) {
                FloatingActionButton(
                    onClick = { showMergeDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Outlined.Merge, contentDescription = stringResource(R.string.action_merge), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        },
    ) { padding ->
        if (isLoading && pr == null) {
            com.pockethub.ui.components.SkeletonList(Modifier.padding(padding).fillMaxSize(), rows = 8, topPadding = 8.dp)
            return@Scaffold
        }

        if (pr == null && error != null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.loading_failed), style = MaterialTheme.typography.titleMedium)
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.retry(owner, repo, prNumber) }) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_retry))
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            pr?.let { data ->
                // Title + state badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val stateColor = when {
                        data.merged -> Color(0xFF8250DF) // purple for merged
                        data.state == "open" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    }
                    val stateText = when {
                        data.merged -> stringResource(R.string.pr_state_merged)
                        data.state == "open" -> stringResource(R.string.issue_state_open)
                        else -> stringResource(R.string.issue_state_closed)
                    }
                    Box(
                        Modifier.clip(CircleShape)
                            .background(stateColor.copy(alpha = 0.12f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(stateText, style = MaterialTheme.typography.labelSmall, color = stateColor)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(data.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }

                // Author info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val user = data.user
                    if (user != null) {
                        PhAsyncImage(
                            model = user.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                                .clickable { onNavigateToUser(user.login) },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            user.login,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { onNavigateToUser(user.login) },
                        )
                    }
                    data.createdAt?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.pr_opened_at, formatDate(it)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Close / Reopen PR — mirrors GitHub web's primary affordance above
                // the PR description. Merged PRs cannot be toggled. While the toggle is
                // in flight, show a small spinner in place of the button label.
                if (!data.merged) {
                    val isOpen = data.state == "open"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { vm.togglePrState(owner, repo, prNumber) },
                            enabled = !isTogglingState,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = if (isOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            if (isTogglingState) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(
                                    if (isOpen) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(if (isOpen) R.string.pr_close_action else R.string.pr_reopen_action),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }

                // Branch info: head → base
                if (data.head != null && data.base != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(10.dp),
                    ) {
                        Icon(Icons.Outlined.Comment, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            data.head.ref,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("→", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            data.base.ref,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.pr_files_summary, data.changedFiles, data.additions, data.deletions),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Labels
                if (data.labels.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.labels.take(5).forEach { label ->
                            val bg = runCatching { Color(("FF" + (label.color ?: "888888")).toLong(16)) }.getOrDefault(MaterialTheme.colorScheme.secondaryContainer)
                            val textColor = com.pockethub.ui.components.rememberContrastColor(bg)
                            Text(
                                label.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(bg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Checks summary — one-line banner showing CI status for the PR head SHA.
                ChecksCard(
                    summary = checkSummary,
                    runs = checkRuns,
                    isRefreshing = isRefreshingCheckRuns,
                    onRefresh = { vm.refreshCheckRuns(owner, repo) },
                )

                // Requested reviewers
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.pr_reviewers),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    data.requestedReviewers.forEach { reviewer ->
                        PhAsyncImage(
                            model = reviewer.avatarUrl,
                            contentDescription = reviewer.login,
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                                .clickable { onNavigateToUser(reviewer.login) },
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    if (data.requestedReviewers.isNotEmpty()) {
                        // Inline remove affordance — small chip per reviewer
                        data.requestedReviewers.forEach { reviewer ->
                            InputChip(
                                selected = false,
                                onClick = { vm.removeReviewer(owner, repo, prNumber, reviewer.login) },
                                label = { Text("@${reviewer.login}", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                avatar = { PhAsyncImage(model = reviewer.avatarUrl, contentDescription = null, modifier = Modifier.size(16.dp).clip(CircleShape)) },
                                trailingIcon = {
                                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_remove), modifier = Modifier.size(14.dp))
                                },
                                enabled = !reviewerWorking,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    } else {
                        Text(
                            stringResource(R.string.pr_no_reviewers),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    AssistChip(
                        onClick = { showAddReviewer = true },
                        label = { Text(stringResource(R.string.pr_add_reviewer)) },
                        leadingIcon = { Icon(Icons.Outlined.PersonAdd, null, modifier = Modifier.size(16.dp)) },
                        enabled = !reviewerWorking,
                    )
                }
                reviewerError?.let { err ->
                    Spacer(Modifier.height(4.dp))
                    Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                actionMessage?.takeIf { it.startsWith("Removed") || it.startsWith("Requested") }?.let { msg ->
                    Spacer(Modifier.height(4.dp))
                    Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }

                // Body
                MarkdownText(
                    markdown = data.body ?: stringResource(R.string.no_description),
                    modifier = Modifier.fillMaxWidth(),
                    repoContext = "$owner/$repo",
                    onLinkClick = onLinkClick,
                )

                // ── Files Changed ──
                if (files.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.pr_files_changed, files.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    files.forEach { file ->
                        FileDiffItem(
                            file = file,
                            commitId = pr?.head?.sha,
                            reviewComments = reviewComments,
                            isSendingLineComment = isSendingLineComment,
                            onPostLineComment = { path, commitId, line, body, _ ->
                                vm.postLineComment(path, line, body)
                            },
                            onReply = { rootId, body -> vm.replyInlineComment(rootId, body) },
                            onResolve = { rootId -> vm.resolveThread(rootId) },
                            onUnresolve = { rootId -> vm.unresolveThread(rootId) },
                            onEditInline = { id, body -> editingInlineId = id; editingInlineBody = body },
                            onDeleteInline = { id -> pendingDeleteInlineId = id },
                            threadState = threadState.mapValues { ThreadState(it.value.threadId, it.value.isResolved) },
                            currentLogin = viewerLogin,
                            busyCommentIds = busyReviewComments,
                        )
                    }
                } else if (filesError != null) {
                    // files list is empty AND there's a load error — show retry.
                    // (Empty without error is just an empty PR diff; we don't warn.)
                    HorizontalDivider()
                    SectionError(message = filesError!!, onRetry = { vm.retryFiles() })
                }

                // ── Inline Review Comments (root-level view for comments not
                // tied to a specific file shown above) ──
                if (reviewComments.isEmpty() && reviewCommentsError != null) {
                    HorizontalDivider()
                    SectionError(message = reviewCommentsError!!, onRetry = { vm.retryReviewComments() })
                }

                // ── Reviews ──
                HorizontalDivider()
                // R5 — merge warning if any non-dismissed CHANGES_REQUESTED review exists.
                val changesRequestedCount = reviews.count { it.state == "CHANGES_REQUESTED" && it.state != "DISMISSED" }
                if (changesRequestedCount > 0 && data.state == "open" && !data.merged) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                            .clickable { showMergeWarningDialog = true }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Warning, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.pr_changes_requested_warning, changesRequestedCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(stringResource(R.string.pr_reviews, reviews.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                // Review submit entry (R1) — open PR only. ModalBottomSheet opened on tap.
                if (data.state == "open" && !data.merged) {
                    val currentLogin = vm.currentLogin.collectAsState().value
                    val alreadyReviewedByMe = currentLogin != null &&
                        reviews.any { it.user?.login == currentLogin && it.state in setOf("APPROVED", "CHANGES_REQUESTED", "COMMENTED") }
                    OutlinedButton(
                        onClick = {
                            reviewEvent = if (alreadyReviewedByMe) ReviewEvent.COMMENT else ReviewEvent.APPROVE
                            showReviewDialog = true
                        },
                        enabled = !isSendingReview,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isSendingReview) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Outlined.RateReview, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (alreadyReviewedByMe) stringResource(R.string.pr_review_already)
                            else stringResource(R.string.pr_review_action_open),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }

                if (reviews.isEmpty()) {
                    if (reviewsError != null) {
                        SectionError(message = reviewsError!!, onRetry = { vm.retryReviews() })
                    } else {
                        Text(stringResource(R.string.pr_no_reviews), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    reviews.forEach { review ->
                        ReviewItem(review, onNavigateToUser = onNavigateToUser, dateFmt = dateFmt, onLinkClick = onLinkClick)
                    }
                    if (reviewsError != null) {
                        SectionError(message = reviewsError!!, onRetry = { vm.retryReviews() })
                    }
                }

                // ── Comments ──
                HorizontalDivider()
                Text(stringResource(R.string.comments_title, comments.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                if (comments.isEmpty()) {
                    if (commentsError != null) {
                        SectionError(message = commentsError!!, onRetry = { vm.retryComments() })
                    } else {
                        Text(stringResource(R.string.no_comments_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    vm.commentStates().forEach { state ->
                        CommentItem(
                            state = state,
                            onNavigateToUser = onNavigateToUser,
                            onLinkClick = onLinkClick,
                            onCopy = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("comment", state.comment.body))
                                scope.launch { snackbarHostState.showSnackbar("Copied") }
                            },
                            onEdit = { editingCommentId = state.comment.id; editingBody = state.comment.body },
                            onDelete = { pendingDeleteId = state.comment.id },
                            onAddReaction = { content -> vm.toggleReaction(state.comment.id, content) },
                            onRemoveReaction = { content -> vm.toggleReaction(state.comment.id, content) },
                        )
                    }
                    if (commentsError != null) {
                        SectionError(message = commentsError!!, onRetry = { vm.retryComments() })
                    }
                }

                // ── Timeline events (labeled / assigned / closed / merged / review_requested / …) ──
                if (events.isNotEmpty()) {
                    HorizontalDivider()
                    Text(stringResource(R.string.issue_activity), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    events.forEach { ev -> com.pockethub.ui.components.IssueEventRow(ev, onNavigateToUser) }
                    if (eventsError != null) {
                        Text(
                            eventsError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Comment input
                CommentInput(
                    isSending = isSendingComment,
                    onSend = { body -> vm.postComment(body) },
                )

                Spacer(Modifier.height(60.dp))
            }
        }
    }

    // Edit inline (PR review) comment dialog (R4)
    editingInlineId?.let { id ->
        EditInlineCommentDialog(
            id = id,
            body = editingInlineBody,
            onBodyChange = { editingInlineBody = it },
            onDismiss = { editingInlineId = null },
            onSave = { vid, newBody -> vm.editInlineComment(vid, newBody) },
        )
    }
    // Delete inline (PR review) comment confirm (R4)
    pendingDeleteInlineId?.let { id ->
        DeleteInlineConfirmDialog(
            id = id,
            onDismiss = { pendingDeleteInlineId = null },
            onDelete = { vm.deleteInlineComment(it) },
        )
    }
    // Edit comment dialog
    editingCommentId?.let { id ->
        EditCommentDialog(
            id = id,
            body = editingBody,
            onBodyChange = { editingBody = it },
            onDismiss = { editingCommentId = null },
            onSave = { vid, newBody -> vm.editComment(vid, newBody) },
        )
    }
    // Delete comment confirm
    pendingDeleteId?.let { id ->
        DeleteCommentConfirmDialog(
            id = id,
            onDismiss = { pendingDeleteId = null },
            onDelete = { vm.deleteComment(it) },
        )
    }
}
