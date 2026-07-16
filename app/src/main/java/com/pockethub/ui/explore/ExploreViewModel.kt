package com.pockethub.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _trending = MutableStateFlow<List<Repository>>(emptyList())
    val trending: StateFlow<List<Repository>> = _trending

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun loadTrending(query: String = "stars:>100 created:>2026-07-01") {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                val result = api.searchTrending(query = query, perPage = 30)
                _trending.update { result.items }
            } catch (_: Exception) {
                _trending.update { emptyList() }
            } finally {
                _isLoading.update { false }
            }
        }
    }
}
