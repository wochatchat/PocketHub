package com.pockethub.data.remote.feed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.feedSourceDataStore: DataStore<Preferences> by preferencesDataStore("pockethub_feed_sources")

/**
 * Per-tab user configuration for the Explore feed sources. Persisted to its
 * own DataStore so the keys never collide with general app settings.
 */
@Singleton
class FeedSourceRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val TRENDING  = stringPreferencesKey("trending_config")
        val FEATURED  = stringPreferencesKey("featured_config")
        val FOLLOWING = stringPreferencesKey("following_config")
    }

    /** Effective config (merging defaults when nothing persisted) for a tab. */
    fun configFlow(tab: FeedTab): Flow<FeedSourceConfig> = context.feedSourceDataStore.data
        .map { prefs -> readConfig(prefs, tab) }

    suspend fun getConfig(tab: FeedTab): FeedSourceConfig =
        configFlow(tab).first()

    suspend fun setConfig(tab: FeedTab, config: FeedSourceConfig) {
        context.feedSourceDataStore.edit { prefs ->
            prefs[keyFor(tab)] = json.encodeToString(config)
        }
    }

    suspend fun setSource(tab: FeedTab, source: FeedSourceOption, customBaseUrl: String = "") {
        val current = getConfig(tab)
        setConfig(
            tab,
            current.copy(sourceId = source.id, customBaseUrl = customBaseUrl.takeIf { source.urlModifiable }.orEmpty()),
        )
    }

    suspend fun setTrendingFilters(language: String, range: String) {
        val current = getConfig(FeedTab.TRENDING)
        setConfig(FeedTab.TRENDING, current.copy(trendingLanguage = language, trendingRange = range))
    }

    private fun readConfig(prefs: Preferences, tab: FeedTab): FeedSourceConfig {
        val default = FeedSourceConfig(
            sourceId = FeedSourceOption.defaultsFor(tab).id,
            customBaseUrl = "",
            trendingLanguage = "All",
            trendingRange = "Daily",
        )
        val raw = prefs[keyFor(tab)] ?: return default
        return runCatching { json.decodeFromString<FeedSourceConfig>(raw) }.getOrDefault(default)
    }

    private fun keyFor(tab: FeedTab): Preferences.Key<String> = when (tab) {
        FeedTab.TRENDING  -> Keys.TRENDING
        FeedTab.FEATURED  -> Keys.FEATURED
        FeedTab.FOLLOWING -> Keys.FOLLOWING
    }
}
