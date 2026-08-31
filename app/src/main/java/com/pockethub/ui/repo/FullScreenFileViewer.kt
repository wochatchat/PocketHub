package com.pockethub.ui.repo

// Full-screen text-file viewer with an IDE-style file-tree side panel.
// Opened from the Code tab's inline file viewer via the fullscreen button;
// shares the [CodeBrowserViewModel] so navigation state stays in sync.

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pockethub.R
import com.pockethub.data.remote.GitHubApi
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Data surface the full-screen viewer needs. Implemented by the GitHub-backed
 * [CodeBrowserViewModel] and the offline zip viewer ([com.pockethub.ui.offline.OfflineCodeViewModel]),
 * so the IDE-style UI (file tree + syntax-highlighted body) is shared verbatim.
 */
interface FullScreenViewerHost {
    val state: StateFlow<CodeBrowserViewModel.State>
    fun loadTree()
    fun openFile(entry: GitHubApi.ContentEntry)
}

/**
 * IDE-style full-screen code viewer: top bar (file name/path + tree toggle +
 * copy), a collapsible left file-tree panel (recursive git tree with
 * expandable folders, current file highlighted) and the syntax-highlighted
 * code body. Hosted by any [FullScreenViewerHost].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun FullScreenFileViewer(
    vm: FullScreenViewerHost,
    onDismiss: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var treeOpenSaved by rememberSaveable { mutableStateOf(true) }

    // ── Same-layer sliding unit ─────────────────────────────────────────
    // The tree and the code body sit side by side in one Row that slides
    // as a single unit: offset 0 = tree fully off-screen (code fullscreen),
    // treeWidthPx = tree flush at the start edge with the code pushed
    // right. Pointer right ⇒ offset grows ⇒ the unit follows the finger;
    // anchors clamp, so a fully-open drawer cannot be dragged further
    // right and a hidden one not further left.
    val treeWidthPx = with(density) { TREE_PANEL_WIDTH.toPx() }
    val dragState = remember {
        AnchoredDraggableState(
            initialValue = if (treeOpenSaved) TreeSide.Open else TreeSide.Closed,
            positionalThreshold = { totalDistance -> totalDistance * 0.5f },
            velocityThreshold = { with(density) { 500.dp.toPx() } },
            snapAnimationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            decayAnimationSpec = exponentialDecay(),
        )
    }
    LaunchedEffect(treeWidthPx) {
        dragState.updateAnchors(
            DraggableAnchors {
                TreeSide.Closed at 0f
                TreeSide.Open at treeWidthPx
            },
        )
    }
    // 1f = fully open, 0f = fully hidden — drives the toggle tint gradient.
    val treeFraction by remember(treeWidthPx) {
        derivedStateOf {
            val off = dragState.offset
            if (off.isNaN()) (if (treeOpenSaved) 1f else 0f) else (off / treeWidthPx).coerceIn(0f, 1f)
        }
    }
    val treeVisible = treeFraction > 0.5f

    fun toggleTree() {
        val opening = dragState.targetValue != TreeSide.Open
        treeOpenSaved = opening
        scope.launch {
            dragState.animateTo(if (opening) TreeSide.Open else TreeSide.Closed)
        }
    }
    fun closeTree() {
        treeOpenSaved = false
        scope.launch { dragState.animateTo(TreeSide.Closed) }
    }

    // Handover between the code scroller and the sliding unit: while the
    // code body can consume a horizontal drag it keeps it; once it hits a
    // scroll edge the leftover delta drives the unit (rightward swipes at
    // the code's start pull the tree out, leftward swipes at its end push
    // it back), and the fling velocity settles it at the nearest anchor.
    // Nested-scroll deltas arrive in raw pointer direction (positive x =
    // finger right = offset grows), so they map 1:1 onto the offset.
    val bodyNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.x == 0f || dragState.isAnimationRunning) return Offset.Zero
                val used = dragState.dispatchRawDelta(available.x)
                return Offset(used, 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val settled = dragState.settle(available.x)
                return Velocity(settled, 0f)
            }
        }
    }

    LaunchedEffect(Unit) { vm.loadTree() }
    // Back first retracts the drawer, then leaves the viewer.
    BackHandler {
        if (treeVisible) closeTree() else onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    fileName = state.viewingFile?.name ?: state.repo,
                    filePath = state.viewingFile?.path ?: "",
                    treeFraction = treeFraction,
                    content = state.fileContent,
                    onToggleTree = ::toggleTree,
                    onCopy = {
                        state.fileContent?.let {
                            clipboard.setText(AnnotatedString(it))
                            Toast.makeText(context, context.getString(R.string.copied_toast), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = onDismiss,
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // The sliding unit: [file tree | divider | code body] side
                // by side. The custom layout modifier widens the unit by the
                // still-hidden tree width and places it left by the same
                // amount, so the code body is exactly full-screen when the
                // tree is hidden and shrinks smoothly as the tree pushes in.
                // The anchoredDraggable sits BEFORE it: its hit region stays
                // the full screen, drags no child scroller claims (gutter,
                // empty space, the tree itself) move the unit directly, and
                // code-scroll drags keep their original behavior.
                Row(
                    Modifier
                        .anchoredDraggable(dragState, Orientation.Horizontal)
                        .nestedScroll(bodyNestedScroll)
                        .layout { measurable, constraints ->
                            val off = dragState.offset
                            val unitOff = if (off.isNaN()) {
                                // Anchors not landed yet (first frame): use
                                // the saved side so nothing flashes.
                                if (treeOpenSaved) treeWidthPx else 0f
                            } else {
                                off
                            }
                            val extra = (treeWidthPx - unitOff).roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + extra),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                placeable.placeRelative(-extra, 0)
                            }
                        }
                        .fillMaxSize(),
                ) {
                    FileTreePanel(
                        vm = vm,
                        modifier = Modifier
                            .width(TREE_PANEL_WIDTH)
                            .fillMaxHeight(),
                    )
                    if (treeFraction > 0.05f) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(0.5.dp),
                        )
                    }
                    // Code body — with the tree hidden, a leftward swipe only
                    // ever scrolls the code; a rightward swipe scrolls until
                    // the code hits its start edge, then the leftover delta
                    // pulls the whole unit — tree included — back out.
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        val content = state.fileContent
                        val entry = state.viewingFile
                        when {
                            entry == null -> EmptyHint()
                            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(strokeWidth = 2.dp)
                            }
                            content != null -> SyntaxHighlightedCode(
                                code = content,
                                fileName = entry.name,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Viewer-host errors: offline markers get real copy,
                            // anything else is already a user-readable message.
                            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    when (state.error) {
                                        "extract_failed" -> stringResource(R.string.offline_extract_failed)
                                        "unpreviewable" -> stringResource(R.string.binary_preview_unavailable)
                                        else -> state.error!!
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> EmptyHint()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    fileName: String,
    filePath: String,
    treeFraction: Float,
    content: String?,
    onToggleTree: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        title = {
            Column {
                Text(
                    fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    filePath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            IconButton(onClick = onToggleTree) {
                // Tracks the panel continuously: tint lerps muted → primary
                // and the icon swaps Filled/Outlined halfway. Reads the drag
                // offset every frame, so it "breathes" with the panel while
                // the user slides it.
                val accent = lerp(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.primary,
                    treeFraction,
                )
                Icon(
                    if (treeFraction > 0.5f) Icons.Filled.ViewSidebar else Icons.Outlined.ViewSidebar,
                    contentDescription = stringResource(R.string.cd_toggle_file_tree),
                    tint = accent,
                )
            }
            if (content != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

// ── File tree panel ─────────────────────────────────────────────────

/** Width of the file-tree drawer panel. */
private val TREE_PANEL_WIDTH = 200.dp

