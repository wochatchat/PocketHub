package com.pockethub.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab { REPOS, USERS, CODE }

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    var query = MutableStateFlow("")
    var currentTab = MutableStateFlow(SearchTab.REPOS)

    private val _repos = MutableStateFlow<List<Repository>>(emptyList())
    val repos: StateFlow<List<Repository>> = _repos

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _code = MutableStateFlow<List<GitHubApi.CodeSearchItem>>(emptyList())
    val code: StateFlow<List<GitHubApi.CodeSearchItem>> = _code

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun search() {
        val q = query.value.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                when (currentTab.value) {
                    SearchTab.REPOS -> _repos.update { api.searchRepositories(q).items }
                    SearchTab.USERS -> _users.update { api.searchUsers(q).items }
                    SearchTab.CODE  -> _code.update { api.searchCode(q).items }
                }
            } catch (_: Exception) {
                // empty list on error
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun switchTab(tab: SearchTab) {
        currentTab.value = tab
        if (query.value.isNotBlank()) search()
    }
}
