package com.pockethub.di

import android.content.Context
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pockethub.BuildConfig
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.GitHubApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import com.pockethub.data.remote.SettingsRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = false
    }

    /**
     * DoH-backed resolver with system-DNS fallback (net branch experiment).
     *
     * Mainland ISPs poison GitHub DNS records; resolving through an encrypted
     * public resolver (AliDNS — reachable in CN, no account) returns the real
     * IPs and sidesteps that. If DoH itself is unreachable we fall back to the
     * system resolver so the app never hard-fails because of this feature.
     */
    @Provides
    @Singleton
    fun provideDns(): okhttp3.Dns {
        val bootstrapClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val doh = okhttp3.dnsoverhttps.DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(SettingsRepository.DEFAULT_DOH_URL.toHttpUrl())
            .bootstrapDnsHosts(
                java.net.InetAddress.getByName("223.5.5.5"),
                java.net.InetAddress.getByName("223.6.6.6"),
            )
            .includeIPv6(false)
            .build()
        return object : okhttp3.Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> =
                try {
                    doh.lookup(hostname)
                } catch (_: Throwable) {
                    okhttp3.Dns.SYSTEM.lookup(hostname)
                }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor, dns: okhttp3.Dns): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .dns(dns)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json; charset=utf-8".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.GITHUB_API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideGitHubApi(retrofit: Retrofit): GitHubApi {
        return retrofit.create(GitHubApi::class.java)
    }

    // Coil ImageLoader with SVG support so README badges / logos / charts (.svg) decode.
    // Explicit 3-tier caching: memory cache (hot images) → disk cache (survives
    // process death) → network. Crossfade smooths cache-miss loads.
    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(SvgDecoder.Factory())
            add(GifDecoder.Factory())
        }
        .memoryCache {
            coil.memory.MemoryCache.Builder(context)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            coil.disk.DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(100L * 1024 * 1024) // 100 MB
                .build()
        }
        .crossfade(true)
        .build()
}
