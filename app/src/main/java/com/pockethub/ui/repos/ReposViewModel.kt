package com.pockethub.ui.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.CachedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RepoTab { MINE, STARRED }
enum class RepoFilter { ALL, OWNER, MEMBER, PUBLIC, PRIVATE, FORKS }

private const val PER_PAGE = 30

@HiltViewModel
class ReposViewModel @Inject constructor(
    private val cache: CachedRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _repos = MutableStateFlow<List<Repository>>(emptyList())
    val repos: StateFlow<List<Repository>> = _repos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var currentTab = MutableStateFlow(RepoTab.MINE)
    var currentFilter = MutableStateFlow(RepoFilter.ALL)
    var currentPage = 1
        private set
    private var canLoadMore = true

    init {
        load()
    }

    fun switchTab(tab: RepoTab) {
        currentTab.value = tab
        currentPage = 1
        canLoadMore = true
        load()
    }

    fun setFilter(filter: RepoFilter) {
        currentFilter.value = filter
        currentPage = 1
        canLoadMore = true
        load()
    }

    fun loadMore() {
        if (!canLoadMore || _isLoading.value) return
        currentPage++
        load(append = true)
    }

    private fun load(append: Boolean = false) {
        val page = currentPage
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                val result = when (currentTab.value) {
                    RepoTab.MINE -> {
                        val filter = currentFilter.value
                        val type = when (filter) {
                            RepoFilter.OWNER -> "owner"
                            RepoFilter.MEMBER -> "member"
                            else -> null
                        }
                        val vis = when (filter) {
                            RepoFilter.PUBLIC -> "public"
                            RepoFilter.PRIVATE -> "private"
                            else -> null
                        }
                        cache.getMyRepositories(page = page, type = type, visibility = vis)
                    }
                    RepoTab.STARRED -> {
                        cache.getStarredRepositories(page = page)
                    }
                }
                // Client-side filtering for filters the API can't express.
                val filtered = when (currentFilter.value) {
                    RepoFilter.FORKS -> result.filter { it.fork }
                    else -> result
                }
                _repos.update { if (append) it + filtered else filtered }
                // A short page means we've hit the end; FORKS filtering shrinks pages
                // client-side so keep paging while the raw page was full.
                canLoadMore = if (currentFilter.value == RepoFilter.FORKS)
                    result.size >= PER_PAGE
                else
                    filtered.size >= PER_PAGE
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载失败" }
                // Roll back the page counter so the next loadMore retries this page.
                if (append && currentPage == page) currentPage--
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun refresh() {
        currentPage = 1
        canLoadMore = true
        load()
    }
}
