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

    private val _starredTotal = MutableStateFlow(0)
    val starredTotal: StateFlow<Int> = _starredTotal

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
                launch { try { _topRepos.value = cache.getMyRepositories(page = 1, sort = "pushed").take(10) } catch (_: Exception) {} }
                launch { try { _starredTotal.value = cache.getStarredRepositories(page = 1).size } catch (_: Exception) {} }
                // Auto-load the default work tab once we know the active login.
                loadWorkList(_workTab.value, force = false)
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载个人资料失败" }
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
                _workError.value = e.localizedMessage ?: "加载工作列表失败"
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
