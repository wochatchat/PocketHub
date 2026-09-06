package com.pockethub.ui.repo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockethub.R
import com.pockethub.util.humanBytes
import com.pockethub.util.parseIsoSafe
import com.pockethub.data.download.openLocalFile
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.repo.WorkflowRunDetailViewModel.ArtifactUi
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * "Artifacts" section for the workflow-run detail screen. Lists everything the
 * run uploaded via `actions/upload-artifact` (any format — GitHub stores each
 * artifact as one zip), lets the user download a zip in-app and, once the
 * download lands, auto-extracts and shows the inner files (tap to open / install APK).
 */
@Composable
fun ArtifactsSection(
    artifacts: List<ArtifactUi>,
    loading: Boolean,
    error: String?,
    onDownload: (GitHubApi.Artifact) -> Unit,
    onRetryDownload: (GitHubApi.Artifact) -> Unit,
    onRetryList: () -> Unit,
    dateFmt: DateFormat,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${artifacts.size} ${stringResource(R.string.workflow_artifacts_title)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        when {
            loading && artifacts.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            artifacts.isEmpty() && error != null -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetryList) {
                        Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
            artifacts.isEmpty() -> {
                Text(
                    stringResource(R.string.workflow_artifacts_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.workflow_artifacts_empty_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            else -> {
                artifacts.forEach { ui ->
                    ArtifactCard(
                        ui = ui,
                        dateFmt = dateFmt,
                        onDownload = { onDownload(ui.artifact) },
                        onRetryDownload = { onRetryDownload(ui.artifact) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtifactCard(
    ui: ArtifactUi,
    dateFmt: DateFormat,
    onDownload: () -> Unit,
    onRetryDownload: () -> Unit,
) {
    val art = ui.artifact
    val dl = ui.download
    val files = ui.extractedFiles
    var expanded by remember(art.id) { mutableStateOf(false) }
    // Auto-expand when the extracted files first land (download + extract
    // finished) and KEEP it expanded afterwards — collapsing stays manual.
    // remember(art.id) alone can't do this: its initializer runs while files
    // is still null, so a completed download used to stay folded.
    LaunchedEffect(files) {
        if (!files.isNullOrEmpty()) expanded = true
    }
    val context = LocalContext.current
    val expiredLabel = stringResource(R.string.workflow_artifacts_expired)

    val downloading = dl?.status == "QUEUED" || dl?.status == "IN_PROGRESS"
    val failed = dl?.status == "FAILED"

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (art.expired) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    art.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(humanBytes(art.sizeInBytes))
                        art.createdAt?.let {
                            append(" · ${dateFmt.format(parseIsoSafe(it) ?: Date())}")
                        }
                        if (art.expired) append(" · ").append(expiredLabel)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (art.expired) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(6.dp))
            when {
                art.expired -> Unit
                downloading -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                failed -> {
                    IconButton(onClick = onRetryDownload, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_retry), modifier = Modifier.size(18.dp))
                    }
                }
                dl?.status == "DONE" && files != null -> {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(30.dp)) {
                        Icon(
                            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                else -> {
                    IconButton(onClick = onDownload, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.workflow_artifact_download), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (downloading) {
            Spacer(Modifier.height(2.dp))
            val progress = dl?.progressPct ?: 0
            if ((dl?.sizeBytes ?: 0) > 0) {
                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = progress.coerceIn(0, 100) / 100f,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${progress}% · ${humanBytes(dl?.downloadedBytes ?: 0)} / ${humanBytes(dl?.sizeBytes ?: 0)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (failed) {
            Text(
                dl?.errorMsg?.ifBlank { stringResource(R.string.workflow_artifact_download_failed) } ?: stringResource(R.string.workflow_artifact_download_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (ui.extracting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.workflow_artifact_extracting),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ui.extractError?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (expanded && files != null) {
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(2.dp))
            if (files.isEmpty()) {
                Text(
                    stringResource(R.string.workflow_artifact_empty_zip),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    files.forEach { f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { openLocalFile(context, File(f.path)) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (f.name.substringAfterLast('.', "").lowercase() in setOf("apk", "zip", "jar", "aab", "exe", "deb", "rpm")) {
                                    Icons.Outlined.Description
                                } else Icons.Outlined.InsertDriveFile,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                f.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                humanBytes(f.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
