package com.pockethub.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pockethub.ui.theme.AppStyle
import com.pockethub.ui.theme.ThemeMode
import com.pockethub.data.local.TokenCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
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
    private val cipher: TokenCipher,
) {
    // ── Keys ──────────────────────────────────────────────
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_STYLE = stringPreferencesKey("app_style")
        val APP_LOCALE = stringPreferencesKey("app_locale")
        val CUSTOM_CLIENT_ID = stringPreferencesKey("custom_client_id")
        val CUSTOM_CLIENT_SECRET = stringPreferencesKey("custom_client_secret")
        val OAUTH_BACKEND_URL = stringPreferencesKey("oauth_backend_url")
        val DOH_URL = stringPreferencesKey("doh_url")
        val PENDING_OAUTH_STATE = stringPreferencesKey("pending_oauth_state")
        val NOTIF_POLL_MINUTES = intPreferencesKey("notif_poll_minutes")
        val NOTIFIED_IDS = stringPreferencesKey("notified_ids")
        val TRANSLATE_TARGET = stringPreferencesKey("translate_target")
        val STORE_LAST_REFRESH = intPreferencesKey("store_last_refresh_epoch_min")
        val IGNORED_UPDATE_VERSION = stringPreferencesKey("ignored_update_version")
        val LAST_UPDATE_CHECK_MS = intPreferencesKey("last_update_check_epoch_ms")
        val LAST_UPDATE_PROMPT_MS = intPreferencesKey("last_update_prompt_epoch_ms")
        val PINNED_REPOS = stringPreferencesKey("pinned_repos_json")
        val DOWNLOAD_FOLDER_URI = stringPreferencesKey("download_folder_tree_uri")
        val DOWNLOAD_MIRROR_PREFIX = stringPreferencesKey("download_mirror_prefix")
        val ISSUE_REPORT_ENABLED = intPreferencesKey("issue_report_enabled")
        val ISSUE_REPORT_INTERVAL_DAYS = intPreferencesKey("issue_report_interval_days")
        val ISSUE_REPORT_EMAIL = stringPreferencesKey("issue_report_email")
        val ISSUE_REPORT_MODE = stringPreferencesKey("issue_report_mode")
        val ISSUE_REPORT_TARGET_REPO = stringPreferencesKey("issue_report_target_repo")
        val FOLLOW_SYSTEM_THEME = booleanPreferencesKey("follow_system_theme")
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

    // ── App style (full visual identity) ─────────────────
    /** null = follow the legacy theme-mode mapping. */
    val appStyle: Flow<AppStyle?> = context.dataStore.data.map { prefs ->
        prefs[Keys.APP_STYLE]?.let { AppStyle.fromKey(it) }
    }

    suspend fun setAppStyle(style: AppStyle?) {
        context.dataStore.edit { prefs ->
            if (style == null) prefs.remove(Keys.APP_STYLE) else prefs[Keys.APP_STYLE] = style.key
        }
    }

    // ── Follow system dark mode ──────────────────────────
    /** When on: system enters night mode → force the built-in dark style;
     *  system leaves it → back to the user's chosen style. */
    val followSystemTheme: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.FOLLOW_SYSTEM_THEME] ?: false
    }

    suspend fun setFollowSystemTheme(on: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.FOLLOW_SYSTEM_THEME] = on }
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

    val oauthBackendUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.OAUTH_BACKEND_URL] ?: DEFAULT_OAUTH_BACKEND_URL
    }
    val dohUrl: Flow<String> = context.dataStore.data.map {
        it[Keys.DOH_URL] ?: DEFAULT_DOH_URL
    }
    suspend fun setDohUrl(url: String) {
        context.dataStore.edit { prefs ->
            val value = url.trim()
            if (value.isBlank()) prefs.remove(Keys.DOH_URL) else prefs[Keys.DOH_URL] = value
        }
    }

    suspend fun setOAuthBackendUrl(url: String) {
        context.dataStore.edit { prefs ->
            val value = url.trim().removeSuffix("/")
            if (value.isBlank()) prefs.remove(Keys.OAUTH_BACKEND_URL) else prefs[Keys.OAUTH_BACKEND_URL] = value
        }
    }

    // ── Custom OAuth Client ───────────────────────────────
    val customClientId: Flow<String> = context.dataStore.data.map {
        it[Keys.CUSTOM_CLIENT_ID].orEmpty()
    }

    val customClientSecret: Flow<String> = context.dataStore.data.map {
        cipher.decrypt(it[Keys.CUSTOM_CLIENT_SECRET].orEmpty())
    }

    suspend fun setCustomOAuthClient(id: String, secret: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CUSTOM_CLIENT_ID] = id
            prefs[Keys.CUSTOM_CLIENT_SECRET] = cipher.encrypt(secret)
        }
    }

    suspend fun setPendingOAuthState(state: String) { context.dataStore.edit { it[Keys.PENDING_OAUTH_STATE] = state } }
    suspend fun consumePendingOAuthState(): String? { var value:String?=null; context.dataStore.edit { value=it[Keys.PENDING_OAUTH_STATE]; it.remove(Keys.PENDING_OAUTH_STATE) }; return value }

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

    // ── Download folder ───────────────────────────────────
    /**
     * User-chosen download folder as a persisted SAF tree URI (from
     * ACTION_OPEN_DOCUMENT_TREE). Null = app-private download dir (default).
     * Completed downloads are mirrored into this folder when set.
     */
    val downloadFolderUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_FOLDER_URI]?.takeIf { it.isNotBlank() }
    }

    suspend fun setDownloadFolderUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) prefs.remove(Keys.DOWNLOAD_FOLDER_URI)
            else prefs[Keys.DOWNLOAD_FOLDER_URI] = uri
        }
    }

    // ── Network acceleration (net branch experiment) ──────
    /**
     * User-provided accelerator prefix ("gh-proxy"-style), appended in front of
     * GitHub FILE urls (releases / raw / codeload). Blank = direct connection.
     */
    val downloadMirrorPrefix: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.DOWNLOAD_MIRROR_PREFIX]?.trim().orEmpty()
    }

    suspend fun setDownloadMirrorPrefix(prefix: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_MIRROR_PREFIX] = prefix.trim()
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

    // ── App update (GitHub Releases) ───────────────────────
    /**
     * Version name the user has explicitly ignored. We will not surface the
     * update dialog for this version again until a newer version is released.
     * Empty string means nothing ignored.
     */
    val ignoredUpdateVersion: Flow<String> = context.dataStore.data.map {
        it[Keys.IGNORED_UPDATE_VERSION].orEmpty()
    }

    suspend fun setIgnoredUpdateVersion(version: String) {
        context.dataStore.edit { prefs ->
            if (version.isBlank()) prefs.remove(Keys.IGNORED_UPDATE_VERSION)
            else prefs[Keys.IGNORED_UPDATE_VERSION] = version
        }
    }

    /** Epoch millis of the last automatic update check, used to throttle (min interval). */
    suspend fun getLastUpdateCheckMs(): Long {
        return context.dataStore.data.map { prefs ->
            (prefs[Keys.LAST_UPDATE_CHECK_MS]?.toLong() ?: 0L) * 1000L
        }.first()
    }

    /** Persist epoch millis — stored as int (seconds) since DataStore prefs are typed. */
    suspend fun markUpdateCheckedNow() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_UPDATE_CHECK_MS] = (System.currentTimeMillis() / 1000L).toInt()
        }
    }

    /**
     * Epoch millis (seconds) of the last time the [UpdateDialog] was actually
     * surfaced to the user. Used to suppress auto-prompt frequency to once per
     * a few days when the user taps "Later". 0 if never prompted.
     */
    suspend fun getLastUpdatePromptMs(): Long {
        return context.dataStore.data.map { prefs ->
            (prefs[Keys.LAST_UPDATE_PROMPT_MS]?.toLong() ?: 0L) * 1000L
        }.first()
    }

    /** Record that the [UpdateDialog] is being shown right now. */
    suspend fun markUpdatePromptedNow() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_UPDATE_PROMPT_MS] = (System.currentTimeMillis() / 1000L).toInt()
        }
    }

    // ── Pinned Repos ─────────────────────────────────────
    /**
     * Flow of the user's pinned repositories, newest first.
     * Each entry is an "owner/repo" slug. Format is a JSON array of objects
     * {"slug":"owner/repo"} to leave room for future per-pin metadata.
     */
    val pinnedRepos: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.PINNED_REPOS].orEmpty()
        runCatching { parsePinned(raw) }.getOrDefault(emptyList())
    }

    /** Add a slug (owner/repo) to the pinned list if absent, capping at KEEP entries. */
    suspend fun pinRepo(slug: String) {
        if (slug.isBlank()) return
        context.dataStore.edit { prefs ->
            val cur = readPinned(prefs[Keys.PINNED_REPOS])
            if (cur.contains(slug)) return@edit
            val next = (listOf(slug) + cur).take(KEEP)
            prefs[Keys.PINNED_REPOS] = writePinned(next)
        }
    }

    /** Remove a slug (owner/repo) from the pinned list. */
    suspend fun unpinRepo(slug: String) {
        if (slug.isBlank()) return
        context.dataStore.edit { prefs ->
            val cur = readPinned(prefs[Keys.PINNED_REPOS])
            val next = cur.filterNot { it == slug }
            if (next.size != cur.size) prefs[Keys.PINNED_REPOS] = writePinned(next)
        }
    }

    private fun readPinned(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { parsePinned(raw) }.getOrDefault(emptyList())
    }

    private fun parsePinned(raw: String): List<String> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.optString("slug")?.takeIf { it.isNotBlank() }
        }
    }

    private fun writePinned(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { slug -> arr.put(JSONObject().put("slug", slug)) }
        return arr.toString()
    }

    // ── Issue reporting (severe events → scheduled email) ─
    /**
     * Whether the periodic severe-issue reporter is enabled.
     * Stored as int (0/1) because DataStore preferences are typed.
     */
    val issueReportEnabled: Flow<Boolean> = context.dataStore.data.map {
        (it[Keys.ISSUE_REPORT_ENABLED] ?: 0) == 1
    }
    suspend fun setIssueReportEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ISSUE_REPORT_ENABLED] = if (enabled) 1 else 0 }
    }

    /** Periodic interval in days (min 1). WorkManager schedules accordingly. */
    val issueReportIntervalDays: Flow<Int> = context.dataStore.data.map {
        it[Keys.ISSUE_REPORT_INTERVAL_DAYS] ?: 7
    }
    suspend fun setIssueReportIntervalDays(days: Int) {
        context.dataStore.edit { it[Keys.ISSUE_REPORT_INTERVAL_DAYS] = days.coerceAtLeast(1) }
    }

    /** Destination email address for severe-issue reports. */
    val issueReportEmail: Flow<String> = context.dataStore.data.map {
        it[Keys.ISSUE_REPORT_EMAIL].orEmpty()
    }
    suspend fun setIssueReportEmail(email: String) {
        context.dataStore.edit { it[Keys.ISSUE_REPORT_EMAIL] = email.trim() }
    }

    /**
     * Delivery mode for the issue report:
     *  - "email"   — stage ACTION_SEND mail composer draft (email destination)
     *  - "github"  — create a new issue on the configured target GitHub repo
     *                (no user interaction, fully automatic via the GitHub API)
     *
     * The "github" path lets us close the audit loop with an external AI task
     * that simply scrapes the target repo's labelled issues.
     */
    val issueReportMode: Flow<String> = context.dataStore.data.map {
        it[Keys.ISSUE_REPORT_MODE] ?: "email"
    }
    suspend fun setIssueReportMode(mode: String) {
        context.dataStore.edit { it[Keys.ISSUE_REPORT_MODE] = when (mode) { "github" -> "github"; else -> "email" } }
    }

    /**
     * Target repo for github-mode reports, in the form "owner/repo" (lowercased).
     * Falls back to empty if unset; the worker will refuse (no-op) in that case.
     */
    val issueReportTargetRepo: Flow<String> = context.dataStore.data.map {
        it[Keys.ISSUE_REPORT_TARGET_REPO].orEmpty()
    }
    suspend fun setIssueReportTargetRepo(slug: String) {
        context.dataStore.edit { it[Keys.ISSUE_REPORT_TARGET_REPO] = slug.trim().lowercase() }
    }

    companion object {
        const val KEEP = 200
        const val DEFAULT_OAUTH_BACKEND_URL = "https://oauth.wxjxpp.de5.net"
        const val DEFAULT_DOH_URL = "https://dns.alidns.com/dns-query"
    }
}
