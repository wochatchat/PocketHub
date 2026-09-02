package com.pockethub.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.data.remote.NotifScheduler
import com.pockethub.data.remote.SessionEventBus
import com.pockethub.data.remote.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Auth session state — the SINGLE source of truth for which gate the app is
 * behind. Derived by observing the active-account row in Room: the database
 * is the truth, and every login / logout / account switch is just a DB write
 * that flows through here. Navigation keys off this state (see PocketHubApp),
 * so there is exactly ONE reactor to auth changes and no cross-talk between
 * ad-hoc signals (the old _startRoute + _signedOut pair raced and left stale
 * destinations on the back stack).
 */
sealed interface AuthState {
    /** Room not read yet — cold start splash. */
    data object Loading : AuthState

    /** No active account row → login gate. Back here exits the app. */
    data object LoggedOut : AuthState

    /** Active account row → main app, keyed by login so an account switch
     *  rebuilds the whole nav graph into a fresh stack for that account. */
    data class LoggedIn(val login: String) : AuthState
}

/**
 * Owns the auth state machine and the global [AuthInterceptor] seeding.
 *
 * Login state transitions and their triggers:
 *  - LoggedIn: [AccountRepository.addAccount] (PAT / OAuth login)
 *  - LoggedOut: [AccountRepository.signOutActive] (manual sign-out or a
 *    remote TokenInvalid event) — soft: rows are kept for quick re-entry
 *  - LoggedIn(other): [AccountRepository.switchAccount] / removeAccount
 */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val authInterceptor: AuthInterceptor,
    private val notifScheduler: NotifScheduler,
    private val settings: SettingsRepository,
    private val sessionBus: SessionEventBus,
) : ViewModel() {

    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    /** The currently active account (avatar/login shown in Home's top-left avatar). */
    val activeAccount: StateFlow<com.pockethub.data.local.AccountEntity?> =
        accounts.activeAccount.stateIn(
            viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null,
        )

    init {
        viewModelScope.launch {
            // One-time at-rest encryption migration for a legacy plaintext
            // OAuth client secret (tokens migrate lazily via getActiveToken).
            settings.sealLegacyCustomSecret()
        }

        viewModelScope.launch {
            // Schedule notification polling in sync with the user's saved setting
            notifScheduler.schedule(settings.notifPollMinutes.first())
        }

        // THE auth state machine. Room drives everything: a login, a sign-out,
        // an account switch or a TokenInvalid-triggered deactivation is just a
        // write, and this collector re-derives the state and re-seeds the
        // interceptor. The UI (PocketHubApp) keys its whole nav graph on this
        // state, so the correct screen — and ONLY that screen — is ever shown.
        viewModelScope.launch {
            accounts.activeAccount.collect { row ->
                // Opens the sealed token (and lazily re-seals legacy plaintext).
                val token = accounts.getActiveToken()
                authInterceptor.token = token
                _auth.value = if (row != null) {
                    AuthState.LoggedIn(row.login)
                } else {
                    AuthState.LoggedOut
                }
            }
        }

        // TokenRefreshAuthenticator emits TokenInvalid when an api.github.com 401
        // survives a refresh attempt (token revoked AND refresh dead). Deactivate
        // the session; the flow above flips the app back to the login gate.
        viewModelScope.launch {
            sessionBus.events.collect { event ->
                if (event is SessionEventBus.Event.TokenInvalid) signOut()
            }
        }
    }

    /**
     * Sign out = deactivate the active row (soft — account history is kept for
     * quick re-entry from the login gate). Auth state and interceptor seeding
     * happen in the activeAccount collector; no manual navigation anywhere.
     */
    fun signOut() {
        viewModelScope.launch { accounts.signOutActive() }
    }
}
