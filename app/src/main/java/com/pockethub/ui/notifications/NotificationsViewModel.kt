package com.pockethub.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.GitHubNotification
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NotifTab { UNREAD, READ }

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: GitHubApi,
) : ViewModel() {

    private val _notifications = MutableStateFlow<List<GitHubNotification>>(emptyList())
    val notifications: StateFlow<List<GitHubNotification>> = _notifications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    var currentTab = MutableStateFlow(NotifTab.UNREAD)

    init { load() }

    fun load(all: Boolean? = null) {
        val showAll = all ?: (currentTab.value == NotifTab.READ)
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                val result = api.getNotifications(perPage = 80, all = showAll)
                _notifications.update { result.filter { it.unread == !showAll } }
            } catch (_: Exception) {
                _notifications.update { emptyList() }
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

    /** Group notifications by repository full name. */
    val grouped: StateFlow<Map<String, List<GitHubNotification>>>
        get() {
            val m = MutableStateFlow<Map<String, List<GitHubNotification>>>(emptyMap())
            viewModelScope.launch {
                _notifications.collect {
                    m.update { _ -> _notifications.value.groupBy { it.repository?.fullName ?: "Unknown" } }
                }
            }
            return m
        }
}
