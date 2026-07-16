package com.pockethub.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing

    private val _followActionInProgress = MutableStateFlow(false)
    val followActionInProgress: StateFlow<Boolean> = _followActionInProgress

    private var loadedLogin: String? = null

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
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载用户失败" }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun refresh() {
        loadedLogin?.let { loadUser(it) }
    }
}
