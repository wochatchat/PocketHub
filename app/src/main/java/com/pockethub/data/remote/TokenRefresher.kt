package com.pockethub.data.remote

import com.pockethub.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes the active account's expiring OAuth access token.
 *
 * Runs SYNCHRONOUSLY — OkHttp's [okhttp3.Authenticator] callback blocks the
 * network thread, so the whole refresh is a plain blocking call. NEVER call
 * from the main thread.
 *
 * Endpoint dispatch mirrors [com.pockethub.ui.auth.LoginViewModel]'s exchange
 * rule: a user-configured custom OAuth client exchanges directly with GitHub
 * (it holds the secret); otherwise the self-hosted backend worker holds the
 * secret and exposes POST /oauth/refresh.
 *
 * The refresh request goes out on a BARE client — no AuthInterceptor (would
 * attach the token being replaced) and no authenticator (would recurse).
 */
@Singleton
class TokenRefresher @Inject constructor(
    private val accounts: AccountRepository,
    private val settings: SettingsRepository,
    private val authInterceptor: AuthInterceptor,
) {
    private val bareClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val lock = Any()

    /**
     * Single-flight refresh of the ACTIVE account's token. Concurrent 401s
     * funnel through the class monitor; whoever finds the token already
     * rotated in memory just reuses it (the authenticator handles that check).
     *
     * @return the NEW access token, or null when there is no refresh
     *         credential, the network failed, or GitHub rejected the refresh
     *         (revoked/expired refresh token → caller signs the user out).
     */
    fun refreshSync(): String? = synchronized(lock) {
        val active = runBlocking { accounts.getActiveAccount() } ?: return null
        if (active.refreshToken.isBlank()) return null
        try {
            val customId = runBlocking { settings.customClientId.first() }
            val request = if (customId.isNotBlank()) {
                // Direct exchange path — same rule as LoginViewModel.exchangeOAuthCode.
                val secret = runBlocking { settings.customClientSecret.first() }
                    .ifBlank { BuildConfig.GITHUB_DEFAULT_CLIENT_SECRET }
                if (secret.isBlank()) return null
                Request.Builder()
                    .url(GITHUB_TOKEN_URL)
                    .header("Accept", "application/json")
                    .post(
                        FormBody.Builder()
                            .add("client_id", customId)
                            .add("client_secret", secret)
                            .add("grant_type", "refresh_token")
                            .add("refresh_token", active.refreshToken)
                            .build()
                    )
            } else {
                val backend = runBlocking { settings.oauthBackendUrl.first() }
                    .ifBlank { SettingsRepository.DEFAULT_OAUTH_BACKEND_URL }
                Request.Builder()
                    .url("$backend/oauth/refresh")
                    .header("Accept", "application/json")
                    .post(
                        JSONObject().put("refresh_token", active.refreshToken).toString()
                            .toRequestBody("application/json; charset=utf-8".toMediaType())
                    )
            }.build()

            bareClient.newCall(request).execute().use { resp ->
                // GitHub's token endpoint answers 200 even for errors — the
                // error JSON is the real signal, not the status code.
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val obj = runCatching { JSONObject(body) }.getOrNull() ?: return null
                if (obj.optString("error").isNotBlank()) return null

                val newToken = obj.optString("access_token")
                if (newToken.isBlank()) return null
                // GitHub ROTATES refresh tokens on use — persist the new one;
                // fall back to the old credential when the response omits it.
                val newRefresh = obj.optString("refresh_token").ifBlank { active.refreshToken }
                val expiresIn = obj.optLong("expires_in", 0L)
                val expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L

                runBlocking {
                    accounts.updateTokensByLogin(active.login, newToken, newRefresh, expiresAt)
                }
                // Update the in-memory token only if the user hasn't switched
                // accounts while we were on the wire.
                if (runBlocking { accounts.getActiveLogin() } == active.login) {
                    authInterceptor.token = newToken
                }
                newToken
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token"
    }
}
