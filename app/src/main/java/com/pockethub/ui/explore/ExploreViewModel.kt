package com.pockethub.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.FeedEvent
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.feed.DiscoverItem
import com.pockethub.data.remote.feed.FeedSourceOption
import com.pockethub.data.remote.feed.FeedSourceRepository
import com.pockethub.data.remote.feed.FeedSourceService
import com.pockethub.data.remote.feed.FeedTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExploreSection { TRENDING, FEATURED, FOLLOWING }

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val sources: FeedSourceService,
    private val sourceRepo: FeedSourceRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _section = MutableStateFlow(ExploreSection.TRENDING)
    val section: StateFlow<ExploreSection> = _section

    private val _trending = MutableStateFlow<List<DiscoverItem>>(emptyList())
    val trending: StateFlow<List<DiscoverItem>> = _trending

    private val _featured = MutableStateFlow<List<DiscoverItem>>(emptyList())
    val featured: StateFlow<List<DiscoverItem>> = _featured

    private val _feed = MutableStateFlow<List<FeedEvent>>(emptyList())
    val feed: StateFlow<List<FeedEvent>> = _feed

    private val _feedAvailable = MutableStateFlow(true)
    val feedAvailable: StateFlow<Boolean> = _feedAvailable

    /** Currently-configured source option per tab — used by the title chip. */
    val trendingSourceOption: StateFlow<FeedSourceOption> = sourceRepo.configFlow(FeedTab.TRENDING)
        .map { FeedSourceOption.fromId(it.sourceId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedSourceOption.GITHUB_SEARCH)

    val featuredSourceOption: StateFlow<FeedSourceOption> = sourceRepo.configFlow(FeedTab.FEATURED)
        .map { FeedSourceOption.fromId(it.sourceId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedSourceOption.OSS_INSIGHT)

    val followingSourceOption: StateFlow<FeedSourceOption> = sourceRepo.configFlow(FeedTab.FOLLOWING)
        .map { FeedSourceOption.fromId(it.sourceId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedSourceOption.GITHUB_EVENTS)

    /** Current trending filters — sourced straight from the persisted config so the
     *  language / time-range chips stay in sync across processes / re-entry. */
    val trendingLang: StateFlow<String> = sourceRepo.configFlow(FeedTab.TRENDING)
        .map { it.trendingLanguage }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "All")

    val trendingRange: StateFlow<String> = sourceRepo.configFlow(FeedTab.TRENDING)
        .map { it.trendingRange }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Daily")

    private var loadJob: Job? = null

    fun switchSection(section: ExploreSection) {
        if (_section.value == section) return
        _section.value = section
        load()
    }

    /** Called by the language / range chips; only meaningful when trending tab is live. */
    fun setTrendingFilters(language: String, range: String) {
        viewModelScope.launch {
            sourceRepo.setTrendingFilters(language, range)
            if (_section.value == ExploreSection.TRENDING) load()
        }
    }

    /** Standard load (cache-friendly, prefers hits). User-initiated refresh is [refresh]. */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                when (_section.value) {
                    ExploreSection.TRENDING  -> _trending.value = sources.loadTrending(forceFresh = false)
                    ExploreSection.FEATURED  -> _featured.value = sources.loadFeatured(forceFresh = false)
                    ExploreSection.FOLLOWING -> loadFollowingFeed(forceFresh = false)
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to load feed."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Pull-to-refresh path — forces fresh fetches, swaps the spinner flag. */
    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isRefreshing.value = true
            _error.update { null }
            try {
                when (_section.value) {
                    ExploreSection.TRENDING  -> _trending.value = sources.loadTrending(forceFresh = true)
                    ExploreSection.FEATURED  -> _featured.value = sources.loadFeatured(forceFresh = true)
                    ExploreSection.FOLLOWING -> loadFollowingFeed(forceFresh = true)
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Failed to load feed."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun loadFollowingFeed(forceFresh: Boolean) {
        val login = accounts.getActiveLogin()
        if (login.isBlank()) {
            _feed.value = emptyList()
            _feedAvailable.value = false
            return
        }
        _feedAvailable.value = true
        _feed.value = sources.loadFollowing(login, perPage = if (forceFresh) 50 else 30)
    }

    fun clearError() { _error.update { null } }
}
