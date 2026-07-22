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
import kotlinx.coroutines.flow.first
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
        val APP_LOCALE = stringPreferencesKey("app_locale")
        val CUSTOM_CLIENT_ID = stringPreferencesKey("custom_client_id")
        val CUSTOM_CLIENT_SECRET = stringPreferencesKey("custom_client_secret")
        val NOTIF_POLL_MINUTES = intPreferencesKey("notif_poll_minutes")
        val NOTIFIED_IDS = stringPreferencesKey("notified_ids")
        val TRANSLATE_TARGET = stringPreferencesKey("translate_target")
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

    // ── Language ──────────────────────────────────────────
    val appLocale: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_LOCALE] ?: com.pockethub.ui.settings.AppLocale.SYSTEM.key
    }

    suspend fun setAppLocale(locale: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.APP_LOCALE] = locale
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

    // ── Translation ───────────────────────────────────────
    /** Target language for README translation: "zh", "en", or null (disabled). */
    val translateTarget: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.TRANSLATE_TARGET]
    }

    suspend fun setTranslateTarget(target: String?) {
        context.dataStore.edit { prefs ->
            if (target != null) prefs[Keys.TRANSLATE_TARGET] = target
            else prefs.remove(Keys.TRANSLATE_TARGET)
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

    // ── System-notification dedup ─────────────────────────
    /**
     * IDs of notification threads the background poller has already surfaced as a
     * system notification. Stored as a comma-separated string; capped so it can't
     * grow without bound.
     */
    suspend fun getNotifiedIds(): Set<String> {
        val raw = context.dataStore.data.map { it[Keys.NOTIFIED_IDS].orEmpty() }.first()
        return raw.split(',').filter { it.isNotBlank() }.toSet()
    }

    /** Merge [ids] into the already-notified set, keeping at most [KEEP] entries. */
    suspend fun addNotifiedIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.NOTIFIED_IDS].orEmpty()
                .split(',').filter { it.isNotBlank() }
            val merged = (existing + ids).distinct().takeLast(KEEP)
            prefs[Keys.NOTIFIED_IDS] = merged.joinToString(",")
        }
    }

    private companion object {
        const val KEEP = 200
    }
}
