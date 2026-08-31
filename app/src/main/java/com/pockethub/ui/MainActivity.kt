package com.pockethub.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import coil.ImageLoader
import com.pockethub.BuildConfig
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.SettingsRepository
import com.pockethub.ui.auth.LoginViewModel
import com.pockethub.ui.main.PocketHubApp
import com.pockethub.ui.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** App-wide Coil ImageLoader (Hilt-provided, with SVG support). Provided at the Compose root so
 *  MarkdownText can pass it explicitly to AsyncImage/SubcomposeAsyncImage. (Coil 2.7 deprecated
 *  LocalImageLoader — AsyncImage now uses the singleton — and SingletonImageLoader.Factory on the
 *  Application breaks Hilt/KSP, so we provide our own local and pass the loader explicitly.) */
val LocalAppImageLoader = staticCompositionLocalOf<ImageLoader> { error("LocalAppImageLoader not provided") }

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var authInterceptor: AuthInterceptor
    @Inject lateinit var accounts: AccountRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var imageLoader: ImageLoader

    // Android 13+ requires a runtime grant before the app can post system
    // notifications (the background poller's alerts are silently dropped without it).
    private val pendingData=MutableStateFlow<Uri?>(null)
    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op — setting stays accessible */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        pendingData.value=intent?.data
        setContent {
            CompositionLocalProvider(LocalAppImageLoader provides imageLoader) {
                val settingsVm: SettingsViewModel = hiltViewModel()
                val themeMode by settingsVm.themeMode.collectAsState()
                val appStyle by settingsVm.appStyle.collectAsState()
                val followSystemTheme by settingsVm.followSystemTheme.collectAsState()
                // Recomposes on uiMode configuration change (covers both the
                // default activity recreation and brands that handle uiMode
                // configChanges in-place, e.g. some OEM ROMs).
                val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                val loginVm: LoginViewModel = hiltViewModel()

                // Process OAuth callback if launched via the pockethub://oauth/callback deep link,
                // or other deep links (pockethub://notifications, etc.) that should land on the
                // matching Compose destination.
                val oauthCode = remember { mutableStateOf<String?>(null) }
                val oauthState = remember { mutableStateOf<String?>(null) }
                val deepLinkUri = remember { mutableStateOf<Uri?>(null) }
                val incomingData by pendingData.asStateFlow().collectAsState()
                LaunchedEffect(incomingData) {
                    val data: Uri? = incomingData
                    val oauthScheme = BuildConfig.GITHUB_OAUTH_REDIRECT_URI.substringBefore("://")
                    if (data != null && data.scheme == oauthScheme && data.host == "oauth") {
                        handleOAuthCallback(data){code,state->oauthCode.value=code;oauthState.value=state}
                    } else if (data != null && data.scheme == "pockethub") {
                        // Non-OAuth pockethub:// deep link — forward to the NavHost for routing.
                        deepLinkUri.value = data
                    }
                }
                LaunchedEffect(oauthCode.value) {
                    oauthCode.value?.let { code ->
                        loginVm.exchangeOAuthCode(code,oauthState.value)
                        oauthCode.value = null
                        oauthState.value=null
                    }
                }

                PocketHubApp(
                    themeMode = themeMode,
                    appStyle = appStyle,
                    forceDark = followSystemTheme && systemDark,
                    deepLinkUri = deepLinkUri.value,
                    onDeepLinkConsumed = { deepLinkUri.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingData.value=intent.data
    }

    /** Inspect intent data for ?code=xxx from the configured OAuth callback URI. */
    private fun handleOAuthCallback(data: Uri?, onCode: (String,String?) -> Unit) {
        data ?: return
        val oauthScheme = BuildConfig.GITHUB_OAUTH_REDIRECT_URI.substringBefore("://")
        if (data.scheme != oauthScheme) return
        if (data.host != "oauth") return
        if(data.getQueryParameter("error")!=null)return
        val code=data.getQueryParameter("code")?:return
        onCode(code,data.getQueryParameter("state"))
    }

    /** Ask once for POST_NOTIFICATIONS on Android 13+ when not yet granted. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
