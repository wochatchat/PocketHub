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
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载个人资料失败" }
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
