package com.pockethub.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab { REPOS, USERS, CODE, ISSUES }

/** Repo search sort options; "" = GitHub default ("best match"). */
enum class RepoSort(val apiValue: String?) {
    BEST_MATCH(null),
    STARS("stars"),
    FORKS("forks"),
    UPDATED("updated"),
}

enum class SortOrder(val apiValue: String) {
    DESC("desc"),
    ASC("asc"),
}

/**
 * Curated list of common programming languages shown as one-tap chips in the
 * repos filter row. The user can still pick "Other…" and type a custom name
 * for anything not listed here.
 */
val COMMON_LANGUAGES: List<String> = listOf(
    "Kotlin", "Java", "Python", "JavaScript", "TypeScript", "Go", "Rust",
    "C", "C++", "C#", "Swift", "Ruby", "PHP", "Dart", "Scala", "Shell",
    "HTML", "CSS", "Vue", "Lua", "Elixir", "Haskell", "OCaml",
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    var query = MutableStateFlow("")
    var currentTab = MutableStateFlow(SearchTab.REPOS)

    /** Repos tab sort + order — applied as `sort` & `order` query params. */
    var repoSort = MutableStateFlow(RepoSort.BEST_MATCH)
    var sortOrder = MutableStateFlow(SortOrder.DESC)
    /** Optional language filter — appended as `language:` qualifier in the q string. Empty = any. */
    var repoLanguage = MutableStateFlow("")

    private val _repos = MutableStateFlow<List<Repository>>(emptyList())
    val repos: StateFlow<List<Repository>> = _repos

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _code = MutableStateFlow<List<GitHubApi.CodeSearchItem>>(emptyList())
    val code: StateFlow<List<GitHubApi.CodeSearchItem>> = _code

    private val _issues = MutableStateFlow<List<Issue>>(emptyList())
    val issues: StateFlow<List<Issue>> = _issues

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** The query that produced the currently displayed results ("" = nothing searched yet). */
    private val _searchedQuery = MutableStateFlow("")
    val searchedQuery: StateFlow<String> = _searchedQuery.asStateFlow()

    // Pagination state is tracked per tab so switching back and forth doesn't lose the page.
    private val pages = mutableMapOf<SearchTab, Int>()
    private val totalCounts = mutableMapOf<SearchTab, Int>()
    private var searchJob: Job? = null

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        val tab = currentTab.value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                when (tab) {
                    SearchTab.REPOS -> {
                        val composedQuery = composeRepoQuery(q, repoLanguage.value)
                        val r = api.searchRepositories(
                            query = composedQuery,
                            page = 1,
                            sort = repoSort.value.apiValue,
                            order = if (repoSort.value.apiValue == null) null else sortOrder.value.apiValue,
                        )
                        _repos.update { r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.USERS -> {
                        val r = api.searchUsers(q, page = 1)
                        _users.update { r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.CODE -> {
                        val r = api.searchCode(q, page = 1)
                        _code.update { r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.ISSUES -> {
                        val r = api.searchIssues(q, page = 1)
                        _issues.update { r.items }
                        totalCounts[tab] = r.total_count
                    }
                }
                pages[tab] = 1
                _searchedQuery.update { q }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _error.update { e.localizedMessage ?: "Search failed" }
                // Keep previous results visible — the error state only shows when there's
                // nothing to display.
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun loadMore() {
        val q = _searchedQuery.value
        if (q.isBlank() || _isLoading.value || _isLoadingMore.value || _error.value != null) return
        val tab = currentTab.value
        val nextPage = (pages[tab] ?: 1) + 1
        viewModelScope.launch {
            _isLoadingMore.update { true }
            try {
                when (tab) {
                    SearchTab.REPOS -> {
                        if (_repos.value.size >= (totalCounts[tab] ?: Int.MAX_VALUE)) return@launch
                        val composedQuery = composeRepoQuery(q, repoLanguage.value)
                        val r = api.searchRepositories(
                            query = composedQuery,
                            page = nextPage,
                            sort = repoSort.value.apiValue,
                            order = if (repoSort.value.apiValue == null) null else sortOrder.value.apiValue,
                        )
                        if (r.items.isEmpty()) { totalCounts[tab] = _repos.value.size; return@launch }
                        _repos.update { it + r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.USERS -> {
                        if (_users.value.size >= (totalCounts[tab] ?: Int.MAX_VALUE)) return@launch
                        val r = api.searchUsers(q, page = nextPage)
                        if (r.items.isEmpty()) { totalCounts[tab] = _users.value.size; return@launch }
                        _users.update { it + r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.CODE -> {
                        if (_code.value.size >= (totalCounts[tab] ?: Int.MAX_VALUE)) return@launch
                        val r = api.searchCode(q, page = nextPage)
                        if (r.items.isEmpty()) { totalCounts[tab] = _code.value.size; return@launch }
                        _code.update { it + r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.ISSUES -> {
                        if (_issues.value.size >= (totalCounts[tab] ?: Int.MAX_VALUE)) return@launch
                        val r = api.searchIssues(q, page = nextPage)
                        if (r.items.isEmpty()) { totalCounts[tab] = _issues.value.size; return@launch }
                        _issues.update { it + r.items }
                        totalCounts[tab] = r.total_count
                    }
                }
                pages[tab] = nextPage
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Swallow pagination errors — the user can scroll again to retry.
            } finally {
                _isLoadingMore.update { false }
            }
        }
    }

    fun canLoadMore(tab: SearchTab): Boolean {
        if (_searchedQuery.value.isBlank()) return false
        val size = when (tab) {
            SearchTab.REPOS -> _repos.value.size
            SearchTab.USERS -> _users.value.size
            SearchTab.CODE -> _code.value.size
            SearchTab.ISSUES -> _issues.value.size
        }
        return size > 0 && size < (totalCounts[tab] ?: Int.MAX_VALUE)
    }

    fun switchTab(tab: SearchTab) {
        if (currentTab.value == tab) return
        currentTab.value = tab
        // Only hit the network if this tab has no results for the current query yet;
        // otherwise keep the previously fetched results (and their error state).
        val hasResults = when (tab) {
            SearchTab.REPOS -> _repos.value.isNotEmpty()
            SearchTab.USERS -> _users.value.isNotEmpty()
            SearchTab.CODE -> _code.value.isNotEmpty()
            SearchTab.ISSUES -> _issues.value.isNotEmpty()
        }
        if (!hasResults) _error.update { null }
        if (query.value.isNotBlank() && !hasResults) search()
    }

    /** Apply a new sort/order/language to the repos tab and re-search immediately. */
    fun applyRepoFilters(
        sort: RepoSort = repoSort.value,
        order: SortOrder = sortOrder.value,
        language: String = repoLanguage.value,
    ) {
        repoSort.value = sort
        sortOrder.value = order
        repoLanguage.value = language
        if (query.value.isNotBlank()) search()
    }

    /** Compose the GitHub search query string — user text plus optional `language:` qualifier. */
    private fun composeRepoQuery(rawQuery: String, language: String): String {
        val trimmed = rawQuery.trim()
        if (language.isBlank()) return trimmed
        // GitHub supports `language:Kotlin` as a qualifier in q. Words and quoted
        // strings are left intact; just append the qualifier.
        return "$trimmed language:$language"
    }
}
