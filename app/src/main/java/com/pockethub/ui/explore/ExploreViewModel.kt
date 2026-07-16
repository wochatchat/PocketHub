package com.pockethub.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.Repository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _trending = MutableStateFlow<List<Repository>>(emptyList())
    val trending: StateFlow<List<Repository>> = _trending.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Load trending repositories using GitHub's search API as a Trending proxy.
     *
     * @param language one of "All", "Kotlin", …
     * @param range    one of "Daily", "Weekly", "Monthly"
     */
    fun loadTrending(language: String = "All", range: String = "Daily") {
        // Cancel any in-flight request — only the latest filter choice should win.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                val datePrefix = when (range) {
                    "Weekly" -> "created:>"
                    "Monthly" -> "created:>"
                    else -> "created:>"
                }
                val created = when (range) {
                    "Weekly" -> LocalDate.now().minusWeeks(1)
                    "Monthly" -> LocalDate.now().minusMonths(1)
                    else -> LocalDate.now().minusDays(1)
                }.format(DateTimeFormatter.ISO_LOCAL_DATE)

                val langPart = if (language == "All") "" else " language:${language}"
                val q = "stars:>50$langPart created:>$created"

                val result = api.searchTrending(query = q, perPage = 30)
                _trending.update { result.items }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "Failed to load trending repositories." }
                // Keep the previous list so the user still sees something.
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun clearError() { _error.update { null } }
}
