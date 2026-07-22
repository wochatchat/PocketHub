package com.pockethub.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.BuildConfig
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
 *
 * Login paths:
 *  1. Primary:   OAuth App (preferred — opens GitHub's authorize page).
 *  2. Fallback:  Personal Access Token (user creates one and pastes here).
 *  3. Freeform:  "Sign in via GitHub website" — opens github.com/login in
 *                a CustomTab and lets GitHub handle the password itself.
 *                After logging in on the web, the user still has to either
 *                come back to PocketHub and authorize via OAuth, or paste a
 *                PAT. We can't intercept the website login automatically.
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
     * Temporarily sets the token on the injected AuthInterceptor, validates by
     * fetching /user, then persists the account.
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
     * Initiate OAuth: build the authorization URL using either the built-in
     * default OAuth Client or a user-provided custom client (from Settings).
     */
    fun startOAuth() {
        viewModelScope.launch {
            val customId = settings.customClientId.first()
            val clientId = customId.ifBlank { BuildConfig.GITHUB_DEFAULT_CLIENT_ID }
            if (clientId.isBlank()) {
                _ui.update {
                    it.copy(
                        error = "OAuth Client ID is not configured.\n\n" +
                            "To create one, go to GitHub Settings → Developer settings → OAuth Apps, " +
                            "create a new OAuth App, then copy the Client ID into PocketHub Settings → Custom OAuth Client.\n\n" +
                            "Or simply sign in with a Personal Access Token (fastest)."
                    )
                }
                return@launch
            }
            val redirectUri = BuildConfig.GITHUB_OAUTH_REDIRECT_URI
            val scope = "repo read:user user:email read:org read:notifications"
            val url = "https://github.com/login/oauth/authorize" +
                "?client_id=$clientId" +
                "&redirect_uri=$redirectUri" +
                "&scope=${java.net.URLEncoder.encode(scope, "UTF-8")}"
            _ui.update { it.copy(oauthUrl = url) }
        }
    }

    /**
     * Exchange the OAuth code (received via the `pockethub://oauth/callback` deep link)
     * for an access token. The deep link is handled by [MainActivity]; once it receives
     * `code=xxx`, it will call this function.
     */
    fun exchangeOAuthCode(code: String) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val customId = settings.customClientId.first()
                val customSecret = settings.customClientSecret.first()
                val clientId = customId.ifBlank { BuildConfig.GITHUB_DEFAULT_CLIENT_ID }
                val clientSecret = customSecret.ifBlank { BuildConfig.GITHUB_DEFAULT_CLIENT_SECRET }
                if (clientId.isBlank() || clientSecret.isBlank()) {
                    _ui.update {
                        it.copy(
                            isLoading = false,
                            error = "OAuth Client ID/Secret not configured — cannot complete the token exchange.\n" +
                                "Please go to Settings → Custom OAuth Client and enter your OAuth App details."
                        )
                    }
                    return@launch
                }

                val tokenResp = api.exchangeOAuthCode(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    code = code,
                    redirectUri = BuildConfig.GITHUB_OAUTH_REDIRECT_URI,
                )
                if (tokenResp.error != null) {
                    _ui.update {
                        it.copy(isLoading = false, error = tokenResp.errorDescription ?: tokenResp.error)
                    }
                    return@launch
                }
                val token = tokenResp.access_token
                if (token.isBlank()) {
                    _ui.update { it.copy(isLoading = false, error = "GitHub returned an empty token.") }
                    return@launch
                }

                // Validate and save the account
                authInterceptor.token = token
                val user = api.getAuthenticatedUser()
                accounts.addAccount(
                    login = user.login,
                    token = token,
                    tokenType = "oauth",
                    name = user.name,
                    avatarUrl = user.avatarUrl,
                    scopes = tokenResp.scope,
                )
                _ui.update { it.copy(isLoading = false, success = true) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(isLoading = false, error = e.localizedMessage ?: "OAuth exchange failed.")
                }
            }
        }
    }

    fun clearError() { _ui.update { it.copy(error = null) } }
    fun clearOAuthUrl() { _ui.update { it.copy(oauthUrl = null) } }
}
