package com.pockethub.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    /** Signalled when the user signs out — AppNavigation observes and resets the nav stack. */
    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    init {
        viewModelScope.launch {
            val token = accounts.getActiveToken()
            if (token.isNotBlank()) {
                authInterceptor.token = token
                _startRoute.value = Routes.HOME
            } else {
                _startRoute.value = Routes.LOGIN
            }
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
            val active = accounts.getActiveAccountSync()
            if (active != null) accounts.removeAccount(active.id)
            authInterceptor.token = ""
            _startRoute.value = Routes.LOGIN
            _signedOut.value = true
        }
    }

    /** Clear the signed-out signal after the UI has navigated back. */
    fun clearSignedOut() { _signedOut.value = false }
}
