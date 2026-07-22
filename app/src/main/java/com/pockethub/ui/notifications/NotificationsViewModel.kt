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
import java.time.OffsetDateTime
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
                _notifications.update { result.filter { it.isEffectivelyUnread() == !showAll } }
            } catch (e: Exception) {
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

    fun refresh() = load()

    fun markRead(threadId: String) {
        // Snapshot for rollback before the optimistic update so a failed PATCH
        // doesn't leave the local list showing the thread as read while GitHub still
        // has it as unread (the next refresh would revert it and confuse the user).
        val before = _notifications.value
        _notifications.update { list ->
            list.map { if (it.id == threadId) it.copy(unread = false) else it }
                .filter { currentTab.value == NotifTab.READ || it.unread }
        }
        viewModelScope.launch {
            try {
                api.markNotificationRead(threadId)
            } catch (e: Exception) {
                _notifications.value = before
                _error.update { e.localizedMessage ?: "标记已读失败" }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                api.markAllNotificationsRead()
                // Reload from cache so the local state reflects the server truth.
                load(all = currentTab.value == NotifTab.READ)
            } catch (e: Exception) {
                // Server still says some are unread; reload rather than pretend it worked.
                load(all = currentTab.value == NotifTab.READ)
                _error.update { e.localizedMessage ?: "标记全部已读失败" }
            }
        }
    }

    /**
     * GitHub's `all=true` list keeps `unread=true` for threads that were read and later
     * got new activity; the reliable signal is `updated_at > last_read_at`.
     */
    private fun GitHubNotification.isEffectivelyUnread(): Boolean {
        if (unread && lastReadAt == null) return true
        val lastRead = parseIso(lastReadAt) ?: return unread
        val updated = parseIso(updatedAt) ?: return false
        return updated > lastRead
    }

    private fun parseIso(iso: String?): Long? = try {
        iso?.let { OffsetDateTime.parse(it.trim().replace("Z", "+00:00")).toInstant().toEpochMilli() }
    } catch (_: Exception) { null }
}
