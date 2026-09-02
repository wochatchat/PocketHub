package com.pockethub.data.remote

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp authenticator on the shared client: on a 401 from the GitHub API it
 * transparently refreshes the owning account's token and retries the request
 * ONCE with the new credentials. Only when the refresh itself fails does it
 * emit [SessionEventBus.Event.TokenInvalid] — the single owner of the sign-out
 * decision (the interceptor no longer emits it, so concurrent 401s can't
 * double-fire, and a transient third-party 401 can never kill the session).
 *
 * The refresh targets the row that owns the FAILED request's token — never a
 * blind swap to the active account's token, which would replay user A's
 * request with user B's credentials after an account switch.
 */
@Singleton
class TokenRefreshAuthenticator @Inject constructor(
    private val refresher: TokenRefresher,
    private val sessionBus: SessionEventBus,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Only the API host can invalidate our token. A 401 from github.com
        // (expired signed download URLs, edge hiccups) or from any third-party
        // host is NOT a dead session — the interceptor host-scoping already
        // keeps the token off those requests, but stay defensive here too.
        if (response.request.url.host != AuthInterceptor.GITHUB_API_HOST) return null
        if (response.priorResponse != null) return null // hard cap: one refresh per request
        // Unauthenticated request → nothing of ours to renew.
        val usedAuth = response.request.header("Authorization") ?: return null

        synchronized(this) {
            // refreshSync resolves ownership (active row / just-switched row /
            // already-rotated mapping) and persists the rotation.
            val newToken = refresher.refreshSync(usedAuth)
            if (newToken.isNullOrBlank()) {
                // No refresh credential, network failure, or GitHub rejected
                // the refresh — the session is genuinely dead. Soft sign-out
                // happens upstream (AppStartupViewModel / NotifPollWorker).
                sessionBus.emit(SessionEventBus.Event.TokenInvalid)
                return null
            }
            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }
    }
}
