package com.pockethub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pockethub.R
import com.pockethub.data.model.FeedEvent

/** Format an ISO-8601 timestamp as a compact relative-time string. */
private fun formatRelativeTime(iso: String): String = try {
    val ts = java.time.OffsetDateTime.parse(iso.trim().replace("Z", "+00:00")).toInstant().toEpochMilli()
    val diffMs = (System.currentTimeMillis() - ts).coerceAtLeast(0)
    val mins = diffMs / 60_000
    when {
        mins < 1L    -> "now"
        mins < 60L   -> "${mins}m"
        mins < 1440L -> "${mins / 60}h"
        else         -> "${mins / 1440}d"
    }
} catch (_: Exception) {
    iso.take(10)
}

/**
 * One card per public GitHub activity event (PushEvent / WatchEvent / ForkEvent / …).
 * Shared between ProfileScreen ("my activity") and UserDetailScreen ("their activity")
 * so the activity stream renders identically in both places.
 */
@Composable
fun ActivityCard(
    event: FeedEvent,
    onNavigateToRepo: (String) -> Unit,
    onNavigateToIssue: ((String, String, Int) -> Unit)? = null,
    onNavigateToPR: ((String, String, Int) -> Unit)? = null,
    onNavigateToCommit: ((String, String, String) -> Unit)? = null,
) {
    val (icon, verb) = when (event.type) {
        "PushEvent" -> Icons.Outlined.CloudUpload to stringResource(R.string.event_pushed)
        "WatchEvent" -> Icons.Outlined.Star to stringResource(R.string.event_starred)
        "ForkEvent" -> Icons.Outlined.ForkRight to stringResource(R.string.event_forked)
        "CreateEvent" -> Icons.Outlined.CreateNewFolder to stringResource(R.string.event_created)
        "IssueCommentEvent" -> Icons.Outlined.Comment to stringResource(R.string.event_commented)
        "IssuesEvent" -> Icons.Outlined.ErrorOutline to stringResource(R.string.event_opened_issue)
        "PullRequestEvent" -> Icons.Outlined.Merge to stringResource(R.string.event_pull_request)
        "ReleaseEvent" -> Icons.Outlined.NewReleases to stringResource(R.string.event_released)
        "DeleteEvent" -> Icons.Outlined.Delete to stringResource(R.string.event_deleted)
        "PublicEvent" -> Icons.Outlined.Public to stringResource(R.string.event_made_public)
        else -> Icons.Outlined.History to event.type.removeSuffix("Event")
    }

    val repoName = event.repo?.name ?: ""
    val summary = when (event.type) {
        "PushEvent" -> event.payload?.commits?.firstOrNull()?.message?.take(80)?.let { "→ $it" } ?: ""
        "CreateEvent" -> event.payload?.ref?.let { stringResource(R.string.event_ref_suffix, it) } ?: ""
        "DeleteEvent" -> event.payload?.ref?.let { stringResource(R.string.event_ref_suffix, it) } ?: ""
        "PullRequestEvent" -> event.payload?.pullRequest?.title?.take(80) ?: ""
        "ForkEvent" -> event.payload?.forkee?.fullName ?: ""
        "IssueCommentEvent" -> event.payload?.pullRequest?.title?.take(80) ?: ""
        "IssuesEvent" -> event.payload?.action ?: ""
        else -> ""
    }
    val createdAt = event.createdAt?.let { formatRelativeTime(it) } ?: ""

    // Click target: land as deep as the event allows — commit for pushes, the
    // issue/PR for issue activity, the fork for forks — falling back to the repo.
    val openEvent: (() -> Unit)? = run {
        val parts = repoName.split("/", limit = 2)
        val owner = parts.getOrNull(0)
        val repo = parts.getOrNull(1)
        if (owner.isNullOrBlank() || repo.isNullOrBlank()) return@run null
        val issue = event.payload?.issue
        val prNumber = event.payload?.pullRequest?.number?.takeIf { it > 0 }
            ?: issue?.number?.takeIf { it > 0 && issue.pullRequest != null }
        val issueNumber = issue?.number?.takeIf { it > 0 && issue.pullRequest == null }
        val sha = event.payload?.commits?.lastOrNull()?.sha?.takeIf { it.isNotBlank() }

        val commitAction: (() -> Unit)? =
            if (event.type == "PushEvent" && sha != null) { { onNavigateToCommit?.invoke(owner, repo, sha) } } else null
        val prAction: (() -> Unit)? =
            if (prNumber != null && (event.type == "PullRequestEvent" || event.type == "IssueCommentEvent")) {
                { onNavigateToPR?.invoke(owner, repo, prNumber) }
            } else null
        val issueAction: (() -> Unit)? =
            if (issueNumber != null && (event.type == "IssueCommentEvent" || event.type == "IssuesEvent")) {
                { onNavigateToIssue?.invoke(owner, repo, issueNumber) }
            } else null
        val forkAction: (() -> Unit)? =
            if (event.type == "ForkEvent" && !event.payload?.forkee?.fullName.isNullOrBlank()) {
                { onNavigateToRepo(event.payload!!.forkee!!.fullName!!) }
            } else null
        val repoAction: (() -> Unit)? =
            if (repoName.isNotEmpty()) { { onNavigateToRepo(repoName) } } else null

        commitAction ?: prAction ?: issueAction ?: forkAction ?: repoAction
    }

    PhCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = openEvent,
        cornerRadius = 16.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Icon with colored background circle
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    verb,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    repoName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                createdAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
        }
    }
}
