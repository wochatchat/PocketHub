package com.pockethub.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.local.AccountEntity
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
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
    private val accounts: AccountRepository,
    private val authInterceptor: AuthInterceptor,
) : ViewModel() {

    private val _user = MutableStateFlow<com.pockethub.data.model.User?>(null)
    val user: StateFlow<com.pockethub.data.model.User?> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** First page of the authed user's own repositories, sorted by pushed_at desc. */
    private val _topRepos = MutableStateFlow<List<Repository>>(emptyList())
    val topRepos: StateFlow<List<Repository>> = _topRepos

    private val _isLoadingRepos = MutableStateFlow(false)
    val isLoadingRepos: StateFlow<Boolean> = _isLoadingRepos

    /** Approx count of starred repos (size of first page only, since the API doesn't easily expose totals). */
    private val _starredTotal = MutableStateFlow(0)
    val starredTotal: StateFlow<Int> = _starredTotal

    val allAccounts: StateFlow<List<AccountEntity>> =
        accounts.allAccounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccount: StateFlow<AccountEntity?> =
        accounts.activeAccount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                val token = accounts.getActiveToken()
                if (token.isNotBlank()) {
                    authInterceptor.token = token
                }
                val me = api.getAuthenticatedUser()
                _user.update { me }
                // Kick off repo + starred loads in parallel — failures don't abort the profile.
                launch { try { _topRepos.value = api.getMyRepositories(perPage = 10, sort = "pushed") } catch (_: Exception) {} }
                launch {
                    try {
                        val starred = api.getStarredRepositories(perPage = 1)
                        _starredTotal.value = starred.size
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                // continue with cached state
            } finally {
                _isLoading.update { false }
            }
        }
    }

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
