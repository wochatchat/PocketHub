package com.pockethub.ui.user

import androidx.lifecycle.ViewModel
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
    private val api: GitHubApi,
    private val cache: CachedRepository,
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

    fun loadUser(login: String) {
        if (loadedLogin == login && _user.value != null) return
        loadedLogin = login
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                _user.update { api.getUser(login) }
                // Load repos in parallel
                launch {
                    try {
                        _repos.update { cache.getUserRepositories(login, sort = "updated") }
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
                        if (!self) {
                            _isFollowing.update { api.checkFollowing(login).isSuccessful }
                        }
                    } catch (_: Exception) {
                        // Non-fatal — follow button just won't reflect state.
                    }
                }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "Failed to load user" }
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
                }
            } catch (_: Exception) {
                // Non-fatal
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
                val f1 = runCatching { api.getFollowers(login) }.getOrDefault(emptyList())
                val f2 = runCatching { api.getFollowing(login) }.getOrDefault(emptyList())
                _followers.update { f1 }
                _followingList.update { f2 }
            } finally {
                _isLoadingFollowLists.update { false }
            }
        }
    }

    fun refresh() {
        loadedLogin?.let { loadUser(it) }
    }
}
