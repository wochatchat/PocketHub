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
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.ui.markdown.MarkdownText
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Locale

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
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val isSendingComment by vm.isSendingComment.collectAsState()
    val isTogglingState by vm.isTogglingState.collectAsState()
    val actionError by vm.actionError.collectAsState()
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val onLinkClick: (String) -> Unit = { url ->
        Regex("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/?.*$").matchEntire(url)?.let {
            onNavigateToRepo(it.groupValues[1], it.groupValues[2])
            return@url
        }
        Regex("^https://github\\.com/([A-Za-z0-9_.-]+)$").matchEntire(url)?.let {
            onNavigateToUser(it.groupValues[1])
            return@url
        }
        runCatching { uriHandler.openUri(url) }
    }

    // Show action errors as snackbar
    LaunchedEffect(actionError) {
        actionError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearActionError()
        }
    }

    LaunchedEffect(owner, repo, issueNumber) { vm.loadIssue(owner, repo, issueNumber) }

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
                    data.user?.avatarUrl?.let {
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        stringResource(R.string.issue_subtitle, data.number, data.user?.login ?: stringResource(R.string.unknown), data.comments),
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
                    comments.forEach { c ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                c.user?.avatarUrl?.let {
                                    AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(c.user?.login ?: stringResource(R.string.unknown), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                c.createdAt?.let {
                                    Text(dateFmt.format(parseIso(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            MarkdownText(
                                markdown = c.body.ifBlank { stringResource(R.string.no_content) },
                                modifier = Modifier.fillMaxWidth(),
                                repoContext = "$owner/$repo",
                                onLinkClick = onLinkClick,
                            )
                            HorizontalDivider()
                        }
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

/** Parse an ISO-8601 timestamp into a Date for SimpleDateFormat. */
private fun parseIso(iso: String): java.util.Date {
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(iso)
    }.getOrDefault(java.util.Date())
}
