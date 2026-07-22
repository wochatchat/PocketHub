package com.pockethub.ui.search

import com.pockethub.R

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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

    val listState = rememberLazyListState()
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

    Column {
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

        // Repos-tab filter row — sort chips + language input + order toggle.
        if (tab == SearchTab.REPOS) {
            RepoFilterRow(vm)
        }

        val hasResults = when (tab) {
            SearchTab.REPOS -> repos.isNotEmpty()
            SearchTab.USERS -> users.isNotEmpty()
            SearchTab.CODE -> code.isNotEmpty()
            SearchTab.ISSUES -> issues.isNotEmpty()
        }

        // Full-screen loading only when this tab has nothing to show yet; otherwise
        // keep stale results visible with a footer spinner (no flicker).
        if (isLoading && !hasResults) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        when {
            // Error with nothing to show — full error state with retry.
            error != null && !hasResults -> {
                ErrorState(message = error!!, onRetry = { vm.search() })
            }
            // Nothing searched yet — guidance.
            searchedQuery.isBlank() -> {
                EmptyState(title = stringResource(R.string.search_initial_hint))
            }
            // Successful search with no hits.
            !hasResults -> {
                EmptyState(title = stringResource(R.string.search_results_empty, searchedQuery))
            }
            else -> LazyColumn(
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
                // Inline error banner when a refresh failed but stale results are visible.
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
                if (isLoading || isLoadingMore) {
                    item(key = "loading-footer") { LoadingFooter() }
                }
            }
        }
    }
}

/**
 * Repos-tab sort + language filter row. Sort is a 4-choice chip row (Best / Stars /
 * Forks / Updated). Language is a single-line OutlinedTextField — it gets appended
 * to the query as the `language:` qualifier on search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoFilterRow(vm: SearchViewModel) {
    val sort by vm.repoSort.collectAsState()
    val order by vm.sortOrder.collectAsState()
    var language by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = sort == RepoSort.BEST_MATCH,
                    onClick = { vm.applyRepoFilters(sort = RepoSort.BEST_MATCH) },
                    label = { Text(stringResource(R.string.sort_best_match)) },
                )
            }
            item {
                FilterChip(
                    selected = sort == RepoSort.STARS,
                    onClick = { vm.applyRepoFilters(sort = RepoSort.STARS) },
                    label = { Text(stringResource(R.string.sort_stars)) },
                )
            }
            item {
                FilterChip(
                    selected = sort == RepoSort.FORKS,
                    onClick = { vm.applyRepoFilters(sort = RepoSort.FORKS) },
                    label = { Text(stringResource(R.string.sort_forks)) },
                )
            }
            item {
                FilterChip(
                    selected = sort == RepoSort.UPDATED,
                    onClick = { vm.applyRepoFilters(sort = RepoSort.UPDATED) },
                    label = { Text(stringResource(R.string.sort_updated)) },
                )
            }
            // Order toggle — only flavorful when a real sort is active.
            if (sort != RepoSort.BEST_MATCH) {
                item {
                    FilterChip(
                        selected = order == SortOrder.ASC,
                        onClick = {
                            val next = if (order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
                            vm.applyRepoFilters(order = next)
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (order == SortOrder.ASC) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (order == SortOrder.ASC) stringResource(R.string.order_asc) else stringResource(R.string.order_desc))
                            }
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = language,
            onValueChange = {
                language = it
                vm.applyRepoFilters(language = it.trim())
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.language_filter_placeholder)) },
            singleLine = true,
            trailingIcon = {
                if (language.isNotEmpty()) {
                    IconButton(onClick = {
                        language = ""
                        vm.applyRepoFilters(language = "")
                    }) {
                        Icon(Icons.Outlined.Clear, contentDescription = null)
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun LazyListScope.repoItems(
    repos: List<com.pockethub.data.model.Repository>,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit,
) {
    items(repos, key = { it.id }) { repo ->
        Row(Modifier.fillMaxWidth().clickable { onNavigateToRepo(repo.owner.login, repo.name) }.padding(vertical = 8.dp)) {
            AsyncImage(
                model = repo.owner.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(16.dp).clip(CircleShape)
                    .clickable { onNavigateToUser(repo.owner.login) },
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("${repo.owner.login}/${repo.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!repo.description.isNullOrBlank()) Text(repo.description!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun LazyListScope.userItems(
    users: List<com.pockethub.data.model.User>,
    onNavigateToUser: (String) -> Unit,
) {
    items(users, key = { it.login }) { user ->
        Row(
            Modifier.fillMaxWidth()
                .clickable { onNavigateToUser(user.login) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(model = user.avatarUrl, contentDescription = null, modifier = Modifier.size(24.dp).clip(CircleShape))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(user.name ?: "@${user.login}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("@${user.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun LazyListScope.codeItems(
    code: List<com.pockethub.data.remote.GitHubApi.CodeSearchItem>,
    onNavigateToRepo: (String, String) -> Unit,
) {
    items(code, key = { it.htmlUrl.ifBlank { it.path } }) { item ->
        Column(Modifier.fillMaxWidth().clickable {
            item.repository?.let { onNavigateToRepo(it.owner.login, it.name) }
        }.padding(vertical = 8.dp)) {
            Text(item.path, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.repository?.let { Text("${it.owner.login}/${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun LazyListScope.issueItems(
    issues: List<com.pockethub.data.model.Issue>,
    onNavigateToIssue: (String, String, Int) -> Unit,
    onNavigateToPR: (String, String, Int) -> Unit,
) {
    items(issues, key = { it.id }) { issue ->
        val owner = issue.repository?.owner?.login
        val repo = issue.repository?.name
        val isPR = issue.pullRequest != null
        val repoLabel = if (owner != null && repo != null) "$owner/$repo" else "—"
        Row(
            Modifier.fillMaxWidth().clickable {
                if (owner != null && repo != null) {
                    if (isPR) onNavigateToPR(owner, repo, issue.number) else onNavigateToIssue(owner, repo, issue.number)
                }
            }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isPR) Icons.Outlined.Merge else Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${issue.number}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    val stateColor = when {
                        issue.state == "closed" && isPR && issue.pullRequest?.let { true } == true -> MaterialTheme.colorScheme.secondary
                        issue.state == "closed" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Text(issue.state, style = MaterialTheme.typography.labelSmall, color = stateColor)
                }
                Text(issue.title.ifBlank { "(no title)" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(repoLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
