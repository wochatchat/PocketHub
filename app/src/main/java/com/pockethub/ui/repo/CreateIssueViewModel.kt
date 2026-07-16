package com.pockethub.ui.repo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Issue
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateIssueViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _result = MutableStateFlow<Result<Issue>?>(null)
    val result: StateFlow<Result<Issue>?> = _result.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun createIssue(owner: String, repo: String, title: String, body: String?) {
        if (_isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _actionError.value = null
            try {
                val issue = api.createIssue(owner, repo, title, body)
                _result.value = Result.success(issue)
            } catch (e: Exception) {
                _actionError.value = e.localizedMessage ?: "创建失败"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun clearResult() {
        _result.value = null
    }

    fun clearActionError() {
        _actionError.value = null
    }
}
