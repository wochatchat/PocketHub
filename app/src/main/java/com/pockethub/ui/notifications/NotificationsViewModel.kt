package com.pockethub.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.GitHubNotification
import com.pockethub.data.remote.CachedRepository
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NotifTab { UNREAD, READ }

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: GitHubApi,
    private val cache: CachedRepository,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<GitHubNotification>>(emptyList())
    val notifications: StateFlow<List<GitHubNotification>> = _notifications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    var currentTab = MutableStateFlow(NotifTab.UNREAD)

    init { load() }

    fun load(all: Boolean? = null) {
        val showAll = all ?: (currentTab.value == NotifTab.READ)
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                val result = cache.getNotifications(perPage = 80, all = showAll)
                _notifications.update { result.filter { it.unread == !showAll } }
            } catch (e: Exception) {
                _notifications.update { emptyList() }
                _error.update { e.localizedMessage ?: "加载通知失败" }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun switchTab(tab: NotifTab) {
        currentTab.value = tab
        load(all = tab == NotifTab.READ)
    }

    fun markRead(threadId: String) {
        viewModelScope.launch {
            try { api.markNotificationRead(threadId) } catch (_: Exception) {}
            load(all = currentTab.value == NotifTab.READ)
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try { api.markAllNotificationsRead() } catch (_: Exception) {}
            load(all = currentTab.value == NotifTab.READ)
        }
    }
}
