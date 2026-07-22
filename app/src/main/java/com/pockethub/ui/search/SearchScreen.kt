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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.MoreHoriz
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Repos-tab sort + language filter row. Sort is a 4-choice chip row (Best /
 * Stars / Forks / Updated). Language uses curated one-tap chips plus an
 * interactive "Custom…" filter — explicitly button-driven, not a search box.
 * The chosen language is appended to the query as the `language:` qualifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoFilterRow(vm: SearchViewModel) {
    val sort by vm.repoSort.collectAsState()
    val order by vm.sortOrder.collectAsState()
    val language by vm.repoLanguage.collectAsState()
    var showLanguagePicker by remember { mutableStateOf(false) }
    var customQuery by remember { mutableStateOf("") }

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
                item { OrderToggleChip(order = order, onToggle = { vm.applyRepoFilters(order = it) }) }
            }
        }
        Spacer(Modifier.height(6.dp))

        // Language row — curated chips + a "Clear" chip when active + "Custom…" chip.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (language.isNotBlank()) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = { vm.applyRepoFilters(language = "") },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(language)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
                            }
                        },
                    )
                }
            }
            COMMON_LANGUAGES.forEach { lang ->
                item {
                    FilterChip(
                        selected = language.equals(lang, ignoreCase = true) && language.isNotBlank(),
                        onClick = {
                            // Toggle off if already selected.
                            val next = if (language.equals(lang, ignoreCase = true)) "" else lang
                            vm.applyRepoFilters(language = next)
                        },
                        label = { Text(lang) },
                    )
                }
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = {
                        customQuery = ""
                        showLanguagePicker = true
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.language_custom))
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            title = { Text(stringResource(R.string.language_custom_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = customQuery,
                        onValueChange = { customQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.language_filter_placeholder)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Code, null) },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.language_custom_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = customQuery.trim()
                    if (trimmed.isNotBlank()) vm.applyRepoFilters(language = trimmed)
                    showLanguagePicker = false
                }) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
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

