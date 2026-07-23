package com.pockethub.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pockethub.data.remote.GitHubApi

/**
 * One timeline event row — labeled / assigned / closed / reopened / referenced / etc.
 * Shared between the Issue and Pull Request detail screens so both render the
 * same chronological event / activity stream.
 */
@Composable
fun IssueEventRow(
    event: GitHubApi.IssueEvent,
    onNavigateToUser: (String) -> Unit,
) {
    val (icon, text) = remember(event.id, event.event) { describeEvent(event) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        event.actor?.let { actor ->
            AsyncImage(
                model = actor.avatarUrl,
                contentDescription = actor.login,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onNavigateToUser(actor.login) },
            )
            Spacer(Modifier.width(6.dp))
            Text(
                actor.login,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onNavigateToUser(actor.login) },
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        event.createdAt?.let {
            Text(formatRelativeShort(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

/** Map an [GitHubApi.IssueEvent] to (icon, message). */
fun describeEvent(event: GitHubApi.IssueEvent): Pair<ImageVector, String> {
    val actor = event.actor?.login ?: "someone"
    return when (event.event) {
        "labeled" -> Icons.Outlined.Label to "$actor added the ${event.label?.name.orEmpty()} label"
        "unlabeled" -> Icons.Outlined.Label to "$actor removed the ${event.label?.name.orEmpty()} label"
        "assigned" -> Icons.Outlined.PersonAdd to "$actor assigned ${event.assignee?.login.orEmpty()}"
        "unassigned" -> Icons.Outlined.Person to "$actor unassigned ${event.assignee?.login.orEmpty()}"
        "closed" -> Icons.Outlined.CheckCircle to "$actor closed this"
        "reopened" -> Icons.Outlined.Refresh to "$actor reopened this"
        "locked" -> Icons.Outlined.Lock to "$actor locked this"
        "unlocked" -> Icons.Outlined.LockOpen to "$actor unlocked this"
        "milestoned" -> Icons.Outlined.Flag to "$actor set milestone ${event.milestone?.title.orEmpty()}"
        "demilestoned" -> Icons.Outlined.Flag to "$actor removed milestone ${event.milestone?.title.orEmpty()}"
        "referenced" -> Icons.Outlined.PushPin to "$actor referenced this"
        "cross-referenced" -> Icons.Outlined.PushPin to "$actor cross-referenced this"
        "renamed" -> Icons.Outlined.Edit to "$actor renamed this issue"
        "merged" -> Icons.Outlined.CheckCircle to "$actor merged this pull request"
        "head_ref_force_pushed" -> Icons.Outlined.Edit to "$actor force-pushed the head branch"
        "review_requested" -> Icons.Outlined.PersonAdd to "$actor requested a review from ${event.assignee?.login.orEmpty()}"
        else -> Icons.Outlined.Person to "$actor performed ${event.event}"
    }
}

/** Short, locale-neutral "2h ago" / "3d ago" style label parsed from an ISO-8601 timestamp. */
fun formatRelativeShort(iso: String): String {
    return try {
        val instant = java.time.OffsetDateTime.parse(iso).toInstant()
        val mins = java.time.Duration.between(instant, java.time.Instant.now()).toMinutes()
        when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            mins < 60 * 24 * 30 -> "${mins / (60 * 24)}d ago"
            else -> "${mins / (60 * 24 * 30)}mo ago"
        }
    } catch (_: Exception) { "" }
}
