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
class CommitDetailViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _commit = MutableStateFlow<GitHubApi.CommitDetail?>(null)
    val commit: StateFlow<GitHubApi.CommitDetail?> = _commit.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadedSha: String? = null

    fun load(owner: String, repo: String, sha: String) {
        if (loadedSha == sha && _commit.value != null) return
        loadedSha = sha
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                _commit.update { api.getCommit(owner, repo, sha) }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载提交详情失败" }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun retry(owner: String, repo: String, sha: String) {
        loadedSha = null
        load(owner, repo, sha)
    }
}
