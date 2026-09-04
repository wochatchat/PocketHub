package com.pockethub.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextOverflow
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.components.PhAsyncImage

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
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            event.actor?.let { actor ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhAsyncImage(
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onNavigateToUser(actor.login) },
                    )
                }
                Spacer(Modifier.width(0.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                event.createdAt?.let {
                    Text(
                        formatRelativeShort(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Map an [GitHubApi.IssueEvent] to (icon, message).
 *  Message text deliberately EXCLUDES the actor — the row renders the avatar
 *  + login separately, so including it here duplicated the name
 *  ("clawsweeper[bot] clawsweeper[bot] added the …"). */
private fun describeEvent(event: GitHubApi.IssueEvent): Pair<ImageVector, String> {
    return when (event.event) {
        "labeled" -> Icons.Outlined.Label to "added the ${event.label?.name.orEmpty()} label"
        "unlabeled" -> Icons.Outlined.Label to "removed the ${event.label?.name.orEmpty()} label"
        "assigned" -> Icons.Outlined.PersonAdd to "assigned ${event.assignee?.login.orEmpty()}"
        "unassigned" -> Icons.Outlined.Person to "unassigned ${event.assignee?.login.orEmpty()}"
        "closed" -> Icons.Outlined.CheckCircle to "closed this"
        "reopened" -> Icons.Outlined.Refresh to "reopened this"
        "locked" -> Icons.Outlined.Lock to "locked this"
        "unlocked" -> Icons.Outlined.LockOpen to "unlocked this"
        "milestoned" -> Icons.Outlined.Flag to "set milestone ${event.milestone?.title.orEmpty()}"
        "demilestoned" -> Icons.Outlined.Flag to "removed milestone ${event.milestone?.title.orEmpty()}"
        "referenced" -> Icons.Outlined.PushPin to "referenced this"
        "cross-referenced" -> Icons.Outlined.PushPin to "cross-referenced this"
        "renamed" -> Icons.Outlined.Edit to "renamed this issue"
        "merged" -> Icons.Outlined.CheckCircle to "merged this pull request"
        "head_ref_force_pushed" -> Icons.Outlined.Edit to "force-pushed the head branch"
        "review_requested" -> Icons.Outlined.PersonAdd to "requested a review from ${event.assignee?.login.orEmpty()}"
        else -> Icons.Outlined.Person to "performed ${event.event}"
    }
}

/** Localized "20分钟前"-style relative label; falls back to the raw ISO on parse failure. */
@Composable
private fun formatRelativeShort(iso: String): String {
    val mins = try {
        val instant = java.time.OffsetDateTime.parse(iso).toInstant()
        java.time.Duration.between(instant, java.time.Instant.now()).toMinutes()
    } catch (_: Exception) {
        return iso
    }
    return when {
        mins < 1 -> androidx.compose.ui.res.stringResource(com.pockethub.R.string.time_just_now)
        mins < 60 -> androidx.compose.ui.res.stringResource(com.pockethub.R.string.time_minutes_ago, mins)
        mins < 60 * 24 -> androidx.compose.ui.res.stringResource(com.pockethub.R.string.time_hours_ago, mins / 60)
        mins < 60L * 24 * 30 -> androidx.compose.ui.res.stringResource(com.pockethub.R.string.time_days_ago, mins / (60 * 24))
        else -> androidx.compose.ui.res.stringResource(com.pockethub.R.string.time_months_ago, mins / (60L * 24 * 30))
    }
}
