package com.pockethub.ui.download

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.R
import com.pockethub.data.local.DownloadEntity
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    initialTab: DownloadTab,
    onBack: () -> Unit,
    vm: DownloadViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(if (initialTab == DownloadTab.DONE) 1 else 0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.download_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            androidx.compose.material.icons.automirrored.outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.download_tab_active)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.download_tab_done)) },
                )
            }
            when (selectedTab) {
                0 -> ActiveDownloadsTab(vm = vm)
                else -> DoneDownloadsTab(vm = vm)
            }
        }
    }
}

@Composable
private fun ActiveDownloadsTab(vm: DownloadViewModel) {
    val active by vm.activeList.collectAsState()
    if (active.isEmpty()) {
        DownloadEmptyState(
            icon = Icons.Outlined.Download,
            message = stringResource(R.string.download_empty_active),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        items(active, key = { it.url }) { entity ->
            ActiveDownloadItem(
                entity = entity,
                onRetry = { vm.retry(entity.url) },
                onCancel = { vm.cancel(entity.url) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DoneDownloadsTab(vm: DownloadViewModel) {
    val done by vm.doneList.collectAsState()
    if (done.isEmpty()) {
        DownloadEmptyState(
            icon = Icons.Outlined.TaskAlt,
            message = stringResource(R.string.download_empty_done),
        )
        return
    }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        items(done, key = { it.url }) { entity ->
            DoneDownloadItem(
                entity = entity,
                onOpen = { openDownloadedFile(context, entity, vm) },
                onRemove = { vm.removeCompleted(entity.url) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ActiveDownloadItem(
    entity: DownloadEntity,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (entity.status == "FAILED") Icons.Outlined.Refresh else Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (entity.status == "FAILED")
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entity.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entity.repoKey + (entity.releaseTag?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (entity.status == "FAILED") {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.download_action_retry))
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.download_action_cancel))
                }
            }

            if (entity.status != "FAILED") {
                Spacer(Modifier.height(10.dp))
                if (entity.sizeBytes > 0) {
                    @Suppress("DEPRECATION")
                    LinearProgressIndicator(
                        progress = entity.progressPct.coerceIn(0, 100) / 100f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${entity.progressPct}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "${humanBytes(entity.downloadedBytes)} / ${humanBytes(entity.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text(
                    entity.errorMsg.ifBlank { stringResource(R.string.download_failed_generic) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DoneDownloadItem(
    entity: DownloadEntity,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entity.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${entity.repoKey}${if (entity.releaseTag.isNotBlank()) " · ${entity.releaseTag}" else ""} · ${humanBytes(entity.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onOpen) {
                Icon(
                    Icons.Outlined.TaskAlt,
                    contentDescription = stringResource(R.string.download_action_open),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.download_action_delete))
            }
        }
    }
}

@Composable
private fun DownloadEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun humanBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/**
 * Opens a completed download:
 *  - .apk → triggers the system PackageInstaller via the FileProvider.
 *  - other files → uses Intent.ACTION_VIEW with the mime type guessed by extension.
 */
private fun openDownloadedFile(
    context: android.content.Context,
    entity: DownloadEntity,
    vm: DownloadViewModel,
) {
    val file = File(entity.localPath)
    if (!file.exists()) {
        // Stale record — file was deleted out-of-band; auto-cleanup.
        vm.removeCompleted(entity.url)
        return
    }
    val authority = "${context.packageName}.fileprovider"
    val uri: Uri
    try {
        uri = FileProvider.getUriForFile(context, authority, file)
    } catch (_: Exception) {
        // FileProvider not configured / file not exposed — silently ignore.
        return
    }

    val intent = if (entity.fileName.lowercase().endsWith(".apk")) {
        // ACTION_INSTALL_PACKAGE is deprecated post-24; use ACTION_VIEW + INSTALL successful intent.
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, guessMime(entity.fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    // Grant read perms to all resolvable handlers for safety (some intent handlers only
    // accept it if FLAG_GRANT_READ_URI_PERMISSION is set AND the resolveInfo grants URI).
    val resolvers = context.packageManager.queryIntentActivities(intent, 0)
    for (r in resolvers) {
        context.grantUriPermission(
            r.activityInfo.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    runCatching { context.startActivity(intent) }
}

private fun guessMime(fileName: String): String {
    val map = mapOf(
        "zip" to "application/zip",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "txt" to "text/plain",
        "json" to "application/json",
        "md" to "text/markdown",
        "pdf" to "application/pdf",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
    )
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return map[ext] ?: "*/*"
}
