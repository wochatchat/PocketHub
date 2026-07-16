package com.pockethub.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pockethub.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("pockethub_settings")

/**
 * User-level app settings, persisted in DataStore.
 *
 * Theme mode, custom OAuth client, notification cadence, etc.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // ── Keys ──────────────────────────────────────────────
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CUSTOM_CLIENT_ID = stringPreferencesKey("custom_client_id")
        val CUSTOM_CLIENT_SECRET = stringPreferencesKey("custom_client_secret")
        val NOTIF_POLL_MINUTES = intPreferencesKey("notif_poll_minutes")
        val STORE_LAST_REFRESH = intPreferencesKey("store_last_refresh_epoch_min")
    }

    // ── Theme ─────────────────────────────────────────────
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.THEME_MODE]) {
            "system" -> ThemeMode.System
            "light"  -> ThemeMode.Light
            else     -> ThemeMode.Dark
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name.lowercase()
        }
    }

    // ── Custom OAuth Client ───────────────────────────────
    val customClientId: Flow<String> = context.dataStore.data.map {
        it[Keys.CUSTOM_CLIENT_ID].orEmpty()
    }

    val customClientSecret: Flow<String> = context.dataStore.data.map {
        it[Keys.CUSTOM_CLIENT_SECRET].orEmpty()
    }

    suspend fun setCustomOAuthClient(id: String, secret: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_CLIENT_ID] = id
            prefs[Keys.CUSTOM_CLIENT_SECRET] = secret
        }
    }

    // ── Notification polling ──────────────────────────────
    /**
     * Polling interval (minutes) for unread notifications refresh.
     * 0 = disabled (Manual only), otherwise minimum 15m enforced by Android WorkManager constraints.
     */
    val notifPollMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.NOTIF_POLL_MINUTES] ?: 0 }

    suspend fun setNotifPollMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIF_POLL_MINUTES] = minutes.coerceAtLeast(0)
        }
    }
}
