package com.pockethub.ui.repo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.R
import com.pockethub.data.download.DownloadManager
import com.pockethub.ui.download.DownloadViewModel
import com.pockethub.ui.markdown.CodeHighlighter
import com.pockethub.util.relativeTime
import com.pockethub.ui.components.Haptics




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
    var showFullViewer by rememberSaveable { mutableStateOf(false) }

    // Lazy initialise for this owner/repo pair on first composition.
    androidx.compose.runtime.LaunchedEffect(owner, repo) {
        vm.init(owner, repo)
    }

    // System back: close the open file or pop one directory before leaving the screen.
    BackHandler(enabled = state.viewingFile != null || state.currentPath.isNotBlank()) {
        vm.handleBack()
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
        // Branch selector row (when not viewing a file)
        if (state.viewingFile == null) {
            BranchSelector(
                currentRef = state.ref ?: defaultBranch ?: "main",
                branches = state.branches,
                isLoading = state.isLoadingBranches,
                onOpen = { vm.loadBranches() },
                onSelect = { vm.switchRef(it) },
            )
        }
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
            state.isLoading && state.entries.isEmpty() && state.viewingFile == null -> com.pockethub.ui.components.SkeletonList(
                Modifier.fillMaxSize(), rows = 8, topPadding = 8.dp,
            )

            state.viewingFile != null -> FileViewerContent(
                entry = state.viewingFile!!,
                content = state.fileContent,
                isLoading = state.isLoading,
                onClose = { vm.closeFile() },
                onDownload = { state.viewingFile?.let { downloadFile(it) } },
                onFullScreen = { showFullViewer = true },
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
                        lastCommit = state.lastCommits[entry.path],
                        onClick = {
                            if (entry.type == "dir") vm.openDir(entry.name) else vm.openFile(entry)
                        },
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

    if (showFullViewer && state.viewingFile != null) {
        FullScreenFileViewer(vm = vm, onDismiss = { showFullViewer = false })
    }
}

/** Branch selector chip + dropdown. Defaults to the repo's default branch. */
@Composable
private fun BranchSelector(
    currentRef: String,
    branches: List<com.pockethub.data.remote.GitHubApi.Branch>,
    isLoading: Boolean,
    onOpen: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    onOpen()
                    expanded = true
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.AccountTree,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                currentRef,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isLoading) {
                Spacer(Modifier.width(6.dp))
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            } else {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = stringResource(R.string.cd_switch_branch),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (branches.isEmpty() && !isLoading) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.branch_load_failed)) },
                    onClick = { expanded = false },
                )
            }
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = {
                        Text(
                            branch.name,
                            fontWeight = if (branch.name == currentRef) androidx.compose.ui.text.font.FontWeight.Bold else null,
                            color = if (branch.name == currentRef) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(branch.name)
                    },
                )
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
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.cd_open_in_browser))
        }
    }
}

@Composable
private fun ContentRow(
    entry: com.pockethub.data.remote.GitHubApi.ContentEntry,
    lastCommit: CodeBrowserViewModel.LastCommit? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = if (entry.type == "dir") Icons.Outlined.Folder else Icons.Outlined.Description
        Icon(
            icon, contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (entry.type == "dir") MaterialTheme.colorScheme.tertiary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // GitHub-style relative time, smaller font, muted — every file/dir.
        if (lastCommit != null) {
            Text(
                relativeTime(lastCommit.dateIso),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    onFullScreen: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val hapticView = androidx.compose.ui.platform.LocalView.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back_to_directory))
            }
            Text(
                entry.path,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (content != null) {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(content))
                    hapticView.let { com.pockethub.ui.components.Haptics.confirm(it) }
                    android.widget.Toast.makeText(context, context.getString(R.string.copied_toast), android.widget.Toast.LENGTH_SHORT).show()
                }) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFullScreen) {
                    Icon(
                        Icons.Outlined.Fullscreen,
                        contentDescription = stringResource(R.string.cd_fullscreen),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (isLoading) {
            com.pockethub.ui.components.SkeletonCodeLines(Modifier.fillMaxSize())
        } else if (content != null) {
            SyntaxHighlightedCode(
                code = content,
                fileName = entry.name,
                modifier = Modifier.fillMaxSize(),
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

/**
 * Files larger than this render without highlighting to keep the UI responsive.
 */
private const val HIGHLIGHT_MAX_CHARS = 200_000

/** Everything the code body needs, prepared OFF the main thread — splitting,
 *  gutter numbering and regex tokenizing of a large file all cost seconds on
 *  the UI thread and ANR the app (hit hardest by the offline zip viewer,
 *  whose files are not capped by the Contents API's 1MB limit). */
private class PreparedCode(
    val lineCount: Int,
    val gutterText: String,
    val highlighted: androidx.compose.ui.text.AnnotatedString,
)

/**
 * Code view with line numbers, syntax highlighting and horizontal scrolling.
 * Line numbers scroll vertically with the code but stay pinned at the start
 * of each line.
 */
@Composable
internal fun SyntaxHighlightedCode(
    code: String,
    fileName: String,
    modifier: Modifier = Modifier,
) {
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    // Capture the palette from the theme once, then cache the tokenized result.
    val colorScheme = MaterialTheme.colorScheme
    val palette = remember(colorScheme) {
        CodeHighlighter.Palette(
            keyword = colorScheme.primary,
            string = colorScheme.tertiary,
            comment = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            number = colorScheme.secondary,
            annotation = colorScheme.primary,
        )
    }
    // Heavy prep runs on a worker thread; composition only renders the result.
    val prepared by androidx.compose.runtime.produceState<PreparedCode?>(null, code, fileName, palette) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val truncated = code.take(HIGHLIGHT_MAX_CHARS)
            val lines = truncated.split("\n")
            PreparedCode(
                lineCount = lines.size,
                gutterText = (1..lines.size).joinToString("\n"),
                highlighted = CodeHighlighter.highlight(truncated, fileName, palette),
            )
        }
    }
    if (prepared == null) {
        // Spinner instead of a multi-second frozen frame.
        androidx.compose.foundation.layout.Box(
            modifier = modifier,
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        return
    }
    // Gutter hugs a 4-digit line number: ~8dp per monospace digit at
    // bodySmall size, plus a small leading inset (Start) and trailing gap.
    val gutterWidth = remember(prepared!!.lineCount) {
        val digits = prepared!!.lineCount.toString().length.coerceAtMost(4)
        (digits * 8 + 10).dp
    }
    val highlighted = prepared!!.highlighted
    val gutterText = prepared!!.gutterText
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        lineHeight = 18.sp,
    )

    Row(modifier.verticalScroll(vScroll)) {
        // Line-number gutter — one Text so line heights always match the code body.
        Text(
            text = gutterText,
            style = codeStyle,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .width(gutterWidth)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(start = 4.dp, end = 6.dp),
            softWrap = false,
        )
        // Code body
        Text(
            text = highlighted,
            style = codeStyle,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .horizontalScroll(hScroll),
            softWrap = false,
        )
    }
}

