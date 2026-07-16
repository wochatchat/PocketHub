package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _issue = MutableStateFlow<Issue?>(null)
    val issue: StateFlow<Issue?> = _issue

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadIssue(owner: String, repo: String, number: Int) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                _issue.update { api.getIssue(owner, repo, number) }
            } catch (_: Exception) {
                // keep cached
            } finally {
                _isLoading.update { false }
            }
        }
    }
}
