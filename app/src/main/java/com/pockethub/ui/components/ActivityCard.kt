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
import com.pockethub.R
import com.pockethub.data.model.FeedEvent

/**
 * One card per public GitHub activity event (PushEvent / WatchEvent / ForkEvent / …).
 * Shared between ProfileScreen ("my activity") and UserDetailScreen ("their activity")
 * so the activity stream renders identically in both places.
 */
@Composable
fun ActivityCard(
    event: FeedEvent,
    onNavigateToRepo: (String) -> Unit,
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
    val createdAt = event.createdAt?.take(10) ?: ""

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable { if (repoName.isNotEmpty()) onNavigateToRepo(repoName) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                "$verb ${repoName}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
