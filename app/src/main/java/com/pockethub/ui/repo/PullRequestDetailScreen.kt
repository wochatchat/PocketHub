package com.pockethub.ui.repo

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.components.CommentItem
import com.pockethub.ui.markdown.MarkdownText
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.text.DateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRequestDetailScreen(
    owner: String,
    repo: String,
    prNumber: Int,
    onNavigateToRepo: (String, String) -> Unit = { _, _ -> },
    onNavigateToUser: (String) -> Unit = {},
    onBack: () -> Unit,
    vm: PullRequestDetailViewModel = hiltViewModel(),
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
    val isSendingLineComment by vm.isSendingLineComment.collectAsState()
    val checkRuns by vm.checkRuns.collectAsState()
    val checkSummary by vm.checkSummary.collectAsState()
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
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var showMergeDialog by remember { mutableStateOf(false) }
    var showMergeWarningDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var reviewEvent by remember { mutableStateOf(ReviewEvent.APPROVE) }
    var editingCommentId by remember { mutableStateOf<Long?>(null) }
    var editingBody by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    // Editing / deleting inline (PR review) comments — kept in the outer composable
    // so dialogs can be rendered outside the scrollable column.
    var editingInlineId by remember { mutableStateOf<Long?>(null) }
    var editingInlineBody by remember { mutableStateOf("") }
    var pendingDeleteInlineId by remember { mutableStateOf<Long?>(null) }

    val onLinkClick: (String, com.pockethub.ui.markdown.LinkKind) -> Unit = link@{ url, kind ->
        if (kind == com.pockethub.ui.markdown.LinkKind.DOWNLOADABLE ||
            kind == com.pockethub.ui.markdown.LinkKind.IMAGE_URL ||
            kind == com.pockethub.ui.markdown.LinkKind.IMAGE
        ) {
            runCatching { uriHandler.openUri(url) }
            return@link
        }
        Regex("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/?.*$").matchEntire(url)?.let {
            onNavigateToRepo(it.groupValues[1], it.groupValues[2])
            return@link
        }
        Regex("^https://github\\.com/([A-Za-z0-9_.-]+)$").matchEntire(url)?.let {
            onNavigateToUser(it.groupValues[1])
            return@link
        }
        runCatching { uriHandler.openUri(url) }
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
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
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
                        AsyncImage(
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
                            Text(
                                label.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
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
                    onRefresh = { vm.refreshCheckRuns(owner, repo) },
                )

                // Requested reviewers
                if (data.requestedReviewers.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.pr_reviewers),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        data.requestedReviewers.forEach { reviewer ->
                            AsyncImage(
                                model = reviewer.avatarUrl,
                                contentDescription = reviewer.login,
                                modifier = Modifier.size(18.dp).clip(CircleShape)
                                    .clickable { onNavigateToUser(reviewer.login) },
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                    }
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
                        ReviewItem(review, onNavigateToUser = onNavigateToUser, dateFmt = dateFmt)
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

                // Comment input
                CommentInput(
                    isSending = isSendingComment,
                    onSend = { body -> vm.postComment(body) },
                )

                Spacer(Modifier.height(60.dp))
            }
        }
    }

    // Merge dialog
    if (showMergeDialog) {
        var mergeMethod by remember { mutableStateOf("merge") }
        AlertDialog(
            onDismissRequest = { if (!isMerging) showMergeDialog = false },
            title = { Text(stringResource(R.string.pr_merge_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.pr_merge_confirm, pr?.number ?: 0))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("merge" to stringResource(R.string.pr_merge_method_merge), "squash" to stringResource(R.string.pr_merge_method_squash), "rebase" to stringResource(R.string.pr_merge_method_rebase)).forEach { (method, label) ->
                            OutlinedButton(
                                onClick = { mergeMethod = method },
                                colors = if (mergeMethod == method) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showMergeDialog = false; vm.merge(owner, repo, prNumber, mergeMethod) },
                    enabled = !isMerging,
                ) {
                    if (isMerging) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(R.string.action_merge))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // Review submit bottom sheet (R1)
    if (showReviewDialog) {
        val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var reviewBody by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { if (!isSendingReview) showReviewDialog = false },
            sheetState = sheetState,
        ) {
            Column(
                Modifier.padding(20.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.pr_review_submit), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                ReviewEvent.entries.forEach { ev ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !isSendingReview) { reviewEvent = ev }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = reviewEvent == ev,
                            onClick = { reviewEvent = ev },
                            enabled = !isSendingReview,
                            colors = RadioButtonDefaults.colors(selectedColor = when (ev) {
                                ReviewEvent.APPROVE -> MaterialTheme.colorScheme.primary
                                ReviewEvent.REQUEST_CHANGES -> MaterialTheme.colorScheme.error
                                ReviewEvent.COMMENT -> MaterialTheme.colorScheme.onSurfaceVariant
                            }),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (ev) {
                                    ReviewEvent.COMMENT -> stringResource(R.string.pr_review_event_comment)
                                    ReviewEvent.APPROVE -> stringResource(R.string.pr_review_event_approve)
                                    ReviewEvent.REQUEST_CHANGES -> stringResource(R.string.pr_review_event_request_changes)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                when (ev) {
                                    ReviewEvent.COMMENT -> stringResource(R.string.pr_review_event_hint_comment)
                                    ReviewEvent.APPROVE -> stringResource(R.string.pr_review_event_hint_approve)
                                    ReviewEvent.REQUEST_CHANGES -> stringResource(R.string.pr_review_event_hint_request_changes)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reviewBody,
                    onValueChange = { reviewBody = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    placeholder = {
                        Text(when (reviewEvent) {
                            ReviewEvent.COMMENT -> stringResource(R.string.pr_review_event_hint_comment)
                            ReviewEvent.APPROVE -> stringResource(R.string.pr_review_event_hint_approve)
                            ReviewEvent.REQUEST_CHANGES -> stringResource(R.string.pr_review_event_hint_request_changes)
                        })
                    },
                    enabled = !isSendingReview,
                    minLines = 3,
                )

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { showReviewDialog = false },
                        enabled = !isSendingReview,
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            showReviewDialog = false
                            vm.submitReview(owner, repo, prNumber, reviewEvent.apiValue, reviewBody)
                        },
                        enabled = !isSendingReview,
                    ) {
                        if (isSendingReview) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(when (reviewEvent) {
                                ReviewEvent.COMMENT -> stringResource(R.string.pr_review_comment)
                                ReviewEvent.APPROVE -> stringResource(R.string.pr_approve)
                                ReviewEvent.REQUEST_CHANGES -> stringResource(R.string.pr_request_changes)
                            })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Merge warning dialog (R5) — reviews requested changes; user taps "merge anyway"
    if (showMergeWarningDialog) {
        AlertDialog(
            onDismissRequest = { showMergeWarningDialog = false },
            title = { Text(stringResource(R.string.pr_merge_warning_title)) },
            text = {
                val count = reviews.count { it.state == "CHANGES_REQUESTED" }
                Text(stringResource(R.string.pr_changes_requested_warning, count))
            },
            confirmButton = {
                Button(onClick = { showMergeWarningDialog = false; showMergeDialog = true }) { Text(stringResource(R.string.action_merge)) }
            },
            dismissButton = {
                TextButton(onClick = { showMergeWarningDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // Edit inline (PR review) comment dialog (R4)
    editingInlineId?.let { id ->
        AlertDialog(
            onDismissRequest = { editingInlineId = null },
            title = { Text(stringResource(R.string.pr_inline_edit_title)) },
            text = {
                OutlinedTextField(
                    value = editingInlineBody,
                    onValueChange = { editingInlineBody = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            },
            confirmButton = {
                Button(
                    onClick = { vm.editInlineComment(id, editingInlineBody.trim()); editingInlineId = null },
                    enabled = editingInlineBody.isNotBlank(),
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingInlineId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // Delete inline (PR review) comment confirm (R4)
    pendingDeleteInlineId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteInlineId = null },
            title = { Text(stringResource(R.string.comment_delete_confirm_title)) },
            text = { Text(stringResource(R.string.comment_delete_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = { vm.deleteInlineComment(id); pendingDeleteInlineId = null },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteInlineId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // Edit comment dialog
    editingCommentId?.let { id ->
        AlertDialog(
            onDismissRequest = { editingCommentId = null },
            title = { Text(stringResource(R.string.comment_edit_title)) },
            text = {
                OutlinedTextField(
                    value = editingBody,
                    onValueChange = { editingBody = it },
                    label = { Text(stringResource(R.string.comment_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.editComment(id, editingBody.trim())
                    editingCommentId = null
                }, enabled = editingBody.isNotBlank()) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingCommentId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    // Delete comment confirm
    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.comment_delete_confirm_title)) },
            text = { Text(stringResource(R.string.comment_delete_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteComment(id)
                        pendingDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CommentInput(
    isSending: Boolean,
    onSend: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.comment_placeholder)) },
            maxLines = 4,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { if (text.isNotBlank()) { onSend(text); text = "" } },
            enabled = text.isNotBlank() && !isSending,
        ) {
            if (isSending) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.cd_send_comment))
            }
        }
    }
}

@Composable
private fun ReviewItem(
    review: GitHubApi.PullRequestReview,
    onNavigateToUser: (String) -> Unit,
    dateFmt: DateFormat,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val stateColor = when (review.state) {
                "APPROVED" -> Color(0xFF2EA043)
                "CHANGES_REQUESTED" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val stateText = when (review.state) {
                "APPROVED" -> "✓ Approved"
                "CHANGES_REQUESTED" -> "✕ Changes requested"
                "COMMENTED" -> "💬 Commented"
                "DISMISSED" -> "Dismissed"
                else -> review.state
            }
            Box(
                Modifier.clip(CircleShape).background(stateColor.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(stateText, style = MaterialTheme.typography.labelSmall, color = stateColor)
            }
            Spacer(Modifier.width(8.dp))
            val user = review.user
            if (user != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).clip(CircleShape)
                        .clickable { onNavigateToUser(user.login) },
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    user.login,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToUser(user.login) },
                )
            }
            review.submittedAt?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    dateFmt.format(parseIso(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!review.body.isNullOrBlank()) {
            MarkdownText(
                markdown = review.body,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FileDiffItem(
    file: GitHubApi.PullRequestFile,
    commitId: String?,
    reviewComments: List<GitHubApi.ReviewComment>,
    isSendingLineComment: Boolean,
    onPostLineComment: (filename: String, commitId: String?, line: Int, body: String, startLine: Int?) -> Unit,
    onReply: (rootCommentId: Long, body: String) -> Unit,
    onResolve: (rootCommentId: Long) -> Unit,
    onUnresolve: (rootCommentId: Long) -> Unit,
    onEditInline: (commentId: Long, currentBody: String) -> Unit,
    onDeleteInline: (commentId: Long) -> Unit,
    threadState: Map<Long, ThreadState>,
    currentLogin: String?,
    busyCommentIds: Set<Long>,
) {
    val statusColor = when (file.status) {
        "added" -> Color(0xFF2EA043)
        "removed" -> MaterialTheme.colorScheme.error
        "modified" -> MaterialTheme.colorScheme.primary
        "renamed" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when (file.status) {
        "added" -> "A"
        "removed" -> "D"
        "modified" -> "M"
        "renamed" -> "R"
        else -> "?"
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp),
    ) {
        // File header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(4.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                file.filename,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "+${file.additions} -${file.deletions}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Patch — line-commentable
        if (!file.patch.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            DiffPatchWithComment(
                patch = file.patch,
                filename = file.filename,
                commitId = commitId,
                reviewComments = reviewComments.filter { it.path == file.filename },
                isSendingComment = isSendingLineComment,
                onPostLineComment = onPostLineComment,
                onReply = onReply,
                onResolve = onResolve,
                onUnresolve = onUnresolve,
                onEdit = onEditInline,
                onDelete = onDeleteInline,
                threadState = threadState,
                currentLogin = currentLogin,
                busyCommentIds = busyCommentIds,
            )
        }
    }
}

private fun formatDate(s: String): String = try {
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(java.time.OffsetDateTime.parse(s))
} catch (_: Exception) { s.take(10) }

/**
 * Single-line CI checks summary shown above labels / reviewers on PR detail.
 *
 * Renders Passed (all checks green) / Failed (any red) / Pending (queued or
 * running) / None (no checks configured). Tapping the trailing Refresh icon
 * refetches the check runs for the PR head SHA. When failed or pending, an
 * expandable list of individual checks is rendered below.
 */
@Composable
private fun ChecksCard(
    summary: CheckSummary,
    runs: List<GitHubApi.CheckRun>,
    onRefresh: () -> Unit,
) {
    if (runs.isEmpty() && summary is CheckSummary.NONE) return

    var expanded by remember { mutableStateOf(false) }

    val (icon, tint, label) = when (summary) {
        is CheckSummary.Passed ->
            Triple(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary,
                stringResource(R.string.checks_passed, summary.passed, summary.total))
        is CheckSummary.Failed ->
            Triple(Icons.Outlined.Close, MaterialTheme.colorScheme.error,
                stringResource(R.string.checks_failed, summary.failed, summary.total))
        is CheckSummary.Pending ->
            Triple(Icons.Outlined.Pending, MaterialTheme.colorScheme.tertiary,
                stringResource(R.string.checks_pending, summary.pending, summary.total))
        CheckSummary.NONE -> return
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    // Failed / Pending checks expand on tap so the user can see what failed.
                    if (summary is CheckSummary.Failed || summary is CheckSummary.Pending) {
                        expanded = !expanded
                    }
                }
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            // Refresh manually refreshes regardless of expansion state.
            IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh_checks), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }

        if (expanded) {
            Spacer(Modifier.height(6.dp))
            runs.forEach { run ->
                val runTint = when {
                    run.status == "completed" && run.conclusion == "success" -> MaterialTheme.colorScheme.primary
                    run.status == "completed" && run.conclusion in setOf("failure", "cancelled", "timed_out") -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val (runIcon, stateLabel) = when {
                        run.status == "completed" && run.conclusion == "success" -> Icons.Outlined.CheckCircle to stringResource(R.string.check_state_success)
                        run.status == "completed" && run.conclusion in setOf("failure", "cancelled", "timed_out") -> Icons.Outlined.Close to stringResource(R.string.check_state_failed)
                        run.status == "completed" && run.conclusion in setOf("neutral", "skipped", "stale") -> Icons.Outlined.CheckCircle to stringResource(R.string.check_state_skipped)
                        run.status == "in_progress" -> Icons.Outlined.Pending to stringResource(R.string.check_state_in_progress)
                        else -> Icons.Outlined.Pending to stringResource(R.string.check_state_queued)
                    }
                    Icon(runIcon, null, modifier = Modifier.size(14.dp), tint = runTint)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${run.app?.name ?: "—"} / ${run.name}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = runTint)
                }
            }
        }
    }
}

/** Parse an ISO-8601 timestamp into a Date for SimpleDateFormat. */
private fun parseIso(iso: String): java.util.Date {
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(iso)
    }.getOrDefault(java.util.Date())
}

/**
 * Inline error + retry row rendered in place of a failed PR section (files, reviews,
 * review comments, comments). Mirrors [IssueDetailScreen]'s error affordance.
 */
@Composable
private fun SectionError(message: String, onRetry: () -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onRetry) {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Retry")
        }
    }
}

/**
 * Review event types the UI can submit. Mirrors the GitHub v3 createReview event
 * values; indexed by the modal bottom sheet radio group.
 */
enum class ReviewEvent(val apiValue: String) {
    COMMENT("COMMENT"),
    APPROVE("APPROVE"),
    REQUEST_CHANGES("REQUEST_CHANGES"),
}
