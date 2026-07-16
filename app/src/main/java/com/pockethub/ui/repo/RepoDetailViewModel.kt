package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RepoTab { OVERVIEW, CODE, ISSUES, PRS, RELEASES, ACTIONS, WIKI, PROJECTS }

@HiltViewModel
class RepoDetailViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _repo = MutableStateFlow<Repository?>(null)
    val repo: StateFlow<Repository?> = _repo

    private val _issues = MutableStateFlow<List<Issue>>(emptyList())
    val issues: StateFlow<List<Issue>> = _issues

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    var currentTab = MutableStateFlow(RepoTab.OVERVIEW)

    fun loadRepo(owner: String, repo: String) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                _repo.update { api.getRepository(owner, repo) }
            } catch (_: Exception) {
                // keep cached
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun loadIssues(owner: String, repo: String, state: String = "open") {
        viewModelScope.launch {
            try {
                _issues.update { api.getIssues(owner, repo, state = state) }
            } catch (_: Exception) {
                _issues.update { emptyList() }
            }
        }
    }
}