// ──────────────────────────────────────────────────────────────────────────────
// Per-tab filter rows (Users / Code / Issues). Share the same visual language as
// RepoFilterRow: a single LazyRow of FilterChip + optional order toggle + a
// "Custom…" chip for open-ended filters (language, extension).
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Users tab — sort by followers / repositories / joined, plus ASC/DESC toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersFilterRow(vm: SearchViewModel) {
    val sort by vm.userSort.collectAsState()
    val order by vm.userOrder.collectAsState()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                UserSort.BEST_MATCH to R.string.sort_best_match,
                UserSort.FOLLOWERS to R.string.user_sort_followers,
                UserSort.REPOSITORIES to R.string.user_sort_repositories,
                UserSort.JOINED to R.string.user_sort_joined,
            ).forEach { (s, labelRes) ->
                item {
                    FilterChip(
                        selected = sort == s,
                        onClick = { vm.applyUsersFilters(sort = s) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            if (sort != UserSort.BEST_MATCH) {
                item {
                    OrderToggleChip(
                        order = order,
                        onToggle = { vm.applyUsersFilters(order = it) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Code tab — language chips (reuses COMMON_LANGUAGES) + extension "Custom…"
 * chip with a dialog where the user can type any extension or language name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeFilterRow(vm: SearchViewModel) {
    val language by vm.codeLanguage.collectAsState()
    val extension by vm.codeExtension.collectAsState()
    var showCustom by remember { mutableStateOf(false) }
    var customMode by remember { mutableStateOf(CodeCustomMode.LANGUAGE) }
    var customText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Active filters first (Clear chips).
        if (language.isNotBlank() || extension.isNotBlank()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (language.isNotBlank()) {
                    item {
                        ActiveFilterChip(
                            label = stringResource(R.string.language_label_fmt, language),
                            onClear = { vm.applyCodeFilters(language = "") },
                        )
                    }
                }
                if (extension.isNotBlank()) {
                    item {
                        ActiveFilterChip(
                            label = stringResource(R.string.extension_label_fmt, extension),
                            onClear = { vm.applyCodeFilters(extension = "") },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            COMMON_LANGUAGES.forEach { lang ->
                item {
                    FilterChip(
                        selected = language.equals(lang, ignoreCase = true),
                        onClick = {
                            val next = if (language.equals(lang, ignoreCase = true)) "" else lang
                            vm.applyCodeFilters(language = next)
                        },
                        label = { Text(lang) },
                    )
                }
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = {
                        customMode = CodeCustomMode.LANGUAGE
                        customText = ""
                        showCustom = true
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.language_custom))
                        }
                    },
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = {
                        customMode = CodeCustomMode.EXTENSION
                        customText = ""
                        showCustom = true
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.extension_custom))
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showCustom) {
        val titleRes = if (customMode == CodeCustomMode.LANGUAGE) R.string.language_custom_title
            else R.string.extension_custom_title
        val placeholderRes = if (customMode == CodeCustomMode.LANGUAGE) R.string.language_filter_placeholder
            else R.string.extension_filter_placeholder
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text(stringResource(titleRes)) },
            text = {
                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(placeholderRes)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Code, null) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = customText.trim()
                    if (trimmed.isNotBlank()) {
                        if (customMode == CodeCustomMode.LANGUAGE) vm.applyCodeFilters(language = trimmed)
                        else vm.applyCodeFilters(extension = trimmed)
                    }
                    showCustom = false
                }) { Text(stringResource(R.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showCustom = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private enum class CodeCustomMode { LANGUAGE, EXTENSION }

/**
 * Issues tab — type (all / issue / pr) + state (all / open / closed) + sort
 * (created / updated / comments) + ASC/DESC toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssuesFilterRow(vm: SearchViewModel) {
    val type by vm.issueType.collectAsState()
    val state by vm.issueState.collectAsState()
    val sort by vm.issueSort.collectAsState()
    val order by vm.issueOrder.collectAsState()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                IssueType.ALL to R.string.issue_type_all,
                IssueType.ISSUE to R.string.issue_type_issue,
                IssueType.PR to R.string.issue_type_pr,
            ).forEach { (t, labelRes) ->
                item {
                    FilterChip(
                        selected = type == t,
                        onClick = { vm.applyIssuesFilters(type = t) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                IssueState.ALL to R.string.issue_state_all,
                IssueState.OPEN to R.string.issue_state_open,
                IssueState.CLOSED to R.string.issue_state_closed,
            ).forEach { (s, labelRes) ->
                item {
                    FilterChip(
                        selected = state == s,
                        onClick = { vm.applyIssuesFilters(state = s) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            listOf(
                IssueSort.CREATED to R.string.sort_created,
                IssueSort.UPDATED to R.string.sort_updated,
                IssueSort.COMMENTS to R.string.sort_comments,
            ).forEach { (s, labelRes) ->
                item {
                    FilterChip(
                        selected = sort == s,
                        onClick = { vm.applyIssuesFilters(sort = s) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
            item { OrderToggleChip(order = order, onToggle = { vm.applyIssuesFilters(order = it) }) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Reusable ASC/DESC toggle chip shown next to sort filters.
 */
@Composable
private fun OrderToggleChip(order: SortOrder, onToggle: (SortOrder) -> Unit) {
    FilterChip(
        selected = order == SortOrder.ASC,
        onClick = {
            val next = if (order == SortOrder.ASC) SortOrder.DESC else SortOrder.ASC
            onToggle(next)
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

/**
 * Pill that shows a currently-active filter with a close icon on the right —
 * clicking it clears the filter.
 */
@Composable
private fun ActiveFilterChip(label: String, onClear: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClear,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.Close, null, Modifier.size(14.dp))
            }
        },
    )
}
