@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pockethub.ui.repo

// Pull request detail sub-components: comment input, review card, file diff,
// checks card, section error. Split out of PullRequestDetailScreen.kt.

import com.pockethub.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.markdown.MarkdownText
import java.text.DateFormat
import com.pockethub.util.parseIso
import com.pockethub.ui.components.PhAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Composable
internal fun CommentInput(
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
internal fun ReviewItem(
    review: GitHubApi.PullRequestReview,
    onNavigateToUser: (String) -> Unit,
    dateFmt: DateFormat,
    onLinkClick: (String, com.pockethub.ui.markdown.LinkKind) -> Unit = { _, _ -> },
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
                PhAsyncImage(
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
                onLinkClick = onLinkClick,
            )
        }
    }
}

@Composable
internal fun FileDiffItem(
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
    var expanded by remember { mutableStateOf(false) }
    var preloadedLines by remember(file.patch) { mutableStateOf<List<DiffLine>?>(null) }
    LaunchedEffect(expanded, file.patch) {
        if (expanded && !file.patch.isNullOrBlank() && preloadedLines == null) {
            preloadedLines = withContext(Dispatchers.Default) { parsePatch(file.patch) }
        }
    }
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
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
        if (expanded && !file.patch.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            if (preloadedLines == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            } else {
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
                preloadedLines = preloadedLines,
            )
        }
    }
}

}

/**
 * Single-line CI checks summary shown above labels / reviewers on PR detail.
 *
 * Renders Passed (all checks green) / Failed (any red) / Pending (queued or
 * running) / None (no checks configured). Tapping the trailing Refresh icon
 * refetches the check runs for the PR head SHA. When failed or pending, an
 * expandable list of individual checks is rendered below.
 */
@Composable
internal fun ChecksCard(
    summary: CheckSummary,
    runs: List<GitHubApi.CheckRun>,
    isRefreshing: Boolean = false,
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
            // Refresh manually refreshes regardless of expansion state. A tiny inline
            // spinner replaces the icon while the re-fetch is in flight, so the tap
            // has visible feedback (previously the button looked dead).
            IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp), enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh_checks), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
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

/**
 * Inline error + retry row rendered in place of a failed PR section (files, reviews,
 * review comments, comments). Mirrors [IssueDetailScreen]'s error affordance.
 */
@Composable
internal fun SectionError(message: String, onRetry: () -> Unit) {
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
internal enum class ReviewEvent(val apiValue: String) {
    COMMENT("COMMENT"),
    APPROVE("APPROVE"),
    REQUEST_CHANGES("REQUEST_CHANGES"),
}
