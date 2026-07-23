package com.pockethub.ui.repo

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.ui.components.CommentItem
import com.pockethub.ui.markdown.MarkdownText
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    owner: String,
    repo: String,
    issueNumber: Int,
    onNavigateToRepo: (String, String) -> Unit = { _, _ -> },
    onNavigateToUser: (String) -> Unit = {},
    onBack: () -> Unit,
    vm: IssueDetailViewModel = hiltViewModel(),
) {
    val issue by vm.issue.collectAsState()
    val comments by vm.comments.collectAsState()
    // Touch these so comment rows re-compose when viewer reactions arrive async
    // (currentLogin hydrates from disk; viewerReactions arrive after a /reactions call).
    @Suppress("unused")
    val currentLogin by vm.currentLogin.collectAsState()
    @Suppress("unused")
    val viewerReactions by vm.viewerReactions.collectAsState()
    @Suppress("unused")
    val busyComments by vm.busyComments.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val isSendingComment by vm.isSendingComment.collectAsState()
    val isTogglingState by vm.isTogglingState.collectAsState()
    val isSaving by vm.isSaving.collectAsState()
    val repositoryLabels by vm.repositoryLabels.collectAsState()
    val milestones by vm.milestones.collectAsState()
    val actionMessage by vm.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val onLinkClick: (String, com.pockethub.ui.markdown.LinkKind) -> Unit = link@{ url, kind ->
        // DOWNLOADABLE — open in browser (issue view lacks a downloadVm hook; users can download via browser)
        if (kind == com.pockethub.ui.markdown.LinkKind.DOWNLOADABLE ||
            kind == com.pockethub.ui.markdown.LinkKind.IMAGE_URL ||
            kind == com.pockethub.ui.markdown.LinkKind.IMAGE
        ) {
            runCatching { uriHandler.openUri(url) }
            return@link
        }
        // Issue / PR links inside the body —
        // Same repo, same issue-number navigation isn't wired through here, so we let it fall
        // through to the browser path below (the user sees it opened externally, which is OK
        // because issue/PR README links in issue bodies are usually cross-repo refs).
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

    var showEditDialog by remember { mutableStateOf(false) }
    var editingCommentId by remember { mutableStateOf<Long?>(null) }
    var editingBody by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    // Show action results as snackbar
    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearActionMessage()
        }
    }

    LaunchedEffect(owner, repo, issueNumber) {
        vm.loadIssue(owner, repo, issueNumber)
        vm.loadMetadata(owner, repo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("#$issueNumber", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }, enabled = issue != null && !isSaving) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit_issue))
                    }
                    // Open in browser
                    IconButton(onClick = {
                        issue?.htmlUrl?.let { url ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.cd_open_in_browser))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (isLoading && issue == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (issue == null && error != null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.loading_failed), style = MaterialTheme.typography.titleMedium)
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.retry(owner, repo, issueNumber) }) {
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
            issue?.let { data ->
                // Title + state badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(data.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val stateColor = if (data.state == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Box(
                        Modifier.clip(CircleShape)
                            .background(stateColor.copy(alpha = 0.12f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(stringResource(if (data.state == "open") R.string.issue_state_open else R.string.issue_state_closed), style = MaterialTheme.typography.labelSmall, color = stateColor)
                    }
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
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        stringResource(R.string.issue_meta, data.number, data.comments),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Labels
                if (data.labels.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.labels.take(5).forEach { label ->
                            val bg = runCatching { androidx.compose.ui.graphics.Color(("FF" + (label.color ?: "888888")).toLong(16)) }.getOrDefault(MaterialTheme.colorScheme.secondaryContainer)
                            Text(
                                label.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    .background(bg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                if (data.milestone != null || data.assignees.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        data.milestone?.let { milestone ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Label, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(milestone.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        data.assignees.take(4).forEach { assignee ->
                            AsyncImage(model = assignee.avatarUrl, contentDescription = assignee.login,
                                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onNavigateToUser(assignee.login) })
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // State toggle button
                OutlinedButton(
                    onClick = { vm.toggleIssueState() },
                    enabled = !isTogglingState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isTogglingState) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        if (data.state == "open") {
                            Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_close_issue))
                        } else {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_reopen_issue))
                        }
                    }
                }

                // Issue body
                MarkdownText(
                    markdown = data.body ?: stringResource(R.string.no_description),
                    modifier = Modifier.fillMaxWidth(),
                    repoContext = "$owner/$repo",
                    onLinkClick = onLinkClick,
                )

                // Comments section
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Text(stringResource(R.string.comments_title, comments.size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (comments.isEmpty() && data.comments > 0) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (comments.isEmpty()) {
                    Text(stringResource(R.string.no_comments_yet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    // Load-more row — appears only when the link header says more
                    // pages exist. Lets the user pull in older comments incrementally
                    // instead of being silently truncated at 50.
                    val hasMore by vm.hasMoreComments.collectAsState()
                    val isLoadingMore by vm.isLoadingMoreComments.collectAsState()
                    val commentsError by vm.commentsError.collectAsState()
                    if (hasMore) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = { vm.loadMoreComments() }) {
                                    Text(stringResource(R.string.load_more_comments))
                                }
                            }
                        }
                    }
                    if (commentsError != null) {
                        Text(
                            commentsError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Comment input box
                CommentInputBox(
                    isSending = isSendingComment,
                    onPost = { body -> vm.postComment(body) { } },
                )
            }
        }
    }
    if (showEditDialog && issue != null) {
        IssueEditDialog(
            issue = issue!!,
            availableLabels = repositoryLabels,
            milestones = milestones,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showEditDialog = false },
            onSave = { title, body, labels, assignees, milestone ->
                showEditDialog = false
                vm.saveIssue(title, body, labels, assignees, milestone)
            },
        )
    }
    editingCommentId?.let { id ->
        EditCommentDialog(
            initialBody = editingBody,
            onDismiss = { editingCommentId = null },
            onSave = { newBody ->
                vm.editComment(id, newBody)
                editingCommentId = null
            },
        )
    }
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun IssueEditDialog(
    issue: com.pockethub.data.model.Issue,
    availableLabels: List<com.pockethub.data.model.Issue.Label>,
    milestones: List<com.pockethub.data.model.Issue.Milestone>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>, List<String>, Int?) -> Unit,
) {
    var title by remember(issue.id) { mutableStateOf(issue.title) }
    var body by remember(issue.id) { mutableStateOf(issue.body.orEmpty()) }
    var labels by remember(issue.id) { mutableStateOf(issue.labels.map { it.name }.toSet()) }
    var assigneesText by remember(issue.id) { mutableStateOf(issue.assignees.joinToString(",") { it.login }) }
    var milestone by remember(issue.id) { mutableStateOf(issue.milestone?.number) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.issue_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.hint_issue_title)) }, enabled = !isSaving, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text(stringResource(R.string.hint_issue_body)) }, enabled = !isSaving, modifier = Modifier.fillMaxWidth(), minLines = 4)
                if (availableLabels.isNotEmpty()) {
                    Text(stringResource(R.string.issue_labels), style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        availableLabels.forEach { label ->
                            androidx.compose.material3.FilterChip(selected = label.name in labels, onClick = {
                                labels = if (label.name in labels) labels - label.name else labels + label.name
                            }, label = { Text(label.name) }, enabled = !isSaving)
                        }
                    }
                }
                OutlinedTextField(assigneesText, { assigneesText = it }, label = { Text(stringResource(R.string.issue_assignees_hint)) }, supportingText = { Text(stringResource(R.string.issue_assignees_help)) }, enabled = !isSaving, modifier = Modifier.fillMaxWidth())
                if (milestones.isNotEmpty()) {
                    Text(stringResource(R.string.issue_milestone), style = MaterialTheme.typography.labelLarge)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.material3.FilterChip(selected = milestone == null, onClick = { milestone = null }, label = { Text(stringResource(R.string.issue_no_milestone)) }, enabled = !isSaving)
                        milestones.forEach { item -> androidx.compose.material3.FilterChip(selected = milestone == item.number, onClick = { milestone = item.number }, label = { Text(item.title) }, enabled = !isSaving) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(title.trim(), body, labels.toList(), assigneesText.split(',').map { it.trim().removePrefix("@") }.filter { it.isNotBlank() }, milestone) }, enabled = title.isNotBlank() && !isSaving) { if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentInputBox(
    isSending: Boolean,
    onPost: (String) -> Unit,
) {
    var commentText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().imePadding()) {
        OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            label = { Text(stringResource(R.string.comment_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            enabled = !isSending,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSending) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = {
                    if (commentText.isNotBlank()) {
                        onPost(commentText.trim())
                        commentText = ""
                    }
                },
                enabled = !isSending && commentText.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_comment))
            }
        }
    }
}

@Composable
private fun EditCommentDialog(
    initialBody: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var body by remember { mutableStateOf(initialBody) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.comment_edit_title)) },
        text = {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.comment_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        },
        confirmButton = {
            Button(onClick = { onSave(body.trim()) }, enabled = body.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
