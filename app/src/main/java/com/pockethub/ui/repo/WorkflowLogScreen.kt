package com.pockethub.ui.repo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.R
import com.pockethub.data.download.DownloadManager
import com.pockethub.ui.download.DownloadViewModel
import com.pockethub.util.humanBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SUCCESS_GREEN = Color(0xFF2EA043)
private val FAILURE_RED = Color(0xFFE5534B)
private val CANCELLED_ORANGE = Color(0xFFDB6D28)
private val HIGHLIGHT_YELLOW = Color(0x66FFD54D)

@Composable
private fun statusColor(status: String?, conclusion: String?): Color = when {
    conclusion == "success" -> SUCCESS_GREEN
    conclusion == "failure" || conclusion == "timed_out" -> FAILURE_RED
    conclusion == "cancelled" -> CANCELLED_ORANGE
    conclusion != null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // skipped / neutral
    status == "in_progress" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
}

/** Flat LazyColumn entry: one header per section + one row per line of expanded sections. */
private data class LogEntry(val sectionIdx: Int, val lineIdx: Int?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowLogScreen(
    owner: String,
    repo: String,
    runId: Long,
    jobId: Long,
    onBack: () -> Unit,
    vm: WorkflowLogViewModel = hiltViewModel(),
    downloadVm: DownloadViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val expandedSteps by vm.expandedSteps.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var matchCursor by rememberSaveable { mutableIntStateOf(-1) }
    var pendingJumpLine by remember { mutableStateOf<Int?>(null) }
    var followBottom by remember { mutableStateOf(false) }

    val parsed = state.parsed
    val job = state.jobs.firstOrNull { it.id == state.jobId }

    val (entries, lineToEntry) = remember(parsed, expandedSteps) {
        val list = ArrayList<LogEntry>()
        val map = HashMap<Int, Int>()
        parsed?.sections?.forEachIndexed { si, sec ->
            list.add(LogEntry(si, null))
            if (sec.number in expandedSteps) {
                for (l in sec.startLine until sec.endLine) {
                    map[l] = list.size
                    list.add(LogEntry(si, l))
                }
            }
        }
        list to map
    }

    // First load per job: failure auto-locates — expand the failed step and
    // jump to its first line (the single highest-value triage affordance).
    var autoJumpDone by remember(state.jobId) { mutableStateOf(false) }

    fun jumpToLine(lineIdx: Int) {
        val sec = parsed?.sections?.firstOrNull { lineIdx in it.startLine until it.endLine } ?: return
        if (sec.number !in expandedSteps) {
            vm.setExpanded(expandedSteps + sec.number)
            pendingJumpLine = lineIdx
        } else {
            lineToEntry[lineIdx]?.let { idx -> scope.launch { listState.scrollToItem(idx) } }
        }
    }

    LaunchedEffect(lineToEntry) {
        pendingJumpLine?.let { line ->
            lineToEntry[line]?.let { idx -> listState.scrollToItem(idx.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) }
            pendingJumpLine = null
            return@LaunchedEffect
        }
        if (followBottom.value && entries.isNotEmpty()) {
            followBottom.value = false
            listState.scrollToItem(entries.lastIndex)
        }
    }

    LaunchedEffect(state.jobId, parsed?.sections) {
        if (autoJumpDone || parsed == null) return@LaunchedEffect
        autoJumpDone = true
        val failed = parsed.sections.firstOrNull { it.conclusion == "failure" || it.conclusion == "timed_out" }
        if (failed != null) {
            vm.setExpanded(setOf(failed.number))
            pendingJumpLine = failed.startLine
        }
    }

    // Live tail: poll while the job is queued/in-progress; follow the bottom
    // only when the user is already reading the tail (never fight their scroll).
    LaunchedEffect(job?.id, job?.status) {
        val j = job ?: return@LaunchedEffect
        if (j.status == "completed") return@LaunchedEffect
        while (true) {
            delay(5_000)
            followBottom.value = !listState.canScrollForward
            vm.refresh()
        }
    }

    val displayLines = parsed?.lines ?: emptyList()
    val matches = remember(displayLines, query) {
        if (query.length < 2) emptyList()
        else displayLines.withIndex().filter { it.value.contains(query, ignoreCase = true) }.map { it.index }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Text(
                        job?.name?.ifBlank { "job ${state.jobId}" } ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) {
                                query = ""
                                matchCursor = -1
                            }
                        },
                    ) {
                        Icon(
                            if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.logs_search_hint),
                        )
                    }
                    IconButton(
                        enabled = parsed != null,
                        onClick = {
                            // Binder-transaction-safe cap for ACTION_SEND text
                            val raw = parsed?.raw?.take(800_000) ?: return@IconButton
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "${job?.name ?: "job"}.log")
                                putExtra(Intent.EXTRA_TEXT, raw)
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                    ) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share_logs)) }
                    IconButton(
                        enabled = parsed != null,
                        onClick = {
                            scope.launch {
                                val loc = vm.freshLocation() ?: return@launch
                                val fileName = (job?.name ?: "job-${state.jobId}")
                                    .replace(Regex("[^A-Za-z0-9._-]+"), "_") + ".log"
                                downloadVm.enqueue(
                                    DownloadManager.EnqueueRequest(
                                        url = loc,
                                        fileName = fileName,
                                        contentType = "text/plain",
                                        sizeBytes = 0L,
                                        repoKey = "$owner/$repo",
                                        releaseTag = "",
                                    )
                                )
                            }
                        },
                    ) { Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.action_download_logs)) }
                    IconButton(
                        enabled = job?.htmlUrl != null,
                        onClick = { job?.htmlUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it))) } },
                    ) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.logs_open_browser)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // ── Job switcher (multi-job runs) ──
            if (state.jobs.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    itemsIndexed(state.jobs, key = { _, j -> j.id }) { _, j ->
                        FilterChip(
                            selected = j.id == state.jobId,
                            onClick = {
                                if (j.id != state.jobId) {
                                    matchCursor = -1
                                    query = ""
                                    vm.load(j.id)
                                }
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StepDot(j.status, j.conclusion)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        j.name.ifBlank { "job ${j.id}" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // ── Step timeline (tap = expand + jump) ──
            if ((parsed?.sections?.size ?: 0) > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    itemsIndexed(parsed!!.sections, key = { _, s -> "chip-${s.number}" }) { _, sec ->
                        FilterChip(
                            selected = sec.number in expandedSteps,
                            onClick = {
                                val wasExpanded = sec.number in expandedSteps
                                vm.toggleStep(sec.number)
                                if (wasExpanded) {
                                    lineToEntry[sec.startLine]?.let { idx -> scope.launch { listState.scrollToItem(idx) } }
                                } else {
                                    pendingJumpLine = sec.startLine
                                }
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StepDot(sec.status, sec.conclusion)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        sec.name.ifBlank { stringResource(R.string.logs_setup) },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    val d = WorkflowLogViewModel.formatMs(sec.durationMs)
                                    if (d.isNotEmpty()) {
                                        Spacer(Modifier.width(3.dp))
                                        Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                        )
                    }
                }
            }

            // ── Search bar ──
            if (searchOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; matchCursor = -1 },
                        placeholder = { Text(stringResource(R.string.logs_search_hint), style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when {
                            query.length < 2 -> ""
                            matches.isEmpty() -> stringResource(R.string.logs_no_matches)
                            else -> stringResource(R.string.logs_matches).format(matches.size)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(
                        enabled = matches.isNotEmpty(),
                        onClick = {
                            if (matches.isNotEmpty()) {
                                matchCursor = (matchCursor + 1) % matches.size
                                jumpToLine(matches[matchCursor])
                            }
                        },
                    ) { Icon(Icons.Outlined.ExpandMore, contentDescription = null) }
                }
            }

            // ── Truncation banner ──
            if (parsed?.truncated == true) {
                Text(
                    stringResource(R.string.logs_truncated).format(humanBytes(parsed.raw.length.toLong())),
                    style = MaterialTheme.typography.labelSmall,
                    color = CANCELLED_ORANGE,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            // ── Body ──
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                parsed == null -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (state.error != null) state.error!! else stringResource(R.string.logs_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (job?.htmlUrl != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.logs_open_browser),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(job.htmlUrl)))
                            },
                        )
                    }
                }
                parsed != null -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                ) {
                    entries.forEach { entry ->
                        val sec = parsed.sections[entry.sectionIdx]
                        if (entry.lineIdx == null) {
                            item(key = "h-${entry.sectionIdx}") {
                                SectionHeader(
                                    sec = sec,
                                    expanded = sec.number in expandedSteps,
                                    lineCount = sec.endLine - sec.startLine,
                                    onToggle = { vm.toggleStep(sec.number) },
                                )
                            }
                        } else {
                            val lineIdx = entry.lineIdx
                            item(key = "l-$lineIdx", contentType = "logline") {
                                LogLine(
                                    text = parsed.lines[lineIdx],
                                    query = query.takeIf { it.length >= 2 },
                                    isCurrentMatch = matches.getOrNull(matchCursor) == lineIdx,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(sec: LogSection, expanded: Boolean, lineCount: Int, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        StepDot(sec.status, sec.conclusion)
        Spacer(Modifier.width(6.dp))
        Text(
            sec.name.ifBlank { stringResource(R.string.logs_setup) },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val d = WorkflowLogViewModel.formatMs(sec.durationMs)
        if (d.isNotEmpty()) {
            Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            stringResource(R.string.logs_line_count).format(lineCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogLine(text: String, query: String?, isCurrentMatch: Boolean) {
    val annotated = remember(text, query) {
        buildAnnotatedString {
            append(text)
            if (query != null) {
                val lower = text.lowercase()
                val q = query.lowercase()
                var i = 0
                while (true) {
                    val idx = lower.indexOf(q, i)
                    if (idx < 0) break
                    addStyle(SpanStyle(background = HIGHLIGHT_YELLOW), idx, idx + q.length)
                    i = idx + q.length
                }
            }
        }
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurface,
        softWrap = true,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when {
                    isCurrentMatch -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    query != null && text.contains(query, ignoreCase = true) -> HIGHLIGHT_YELLOW.copy(alpha = 0.35f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 10.dp),
    )
}

@Composable
private fun StepDot(status: String?, conclusion: String?) {
    Box(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(statusColor(status, conclusion)),
    )
}
