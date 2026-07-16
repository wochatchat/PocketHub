package com.pockethub.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.NotifScheduler
import com.pockethub.data.remote.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Determines the app's first screen (login vs home) by reading the active account token
 * on startup, and seeds the global [AuthInterceptor] so the first API call is already authed.
 *
 * Also exposes [syncAuthInterceptor] for re-seeding after a fresh login, and [signOut] for
 * the Settings screen.
 */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val authInterceptor: AuthInterceptor,
    private val notifScheduler: NotifScheduler,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    /** The currently active account (avatar/login shown in Home's top-left avatar). */
    val activeAccount: StateFlow<com.pockethub.data.local.AccountEntity?> =
        accounts.activeAccount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Signalled when the user signs out — AppNavigation observes and resets the nav stack. */
    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    init {
        viewModelScope.launch {
            // Re-seed auth interceptor from the persisted active account
            val token = accounts.getActiveToken()
            if (token.isNotBlank()) {
                authInterceptor.token = token
                _startRoute.value = Routes.HOME
            } else {
                _startRoute.value = Routes.LOGIN
            }

            // Schedule notification polling in sync with the user's saved setting
            val minutes = settings.notifPollMinutes.first()
            notifScheduler.schedule(minutes)
        }
    }

    /** Re-seed the interceptor with the current active account's token. */
    fun syncAuthInterceptor() {
        viewModelScope.launch {
            val token = accounts.getActiveToken()
            if (token.isNotBlank()) authInterceptor.token = token
        }
    }

    /** Sign out the active account and trigger return to login. */
    fun signOut() {
        viewModelScope.launch {
            val active = accounts.activeAccount.first()
            if (active != null) accounts.removeAccount(active.id)
            authInterceptor.token = ""
            _startRoute.value = Routes.LOGIN
            _signedOut.value = true
        }
    }

    /** Clear the signed-out signal after the UI has navigated back. */
    fun clearSignedOut() { _signedOut.value = false }
}
