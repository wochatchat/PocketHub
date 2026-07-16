package com.pockethub.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.SettingsRepository
import com.pockethub.ui.main.PocketHubApp
import com.pockethub.ui.settings.SettingsViewModel
import com.pockethub.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authInterceptor: AuthInterceptor
    @Inject lateinit var accounts: AccountRepository
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsVm: SettingsViewModel = hiltViewModel()
            val themeMode by settingsVm.themeMode.collectAsState()

            // Sync auth interceptor with the active account token on startup
            val activeAccount = remember { accounts.activeAccount }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val token = accounts.getActiveToken()
                if (token.isNotBlank()) authInterceptor.token = token
            }

            PocketHubApp(themeMode = themeMode)
        }
    }
}
