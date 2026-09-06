package com.pockethub.ui.user

import androidx.lifecycle.ViewModel
import com.pockethub.util.userMessage
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.FeedEvent
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User
import com.pockethub.data.remote.CachedRepository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserDetailViewModel @Inject constructor(
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,
    private val api: GitHubApi,
    private val cache: CachedRepository,
    private val publicApi: com.pockethub.data.remote.PublicEndpoints,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _repos = MutableStateFlow<List<Repository>>(emptyList())
    val repos: StateFlow<List<Repository>> = _repos

    private val _events = MutableStateFlow<List<FeedEvent>>(emptyList())
    val events: StateFlow<List<FeedEvent>> = _events

    private val _isLoadingEvents = MutableStateFlow(false)
    val isLoadingEvents: StateFlow<Boolean> = _isLoadingEvents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing

    private val _followActionInProgress = MutableStateFlow(false)
    val followActionInProgress: StateFlow<Boolean> = _followActionInProgress

    private var loadedLogin: String? = null

    private val _followers = MutableStateFlow<List<User>>(emptyList())
    val followers: StateFlow<List<User>> = _followers

    private val _followingList = MutableStateFlow<List<User>>(emptyList())
    val followingList: StateFlow<List<User>> = _followingList

    private val _isLoadingFollowLists = MutableStateFlow(false)
    val isLoadingFollowLists: StateFlow<Boolean> = _isLoadingFollowLists

    /** True when viewing your own profile — hides the follow button. */
    private val _isSelf = MutableStateFlow(false)
    val isSelf: StateFlow<Boolean> = _isSelf

    /** Whether the profile is an Organization — orgs can't be followed on GitHub. */
    private val _isOrganization = MutableStateFlow(false)
    val isOrganization: StateFlow<Boolean> = _isOrganization

    /** One-shot message for follow / follow-list failures (shown as a toast). */
    private val _followMessage = MutableStateFlow<String?>(null)
    val followMessage: StateFlow<String?> = _followMessage

    /** True when the last follow-lists load failed — the sheet shows retry. */
    private val _followListsFailed = MutableStateFlow(false)
    val followListsFailed: StateFlow<Boolean> = _followListsFailed

    /** Human-readable reason for the last follow-lists load failure. */
    private val _followListsError = MutableStateFlow<String?>(null)
    val followListsError: StateFlow<String?> = _followListsError

    /** True when that failure was a permission/authorization class (403/404). */
    private val _followListsPermission = MutableStateFlow(false)
    val followListsPermission: StateFlow<Boolean> = _followListsPermission

    fun consumeFollowMessage() {
        _followMessage.update { null }
    }

    fun loadUser(login: String, force: Boolean = false) {
        if (!force && loadedLogin == login && _user.value != null) return
        loadedLogin = login
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                if (force) {
                    cache.invalidateUserRepositories(login)
                    _repos.value = emptyList()
                    _events.value = emptyList()
                }
                _user.update { api.getUser(login) }
                // Orgs can't be followed (GitHub web shows no follow button on
                // org pages either) — gate the follow button on account type.
                _isOrganization.update { _user.value?.type.equals("Organization", ignoreCase = true) }
                // Load repos in parallel
                launch {
                    try {
                        // force=true → straight to the network; otherwise cache-first.
                        if (force) {
                            _repos.update { api.getUserRepositories(login, sort = "updated") }
                        } else {
                            _repos.update { cache.getUserRepositories(login, sort = "updated") }
                        }
                    } catch (_: Exception) {
                        // Non-fatal
                    }
                }
                // Load public activity feed in parallel
                launch {
                    _isLoadingEvents.update { true }
                    try {
                        _events.update { runCatching { api.getUserEvents(login) }.getOrDefault(emptyList()) }
                    } finally {
                        _isLoadingEvents.update { false }
                    }
                }
                // Determine whether this is the authenticated user's own profile,
                // and whether we already follow them.
                launch {
                    try {
                        val me = api.getAuthenticatedUser()
                        val self = me.login.equals(login, ignoreCase = true)
                        _isSelf.update { self }
                        if (self) {
                            // /users/{login} only returns public counts; the
                            // /user payload carries private repo totals too.
                            _user.update { me }
                        }
                        if (!self) {
                            _isFollowing.update { api.checkFollowing(login).isSuccessful }
                        }
                    } catch (_: Exception) {
                        // Non-fatal — follow button just won't reflect state.
                    }
                }
            } catch (e: Exception) {
                issueReporter.reportError("UserDetail", "loadUser", e)
                _error.update { e.userMessage("Failed to load user") }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    /** Toggle follow / unfollow on the loaded user. */
    fun toggleFollow() {
        val login = loadedLogin ?: return
        if (_followActionInProgress.value || _isSelf.value) return
        viewModelScope.launch {
            _followActionInProgress.update { true }
            try {
                val currentlyFollowing = _isFollowing.value
                val resp = if (currentlyFollowing) api.unfollowUser(login) else api.followUser(login)
                if (resp.isSuccessful) {
                    _isFollowing.update { !currentlyFollowing }
                    // Optimistically adjust the follower count shown in the stats row.
                    _user.update { u ->
                        u?.copy(followers = (u.followers ?: 0) + if (currentlyFollowing) -1 else 1)
                    }
                } else if (resp.code() == 404) {
                    // Two distinct 404 causes with different remedies. The
                    // response's X-OAuth-Scopes header tells them apart:
                    // scope absent → token predates user:follow (re-login);
                    // scope present → org OAuth-app restriction (browser).
                    val hasFollowScope = resp.headers()["x-oauth-scopes"]
                        ?.split(',')?.map { it.trim() }
                        ?.any { it == "user" || it == "user:follow" }
                    val msg = when (hasFollowScope) {
                        false -> appContext.getString(com.pockethub.R.string.follow_needs_relogin)
                        true -> appContext.getString(com.pockethub.R.string.follow_org_restricted)
                        // Header absent (uncommon) — fall back to probing whether
                        // the org's public data is reachable anonymously.
                        null -> {
                            val orgReachable = runCatching {
                                publicApi.getFollowers(login, page = 1, perPage = 1)
                            }.isSuccess
                            appContext.getString(
                                if (orgReachable) com.pockethub.R.string.follow_org_restricted
                                else com.pockethub.R.string.follow_needs_relogin
                            )
                        }
                    }
                    _followMessage.update { msg }
                } else {
                    "HTTP ${resp.code()}: ${resp.message()}".let { m ->
                        _followMessage.update { m }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _followMessage.update { e.userMessage("Network error") }
            } finally {
                _followActionInProgress.update { false }
            }
        }
    }

    /** Load followers + following lists (shown in a bottom sheet). */
    fun loadFollowLists() {
        val login = loadedLogin ?: return
        if (_isLoadingFollowLists.value) return
        viewModelScope.launch {
            _isLoadingFollowLists.update { true }
            try {
                // Authed call first; on failure retry anonymously — org policies
                // (OAuth-app access restrictions) 404 member tokens on org-related
                // endpoints even for public data (see PublicEndpoints).
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
                firstError?.let { issueReporter.reportError("UserDetail", "loadFollowLists", it) }
                _followListsFailed.update { firstError != null }
                _followListsError.update { firstError?.userMessage("Couldn't load follow lists") }
                // Distinguish "check your network" from a permission problem:
                // GitHub signals OAuth-scope / org-restriction denials with
                // 403/404, network failures arrive as IOException.
                _followListsPermission.update {
                    firstError is retrofit2.HttpException &&
                        (firstError as retrofit2.HttpException).code() in intArrayOf(403, 404)
                }
            } finally {
                _isLoadingFollowLists.update { false }
            }
        }
    }

    fun refresh() {
        loadedLogin?.let { loadUser(it, force = true) }
    }
}
