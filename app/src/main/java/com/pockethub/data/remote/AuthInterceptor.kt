package com.pockethub.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the current account's Bearer token to every GitHub API request, and
 * surfaces authentication failures (401) via [SessionEventBus] so the app can
 * sign the user out and return them to login instead of leaving each screen
 * showing a generic "load failed" forever.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionBus: SessionEventBus,
) : Interceptor {
    @Volatile var token: String = ""

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (token.isBlank()) return chain.proceed(original)
        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val response = chain.proceed(authed)

        // 401 => token revoked / expired / invalid. Don't silently re-route every
        // ViewModel to its generic error state; tell the app once so it can sign
        // out the active account and return to login.
        if (response.code == 401) {
            sessionBus.emit(SessionEventBus.Event.TokenInvalid)
        }
        return response
    }
}
