package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _issue = MutableStateFlow<Issue?>(null)
    val issue: StateFlow<Issue?> = _issue

    private val _comments = MutableStateFlow<List<GitHubApi.IssueComment>>(emptyList())
    val comments: StateFlow<List<GitHubApi.IssueComment>> = _comments

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var loadedNumber: Int? = null

    fun loadIssue(owner: String, repo: String, number: Int) {
        if (loadedNumber == number && _issue.value != null) return
        loadedNumber = number
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                _issue.update { api.getIssue(owner, repo, number) }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "加载 Issue 失败" }
            } finally {
                _isLoading.update { false }
            }
            // Load comments independently so a comment fetch failure doesn't mask the issue body.
            viewModelScope.launch {
                try {
                    _comments.update { api.getIssueComments(owner, repo, number) }
                } catch (e: Exception) {
                    // Comments are optional — only log via error flow if the issue itself also failed.
                }
            }
        }
    }

    fun retry(owner: String, repo: String, number: Int) {
        loadedNumber = null
        loadIssue(owner, repo, number)
    }
}