/** Anchors of the tree drawer: [Open] flush at the start edge, [Closed] slid out. */
private enum class TreeSide { Open, Closed }

private data class TreeRow(
    val entry: GitHubApi.GitTreeEntry,
    val depth: Int,
)

/**
 * IDE-style file tree: directories first (expand/collapse), then files,
 * both alphabetically. Clicking a file loads it through [vm.openFile].
 */
@Composable
private fun FileTreePanel(
    vm: FullScreenViewerHost,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }

    // On first tree load, expand the current file's ancestor folders so the
    // opened file is visible in the tree.
    LaunchedEffect(state.fullTree) {
        val cur = state.viewingFile?.path ?: return@LaunchedEffect
        val ancestors = buildList {
            var acc = ""
            cur.substringBeforeLast('/').split('/').forEach { seg ->
                acc = if (acc.isEmpty()) seg else "$acc/$seg"
                add(acc)
            }
        }
        expanded = expanded + ancestors
    }

    val rows = remember(state.fullTree, expanded, query) {
        if (query.isNotBlank()) {
            // Filter mode: flat list of everything matching the query, indented
            // by path depth — this is how very long trees stay navigable.
            state.fullTree
                .filter { it.path.contains(query.trim(), ignoreCase = true) }
                .sortedBy { it.path.lowercase() }
                .map { TreeRow(it, depth = it.path.count { ch -> ch == '/' }.coerceAtMost(4)) }
        } else {
            buildList {
                val children = state.fullTree.groupBy { it.path.substringBeforeLast('/', "") }
                fun walk(parent: String, depth: Int) {
                    val dirs = children[parent].orEmpty()
                        .filter { it.type == "tree" }
                        .sortedBy { it.path.substringAfterLast('/').lowercase() }
                    val files = children[parent].orEmpty()
                        .filter { it.type == "blob" }
                        .sortedBy { it.path.substringAfterLast('/').lowercase() }
                    dirs.forEach { dir ->
                        add(TreeRow(dir, depth))
                        if (dir.path in expanded) walk(dir.path, depth + 1)
                    }
                    files.forEach { add(TreeRow(it, depth)) }
                }
                walk("", 0)
            }
        }
    }

    Column(modifier) {
        Text(
            state.repo,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        // Filter box — type to search the whole tree by path.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.file_tree_search), style = MaterialTheme.typography.labelSmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        when {
            state.isLoadingTree -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
            rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (query.isNotBlank()) R.string.file_tree_no_match else R.string.file_tree_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.entry.path }) { row ->
                    TreeRowItem(
                        row = row,
                        isCurrent = row.entry.path == state.viewingFile?.path,
                        isExpanded = row.entry.path in expanded,
                        onClick = {
                            if (row.entry.type == "tree") {
                                expanded = if (row.entry.path in expanded) expanded - row.entry.path
                                else expanded + row.entry.path
                            } else {
                                vm.openFile(row.entry.toContentEntry())
                            }
                        },
                    )
                }
                if (state.treeTruncated) {
                    item {
                        Text(
                            stringResource(R.string.file_tree_truncated),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeRowItem(
    row: TreeRow,
    isCurrent: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val name = row.entry.path.substringAfterLast('/')
    val isDir = row.entry.type == "tree"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(start = (12 + row.depth * 14).dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when {
            isDir && isExpanded -> Icons.Outlined.FolderOpen
            isDir -> Icons.Outlined.Folder
            else -> Icons.Outlined.Description
        }
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isDir) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun GitHubApi.GitTreeEntry.toContentEntry(): GitHubApi.ContentEntry =
    GitHubApi.ContentEntry(
        name = path.substringAfterLast('/'),
        path = path,
        sha = sha,
        type = "file",
        size = size,
    )

@Composable
private fun EmptyHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.file_viewer_pick_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}