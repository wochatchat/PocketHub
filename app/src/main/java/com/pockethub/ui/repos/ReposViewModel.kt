package com.pockethub.ui.repos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RepoTab { MINE, STARRED }
enum class RepoFilter { ALL, OWNER, MEMBER, PUBLIC, PRIVATE, FORKS }

@HiltViewModel
class ReposViewModel @Inject constructor(
    private val api: GitHubApi,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _repos = MutableStateFlow<List<Repository>>(emptyList())
    val repos: StateFlow<List<Repository>> = _repos

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    var currentTab = MutableStateFlow(RepoTab.MINE)
    var currentFilter = MutableStateFlow(RepoFilter.ALL)
    var currentPage = 1
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
        viewModelScope.launch {
            _isLoading.update { true }
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
                        api.getMyRepositories(page = currentPage, type = type, visibility = vis)
                    }
                    RepoTab.STARRED -> {
                        api.getStarredRepositories(page = currentPage)
                    }
                }
                _repos.update { if (append) it + result else result }
                canLoadMore = result.isNotEmpty()
            } catch (_: Exception) {
                if (!append) _repos.update { emptyList() }
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
