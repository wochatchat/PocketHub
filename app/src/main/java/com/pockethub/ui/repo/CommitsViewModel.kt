package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommitsViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _commits = MutableStateFlow<List<GitHubApi.Commit>>(emptyList())
    val commits: StateFlow<List<GitHubApi.Commit>> = _commits

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentPage = 1
    private var canLoadMore = true
    private var loadedOwner: String? = null
    private var loadedRepo: String? = null

    fun loadCommits(owner: String, repo: String) {
        if (loadedOwner == owner && loadedRepo == repo && _commits.value.isNotEmpty()) return
        loadedOwner = owner; loadedRepo = repo
        currentPage = 1
        canLoadMore = true
        fetchCommits(owner, repo, append = false)
    }

    fun loadMore(owner: String, repo: String) {
        if (!canLoadMore || _isLoading.value) return
        currentPage++
        fetchCommits(owner, repo, append = true)
    }

    fun refresh(owner: String, repo: String) {
        currentPage = 1
        canLoadMore = true
        fetchCommits(owner, repo, append = false)
    }

    private fun fetchCommits(owner: String, repo: String, append: Boolean) {
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                val result = api.getCommits(owner, repo, page = currentPage, perPage = 30)
                _commits.update { if (append) it + result else result }
                canLoadMore = result.size >= 30
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载提交失败" }
                if (!append) _commits.update { emptyList() }
            } finally {
                _isLoading.update { false }
            }
        }
    }
}
