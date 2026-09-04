package com.pockethub.ui.search

import com.pockethub.R

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.outlined.SearchOff
import com.pockethub.ui.components.EmptyState
import com.pockethub.ui.components.ErrorState
import com.pockethub.ui.components.LoadingFooter

@Composable
private fun searchTabLabel(tab: SearchTab): String = when (tab) {
    SearchTab.REPOS  -> stringResource(R.string.search_tab_repos)
    SearchTab.USERS  -> stringResource(R.string.search_tab_users)
    SearchTab.CODE   -> stringResource(R.string.search_tab_code)
    SearchTab.ISSUES -> stringResource(R.string.search_tab_issues)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToIssue: (String, String, Int) -> Unit = { _, _, _ -> },
    onNavigateToPR: (String, String, Int) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    // Auto-focus the search field when the screen opens
    val focusRequester = remember { FocusRequester() }

    // Seed the query from the route argument on first composition.
    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank() && vm.query.value.isBlank()) {
            vm.query.value = initialQuery
            vm.search()
        }
        // Request focus for the search TextField
        focusRequester.requestFocus()
    }
    val query by vm.query.collectAsState()
    val tab by vm.currentTab.collectAsState()
    val repos by vm.repos.collectAsState()
    val users by vm.users.collectAsState()
    val code by vm.code.collectAsState()
    val issues by vm.issues.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isLoadingMore by vm.isLoadingMore.collectAsState()
    val error by vm.error.collectAsState()
    val searchedQuery by vm.searchedQuery.collectAsState()

    val listState = com.pockethub.ui.components.rememberRestorableListState(
        contentReady = repos.isNotEmpty() || users.isNotEmpty() || issues.isNotEmpty(),
    )
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, tab) {
        if (shouldLoadMore && vm.canLoadMore(tab)) vm.loadMore()
    }

    Column(Modifier.fillMaxSize()) {
        // Search bar
        TopAppBar(
            title = {
                TextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                    trailingIcon = {
                        IconButton(onClick = { vm.search() }) { Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search)) }
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
            },
        )

        // Tab selector
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SearchTab.entries.forEachIndexed { idx, current ->
                SegmentedButton(
                    selected = tab == current,
                    onClick = { vm.switchTab(current) },
                    shape = SegmentedButtonDefaults.itemShape(idx, SearchTab.entries.size),
                    label = { Text(searchTabLabel(current)) },
                )
            }
        }

        // Per-tab filter row.
        when (tab) {
            SearchTab.REPOS -> RepoFilterRow(vm)
            SearchTab.USERS -> UsersFilterRow(vm)
            SearchTab.CODE -> CodeFilterRow(vm)
            SearchTab.ISSUES -> IssuesFilterRow(vm)
        }

        val hasResults = when (tab) {
            SearchTab.REPOS -> repos.isNotEmpty()
            SearchTab.USERS -> users.isNotEmpty()
            SearchTab.CODE -> code.isNotEmpty()
            SearchTab.ISSUES -> issues.isNotEmpty()
        }

        // Full-screen loading only when this tab has nothing to show yet; otherwise
        // keep stale results visible with a footer spinner (no flicker). The same
        // container also makes a search result page refreshable without changing
        // the active query or filter state.
        com.pockethub.ui.components.RefreshContainer(
            isRefreshing = isLoading,
            onRefresh = { if (searchedQuery.isNotBlank()) vm.search() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                isLoading && !hasResults -> {
                    com.pockethub.ui.components.SkeletonList(Modifier.fillMaxSize(), rows = 9)
                }
                error != null && !hasResults -> {
                    ErrorState(message = error!!, onRetry = { vm.search() })
                }
                searchedQuery.isBlank() -> {
                    EmptyState(
                        icon = androidx.compose.material.icons.Icons.Outlined.Search,
                        title = stringResource(R.string.search_initial_hint),
                    )
                }
                !hasResults -> {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.search_results_empty, searchedQuery),
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (tab) {
                            SearchTab.REPOS -> repoItems(repos, onNavigateToRepo, onNavigateToUser)
                            SearchTab.USERS -> userItems(users, onNavigateToUser)
                            SearchTab.CODE -> codeItems(code, onNavigateToRepo)
                            SearchTab.ISSUES -> issueItems(issues, onNavigateToIssue, onNavigateToPR)
                        }
                        if (error != null) {
                            item(key = "error-banner") {
                                Text(
                                    text = error!!,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                )
                            }
                        }
                        // Footer spinner is for *pagination* only. A pull-to-refresh
                        // already shows the top indicator — showing this footer too
                        // was the double-spinner bug.
                        if (isLoadingMore) {
                            item(key = "loading-footer") { LoadingFooter() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Repos-tab sort + language filter row. Sort is a 4-choice chip row (Best /
 * Stars / Forks / Updated). Language uses curated one-tap chips plus an
 * interactive "Custom…" filter — explicitly button-driven, not a search box.
 * The chosen language is appended to the query as the `language:` qualifier.
 */
