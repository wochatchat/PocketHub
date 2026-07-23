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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.remote.GitHubApi
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommitDetailScreen(
    owner: String,
    repo: String,
    sha: String,
    onNavigateToUser: (String) -> Unit = {},
    onBack: () -> Unit,
    vm: CommitDetailViewModel = hiltViewModel(),
) {
    val commit by vm.commit.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val comments by vm.comments.collectAsState()
    val commentsError by vm.commentsError.collectAsState()
    val isSendingComment by vm.isSendingComment.collectAsState()
    val commentError by vm.commentError.collectAsState()
    val actionMessage by vm.actionMessage.collectAsState()
    val context = LocalContext.current
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(owner, repo, sha) { vm.load(owner, repo, sha) }

    // Surface snackbar whenever the action message or comment error changes.
    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearActionMessage()
        }
    }
    LaunchedEffect(commentError) {
        commentError?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearCommentError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        sha.take(7),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val url = commit?.htmlUrl ?: "https://github.com/$owner/$repo/commit/$sha"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.cd_open_in_browser))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (isLoading && commit == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (commit == null && error != null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.loading_failed), style = MaterialTheme.typography.titleMedium)
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.retry(owner, repo, sha) }) { Text(stringResource(R.string.action_retry)) }
            }
            return@Scaffold
        }

        val data = commit ?: return@Scaffold

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Commit message + author header
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        data.commit?.message?.substringBefore("\n") ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    data.commit?.message?.substringAfter("\n", "")?.takeIf { it.isNotBlank() }?.let { body ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            body.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val login = data.author?.login
                        if (data.author?.avatarUrl != null) {
                            AsyncImage(
                                model = data.author.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .then(if (login != null) Modifier.clickable { onNavigateToUser(login) } else Modifier),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            login ?: data.commit?.author?.name ?: stringResource(R.string.unknown),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (login != null) Modifier.clickable { onNavigateToUser(login) } else Modifier,
                        )
                        Spacer(Modifier.width(8.dp))
                        data.commit?.author?.date?.let { dateStr ->
                            Text(
                                dateFmt.format(parseIsoDateTime(dateStr)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Stats + parents
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val stats = data.stats
                        if (stats != null) {
                            Text(
                                stringResource(R.string.commit_additions_deletions, stats.additions, stats.deletions),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            stringResource(R.string.commit_files_changed, data.files.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    data.parents.firstOrNull()?.let { parent ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.commit_parent, parent.sha.take(7)),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider()
                }
            }

            // Changed files
            items(data.files, key = { it.sha + it.filename }) { file ->
                CommitFileCard(file)
            }

            // Commit comments section (GitHub web "Comments on this commit")
            item(key = "comments_header") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        stringResource(R.string.commit_comments, comments.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }
            }

            if (commentsError != null) {
                item(key = "comments_error") {
                    Column(
                        Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.loading_failed), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            commentsError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { vm.retryComments(owner, repo, sha) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            } else if (comments.isEmpty()) {
                item(key = "comments_empty") {
                    Text(
                        stringResource(R.string.commit_no_comments),
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(comments, key = { it.id }) { comment ->
                    CommitCommentItem(comment, dateFmt, onNavigateToUser)
                }
            }

            // Comment composer
            item(key = "comment_composer") {
                CommitCommentComposer(
                    isSending = isSendingComment,
                    onSubmit = { body -> vm.postComment(owner, repo, sha, body) },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CommitFileCard(file: GitHubApi.CommitDetail.CommitFile) {
    var expanded by remember(file.filename) { mutableStateOf(false) }
    val statusColor = when (file.status) {
        "added" -> Color(0xFF3FB950)
        "removed" -> Color(0xFFF85149)
        "renamed" -> Color(0xFFD29922)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { if (file.patch != null) expanded = !expanded }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (file.status == "removed") Icons.Outlined.Remove else Icons.Outlined.Add,
                contentDescription = file.status,
                modifier = Modifier.size(14.dp),
                tint = statusColor,
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.filename,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (file.status == "renamed" && !file.previousFilename.isNullOrBlank()) {
                    Text(
                        "← ${file.previousFilename}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "+${file.additions}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF3FB950),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "−${file.deletions}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFFF85149),
            )
        }

        if (expanded) {
            Spacer(Modifier.height(8.dp))
            if (file.patch != null) {
                val hScroll = rememberScrollState()
                Text(
                    text = remember(file.patch) { annotateDiff(file.patch!!) },
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 16.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .horizontalScroll(hScroll)
                        .padding(10.dp),
                    softWrap = false,
                )
            } else {
                Text(
                    stringResource(R.string.commit_no_diff),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Color diff lines: additions green, deletions red, hunk headers blue. */
private fun annotateDiff(patch: String): AnnotatedString = buildAnnotatedString {
    val addColor = Color(0xFF3FB950)
    val delColor = Color(0xFFF85149)
    val hunkColor = Color(0xFF58A6FF)
    patch.split("\n").forEachIndexed { idx, line ->
        if (idx > 0) append("\n")
        when {
            line.startsWith("+++") || line.startsWith("---") ->
                withStyle(SpanStyle(color = hunkColor)) { append(line) }
            line.startsWith("+") ->
                withStyle(SpanStyle(color = addColor)) { append(line) }
            line.startsWith("-") ->
                withStyle(SpanStyle(color = delColor)) { append(line) }
            line.startsWith("@@") ->
                withStyle(SpanStyle(color = hunkColor)) { append(line) }
            else -> append(line)
        }
    }
}

private fun parseIsoDateTime(iso: String): Date = runCatching {
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.parse(iso)
}.getOrDefault(Date())

@Composable
private fun CommitCommentItem(
    comment: GitHubApi.CommitComment,
    dateFmt: DateFormat,
    onNavigateToUser: (String) -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val login = comment.user?.login
            if (comment.user?.avatarUrl != null) {
                AsyncImage(
                    model = comment.user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .then(if (login != null) Modifier.clickable { onNavigateToUser(login) } else Modifier),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                login ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                modifier = if (login != null) Modifier.clickable { onNavigateToUser(login) } else Modifier,
            )
            Spacer(Modifier.width(8.dp))
            comment.createdAt?.let { dateStr ->
                Text(
                    dateFmt.format(parseIsoDateTime(dateStr)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            comment.body,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CommitCommentComposer(
    isSending: Boolean,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.commit_comment_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            enabled = !isSending,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (text.isNotBlank() && !isSending) {
                        onSubmit(text.trim())
                        text = ""
                    }
                },
                enabled = text.isNotBlank() && !isSending,
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.commit_comment_send))
            }
        }
    }
}
