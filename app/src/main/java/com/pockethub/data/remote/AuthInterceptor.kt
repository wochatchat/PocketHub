package com.pockethub.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the current account's Bearer token to every GitHub API request.
 */
class AuthInterceptor @Inject constructor() : Interceptor {
    @Volatile var token: String = ""

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (token.isBlank()) return chain.proceed(original)
        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        return chain.proceed(authed)
    }
}
