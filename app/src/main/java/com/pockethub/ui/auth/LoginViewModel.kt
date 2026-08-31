package com.pockethub.ui.auth

import androidx.lifecycle.ViewModel
import com.pockethub.util.userMessage
import androidx.lifecycle.viewModelScope
import com.pockethub.BuildConfig
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.GitHubApi
import com.pockethub.data.remote.OAuthEndpoints
import com.pockethub.data.remote.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom
import android.util.Base64
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
    private val issueReporter: com.pockethub.data.reporting.IssueReporter,
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
    val loginHistory = accounts.allAccounts

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
                issueReporter.reportError("Login", "signInWithToken", e)
                authInterceptor.token = ""
                _ui.update {
                    it.copy(
                        isLoading = false,
                        error = e.userMessage("Token validation failed.")
                    )
                }
            }
        }
    }

    /**
     * Initiate OAuth: build the authorization URL using either the built-in
     * default OAuth Client or a user-provided custom client (from Settings).
     */
    fun loginFromHistory(id: Long) { viewModelScope.launch { _ui.update { it.copy(isLoading=true,error=null) }; if(accounts.loginStoredAccount(id)) _ui.update { it.copy(isLoading=false,success=true) } else _ui.update { it.copy(isLoading=false,error="Stored login is no longer valid.") } } }
    private var oauthState: String? = null
    fun startOAuth() {
        viewModelScope.launch {
            try {
            val backend = settings.oauthBackendUrl.first().trim().removeSuffix("/")
            if (!backend.startsWith("https://")) {
                _ui.update { it.copy(error = "请先在设置 → Custom OAuth Client 中填写 OAuth 后端地址。") }
                return@launch
            }
            val config = api.getOAuthBackendConfig("$backend/config")
            val clientId = config.clientId
            val redirectUri = config.redirectUri.ifBlank { BuildConfig.GITHUB_OAUTH_REDIRECT_URI }
            if (clientId.isBlank()) {
                _ui.update { it.copy(error = "OAuth 后端未配置 GITHUB_CLIENT_ID。") }
                return@launch
            }
            val scope="repo read:user user:email read:org read:notifications"
            val b=ByteArray(32).also{SecureRandom().nextBytes(it)}
            oauthState=Base64.encodeToString(b,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            settings.setPendingOAuthState(oauthState!!)
            val url=config.authorizeUrl+"?client_id=${java.net.URLEncoder.encode(clientId,"UTF-8")}"+"&redirect_uri=${java.net.URLEncoder.encode(redirectUri,"UTF-8")}"+"&scope=${java.net.URLEncoder.encode(scope,"UTF-8")}"+"&state=${java.net.URLEncoder.encode(oauthState,"UTF-8")}"
            _ui.update { it.copy(oauthUrl = url) }
            } catch (e: Exception) {
                issueReporter.reportError("Login", "startOAuth", e)
                _ui.update { it.copy(isLoading = false, error = e.userMessage("OAuth 后端不可用，请检查地址和 GITHUB_CLIENT_ID 配置。")) }
            }
        }
    }

    /**
     * Exchange the OAuth code (received via the `pockethub://oauth/callback` deep link)
     * for an access token. The deep link is handled by [MainActivity]; once it receives
     * `code=xxx`, it will call this function.
     */
    fun exchangeOAuthCode(code: String, state: String?) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val expected=oauthState?:settings.consumePendingOAuthState()
                if(expected==null||expected!=state){_ui.update{it.copy(isLoading=false,error="OAuth callback verification failed.")};return@launch}
                settings.consumePendingOAuthState();oauthState=null
                val backend = settings.oauthBackendUrl.first().trim().removeSuffix("/")
                if (!backend.startsWith("https://")) {
                    _ui.update { it.copy(isLoading = false, error = "OAuth 后端地址未配置。") }
                    return@launch
                }
                val tokenResp = api.exchangeOAuthCodeViaBackend(
                    url = "$backend/oauth/exchange",
                    body = OAuthEndpoints.BackendExchangeRequest(
                        code = code,
                        redirectUri = BuildConfig.GITHUB_OAUTH_REDIRECT_URI,
                    ),
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
                issueReporter.reportError("Login", "exchangeOAuthCode", e)
                _ui.update {
                    it.copy(isLoading = false, error = e.userMessage("OAuth exchange failed."))
                }
            }
        }
    }

    fun clearError() { _ui.update { it.copy(error = null) } }
    fun clearOAuthUrl() { _ui.update { it.copy(oauthUrl = null) } }
}
