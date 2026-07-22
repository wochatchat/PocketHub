package com.pockethub.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.feed.FeedSourceConfig
import com.pockethub.data.remote.feed.FeedSourceOption
import com.pockethub.data.remote.feed.FeedSourceRepository
import com.pockethub.data.remote.feed.FeedTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Per-tab configuration state for the Explore feed-source settings screen.
 * Acts as the single source of truth: each tab's selected source + custom URL
 * is mirrored here so the UI can stage unsaved changes locally before the
 * user taps Save. We currently persist immediately on every change to keep
 * the model simple (the screen behaves as a continuous editor).
 */
@HiltViewModel
class FeedSourcesViewModel @Inject constructor(
    private val repo: FeedSourceRepository,
) : ViewModel() {

    /** Snapshot of every tab's current persistence-layer state. */
    val trendingConfig: StateFlow<FeedSourceConfig> = repo.configFlow(FeedTab.TRENDING)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedSourceConfig(FeedSourceOption.GITHUB_SEARCH.id))

    val featuredConfig: StateFlow<FeedSourceConfig> = repo.configFlow(FeedTab.FEATURED)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedSourceConfig(FeedSourceOption.OSS_INSIGHT.id))

    val followingConfig: StateFlow<FeedSourceConfig> = repo.configFlow(FeedTab.FOLLOWING)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeedSourceConfig(FeedSourceOption.GITHUB_EVENTS.id))

    fun selectSource(tab: FeedTab, source: FeedSourceOption, customBaseUrl: String = "") {
        viewModelScope.launch { repo.setSource(tab, source, customBaseUrl) }
    }

    fun setCustomBaseUrl(tab: FeedTab, source: FeedSourceOption, url: String) {
        viewModelScope.launch {
            if (source.urlModifiable) {
                repo.setSource(tab, source, url)
            }
        }
    }

    /** Reset [tab] back to the built-in default source. */
    fun resetTab(tab: FeedTab) {
        viewModelScope.launch {
            repo.setSource(tab, FeedSourceOption.defaultsFor(tab), "")
        }
    }
}
