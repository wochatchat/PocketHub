package com.pockethub.ui.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockethub.data.remote.GitHubApi

/**
 * Parsed representation of one patch hunk line.
 *
 * Unified-diff types:
 *  - context line (` `): both old and new share the same line — comments not anchored to old
 *    version, but can attach to the new line on RIGHT side.
 *  - addition line (`+`): present only on new (RIGHT) side.
 *  - deletion line (`-`): present on old (LEFT) side.
 *  - hunk header (`@@`): where line counts start/finish.
 */
data class DiffLine(
    val type: Char,
    val oldNumber: Int? = null,
    val newNumber: Int? = null,
    val content: String,
)

/**
 * Parse a GitHub unified-diff `patch` field into [DiffLine]s.
 *
 * Each `@@` line resets the running old/new line counters from the hunk header.
 * The patch's first `@@` line carries the starting line numbers for both sides.
 */
fun parsePatch(patch: String): List<DiffLine> {
    val result = mutableListOf<DiffLine>()
    var oldLine = 0
    var newLine = 0
    for (raw in patch.lines()) {
        if (raw.startsWith("@@")) {
            val m = Regex("@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@").find(raw) ?: continue
            oldLine = m.groupValues[1].toInt()
            newLine = m.groupValues[2].toInt()
            result.add(DiffLine(type = '@', content = raw))
            continue
        }
        if (raw.isEmpty()) {
            result.add(DiffLine(type = ' ', content = ""))
            continue
        }
        val head = raw.first()
        val rest = raw.removePrefix(head.toString())
        when (head) {
            '+' -> result.add(DiffLine(type = '+', newNumber = newLine, content = rest)).also { newLine++ }
            '-' -> result.add(DiffLine(type = '-', oldNumber = oldLine, content = rest)).also { oldLine++ }
            else -> result.add(DiffLine(type = ' ', oldNumber = oldLine, newNumber = newLine, content = raw.removePrefix(" "))).also {
                if (oldLine > 0) oldLine++
                if (newLine > 0) newLine++
            }
        }
    }
    return result
}

/**
 * Renders a unified-diff patch with line-level click handlers, anchoring inline review
 * comments to a specific file + line.
 *
 * Tap a `+` / context line with a [newNumber] to anchor a comment on the RIGHT side.
 * Tap a `-` line with an [oldNumber] to anchor on LEFT (in_reply_to is not used here —
 * single anchor comments only).
 */
@Composable
fun DiffPatchWithComment(
    patch: String,
    filename: String,
    commitId: String?,
    reviewComments: List<GitHubApi.ReviewComment>,
    isSendingComment: Boolean,
    onPostLineComment: (filename: String, commitId: String?, line: Int, body: String, startLine: Int?) -> Unit,
    onReply: (rootCommentId: Long, body: String) -> Unit = { _, _ -> },
    onResolve: (rootCommentId: Long) -> Unit = {},
    onUnresolve: (rootCommentId: Long) -> Unit = {},
    onEdit: (commentId: Long, currentBody: String) -> Unit = { _, _ -> },
    onDelete: (commentId: Long) -> Unit = {},
    threadState: Map<Long, ThreadState> = emptyMap(),
    currentLogin: String? = null,
    busyCommentIds: Set<Long> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val lines = remember(patch) { parsePatch(patch) }
    // Track which line is currently being commented — show an inline input below it.
    var activeLine by remember { mutableStateOf<Int?>(null) }
    var draftBody by remember { mutableStateOf("") }
    // Reply state for a given thread root id.
    var replyTo by remember { mutableStateOf<Long?>(null) }
    var replyBody by remember { mutableStateOf("") }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(
        modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(vScroll)
            .horizontalScroll(hScroll)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        lines.forEachIndexed { idx, line ->
            // Pull all comments anchored at this line, AS WELL AS every reply in the
            // same thread (matching root id) — render them as a single thread block.
            val anchoredHere = remember(reviewComments, line.newNumber) {
                val direct = reviewComments.filter { rc ->
                    rc.path == filename && rc.line == line.newNumber && rc.inReplyToId == null
                }
                direct.map { root ->
                    val rootId = root.id
                    val replies = reviewComments.filter { it.path == filename && it.inReplyToId == rootId }
                    listOf(root) + replies
                }
            }

            // Background color tuned per line type.
            val bg = when (line.type) {
                '+' -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                '-' -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                '@' -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> androidx.compose.ui.graphics.Color.Transparent
            }
            val lineColor = when (line.type) {
                '+' -> MaterialTheme.colorScheme.primary
                '-' -> MaterialTheme.colorScheme.error
                '@' -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            }
            val commentable = (line.type == '+' && line.newNumber != null)

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .clickable(enabled = commentable) {
                        activeLine = if (activeLine == line.newNumber) null
                                     else line.newNumber
                        draftBody = ""
                    }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Line numbers
                val lnStr = (line.newNumber ?: line.oldNumber)?.let { "%4d".format(it) } ?: "    "
                Text(lnStr, style = tyLineNum(), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Spacer(Modifier.width(4.dp))
                val prefix = when (line.type) {
                    '+' -> "+"; '-' -> "-"; '@' -> " "; else -> " "
                }
                Text(
                    "$prefix${line.content}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    ),
                    color = lineColor,
                )
            }

            // Render every thread whose root is anchored at this line.
            anchoredHere.forEach { thread ->
                val root = thread.first()
                val info = threadState[root.id]
                InlineCommentThread(
                    thread = thread,
                    isResolved = info?.isResolved == true,
                    currentLogin = currentLogin,
                    isBusy = thread.any { it.id in busyCommentIds },
                    canResolve = info?.threadId?.isNotBlank() == true,
                    replyOpen = replyTo == root.id,
                    replyBody = replyBody,
                    onReplyToggle = {
                        replyTo = if (replyTo == root.id) null else root.id
                        replyBody = ""
                    },
                    onReplyBodyChange = { replyBody = it },
                    onSubmitReply = {
                        onReply(root.id, replyBody.trim())
                        replyTo = null
                        replyBody = ""
                    },
                    onResolve = { onResolve(root.id) },
                    onUnresolve = { onUnresolve(root.id) },
                    onEdit = { c -> onEdit(c.id, c.body) },
                    onDelete = { c -> onDelete(c.id) },
                    isSendingComment = isSendingComment,
                )
            }

            // Show inline comment composer when this is the active anchor line.
            if (commentable && activeLine != null && line.newNumber == activeLine) {
                Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
                    OutlinedTextField(
                        value = draftBody,
                        onValueChange = { draftBody = it },
                        placeholder = { Text("Comment on this line…") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSendingComment,
                        minLines = 2,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = {
                                val lineAnchor = line.newNumber ?: return@TextButton
                                onPostLineComment(filename, commitId, lineAnchor, draftBody.trim(), null)
                                activeLine = null
                                draftBody = ""
                            },
                            enabled = draftBody.isNotBlank() && !isSendingComment,
                        ) {
                            if (isSendingComment) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            } else {
                                Icon(Icons.AutoMirrored.Outlined.Send, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("Send")
                        }
                    }
                }
            }
        }
    }
}

