package com.pockethub.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.local.AccountEntity
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.CachedRepository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: GitHubApi,
    private val cache: CachedRepository,
    private val accounts: AccountRepository,
    private val authInterceptor: AuthInterceptor,
) : ViewModel() {

    /** Work-list scope — what to surface on the user's "to handle" board. */
    enum class WorkTab(val queryQualifier: String) {
        ASSIGNED("assignee"),
        MENTIONED("mentions"),
        CREATED("author"),
        INVOLVED("involves"),
    }

    private val _user = MutableStateFlow<com.pockethub.data.model.User?>(null)
    val user: StateFlow<com.pockethub.data.model.User?> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _topRepos = MutableStateFlow<List<Repository>>(emptyList())
    val topRepos: StateFlow<List<Repository>> = _topRepos

    private val _isLoadingRepos = MutableStateFlow(false)
    val isLoadingRepos: StateFlow<Boolean> = _isLoadingRepos

    // ── Repository pagination ──
    // Show *all* of the user's own repositories (not just a top-10 slice), paging
    // through the API 30-per-page and appending lazy-loaded chunks to the visible list.
    private val _reposPage = MutableStateFlow(1)
    private val _hasMoreRepos = MutableStateFlow(true)
    val hasMoreRepos: StateFlow<Boolean> = _hasMoreRepos.asStateFlow()
    private val _isLoadingMoreRepos = MutableStateFlow(false)
    val isLoadingMoreRepos: StateFlow<Boolean> = _isLoadingMoreRepos.asStateFlow()

    private val _starredTotal = MutableStateFlow(0)
    val starredTotal: StateFlow<Int> = _starredTotal

    /** My public activity feed (PushEvent / WatchEvent / ForkEvent …). Loaded once when the page opens. */
    private val _events = MutableStateFlow<List<com.pockethub.data.model.FeedEvent>>(emptyList())
    val events: StateFlow<List<com.pockethub.data.model.FeedEvent>> = _events
    private val _isLoadingEvents = MutableStateFlow(false)
    val isLoadingEvents: StateFlow<Boolean> = _isLoadingEvents

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val allAccounts: StateFlow<List<AccountEntity>> =
        accounts.allAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccount: StateFlow<AccountEntity?> =
        accounts.activeAccount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Work-list (Assigned / Mentioned / Created / Involved) ─────────────
    private val _workTab = MutableStateFlow(WorkTab.ASSIGNED)
    val workTab: StateFlow<WorkTab> = _workTab

    private val _workItems = MutableStateFlow<List<com.pockethub.data.model.Issue>>(emptyList())
    val workItems: StateFlow<List<com.pockethub.data.model.Issue>> = _workItems

    private val _isLoadingWork = MutableStateFlow(false)
    val isLoadingWork: StateFlow<Boolean> = _isLoadingWork

    private val _workError = MutableStateFlow<String?>(null)
    val workError: StateFlow<String?> = _workError

    private var loadedWorkLogin: String? = null
    private var loadedWorkTab: WorkTab? = null

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                val token = accounts.getActiveToken()
                if (token.isNotBlank()) authInterceptor.token = token
                val me = api.getAuthenticatedUser()
                _user.update { me }
                // Reset pagination then fetch page 1 (clears anything stale).
                _reposPage.value = 1
                _hasMoreRepos.value = true
                launch { try { loadMoreRepos(reset = true) } catch (_: Exception) {} }
                launch { try { _starredTotal.value = cache.getStarredRepositories(page = 1).size } catch (_: Exception) {} }
                launch {
                    _isLoadingEvents.update { true }
                    try { _events.value = runCatching { api.getUserEvents(me.login) }.getOrDefault(emptyList()) }
                    finally { _isLoadingEvents.update { false } }
                }
                // Auto-load the default work tab once we know the active login.
                loadWorkList(_workTab.value, force = false)
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "Failed to load profile" }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    /**
     * Append the next page of my repositories (pushed-sorted). When [reset] is
     * true the list is cleared and page 1 is fetched fresh.
     *
     * GitHub's repository listing has no total_count, so we infer "more" from
     * the returned chunk size — a page smaller than the API's per-page cap
     * (currently 30) means we've reached the end.
     */
    fun loadMoreRepos(reset: Boolean = false) {
        val login = _user.value?.login ?: return
        if (!reset && (_isLoadingMoreRepos.value || !_hasMoreRepos.value)) return
        viewModelScope.launch {
            _isLoadingMoreRepos.update { true }
            if (reset) {
                _isLoadingRepos.value = true
                _reposPage.value = 1
                _hasMoreRepos.value = true
            }
            val nextPage = if (reset) 1 else _reposPage.value + 1
            try {
                val chunk = cache.getMyRepositories(page = nextPage, sort = "pushed")
                if (reset) _topRepos.value = chunk
                else _topRepos.value = _topRepos.value + chunk
                _reposPage.value = nextPage
                // End-of-list detection: a short page means no more are coming.
                _hasMoreRepos.value = chunk.size >= 30
            } catch (_: Exception) {
                if (reset) _topRepos.value = emptyList()
                _hasMoreRepos.value = false
            } finally {
                _isLoadingMoreRepos.value = false
                if (reset) _isLoadingRepos.value = false
            }
        }
    }

    fun switchWorkTab(tab: WorkTab) {
        _workTab.value = tab
        loadWorkList(tab, force = false)
    }

    /** Fetch the user's work-list for the given qualifier. Cached by tab+login. */
    fun loadWorkList(tab: WorkTab, force: Boolean = false) {
        val login = _user.value?.login
        if (login.isNullOrBlank()) return
        if (!force && loadedWorkLogin == login && loadedWorkTab == tab && _workItems.value.isNotEmpty()) return
        loadedWorkLogin = login
        loadedWorkTab = tab
        viewModelScope.launch {
            _isLoadingWork.update { true }
            _workError.update { null }
            try {
                // state:open keeps the board actionable; sort by updated desc so the
                // freshest items surface first. GitHub /search/issues returns issues
                // and PRs together — `pullRequest` on each item distinguishes them.
                val q = "${tab.queryQualifier}:$login state:open sort:updated-desc"
                val result = api.searchIssues(q, sort = "updated", order = "desc", perPage = 30)
                _workItems.value = result.items
            } catch (e: Exception) {
                _workError.value = e.localizedMessage ?: "Failed to load work list"
                _workItems.value = emptyList()
            } finally {
                _isLoadingWork.update { false }
            }
        }
    }

    fun refreshWorkList() = loadWorkList(_workTab.value, force = true)

    fun switchAccount(id: Long) {
        viewModelScope.launch {
            accounts.switchAccount(id)
            loadProfile()
        }
    }

    fun removeAccount(id: Long) {
        viewModelScope.launch {
            accounts.removeAccount(id)
            loadProfile()
        }
    }
}
