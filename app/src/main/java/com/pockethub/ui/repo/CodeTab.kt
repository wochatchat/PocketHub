package com.pockethub.ui.repo

import com.pockethub.R
import com.pockethub.data.download.DownloadManager
import com.pockethub.ui.download.DownloadViewModel

import androidx.compose.ui.res.stringResource

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Code tab — directory navigation with inline text file viewer.
 */
@Composable
fun CodeTab(
    owner: String,
    repo: String,
    defaultBranch: String? = null,
    onOpenInBrowser: () -> Unit = {},
    downloadVm: DownloadViewModel = hiltViewModel(),
    onNavigateToDownloads: (String) -> Unit = {},
    vm: CodeBrowserViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    // Lazy initialise for this owner/repo pair on first composition.
    androidx.compose.runtime.LaunchedEffect(owner, repo) {
        vm.init(owner, repo)
    }

    // Download a single file (raw download_url) — used for archives / binaries
    // that can't be previewed inline, but available for any file.
    fun downloadFile(entry: com.pockethub.data.remote.GitHubApi.ContentEntry) {
        val url = entry.downloadUrl ?: return
        downloadVm.enqueue(
            DownloadManager.EnqueueRequest(
                url = url,
                fileName = entry.name,
                contentType = guessAssetMime(entry.name),
                sizeBytes = entry.size,
                repoKey = "$owner/$repo",
                releaseTag = "",
            )
        )
        onNavigateToDownloads("active")
    }

    // Download the whole repository at the current ref as a ZIP (GitHub zipball).
    val ref = state.ref ?: defaultBranch ?: "HEAD"
    val onDownloadZip: () -> Unit = {
        downloadVm.enqueue(
            DownloadManager.EnqueueRequest(
                url = "https://api.github.com/repos/$owner/$repo/zipball/$ref",
                fileName = "$repo-$ref.zip",
                contentType = "application/zip",
                sizeBytes = 0,
                repoKey = "$owner/$repo",
                releaseTag = ref,
            )
        )
        onNavigateToDownloads("active")
    }

    Column(Modifier.fillMaxSize()) {
        // Breadcrumb bar (when not viewing a file)
        if (state.viewingFile == null) {
            BreadcrumbBar(
                pathStack = state.pathStack,
                currentPath = state.currentPath,
                canGoUp = state.currentPath.isNotBlank(),
                onUp = { vm.popDir() },
                onJump = { vm.listDir(it) },
                onOpenInBrowser = onOpenInBrowser,
                onDownloadZip = onDownloadZip,
            )
        }

        when {
            state.isLoading && state.entries.isEmpty() && state.viewingFile == null -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.viewingFile != null -> FileViewerContent(
                entry = state.viewingFile!!,
                content = state.fileContent,
                isLoading = state.isLoading,
                onClose = { vm.closeFile() },
                onDownload = { state.viewingFile?.let { downloadFile(it) } },
            )

            state.error != null && state.entries.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error ?: stringResource(R.string.error_load_files), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.listDir(state.currentPath) }) { Text(stringResource(R.string.action_retry)) }
            }

            else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(state.entries, key = { it.path + it.sha + it.type }) { entry ->
                    ContentRow(
                        entry = entry,
                        onClick = {
                            if (entry.type == "dir") vm.openDir(entry.name) else vm.openFile(entry)
                        },
                        onDownload = if (entry.type == "file") {
                            { downloadFile(entry) }
                        } else null,
                    )
                }
                if (state.entries.isEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.directory_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    pathStack: List<String>,
    currentPath: String,
    canGoUp: Boolean,
    onUp: () -> Unit,
    onJump: (String) -> Unit,
    onOpenInBrowser: () -> Unit,
    onDownloadZip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoUp) {
            IconButton(onClick = onUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_up))
            }
        }
        pathStack.forEachIndexed { idx, path ->
            val label = if (idx == 0) stringResource(R.string.breadcrumb_root) else path.substringAfterLast('/')
            if (idx > 0) Text(" / ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (path == currentPath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (path == currentPath) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                    .clickable { onJump(path) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDownloadZip) {
            Icon(Icons.Outlined.FolderZip, contentDescription = stringResource(R.string.cd_download_zip))
        }
        IconButton(onClick = onOpenInBrowser) {
            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.cd_open_in_browser))
        }
    }
}

@Composable
private fun ContentRow(
    entry: com.pockethub.data.remote.GitHubApi.ContentEntry,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (entry.type == "dir") Icons.Outlined.Folder else Icons.Outlined.Description
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
            if (entry.type == "file" && entry.size > 0) {
                Text(humanReadableSize(entry.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onDownload != null) {
            IconButton(onClick = onDownload) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = stringResource(R.string.cd_download_file),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FileViewerContent(
    entry: com.pockethub.data.remote.GitHubApi.ContentEntry,
    content: String?,
    isLoading: Boolean,
    onClose: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back_to_directory))
            }
            Text(entry.path, style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (content != null) {
            val hScroll = rememberScrollState()
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .horizontalScroll(hScroll),
            )
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.AutoMirrored.Outlined.Article, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.binary_preview_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDownload) {
                    Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_download))
                }
            }
        }
    }
}

private fun humanReadableSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

