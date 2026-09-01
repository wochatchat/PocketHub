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
     * Initiate OAuth. Two paths (dispatch rule: a user-configured custom
     * client always wins — legacy direct exchange; otherwise use the
     * self-hosted backend so no client secret lives on the device):
     *
     *  - Direct:  build the authorize URL from the custom client ID.
     *  - Backend: fetch {client_id, authorize_url, redirect_uri} from the
     *             backend's /config (protocol from #32 by @Wxjxpp).
     *
     * Both paths attach a random `state` — persisted as a one-time pending
     * value and verified against the callback in [exchangeOAuthCode].
     */
    private var oauthState: String? = null

    private fun newOAuthState(): String {
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
        )
    }

    fun startOAuth() {
        viewModelScope.launch {
            try {
                val state = newOAuthState()
                oauthState = state
                settings.setPendingOAuthState(state)
                val scope = "repo read:user user:email read:org read:notifications"
                val encodedScope = java.net.URLEncoder.encode(scope, "UTF-8")
                val encodedState = java.net.URLEncoder.encode(state, "UTF-8")
                val customId = settings.customClientId.first()
                val url = if (customId.isNotBlank()) {
                    "https://github.com/login/oauth/authorize" +
                        "?client_id=$customId" +
                        "&redirect_uri=${BuildConfig.GITHUB_OAUTH_REDIRECT_URI}" +
                        "&scope=$encodedScope" +
                        "&state=$encodedState"
                } else {
                    val backend = settings.oauthBackendUrl.first()
                    val config = api.getOAuthBackendConfig("$backend/config")
                    if (config.clientId.isBlank()) {
                        _ui.update { it.copy(error = "OAuth backend is reachable but has no client_id configured.") }
                        return@launch
                    }
                    val redirectUri = config.redirectUri.ifBlank { BuildConfig.GITHUB_OAUTH_REDIRECT_URI }
                    config.authorizeUrl +
                        "?client_id=${java.net.URLEncoder.encode(config.clientId, "UTF-8")}" +
                        "&redirect_uri=${java.net.URLEncoder.encode(redirectUri, "UTF-8")}" +
                        "&scope=$encodedScope" +
                        "&state=$encodedState"
                }
                _ui.update { it.copy(oauthUrl = url) }
            } catch (e: Exception) {
                issueReporter.reportError("Login", "startOAuth", e)
                _ui.update {
                    it.copy(error = e.userMessage("Could not start OAuth sign-in. Check your connection or the backend URL in Settings."))
                }
            }
        }
    }

    /**
     * Exchange the OAuth code (received via the `pockethub://oauth/callback` deep link)
     * for an access token. The deep link is handled by [MainActivity]; once it receives
     * `code=xxx&state=yyy`, it will call this function.
     *
     * The `state` echoed by GitHub must match the pending one-time value stored at
     * [startOAuth] time (CSRF protection, from #32 by @Wxjxpp; comparison hardened
     * to constant-time).
     */
    fun exchangeOAuthCode(code: String, state: String?) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val expected = oauthState ?: settings.consumePendingOAuthState()
                settings.consumePendingOAuthState()
                oauthState = null
                val stateOk = expected != null && state != null &&
                    java.security.MessageDigest.isEqual(
                        expected.toByteArray(Charsets.UTF_8),
                        state.toByteArray(Charsets.UTF_8),
                    )
                if (!stateOk) {
                    _ui.update {
                        it.copy(isLoading = false, error = "OAuth callback verification failed (state mismatch). Please try signing in again.")
                    }
                    return@launch
                }

                val customId = settings.customClientId.first()
                val tokenResp = if (customId.isNotBlank()) {
                    // Legacy direct exchange — user-configured custom client.
                    val customSecret = settings.customClientSecret.first()
                    val clientSecret = customSecret.ifBlank { BuildConfig.GITHUB_DEFAULT_CLIENT_SECRET }
                    if (clientSecret.isBlank()) {
                        _ui.update {
                            it.copy(
                                isLoading = false,
                                error = "OAuth Client Secret not configured — cannot complete the token exchange.\n" +
                                    "Please go to Settings → Custom OAuth Client and enter your OAuth App details."
                            )
                        }
                        return@launch
                    }
                    api.exchangeOAuthCode(
                        clientId = customId,
                        clientSecret = clientSecret,
                        code = code,
                        redirectUri = BuildConfig.GITHUB_OAUTH_REDIRECT_URI,
                    )
                } else {
                    // Backend exchange (#32 by @Wxjxpp) — secret stays server-side.
                    val backend = settings.oauthBackendUrl.first()
                    api.exchangeOAuthCodeViaBackend(
                        url = "$backend/oauth/exchange",
                        body = OAuthEndpoints.BackendExchangeRequest(
                            code = code,
                            redirectUri = BuildConfig.GITHUB_OAUTH_REDIRECT_URI,
                        ),
                    )
                }
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

    /** Consume the success signal after the login screen has navigated away.
     *  Without this, the SHARED activity-scoped VM still reports success=true
     *  the next time the login screen appears (e.g. right after sign-out),
     *  which instantly bounced the user back to Home. */
    fun consumeLoginSuccess() { _ui.update { it.copy(success = false) } }
}
