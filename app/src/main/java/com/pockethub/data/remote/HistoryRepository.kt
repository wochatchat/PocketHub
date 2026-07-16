package com.pockethub.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pockethub.data.remote.HistoryRepository.Companion.FILE_NAME
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(FILE_NAME)

/**
 * Recently visited repos, persisted in DataStore.
 * Keeps the latest 50 entries, most-recent first.
 */
@Singleton
class HistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("repo_history")

    val history: Flow<List<HistoryEntry>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { Json.decodeFromString(it) } ?: emptyList()
    }

    suspend fun recordVisit(owner: String, repo: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { Json.decodeFromString<List<HistoryEntry>>(it) } ?: emptyList()
            val trimmed = current.filterNot { it.owner == owner && it.repo == repo }
            val updated = listOf(HistoryEntry(owner, repo, System.currentTimeMillis())) + trimmed
            prefs[key] = Json.encodeToString(updated.take(MAX_ENTRIES))
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs -> prefs[key] = Json.encodeToString(emptyList<HistoryEntry>()) }
    }

    companion object {
        internal const val FILE_NAME = "pockethub_history"
        private const val MAX_ENTRIES = 50
    }
}

@Serializable
data class HistoryEntry(
    val owner: String,
    val repo: String,
    val visitedAt: Long,
)
