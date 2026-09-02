package com.pockethub.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the current account's Bearer token to GitHub API requests.
 *
 * Host-scoping: the shared OkHttp client is inherited by derived clients
 * (release/mirror APK downloads, changelog fetches, OAuth backend calls) —
 * mirrors and CDNs REJECT a foreign Authorization header with 401, and that
 * 401 previously cascaded into TokenInvalid → auto sign-out ("login vanished"
 * after configuring a download mirror).
 *
 * It deliberately does NOT emit TokenInvalid on 401 anymore: renewal and the
 * sign-out decision live in [TokenRefreshAuthenticator] (refresh → retry once
 * → emit only when the refresh fails).
 */
@Singleton
class AuthInterceptor @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
) : Interceptor {
    @Volatile var token: String = ""

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val host = original.url.host
        // Token only travels to GitHub's own hosts; api.github.com additionally
        // carries the JSON-API headers.
        if (token.isBlank() || (host != GITHUB_API_HOST && host != GITHUB_WEB_HOST)) {
            return chain.proceed(original)
        }
        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .apply {
                // JSON-API headers are api.github.com-only. Overriding Accept on
                // github.com clobbered the OAuth token endpoint's REQUIRED
                // `Accept: application/json` (set by Retrofit) — the exchange
                // then came back form-encoded and second-account sign-in broke.
                if (host == GITHUB_API_HOST) {
                    header("Accept", "application/vnd.github+json")
                    header("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            .build()
        val response = chain.proceed(authed)

        // Severe-issue breadcrumb: last ~20 API calls appear in a crash/ANR
        // digest so "what request was in flight / just failed" is answerable.
        // Needs an IssueReporter reference — resolved lazily from the app
        // singleton (AuthInterceptor is created before Hilt graph is warm on
        // the very first call; a null just skips the breadcrumb).
        runCatching {
            (appContext as? com.pockethub.PocketHubApp)
                ?.issueReporter?.breadcrumb("API ${original.method} ${original.url.encodedPath} → ${response.code}")
        }

        return response
    }

    companion object {
        const val GITHUB_API_HOST = "api.github.com"
        const val GITHUB_WEB_HOST = "github.com"
    }
}
