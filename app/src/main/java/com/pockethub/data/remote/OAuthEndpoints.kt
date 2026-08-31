package com.pockethub.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Url

@kotlinx.serialization.Serializable
data class OAuthBackendConfig(
    @kotlinx.serialization.SerialName("client_id") val clientId: String = "",
    @kotlinx.serialization.SerialName("authorize_url") val authorizeUrl: String = "https://github.com/login/oauth/authorize",
    @kotlinx.serialization.SerialName("redirect_uri") val redirectUri: String = "",
)

// OAuth token exchange endpoint.
interface OAuthEndpoints {
    @GET
    suspend fun getOAuthBackendConfig(@Url url: String): OAuthBackendConfig

    @kotlinx.serialization.Serializable
    data class BackendExchangeRequest(
        val code: String,
        @kotlinx.serialization.SerialName("redirect_uri") val redirectUri: String,
    )

    @retrofit2.http.Headers("Content-Type: application/json")
    @POST
    suspend fun exchangeOAuthCodeViaBackend(
        @Url url: String,
        @Body body: BackendExchangeRequest,
    ): GitHubApi.OAuthTokenResponse
}
