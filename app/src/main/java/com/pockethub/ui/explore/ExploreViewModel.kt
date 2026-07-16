package com.pockethub.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.FeedEvent
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Section in the Explore feed.
 *
 * `trending` - classic GitHub-style trending, filtered by language and time window.
 * `featured`  - high-star repos that have been pushed in the last 30 days (Editor picks vibe).
 * `following` - GitHub-like received-events feed of the people you follow.
 */
enum class ExploreSection { TRENDING, FEATURED, FOLLOWING }

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val api: GitHubApi,
    private val accounts: AccountRepository,
) : ViewModel() {

    // ── common ─────────────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _section = MutableStateFlow(ExploreSection.TRENDING)
    val section: StateFlow<ExploreSection> = _section.asStateFlow()

    // ── Trending ───────────────────────────────────────
    private val _trending = MutableStateFlow<List<Repository>>(emptyList())
    val trending: StateFlow<List<Repository>> = _trending.asStateFlow()

    // Persisted filter state for the Trending section (memory-only).
    private val _trendingLang = MutableStateFlow("All")
    val trendingLang: StateFlow<String> = _trendingLang
    private val _trendingRange = MutableStateFlow("Daily")
    val trendingRange: StateFlow<String> = _trendingRange

    // ── Featured ───────────────────────────────────────
    private val _featured = MutableStateFlow<List<Repository>>(emptyList())
    val featured: StateFlow<List<Repository>> = _featured.asStateFlow()

    // ── Following ──────────────────────────────────────
    private val _feed = MutableStateFlow<List<FeedEvent>>(emptyList())
    val feed: StateFlow<List<FeedEvent>> = _feed.asStateFlow()

    private val _feedAvailable = MutableStateFlow(true)
    val feedAvailable: StateFlow<Boolean> = _feedAvailable.asStateFlow()

    private var loadJob: Job? = null

    fun switchSection(section: ExploreSection) {
        _section.value = section
        load()
    }

    fun setTrendingFilters(language: String, range: String) {
        _trendingLang.value = language
        _trendingRange.value = range
        if (_section.value == ExploreSection.TRENDING) load()
    }

    /** Total load dispatcher; reloads the active section. */
    fun load(language: String? = null, range: String? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                when (_section.value) {
                    ExploreSection.TRENDING -> loadTrending(
                        language ?: _trendingLang.value,
                        range ?: _trendingRange.value,
                    )
                    ExploreSection.FEATURED -> loadFeatured()
                    ExploreSection.FOLLOWING -> loadFollowingFeed()
                }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "Failed to load feed." }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    private suspend fun loadTrending(language: String, range: String) {
        val created = when (range) {
            "Weekly" -> LocalDate.now().minusWeeks(1)
            "Monthly" -> LocalDate.now().minusMonths(1)
            else      -> LocalDate.now().minusDays(1)
        }.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val langPart = if (language == "All") "" else " language:$language"
        val q = "stars:>50$langPart created:>$created"
        val result = api.searchTrending(query = q, perPage = 30, sort = "stars")
        _trending.update { result.items }
    }

    /**
     * Featured — repos with 1k+ stars that have been pushed in the last 30 days,
     * sorted by stars descending. Same search endpoint, different query window.
     */
    private suspend fun loadFeatured() {
        val cutoff = LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val q = "stars:>1000 pushed:>$cutoff"
        val result = api.searchTrending(query = q, perPage = 25, sort = "stars")
        _featured.update { result.items }
    }

    private suspend fun loadFollowingFeed() {
        val login = accounts.getActiveLogin()
        if (login.isBlank()) {
            // No active account → silently mark the section as unavailable so the UI
            // can show a friendly "Sign in to see what people you follow are up to" message.
            _feed.update { emptyList() }
            _feedAvailable.update { false }
            return
        }
        try {
            val events = api.getReceivedEvents(login, perPage = 30)
            _feed.update { events }
            _feedAvailable.update { true }
        } catch (e: Exception) {
            _feed.update { emptyList() }
            _feedAvailable.update { true }
            throw e
        }
    }

    fun clearError() { _error.update { null } }
}
