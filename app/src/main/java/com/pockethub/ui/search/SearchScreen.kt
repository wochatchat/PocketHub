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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
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
    SearchTab.REPOS -> stringResource(R.string.search_tab_repos)
    SearchTab.USERS -> stringResource(R.string.search_tab_users)
    SearchTab.CODE  -> stringResource(R.string.search_tab_code)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
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

        val hasResults = when (tab) {
            SearchTab.REPOS -> repos.isNotEmpty()
            SearchTab.USERS -> users.isNotEmpty()
            SearchTab.CODE -> code.isNotEmpty()
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
