package com.pockethub.ui.profile

import androidx.lifecycle.ViewModel
import com.pockethub.util.userMessage
import androidx.lifecycle.viewModelScope
import com.pockethub.data.local.AccountEntity
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.CachedRepository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,
    private val api: GitHubApi,
    private val cache: CachedRepository,
    private val accounts: AccountRepository,
    private val authInterceptor: AuthInterceptor,
    private val publicApi: com.pockethub.data.remote.PublicEndpoints,
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

    private val _starredTotal = MutableStateFlow(0)
    val starredTotal: StateFlow<Int> = _starredTotal

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
    private var workRequestId = 0

    // ── Followers / following bottom sheet (same data source as UserDetail) ──
    private val _followers = MutableStateFlow<List<com.pockethub.data.model.User>>(emptyList())
    val followers: StateFlow<List<com.pockethub.data.model.User>> = _followers

    private val _followingList = MutableStateFlow<List<com.pockethub.data.model.User>>(emptyList())
    val followingList: StateFlow<List<com.pockethub.data.model.User>> = _followingList

    private val _isLoadingFollowLists = MutableStateFlow(false)
    val isLoadingFollowLists: StateFlow<Boolean> = _isLoadingFollowLists

    private val _followListsFailed = MutableStateFlow(false)
    val followListsFailed: StateFlow<Boolean> = _followListsFailed

    /** Load the signed-in user's followers + following (sheet data). */
    fun loadFollowLists() {
        val login = _user.value?.login ?: return
        if (_isLoadingFollowLists.value) return
        viewModelScope.launch {
            _isLoadingFollowLists.update { true }
            try {
                // Authed first, anonymous retry on failure — org policies can
                // 404 the OAuth-app token on org-related public data.
                val (f1, e1) = com.pockethub.data.remote.withPublicFallback(
                    { api.getFollowers(login) },
                    { publicApi.getFollowers(login) },
                )
                val (f2, e2) = com.pockethub.data.remote.withPublicFallback(
                    { api.getFollowing(login) },
                    { publicApi.getFollowing(login) },
                )
                _followers.update { f1 ?: emptyList() }
                _followingList.update { f2 ?: emptyList() }
                val firstError = e1 ?: e2
                firstError?.let { issueReporter.reportError("Profile", "loadFollowLists", it) }
                _followListsFailed.update { firstError != null }
            } finally {
                _isLoadingFollowLists.update { false }
            }
        }
    }

    init { loadProfile() }

    fun loadProfile(force: Boolean = false) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                val token = accounts.getActiveToken()
                if (token.isNotBlank()) authInterceptor.token = token
                val me = api.getAuthenticatedUser()
                _user.update { me }
                launch {
                    try { _starredTotal.value = cache.getStarredTotalCount() } catch (_: Exception) {}
                }
                loadWorkList(_workTab.value, force = force)
            } catch (e: Exception) {
                issueReporter.reportError("Profile", "loadProfile", e)
            } finally {
                _isLoading.update { false }
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
        val requestId = ++workRequestId
        viewModelScope.launch {
            _isLoadingWork.update { true }
            _workError.update { null }
            try {
                // state:open keeps the board actionable; sort by updated desc so the
                // freshest items surface first. GitHub /search/issues returns issues
                // and PRs together — `pullRequest` on each item distinguishes them.
                val q = "${tab.queryQualifier}:$login state:open"
                val result = api.searchIssues(q, sort = "updated", order = "desc", perPage = 30)
                if (requestId != workRequestId) return@launch
                _workItems.value = result.items
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId != workRequestId) return@launch
                issueReporter.reportError("Profile", "loadWorkList", e)
                _workError.value = e.userMessage("Failed to load work list")
                _workItems.value = emptyList()
            } finally {
                if (requestId == workRequestId) _isLoadingWork.update { false }
            }
        }
    }

    fun refreshWorkList() = loadWorkList(_workTab.value, force = true)

    fun refresh() {
        _followListsFailed.update { false }
        loadProfile(force = true)
    }

}
