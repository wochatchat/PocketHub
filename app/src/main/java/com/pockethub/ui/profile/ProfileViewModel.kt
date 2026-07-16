package com.pockethub.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.local.AccountEntity
import com.pockethub.data.model.User
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val api: GitHubApi,
    private val accounts: AccountRepository,
    private val authInterceptor: AuthInterceptor,
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val allAccounts = accounts.allAccounts
    val activeAccount = accounts.activeAccount

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                // Sync the interceptor with the active token
                val token = accounts.getActiveToken()
                if (token.isNotBlank()) {
                    authInterceptor.token = token
                }
                _user.update { api.getAuthenticatedUser() }
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
