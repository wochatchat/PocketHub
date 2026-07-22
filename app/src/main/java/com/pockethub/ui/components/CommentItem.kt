package com.pockethub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pockethub.R
import com.pockethub.data.model.User
import com.pockethub.data.remote.GitHubApi

/**
 * UI state for a single comment row. Wraps the raw GitHub comment plus the
 * currently-known reaction IDs owned by the viewer (keyed by ReactionContent).
 */
data class CommentUiState(
    val comment: GitHubApi.IssueComment,
    val repoContext: String,
    val isMine: Boolean,
    val isAuthor: Boolean = false,
    val canModerate: Boolean = false,
    /** Map from ReactionContent.apiValue -> reaction_id for reactions this viewer created. */
    val viewerReactions: Map<String, Long> = emptyMap(),
    val isReacting: Boolean = false,
    val isSaving: Boolean = false,
)

private data class ReactionChip(val content: GitHubApi.ReactionContent, val emoji: String, val count: Int)

private fun Reactions.toChips(): List<ReactionChip> {
    val raw = this
    return buildList {
        add(ReactionChip(GitHubApi.ReactionContent.PLUS_ONE, "\uD83D\uDC4D", raw.plusOne))
        add(ReactionChip(GitHubApi.ReactionContent.MINUS_ONE, "\uD83D\uDC4E", raw.minusOne))
        add(ReactionChip(GitHubApi.ReactionContent.LAUGH, "\uD83D\uDE04", raw.laugh))
        add(ReactionChip(GitHubApi.ReactionContent.CONFUSED, "\uD83D\uDE15", raw.confused))
        add(ReactionChip(GitHubApi.ReactionContent.HEART, "\u2764\uFE0F", raw.heart))
        add(ReactionChip(GitHubApi.ReactionContent.HOORAY, "\uD83C\uDF89", raw.hooray))
        add(ReactionChip(GitHubApi.ReactionContent.ROCKET, "\uD83D\uDE80", raw.rocket))
        add(ReactionChip(GitHubApi.ReactionContent.EYES, "\uD83D\uDC40", raw.eyes))
    }.filter { it.count > 0 }
}

/** Shared row composable for issue / PR comments. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CommentItem(
    state: CommentUiState,
    onNavigateToUser: (String) -> Unit,
    onLinkClick: (String, com.pockethub.ui.markdown.LinkKind) -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddReaction: (GitHubApi.ReactionContent) -> Unit,
    onRemoveReaction: (GitHubApi.ReactionContent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = state.comment
    var menuOpen by remember { mutableStateOf(false) }
    var reactionPickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val user = c.user
            if (user != null) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onNavigateToUser(user.login) },
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    user.login,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onNavigateToUser(user.login) },
                )
                state.authorAssociation()?.let { role ->
                    Spacer(Modifier.width(6.dp))
                    RoleBadge(role)
                }
                Spacer(Modifier.width(8.dp))
            } else {
                Text(stringResource(R.string.unknown), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            c.createdAt?.let {
                Text(
                    it.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.isMine || state.canModerate) {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_comment_actions), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (state.isMine) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp)) },
                            onClick = { menuOpen = false; onEdit() },
                        )
                    }
                    if (state.canModerate || state.isMine) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(18.dp)) },
                        onClick = { menuOpen = false; onCopy() },
                    )
                }
            }
        }

        com.pockethub.ui.markdown.MarkdownText(
            markdown = c.body.ifBlank { stringResource(R.string.no_content) },
            modifier = Modifier.fillMaxWidth(),
            repoContext = state.repoContext,
            onLinkClick = onLinkClick,
        )

        val reactions = c.reactions
        val hasReactions = reactions != null && reactions.totalCount > 0
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (hasReactions || reactionPickerOpen) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (reactions?.toChips() ?: emptyList()).forEach { chip ->
                        val mine = state.viewerReactions[chip.content.apiValue] != null
                        AssistChip(
                            onClick = {
                                if (mine) onRemoveReaction(chip.content) else onAddReaction(chip.content)
                            },
                            label = { Text("$chip.emoji ${chip.count}", style = MaterialTheme.typography.labelMedium) },
                            colors = if (mine) AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) else AssistChipDefaults.assistChipColors(),
                            border = null,
                        )
                    }
                }
            }
            IconButton(
                onClick = { reactionPickerOpen = !reactionPickerOpen },
                modifier = Modifier.size(20.dp),
            ) {
                Text(
                    if (reactionPickerOpen) "✕" else "\uD83D\uDE42",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isReacting) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            }
        }

        if (reactionPickerOpen) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                GitHubApi.ReactionContent.entries.forEach { rc ->
                    val mine = state.viewerReactions[rc.apiValue] != null
                    val emoji = rc.emoji()
                    AssistChip(
                        onClick = {
                            if (mine) onRemoveReaction(rc) else onAddReaction(rc)
                        },
                        label = { Text(emoji, style = MaterialTheme.typography.bodyMedium) },
                        colors = if (mine) AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) else AssistChipDefaults.assistChipColors(),
                        border = null,
                    )
                }
            }
        }

        HorizontalDivider()
    }
}

@Composable
private fun RoleBadge(role: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun CommentUiState.authorAssociation(): String? {
    val raw = comment.authorAssociation ?: return null
    if (raw == "NONE" || raw == "COLLABORATOR") return null
    return raw.lowercase()
}

private fun GitHubApi.ReactionContent.emoji(): String = when (this) {
    GitHubApi.ReactionContent.PLUS_ONE -> "\uD83D\uDC4D"
    GitHubApi.ReactionContent.MINUS_ONE -> "\uD83D\uDC4E"
    GitHubApi.ReactionContent.LAUGH -> "\uD83D\uDE04"
    GitHubApi.ReactionContent.CONFUSED -> "\uD83D\uDE15"
    GitHubApi.ReactionContent.HEART -> "\u2764\uFE0F"
    GitHubApi.ReactionContent.HOORAY -> "\uD83C\uDF89"
    GitHubApi.ReactionContent.ROCKET -> "\uD83D\uDE80"
    GitHubApi.ReactionContent.EYES -> "\uD83D\uDC40"
}

private typealias Reactions = com.pockethub.data.model.Reactions
