package com.pockethub.ui.repo

// Releases tab: release cards with assets + date/size formatting.
// Split out of RepoDetailScreen.kt for readability.

import com.pockethub.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.remember
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.markdown.MarkdownText
import com.pockethub.ui.components.PhAsyncImage
import java.text.DateFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReleasesTab(
    releases: List<GitHubApi.Release>,
    repoContext: String,
    defaultBranch: String? = null,
    canDelete: Boolean = false,
    isDeletingRelease: Boolean = false,
    isLoading: Boolean = false,
    onLinkClick: (String, com.pockethub.ui.markdown.LinkKind) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    onDownloadAsset: (GitHubApi.Release.ReleaseAsset) -> Unit = {},
    onDeleteRelease: (Long) -> Unit = {},
) {
    if (isLoading && releases.isEmpty()) {
        com.pockethub.ui.components.SkeletonList(Modifier.fillMaxSize(), rows = 6, topPadding = 8.dp)
        return
    }
    if (releases.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Campaign,
                    null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.no_releases_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(releases, key = { it.id }) { release ->
            var showDeleteConfirm by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(release.tagName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (release.prerelease) {
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.pre_release), style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (canDelete) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !isDeletingRelease,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete_release),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                release.name?.let { if (it != release.tagName) Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                release.publishedAt?.let {
                    Text(
                        stringResource(R.string.released_at, formatDate(it)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (release.author != null) {
                    val author = release.author
                    val authorClick = Modifier.clickable { onNavigateToUser(author.login) }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PhAsyncImage(
                            model = author.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).clip(CircleShape).then(authorClick),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.by_author, author.login),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = authorClick,
                        )
                    }
                }
                if (!release.body.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    MarkdownText(
                        markdown = release.body.take(2000),
                        modifier = Modifier.fillMaxWidth(),
                        repoContext = repoContext,
                        defaultBranch = defaultBranch,
                        onLinkClick = onLinkClick,
                    )
                }
                if (release.assets.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    release.assets.forEach { asset ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onDownloadAsset(asset) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.asset_download, asset.name, humanReadableSize(asset.size), asset.downloadCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()

            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { if (!isDeletingRelease) showDeleteConfirm = false },
                    title = { Text(stringResource(R.string.delete_release_title)) },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.delete_release_warning, release.tagName),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                onDeleteRelease(release.id)
                            },
                            enabled = !isDeletingRelease,
                        ) { Text(stringResource(R.string.delete_release_confirm), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirm = false },
                            enabled = !isDeletingRelease,
                        ) { Text(stringResource(R.string.action_cancel)) }
                    },
                )
            }
        }
    }
}


internal fun formatDate(s: String): String = try {
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(java.time.OffsetDateTime.parse(s))
} catch (_: Exception) { s.take(10) }

internal fun humanReadableSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

