package com.pockethub.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pockethub.R
import com.pockethub.data.remote.UpdateChecker
import java.util.Locale

/**
 * In-place updater flow: prompt → download (with progress) → install, without
 * leaving the app. The dialog never opens the browser; the APK is fetched into
 * cache and handed to the system PackageInstaller via a FileProvider URI.
 *
 * The layout uses [Dialog] (not AlertDialog) so the body can grow taller and the
 * buttons wrap on narrow screens via [FlowRow], fixing text-overflow on small
 * devices.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateDialog(
    info: UpdateChecker.UpdateInfo,
    downloadState: UpdateViewModel.DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: (path: String) -> Unit,
    onRetry: () -> Unit,
    onIgnore: () -> Unit,
    onLater: () -> Unit,
) {
    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = stringResource(R.string.update_version_line, info.latestVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                info.publishedAt?.let {
                    Text(
                        text = stringResource(R.string.update_published, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                info.releaseNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                    val items = parseChangelogItems(notes).take(8)
                    if (items.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items.forEach { item ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        text = item.tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = item.tagColor,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(56.dp),
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Download progress / status surface — only rendered when relevant.
                when (val ds = downloadState) {
                    is UpdateViewModel.DownloadState.Running -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { ds.progressPct / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val status = if (ds.totalBytes > 0) {
                                "${humanBytes(ds.downloadedBytes)} / ${humanBytes(ds.totalBytes)}  ·  ${ds.progressPct}%"
                            } else {
                                "${humanBytes(ds.downloadedBytes)}  ·  ${ds.progressPct}%"
                            }
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    is UpdateViewModel.DownloadState.Done -> {
                        Text(
                            text = stringResource(R.string.update_downloaded_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is UpdateViewModel.DownloadState.Failed -> {
                        Text(
                            text = stringResource(R.string.update_download_failed, ds.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> Unit
                }

                Spacer(Modifier.size(2.dp))

                // Actions — wrapped so tight screens don't squeeze the labels.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (val ds = downloadState) {
                        is UpdateViewModel.DownloadState.Running -> {
                            TextButton(onClick = onCancel) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                        is UpdateViewModel.DownloadState.Done -> {
                            Button(onClick = { onInstall(ds.path) }) {
                                Text(stringResource(R.string.action_install))
                            }
                            TextButton(onClick = onLater) {
                                Text(stringResource(R.string.action_remind_later))
                            }
                        }
                        is UpdateViewModel.DownloadState.Failed -> {
                            Button(onClick = onRetry) {
                                Text(stringResource(R.string.action_retry))
                            }
                            TextButton(onClick = onIgnore) {
                                Text(stringResource(R.string.action_ignore_version))
                            }
                            TextButton(onClick = onLater) {
                                Text(stringResource(R.string.action_remind_later))
                            }
                        }
                        else -> {
                            Button(onClick = onDownload) {
                                Text(stringResource(R.string.action_download))
                            }
                            TextButton(onClick = onIgnore) {
                                Text(stringResource(R.string.action_ignore_version))
                            }
                            TextButton(onClick = onLater) {
                                Text(stringResource(R.string.action_remind_later))
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper: display bytes with a single-decimal unit string (e.g. "8.5 MB").
private fun humanBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/** A skimmable changelog line shown in the update dialog. */
private data class ChangeItem(
    val tag: String,
    val text: String,
    val tagColor: Color,
)

/**
 * Parse raw release notes into a flat list of short skimmable items.
 *
 * Recognises the conventional-commit prefix (`feat(scope):` / `fix:` / `chore:` …)
 * and converts each line into a 3-4 char uppercase category tag plus the rest
 * of the message. Lines without a recognisable prefix get a "+ tag.
 *
 * Only bullet / `- ` lines or pure-message lines are kept; HTML or section
 * headers (lines starting with `#`) are dropped, since we want a tall vertical
 * list rather than a markdown essay.
 */
private fun parseChangelogItems(notes: String): List<ChangeItem> {
    val featColor = Color(0xFF3FB950)
    val fixColor = Color(0xFF58A6FF)
    val refactorColor = Color(0xFFD29922)
    val choreColor = Color(0xFF8B949E)
    val otherColor = Color(0xFF8B949E)

    return notes.lines().mapNotNull { rawLine ->
        // Strip leading "- " / "* " bullet.
        val line = rawLine.trim().removePrefix("-").removePrefix("*").trim()
        if (line.isEmpty()) return@mapNotNull null
        // Drop markdown section headers like "## v0.1.44".
        if (line.startsWith("#")) return@mapNotNull null

        // Conventional-commit prefix match: "type(scope): message" or "type: message".
        val match = Regex("^(feat|fix|chore|refactor|docs|style|test|perf|build|ci|revert)(\\([^)]+\\))?[:：]\\s*(.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(line)
        if (match != null) {
            val type = match.groupValues[1].lowercase()
            val msg = match.groupValues[3].trim()
            val tagText = when (type) {
                "feat" -> "feat"
                "fix" -> "fix"
                "refactor", "refact" -> "ref"
                "chore" -> "chore"
                "docs" -> "docs"
                "perf" -> "perf"
                "test" -> "test"
                "ci" -> "ci"
                "build" -> "build"
                "style" -> "style"
                "revert" -> "rev"
                else -> "feat"
            }
            val color = when (type) {
                "feat" -> featColor
                "fix" -> fixColor
                "refactor" -> refactorColor
                "chore" -> choreColor
                "docs", "style", "test" -> choreColor
                "perf", "build", "ci" -> choreColor
                "revert" -> fixColor
                else -> otherColor
            }
            return@mapNotNull ChangeItem(tagText, msg, color)
        }
        // Plain line — show with a "+" tag.
        ChangeItem("+", line, otherColor)
    }
}
