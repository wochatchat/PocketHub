package com.pockethub.ui.repo

// Workflows tab: run list, badges, manual-dispatch dialog, branch chip.
// Split out of RepoDetailScreen.kt for readability.

import com.pockethub.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.remember
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.components.PhAsyncImage
import androidx.compose.foundation.lazy.rememberLazyListState
import java.util.Locale

@Composable
internal fun WorkflowsTab(
    runs: List<GitHubApi.WorkflowRun>,
    isLoading: Boolean = false,
    workflows: List<GitHubApi.Workflow> = emptyList(),
    branches: List<GitHubApi.Branch> = emptyList(),
    selectedWorkflowId: Long? = null,
    selectedBranch: String? = null,
    onFilterChange: (workflowId: Long?, branch: String?) -> Unit = { _, _ -> },
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToWorkflowRun: (Long) -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        // Filter chips: row 1 = workflow (run script), row 2 = branch. The
        // branch row hides entirely when the repo has at most one branch —
        // nothing to filter. Each row scrolls horizontally when chips overflow.
        if (workflows.isNotEmpty() || branches.size > 1) {
            FilterChipRow(
                modifier = Modifier.padding(top = 6.dp),
            ) {
                if (workflows.isNotEmpty()) {
                    WorkflowFilterRow(
                        workflows = workflows,
                        selectedWorkflowId = selectedWorkflowId,
                        onSelect = { onFilterChange(it, selectedBranch) },
                    )
                    if (branches.size > 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
                if (branches.size > 1) {
                    BranchFilterRow(
                        branches = branches,
                        selectedBranch = selectedBranch,
                        onSelect = { onFilterChange(selectedWorkflowId, it) },
                    )
                }
            }
        }
        when {
            isLoading && runs.isEmpty() -> {
                com.pockethub.ui.components.SkeletonList(Modifier.fillMaxSize(), rows = 7, topPadding = 8.dp)
            }
            runs.isEmpty() -> {
                com.pockethub.ui.components.EmptyStateV2(
                    icon = androidx.compose.material.icons.Icons.Outlined.PlayArrow,
                    title = stringResource(R.string.no_workflow_runs),
                )
            }
            else -> WorkflowRunList(
                runs = runs,
                onNavigateToUser = onNavigateToUser,
                onNavigateToWorkflowRun = onNavigateToWorkflowRun,
            )
        }
    }
}

/** Vertical container for the filter chip rows. */
@Composable
private fun FilterChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        content()
    }
}

