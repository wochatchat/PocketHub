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

enum class SortOrder(val apiValue: String) {
    DESC("desc"),
    ASC("asc"),
}

/** Repo search sort options; "" = GitHub default ("best match"). */
enum class RepoSort(val apiValue: String?) {
    BEST_MATCH(null),
    STARS("stars"),
    FORKS("forks"),
    UPDATED("updated"),
}

/** User search sort options; "" = GitHub default ("best match"). */
enum class UserSort(val apiValue: String?) {
    BEST_MATCH(null),
    FOLLOWERS("followers"),
    REPOSITORIES("repositories"),
    JOINED("joined"),
}

/** Issue search sort options (works on /search/issues). */
enum class IssueSort(val apiValue: String) {
    CREATED("created"),
    UPDATED("updated"),
    COMMENTS("comments"),
}

/** Issue / PR type filter for the Issues tab. */
enum class IssueType(val qualifier: String) {
    ALL(""),
    ISSUE(" is:issue"),
    PR(" is:pr"),
}

/** Issue state filter for the Issues tab. */
enum class IssueState(val qualifier: String) {
    ALL(""),
    OPEN(" state:open"),
    CLOSED(" state:closed"),
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

    /** Users tab sort — applied as `sort` query param. */
    var userSort = MutableStateFlow(UserSort.BEST_MATCH)
    /** Users tab order — shared with repos tab. */
    var userOrder = MutableStateFlow(SortOrder.DESC)

    /** Code tab language filter — appended as `language:` qualifier. Empty = any. */
    var codeLanguage = MutableStateFlow("")
    /** Code tab extension filter — appended as `extension:xxx` qualifier. Empty = any. */
    var codeExtension = MutableStateFlow("")

    /** Issues tab filters — all qualifiers appended to q string. */
    var issueSort = MutableStateFlow(IssueSort.UPDATED)
    /** Issues tab order — shared with repos tab. */
    var issueOrder = MutableStateFlow(SortOrder.DESC)
    var issueType = MutableStateFlow(IssueType.ALL)
    var issueState = MutableStateFlow(IssueState.ALL)

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
                        val r = api.searchUsers(
                            query = q,
                            page = 1,
                            sort = userSort.value.apiValue,
                            order = if (userSort.value.apiValue == null) null else userOrder.value.apiValue,
                        )
                        _users.update { r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.CODE -> {
                        val composedQuery = composeCodeQuery(q, codeLanguage.value, codeExtension.value)
                        val r = api.searchCode(composedQuery, page = 1)
                        _code.update { r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.ISSUES -> {
                        val composedQuery = composeIssueQuery(q, issueType.value, issueState.value)
                        val r = api.searchIssues(
                            query = composedQuery,
                            page = 1,
                            sort = issueSort.value.apiValue,
                            order = issueOrder.value.apiValue,
                        )
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
                        val r = api.searchUsers(
                            query = q,
                            page = nextPage,
                            sort = userSort.value.apiValue,
                            order = if (userSort.value.apiValue == null) null else userOrder.value.apiValue,
                        )
                        if (r.items.isEmpty()) { totalCounts[tab] = _users.value.size; return@launch }
                        _users.update { it + r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.CODE -> {
                        if (_code.value.size >= (totalCounts[tab] ?: Int.MAX_VALUE)) return@launch
                        val composedQuery = composeCodeQuery(q, codeLanguage.value, codeExtension.value)
                        val r = api.searchCode(composedQuery, page = nextPage)
                        if (r.items.isEmpty()) { totalCounts[tab] = _code.value.size; return@launch }
                        _code.update { it + r.items }
                        totalCounts[tab] = r.total_count
                    }
                    SearchTab.ISSUES -> {
                        if (_issues.value.size >= (totalCounts[tab] ?: Int.MAX_VALUE)) return@launch
                        val composedQuery = composeIssueQuery(q, issueType.value, issueState.value)
                        val r = api.searchIssues(
                            query = composedQuery,
                            page = nextPage,
                            sort = issueSort.value.apiValue,
                            order = issueOrder.value.apiValue,
                        )
                        if (r.items.isEmpty()) { totalCounts[tab] = _issues.value.size; return@launch }
                        _issues.update { it + r.items }
                        totalCounts[tab] = r.total_count
                    }
                }
                pages[tab] = nextPage
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Restore the previous page counter so the next scroll attempts the
                // same page again (otherwise we'd silently skip a page on retry).
                // Don't surface as a hard error overlay — the user already has stale
                // results visible; we keep the footer spinner off instead.
                // (Hard failures are surfaced by the initial search(), not loadMore.)
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

    /** Apply users tab filters and re-search. */
    fun applyUsersFilters(
        sort: UserSort = userSort.value,
        order: SortOrder = userOrder.value,
    ) {
        userSort.value = sort
        userOrder.value = order
        if (query.value.isNotBlank()) search()
    }

    /**
     * Apply code tab filters and re-search. Language and extension are appended
     * to the q string (language: / extension: qualifiers).
     */
    fun applyCodeFilters(
        language: String = codeLanguage.value,
        extension: String = codeExtension.value,
    ) {
        codeLanguage.value = language
        codeExtension.value = extension
        if (query.value.isNotBlank()) search()
    }

    /** Compose code search query — append `language:` and `extension:` qualifiers. */
    private fun composeCodeQuery(rawQuery: String, language: String, extension: String): String {
        val trimmed = rawQuery.trim()
        val langPart = if (language.isBlank()) "" else " language:$language"
        val extPart = if (extension.isBlank()) "" else " extension:$extension"
        val composed = langPart + extPart
        return if (composed.isBlank()) trimmed else "$trimmed$composed"
    }

    /** Apply issues tab filters and re-search. */
    fun applyIssuesFilters(
        sort: IssueSort = issueSort.value,
        order: SortOrder = issueOrder.value,
        type: IssueType = issueType.value,
        state: IssueState = issueState.value,
    ) {
        issueSort.value = sort
        issueOrder.value = order
        issueType.value = type
        issueState.value = state
        if (query.value.isNotBlank()) search()
    }

    /** Compose issue search query — append `is:issue`/`is:pr` and `state:` qualifiers. */
    private fun composeIssueQuery(rawQuery: String, type: IssueType, state: IssueState): String {
        val trimmed = rawQuery.trim()
        val composed = type.qualifier + state.qualifier
        return if (composed.isBlank()) trimmed else "$trimmed$composed"
    }
}
