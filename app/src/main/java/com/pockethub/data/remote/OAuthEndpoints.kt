package com.pockethub.data.remote

// OAuth token exchange endpoint.
// Split out of GitHubApi.kt; the endpoint methods are inherited by
// GitHubApi, so Retrofit and call sites are unchanged. All DTOs stay
// in GitHubApi.kt and are referenced as GitHubApi.X.
//
// Two exchange paths (ported from #32 by @Wxjxpp, kept as alternatives):
//  - Direct:   exchangeOAuthCode — client_id/secret live on the device
//              (user-configured custom client). Legacy behavior.
//  - Backend:  getOAuthBackendConfig + exchangeOAuthCodeViaBackend — the
//              client secret never leaves the user-deployed backend worker.

import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
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

interface OAuthEndpoints {

    /** Exchange OAuth code for access token (POST to GitHub, not api.github.com).
     *  Accept: application/json is REQUIRED — GitHub answers form-encoded
     *  (`access_token=…`) otherwise, which breaks the JSON converter. */
    @FormUrlEncoded
    @Headers("Accept: application/json")
    @POST("https://github.com/login/oauth/access_token")
    suspend fun exchangeOAuthCode(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
    ): GitHubApi.OAuthTokenResponse

    /** Fetch the OAuth client config (client_id, authorize URL, redirect) from the backend. */
    @GET
    suspend fun getOAuthBackendConfig(@Url url: String): OAuthBackendConfig

    @kotlinx.serialization.Serializable
    data class BackendExchangeRequest(
        val code: String,
        @kotlinx.serialization.SerialName("redirect_uri") val redirectUri: String,
    )

    /** Exchange the code through the backend (secret stays server-side). */
    @Headers("Content-Type: application/json")
    @POST
    suspend fun exchangeOAuthCodeViaBackend(
        @Url url: String,
        @Body body: BackendExchangeRequest,
    ): GitHubApi.OAuthTokenResponse

    // ──────────────────────────────────────────────
    //  Search result wrappers
    // ──────────────────────────────────────────────
}