/** Horizontal scrolling row of filter chips with an "所有" (all) first chip. */
@Composable
private fun FilterChipScrollRow(
    selected: Boolean,
    onSelect: () -> Unit,
    chips: List<FilterChipData>,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            FilterChip(
                selected = selected,
                onClick = onSelect,
                label = { Text(stringResource(R.string.repo_filter_all)) },
            )
        }
        items(chips, key = { it.key }) { chip ->
            FilterChip(
                selected = chip.selected,
                onClick = chip.onClick,
                label = { Text(chip.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

internal data class FilterChipData(val key: String, val label: String, val selected: Boolean, val onClick: () -> Unit)

@Composable
private fun WorkflowFilterRow(
    workflows: List<GitHubApi.Workflow>,
    selectedWorkflowId: Long?,
    onSelect: (Long?) -> Unit,
) {
    FilterChipScrollRow(
        selected = selectedWorkflowId == null,
        onSelect = { onSelect(null) },
        chips = workflows.map { wf ->
            FilterChipData(
                key = "wf-${wf.id}",
                label = wf.name.ifBlank { wf.path.substringAfterLast('/') },
                selected = selectedWorkflowId == wf.id,
                onClick = { onSelect(wf.id) },
            )
        },
    )
}

@Composable
private fun BranchFilterRow(
    branches: List<GitHubApi.Branch>,
    selectedBranch: String?,
    onSelect: (String?) -> Unit,
) {
    FilterChipScrollRow(
        selected = selectedBranch == null,
        onSelect = { onSelect(null) },
        chips = branches.map { b ->
            FilterChipData(
                key = "br-${b.name}",
                label = b.name,
                selected = selectedBranch == b.name,
                onClick = { onSelect(b.name) },
            )
        },
    )
}

/** A refresh replaces the whole list — jump back to the newest run at the
 *  top instead of staying scrolled wherever the old list was. */
@Composable
private fun WorkflowRunList(
    runs: List<GitHubApi.WorkflowRun>,
    onNavigateToUser: (String) -> Unit,
    onNavigateToWorkflowRun: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(runs.firstOrNull()?.id, runs.size) {
        listState.scrollToItem(0)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        items(runs, key = { it.id }) { run ->
            WorkflowRunRow(
                run = run,
                onNavigateToUser = onNavigateToUser,
                onNavigateToWorkflowRun = onNavigateToWorkflowRun,
            )
            HorizontalDivider()
        }
    }
}

@Composable
internal fun WorkflowRunRow(
    run: GitHubApi.WorkflowRun,
    onNavigateToUser: (String) -> Unit,
    onNavigateToWorkflowRun: (Long) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable { onNavigateToWorkflowRun(run.id) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Conclusion badge (success/failure/running/…) instead of a bare dot
        Box(Modifier.width(30.dp), contentAlignment = Alignment.TopStart) {
            Text(conclusionBadge(run), color = conclusionColor(run.conclusion),
                style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            // Overline: workflow name — the task identity (github.com style) —
            // with the event tag on the right. Standalone line so it never
            // mixes with sha/event detail again.
            val wfName = run.name.ifBlank {
                run.path?.substringAfterLast('/')?.removeSuffix(".yml") ?: (run.event ?: "")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    wfName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                run.event?.let {
                    Text(
                        eventLabel(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            // Title: run display title = commit message; short SHA trails right.
            val commitMsg = run.displayTitle?.takeIf { it.isNotBlank() }
                ?: run.headCommit?.message?.substringBefore('\n')?.trim().orEmpty()
            if (commitMsg.isNotEmpty() || !run.headSha.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        commitMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    run.headSha?.take(7)?.let { sha ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            sha,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            // Line 3: branch chip · actor · … · date · duration.
            // Chip wraps its content (weight fill=false) but yields space under
            // pressure; date/duration stay single-line and are pushed to the
            // row's end, GitHub-mobile style.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val branch = run.headBranch?.takeIf { it.isNotBlank() }
                if (branch != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            branch,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                val actor = run.actor
                if (actor != null) {
                    PhAsyncImage(
                        model = actor.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp).clip(CircleShape)
                            .clickable { onNavigateToUser(actor.login) },
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        actor.login,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onNavigateToUser(actor.login) },
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f))
                run.createdAt?.let {
                    Text(
                        formatDateTime(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                formatDuration(run.runStartedAt, run.updatedAt)?.let { d ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        d,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Single-glyph conclusion badge; running/pending gets an animated feel via color. */
internal fun conclusionBadge(run: GitHubApi.WorkflowRun): String = when (run.conclusion) {
    "success" -> "✓"
    "failure" -> "✕"
    "cancelled" -> "⊘"
    "timed_out" -> "⏱"
    "skipped" -> "↷"
    else -> if (run.status == "in_progress" || run.status == "queued") "…" else "•"
}

@Composable
internal fun conclusionColor(conclusion: String?): androidx.compose.ui.graphics.Color =
    when {
        conclusion == "success" -> androidx.compose.ui.graphics.Color(0xFF2EA043)
        conclusion == "failure" || conclusion == "cancelled" || conclusion == "timed_out" ->
            MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

/** Human-readable event names; push stays terse. */
internal fun eventLabel(event: String): String = when (event) {
    "push" -> "push"
    "pull_request" -> "PR"
    "workflow_dispatch" -> "manual"
    "schedule" -> "cron"
    else -> event
}

/** ISO timestamp → localized "MMM d, HH:mm" (in the device timezone). */
@Composable
internal fun formatDateTime(s: String): String {
    val pattern = stringResource(R.string.workflow_run_datetime)
    return try {
        val dt = java.time.OffsetDateTime.parse(s)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
        val date = java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(dt)
        val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(dt)
        String.format(Locale.getDefault(), pattern, date, time)
    } catch (_: Exception) {
        s.take(16)
    }
}

/**
 * Duration between run start and last update, e.g. "2m 34s" / "1h 03m".
 * Returns null when the timestamps are missing or the run is still going.
 */
internal fun formatDuration(startedAt: String?, updatedAt: String?): String? {
    val start = startedAt ?: return null
    val end = updatedAt ?: return null
    return try {
        val secs = java.time.Duration.between(
            java.time.OffsetDateTime.parse(start),
            java.time.OffsetDateTime.parse(end),
        ).seconds
        if (secs < 0) null
        else when {
            secs < 60 -> "${secs}s"
            secs < 3600 -> "%dm %02ds".format(secs / 60, secs % 60)
            else -> "%dh %02dm".format(secs / 3600, (secs % 3600) / 60)
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Dialog for manually running a workflow (`workflow_dispatch`).
 *
 * Lists all active workflows in the repo and lets the user pick one + enter the
 * branch/tag ref to run on. GitHub doesn't expose which workflows declare
 * `on: workflow_dispatch` in the list endpoint, so on failure (HTTP 422) we
 * surface a helpful message instead.
 */
@Composable
internal fun WorkflowDispatchDialog(
    workflows: List<GitHubApi.Workflow>,
    branches: List<GitHubApi.Branch>,
    isLoading: Boolean,
    isLoadingBranches: Boolean,
    defaultBranch: String?,
    isDispatching: Boolean,
    currentBranch: String?,
    onDismiss: () -> Unit,
    onDispatch: (workflowId: Long, ref: String) -> Unit,
) {
    var selectedId by remember(workflows.size) {
        mutableStateOf(workflows.firstOrNull()?.id)
    }
    // Seed the branch picker from the repo's real branches (default first),
    // falling back to defaultBranch / "main" if the list isn't loaded yet.
    val branchNames = remember(branches) { branches.map { it.name } }
    var ref by remember(workflows.size) { mutableStateOf("___init___") }
    // Mirror the Code tab's branch into the dialog so switching branches in the
    // Code tab updates the dispatch ref in real time even while the dialog is
    // open. Once the user picks manually below, they stay on their choice.
    val effectiveBranch = currentBranch ?: defaultBranch ?: "main"
    LaunchedEffect(effectiveBranch, branchNames) {
        ref = if (branchNames.contains(effectiveBranch)) effectiveBranch else ref
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workflow_dispatch_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                if (isLoading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else if (workflows.isEmpty()) {
                    Text(
                        stringResource(R.string.workflow_dispatch_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.workflow_dispatch_select),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val scrollState = rememberScrollState()
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(scrollState)) {
                        workflows.forEach { wf ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !isDispatching) { selectedId = wf.id }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedId == wf.id,
                                    onClick = { selectedId = wf.id },
                                    enabled = !isDispatching,
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        wf.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        wf.path,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Branch selector — mirrors the repo's Code tab so the dispatch
                    // ref stays in sync; tapping opens the dropdown to switch manually.
                    BranchSelectorChip(
                        currentRef = ref,
                        branchNames = branchNames,
                        enabled = !isDispatching,
                        isLoadingBranches = isLoadingBranches,
                        onToggle = { /* unused — chip manages its own expanded state */ },
                        onSelect = { newRef -> ref = newRef },
                    )
                    Text(
                        stringResource(R.string.workflow_dispatch_ref_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.workflow_dispatch_sync_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isDispatching && selectedId != null && ref.isNotBlank(),
                onClick = { selectedId?.let { onDispatch(it, ref.trim()) } },
            ) {
                if (isDispatching) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_dispatch_workflow))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDispatching) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
internal fun BranchSelectorChip(
    currentRef: String,
    branchNames: List<String>,
    enabled: Boolean,
    isLoadingBranches: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    // Inline expandable picker instead of a DropdownMenu: popups anchored
    // inside a dialog re-position when their anchor moves — with many branches
    // the menu visibly "drifted". The embedded list lives inside the dialog
    // layout, so it can't.
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Public,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.workflow_dispatch_ref_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (isLoadingBranches) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    currentRef,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded && enabled) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            ) {
                branchNames.forEach { name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(name); expanded = false }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (name == currentRef) Icons.Outlined.Check else Icons.Outlined.Circle,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = if (name == currentRef) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (name == currentRef) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

