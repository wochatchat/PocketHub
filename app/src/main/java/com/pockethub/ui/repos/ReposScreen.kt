package com.pockethub.ui.repos

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

private val FILTERS = listOf(
    RepoFilter.ALL, RepoFilter.OWNER, RepoFilter.MEMBER,
    RepoFilter.PUBLIC, RepoFilter.PRIVATE, RepoFilter.FORKS,
)

@Composable
private fun repoFilterLabel(filter: RepoFilter): String = when (filter) {
    RepoFilter.ALL    -> stringResource(R.string.repo_filter_all)
    RepoFilter.OWNER  -> stringResource(R.string.repo_filter_owner)
    RepoFilter.MEMBER -> stringResource(R.string.repo_filter_member)
    RepoFilter.PUBLIC -> stringResource(R.string.repo_filter_public)
    RepoFilter.PRIVATE -> stringResource(R.string.repo_filter_private)
    RepoFilter.FORKS  -> stringResource(R.string.repo_filter_forks)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReposScreen(
    modifier: Modifier = Modifier,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    vm: ReposViewModel = hiltViewModel(),
) {
    val repos by vm.repos.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val error by vm.error.collectAsState()
    val tab by vm.currentTab.collectAsState()
    val filter by vm.currentFilter.collectAsState()
    val listState = com.pockethub.ui.components.rememberRestorableGridState(contentReady = repos.isNotEmpty())

    // ReposScreen is disposed when the user leaves the Repos tab and recomposed
    // on return (HomeScreen uses `when(selectedTab)` to switch content).  A fresh
    // composition is the signal that the user came back — reload so any mutation
    // (delete / visibility toggle) done on RepoDetail is reflected immediately.
    // The cache was already invalidated by the mutation, so this is a cheap
    // cache-miss → single network fetch; no mutation means a fast cache hit.
    // Plain load() instead of refresh(): refresh() would force a second network
    // round-trip on every tab return AND flash both spinners alongside init{}'s
    // own first load — the double-spinner bug. Cache-first load() already
    // reflects mutations because mutations invalidate their cache keys.
    LaunchedEffect(Unit) { vm.load() }

    // Infinite scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && error == null) vm.loadMore()
    }

    com.pockethub.ui.components.RefreshContainer(
        isRefreshing = isRefreshing,
        onRefresh = { vm.refresh() },
        modifier = modifier,
    ) {
    Column(Modifier.fillMaxSize()) {
        // Tab selector
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedButton(selected = tab == ReposTab.MINE, onClick = { vm.switchTab(ReposTab.MINE) },
                shape = SegmentedButtonDefaults.itemShape(0, 2), label = { Text(stringResource(R.string.tab_my_repos)) })
            SegmentedButton(selected = tab == ReposTab.STARRED, onClick = { vm.switchTab(ReposTab.STARRED) },
                shape = SegmentedButtonDefaults.itemShape(1, 2), label = { Text(stringResource(R.string.tab_starred)) })
        }

        // Filter chips — only meaningful for "My Repos" (the starred endpoint doesn't
        // support type/visibility filters).
        if (tab == ReposTab.MINE) {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FILTERS.size) { idx ->
                    FilterChip(
                        selected = filter == FILTERS[idx],
                        onClick = { vm.setFilter(FILTERS[idx]) },
                        label = { Text(repoFilterLabel(FILTERS[idx]), style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            // First-load skeleton — shimmering rows read as "fast", not stuck.
            isLoading && repos.isEmpty() ->
                com.pockethub.ui.components.SkeletonList(Modifier.fillMaxSize(), rows = 9, spacing = 12.dp)
            // Error with nothing cached/stale to show.
            error != null && repos.isEmpty() ->
                com.pockethub.ui.components.ErrorState(message = error!!, onRetry = { vm.refresh() })
            repos.isEmpty() ->
                com.pockethub.ui.components.EmptyStateV2(
                    icon = Icons.Outlined.Code,
                    title = stringResource(R.string.no_repositories_found),
                )
            else -> {
                    // Responsive grid: single column on phones, multi-column on tablets.
                    // BoxWithConstraints measures available width to pick column count.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val columns = com.pockethub.ui.components.adaptiveColumnCount(maxWidth)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(repos.size, key = { repos[it].id }) { index ->
                                val repo = repos[index]
                                com.pockethub.ui.components.StaggeredAppear(index = index) {
                                    RepositoryRow(
                                        repo = repo,
                                        onOpen = { onNavigateToRepo(repo.owner.login, repo.name) },
                                        onOpenOwner = { onNavigateToUser(repo.owner.login) },
                                    )
                                }
                            }
                            // Inline error banner when a page/refresh failed but stale data is visible.
                            if (error != null) {
                                item(key = "error-banner", span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = error!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    )
                                }
                            }
                            if (isLoading) {
                                item(key = "loading-footer", span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                            }
                        }
                    }
            }
        }
    }
    }
}


