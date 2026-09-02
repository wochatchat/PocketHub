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

    /** Old access token → its replacement, for in-flight requests that raced a
     *  concurrent refresh (bounded, cleared wholesale when it grows). */
    private val rotated = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Single-flight refresh. Concurrent 401s funnel through the class monitor.
     *
     * The refresh targets the row that OWNS [failedAuthHeader] (the
     * `Authorization` value of the failed request) — normally the active
     * account. A 401 from a stale in-flight request of a just-switched account
     * refreshes THAT account's row instead of the active one, so a
     * switched-to account's credentials are never consumed for the wrong user.
     *
     * @return the NEW access token to retry with (same account as the failed
     *         request), or null when there is no refresh credential, the
     *         network failed, or GitHub rejected the refresh (revoked/expired
     *         refresh token → caller signs out).
     */
    fun refreshSync(failedAuthHeader: String?): String? = synchronized(lock) {
        try {
            val failedToken = failedAuthHeader?.removePrefix("Bearer ").orEmpty()
            val active = runBlocking { accounts.getActiveAccount() } ?: return null

            // Fast path: this token was already rotated by the previous
            // authenticate() round while more 401 responses were in flight —
            // no network round-trip, just retry with the replacement.
            rotated[failedToken]?.let { return it }

            val row = if (failedToken.isBlank() || failedToken == active.token) {
                active
            } else {
                // Stale in-flight request of a non-active (just switched,
                // still stored) account — refresh its OWN row.
                runBlocking { accounts.getAccountByToken(failedToken) } ?: return null
            }
            if (row.refreshToken.isBlank()) return null
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
                            .add("refresh_token", row.refreshToken)
                            .build()
                    )
            } else {
                val backend = runBlocking { settings.oauthBackendUrl.first() }
                    .ifBlank { SettingsRepository.DEFAULT_OAUTH_BACKEND_URL }
                Request.Builder()
                    .url("$backend/oauth/refresh")
                    .header("Accept", "application/json")
                    .post(
                        JSONObject().put("refresh_token", row.refreshToken).toString()
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
                val newRefresh = obj.optString("refresh_token").ifBlank { row.refreshToken }
                val expiresIn = obj.optLong("expires_in", 0L)
                val expiresAt = if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0L

                runBlocking {
                    accounts.updateTokensByLogin(row.login, newToken, newRefresh, expiresAt)
                }
                // Racing requests still holding the old access token retry with
                // the replacement instead of triggering another refresh.
                if (rotated.size > 16) rotated.clear()
                rotated[row.token] = newToken
                // Update the in-memory token only if the user hasn't switched
                // accounts while we were on the wire.
                if (runBlocking { accounts.getActiveLogin() } == row.login) {
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
