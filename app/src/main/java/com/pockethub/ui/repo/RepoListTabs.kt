package com.pockethub.ui.repo

// Issue & PR list tabs share the state-filter chips and row layouts.
// Split out of RepoDetailScreen.kt for readability.

import com.pockethub.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pockethub.data.model.Issue
import com.pockethub.ui.components.PhAsyncImage
import com.pockethub.ui.theme.semanticColors
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf

@Composable
internal fun IssueStateFilterChips(
    selected: IssueStateFilter,
    onSelect: (IssueStateFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IssueStateFilter.entries.forEach { filter ->
            val label = when (filter) {
                IssueStateFilter.OPEN -> stringResource(R.string.issue_state_open)
                IssueStateFilter.CLOSED -> stringResource(R.string.issue_state_closed)
                IssueStateFilter.ALL -> stringResource(R.string.issue_state_all)
            }
            androidx.compose.material3.FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IssuesTab(
    issues: List<Issue>,
    stateFilter: IssueStateFilter,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onSelectFilter: (IssueStateFilter) -> Unit,
    onLoadMore: () -> Unit,
    onClick: (Int) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && issues.isNotEmpty()) onLoadMore()
    }

    Column(Modifier.fillMaxSize()) {
        IssueStateFilterChips(selected = stateFilter, onSelect = onSelectFilter)

        if (isLoading && issues.isEmpty()) {
            com.pockethub.ui.components.SkeletonList(Modifier.fillMaxSize(), rows = 8, topPadding = 8.dp)
            return@Column
        }
        if (issues.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val emptyText = when (stateFilter) {
                    IssueStateFilter.OPEN -> stringResource(R.string.no_open_issues)
                    IssueStateFilter.CLOSED -> stringResource(R.string.no_closed_issues)
                    IssueStateFilter.ALL -> stringResource(R.string.no_issues)
                }
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(issues, key = { it.id }) { issue ->
            Row(
                Modifier.fillMaxWidth().clickable { onClick(issue.number) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // State indicator dot — green for open, purple for closed
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (issue.state == "open") semanticColors().success else semanticColors().neutral),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        issue.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val user = issue.user
                        if (user != null) {
                            PhAsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clip(CircleShape)
                                    .clickable { onNavigateToUser(user.login) },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                user.login,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onNavigateToUser(user.login) },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            stringResource(R.string.issue_meta, issue.number, issue.comments),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (issue.labels.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            issue.labels.take(5).forEach { label ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(label.name, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            }
            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

/** PR tab filter chips: Open / Closed / Merged / All (GitHub web parity). */
@Composable
internal fun PRStateFilterChips(
    selected: PRStateFilter,
    onSelect: (PRStateFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PRStateFilter.entries.forEach { filter ->
            val label = when (filter) {
                PRStateFilter.OPEN -> stringResource(R.string.issue_state_open)
                PRStateFilter.CLOSED -> stringResource(R.string.issue_state_closed)
                PRStateFilter.MERGED -> stringResource(R.string.pr_state_merged)
                PRStateFilter.ALL -> stringResource(R.string.issue_state_all)
            }
            androidx.compose.material3.FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PullsTab(
    pulls: List<Issue>,
    stateFilter: PRStateFilter,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    onSelectFilter: (PRStateFilter) -> Unit,
    onLoadMore: () -> Unit,
    onClick: (Int) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && pulls.isNotEmpty()) onLoadMore()
    }

    Column(Modifier.fillMaxSize()) {
        PRStateFilterChips(selected = stateFilter, onSelect = onSelectFilter)

        if (isLoading && pulls.isEmpty()) {
            com.pockethub.ui.components.SkeletonList(Modifier.fillMaxSize(), rows = 8, topPadding = 8.dp)
            return@Column
        }
        if (pulls.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val emptyText = when (stateFilter) {
                    PRStateFilter.OPEN -> stringResource(R.string.no_open_prs)
                    PRStateFilter.CLOSED -> stringResource(R.string.no_closed_prs)
                    PRStateFilter.MERGED -> stringResource(R.string.no_merged_prs)
                    PRStateFilter.ALL -> stringResource(R.string.no_prs)
                }
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(pulls, key = { it.id }) { pr ->
            Row(
                Modifier.fillMaxWidth().clickable { onClick(pr.number) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // State indicator dot — green=open, violet-red=merged, red=closed
                val prColor = when {
                    pr.state == "open" -> semanticColors().success
                    pr.isMerged -> semanticColors().merged
                    else -> semanticColors().danger
                }
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(prColor),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        pr.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val user = pr.user
                        if (user != null) {
                            PhAsyncImage(
                                model = user.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp).clip(CircleShape)
                                    .clickable { onNavigateToUser(user.login) },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                user.login,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onNavigateToUser(user.login) },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            stringResource(R.string.issue_meta, pr.number, pr.comments),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        val stateLabel = when {
                            pr.isMerged -> stringResource(R.string.pr_state_merged)
                            pr.state == "open" -> stringResource(R.string.issue_state_open)
                            else -> stringResource(R.string.issue_state_closed)
                        }
                        val stateColor = when {
                            pr.isMerged -> semanticColors().merged
                            pr.state == "open" -> semanticColors().success
                            else -> semanticColors().danger
                        }
                        Text(
                            stateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = stateColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
            HorizontalDivider()
            }
            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
