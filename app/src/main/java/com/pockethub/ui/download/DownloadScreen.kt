package com.pockethub.ui.download

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.R
import com.pockethub.util.humanBytes
import com.pockethub.data.download.openLocalFile
import com.pockethub.data.local.DownloadEntity
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    initialTab: DownloadTab,
    onBack: () -> Unit,
    vm: DownloadViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(if (initialTab == DownloadTab.DONE) 1 else 0) }

    val active by vm.activeList.collectAsState()

    // Auto-switch to the Done tab when the last active download completes.
    var hadActive by remember { mutableStateOf(active.isNotEmpty()) }
    LaunchedEffect(active.size) {
        if (active.isEmpty() && hadActive) {
            selectedTab = 1
        }
        hadActive = active.isNotEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.download_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    DownloadFolderButton(vm = vm)
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
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

/**
 * Top-bar entry for the download location (moved off the former inline card).
 * Tap: system folder picker (supports creating a new folder); finished
 * downloads are mirrored there. Long-press: back to the app default.
 * The icon is tinted primary while a custom folder is active.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DownloadFolderButton(vm: DownloadViewModel) {
    val context = LocalContext.current
    val folderUri by vm.downloadFolderUri.collectAsState()
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.setDownloadFolder(uri.toString())
        }
    }
    IconButton(onClick = { picker.launch(null) }) {
        Icon(
            Icons.Outlined.FolderOpen,
            contentDescription = stringResource(R.string.download_folder_change),
            tint = if (folderUri != null) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                onOpen = {
                    val f = File(entity.localPath)
                    if (!f.exists()) {
                        // Stale record — file deleted out-of-band; auto-cleanup.
                        vm.removeCompleted(entity.url)
                    } else {
                        openLocalFile(context, f)
                    }
                },
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
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
                        modifier = Modifier.size(18.dp),
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
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.download_action_delete))
            }
        }
    }
}

@Composable
private fun DownloadEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    com.pockethub.ui.components.EmptyStateV2(icon = icon, title = message)
}