/** Minimal state the UI needs about a thread — surfaced from the VM's ThreadInfo. */
data class ThreadState(val threadId: String, val isResolved: Boolean)

@Composable
private fun InlineCommentThread(
    thread: List<GitHubApi.ReviewComment>,
    isResolved: Boolean,
    currentLogin: String?,
    isBusy: Boolean,
    canResolve: Boolean,
    replyOpen: Boolean,
    replyBody: String,
    isSendingComment: Boolean,
    onReplyToggle: () -> Unit,
    onReplyBodyChange: (String) -> Unit,
    onSubmitReply: () -> Unit,
    onResolve: () -> Unit,
    onUnresolve: () -> Unit,
    onEdit: (GitHubApi.ReviewComment) -> Unit,
    onDelete: (GitHubApi.ReviewComment) -> Unit,
) {
    val root = thread.first()
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isResolved) 0.2f else 0.4f))
            .padding(6.dp),
    ) {
        if (isResolved) {
            Text(
                text = "Resolved",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
        }
        thread.forEach { comment ->
            InlineCommentRow(
                comment = comment,
                isMine = currentLogin != null && comment.user?.login == currentLogin,
                onEdit = { onEdit(comment) },
                onDelete = { onDelete(comment) },
            )
            if (comment != thread.last()) Spacer(Modifier.height(4.dp))
        }
        if (thread.isNotEmpty()) Spacer(Modifier.height(4.dp))

        // Action buttons row — Reply + (Resolve / Unresolve) only show when canResolve
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onReplyToggle, enabled = !isBusy) {
                Icon(Icons.AutoMirrored.Outlined.Reply, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(2.dp))
                Text("Reply", style = MaterialTheme.typography.labelSmall)
            }
            if (canResolve) {
                TextButton(onClick = if (isResolved) onUnresolve else onResolve, enabled = !isBusy) {
                    Icon(
                        if (isResolved) Icons.Outlined.Check else Icons.Outlined.TaskAlt,
                        null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        if (isResolved) "Unmark resolved" else "Mark resolved",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isResolved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Reply composer (collapsible)
        if (replyOpen) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = replyBody,
                onValueChange = onReplyBodyChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                minLines = 2,
                placeholder = { Text("Reply…") },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onSubmitReply,
                    enabled = replyBody.isNotBlank() && !isBusy,
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Send, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun InlineCommentRow(
    comment: GitHubApi.ReviewComment,
    isMine: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        val author: String = comment.user?.login ?: "ghost"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(author, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
            if (isMine) {
                Spacer(Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Outlined.Edit, "Edit", modifier = Modifier.size(12.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Outlined.Delete, "Delete", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        Text(comment.body, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun tyLineNum() = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
