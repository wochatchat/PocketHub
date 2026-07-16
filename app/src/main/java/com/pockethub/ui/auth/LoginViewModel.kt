package com.pockethub.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.GitHubApi
import com.pockethub.data.remote.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Login screen ViewModel — handles PAT validation and OAuth flow initiation.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: GitHubApi,
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val authInterceptor: AuthInterceptor,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
        val oauthUrl: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    /**
     * Sign in with a Personal Access Token.
     * Temporarily sets the token on the injected AuthInterceptor, validates by fetching /user,
     * then persists the account and keeps the token on the interceptor.
     */
    fun signInWithToken(token: String) {
        if (token.isBlank()) {
            _ui.update { it.copy(error = "Token cannot be empty.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                authInterceptor.token = token
                val user = api.getAuthenticatedUser()
                accounts.addAccount(
                    login = user.login,
                    token = token,
                    name = user.name,
                    avatarUrl = user.avatarUrl,
                )
                _ui.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                authInterceptor.token = ""
                _ui.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Token validation failed."
                    )
                }
            }
        }
    }

    /**
     * Initiate OAuth: build the authorization URL using either the built-in client
     * or a user-provided custom client.
     */
    fun startOAuth() {
        viewModelScope.launch {
            val customId = settings.customClientId.first()
            val clientId = customId.ifBlank { "" }
            if (clientId.isBlank()) {
                _ui.update { it.copy(error = "No OAuth Client ID configured. Use a Personal Access Token, or add your Client ID in Settings → About → Custom OAuth Client.") }
                return@launch
            }
            val redirectUri = "pockethub://oauth/callback"
            val url = "https://github.com/login/oauth/authorize" +
                "?client_id=$clientId" +
                "&redirect_uri=$redirectUri" +
                "&scope=repo read:user user:email read:org read:notifications"
            _ui.update { it.copy(oauthUrl = url) }
        }
    }

    fun clearError() { _ui.update { it.copy(error = null) } }
    fun clearOAuthUrl() { _ui.update { it.copy(oauthUrl = null) } }
}
