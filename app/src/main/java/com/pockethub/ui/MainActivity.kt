package com.pockethub.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.staticCompositionLocalOf
import coil.ImageLoader
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.SettingsRepository
import com.pockethub.ui.auth.LoginViewModel
import com.pockethub.ui.main.AppStartupViewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalAppImageLoader provides imageLoader) {
                val settingsVm: SettingsViewModel = hiltViewModel()
                val themeMode by settingsVm.themeMode.collectAsState()
                val loginVm: LoginViewModel = hiltViewModel()

                // Process OAuth callback if launched via the pockethub://oauth/callback deep link.
                val oauthCode = remember { mutableStateOf<String?>(null) }
                LaunchedEffect(intent) {
                    handleOAuthCallback(intent) { code -> oauthCode.value = code }
                }
                LaunchedEffect(oauthCode.value) {
                    oauthCode.value?.let { code ->
                        loginVm.exchangeOAuthCode(code)
                        oauthCode.value = null
                    }
                }

                PocketHubApp(themeMode = themeMode)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    /** Inspect intent data for ?code=xxx from the pockethub://oauth/callback URI. */
    private fun handleOAuthCallback(intent: Intent?, onCode: (String) -> Unit) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "pockethub") return
        if (data.host != "oauth") return
        val code = data.getQueryParameter("code") ?: return
        onCode(code)
    }
}
