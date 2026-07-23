package com.pockethub.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.model.GitHubNotification
import com.pockethub.data.model.NotificationReason
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

/**
 * Mirrors GitHub web's three notification tabs. UNREAD pulls the default list
 * (`all=false`) — threads GitHub still considers unread; PARTICIPATING is the
 * `participating=true` slice (threads you commented on / were assigned); ALL is
 * the wider `all=true` bucket past 20 threads (client-side filtered to effectively
 * unread for UNREAD).
 */
enum class NotifTab { UNREAD, PARTICIPATING, ALL }

/**
 * Reason filter chip state. [ALL] shows every notification regardless of reason;
 * the rest scope the list to a single GitHub notification reason.
 */
enum class ReasonFilter(val labelKey: String, val apiValue: String?) {
    ALL("notif_reason_all", null),
    ASSIGN("notif_reason_assign", "assign"),
    MENTION("notif_reason_mention", "mention"),
    REVIEW_REQUESTED("notif_reason_review_requested", "review_requested"),
    AUTHOR("notif_reason_author", "author"),
    STATE_CHANGE("notif_reason_state_change", "state_change"),
    CI_ACTIVITY("notif_reason_ci_activity", "ci_activity"),
}

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

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage

    var currentTab = MutableStateFlow(NotifTab.UNREAD)
    var reasonFilter = MutableStateFlow(ReasonFilter.ALL)

    init { load() }

    fun load(all: Boolean? = null) {
        val tab = currentTab.value
        val showAll = all ?: (tab == NotifTab.ALL)
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                val participating = (tab == NotifTab.PARTICIPATING)
                val result = cache.getNotifications(perPage = 80, all = showAll, participating = participating)
                // UNREAD tab falls back to the all=false list and filters to effectively
                // unread threads client-side; PARTICIPATING / ALL show the server list
                // as-is (they were already scoped server-side).
                _notifications.update {
                    if (tab == NotifTab.UNREAD) result.filter { it.isEffectivelyUnread() } else result
                }
            } catch (e: Exception) {
                _error.update { e.localizedMessage ?: "Failed to load notifications" }
            } finally {
                _isLoading.update { false }
            }
        }
    }

    fun switchTab(tab: NotifTab) {
        currentTab.value = tab
        // Only ALL passes all=true; PARTICIPATING is scoped server-side via participating=true.
        load(all = tab == NotifTab.ALL)
    }

    fun switchReasonFilter(filter: ReasonFilter) {
        if (reasonFilter.value == filter) return
        reasonFilter.value = filter
    }

    fun refresh() = load()

    fun markRead(threadId: String) {
        // Snapshot for rollback before the optimistic update so a failed PATCH
        // doesn't leave the local list showing the thread as read while GitHub still
        // has it as unread (the next refresh would revert it and confuse the user).
        val before = _notifications.value
        _notifications.update { list ->
            list.map { if (it.id == threadId) it.copy(unread = false) else it }
                .filter { currentTab.value == NotifTab.ALL || it.unread }
        }
        viewModelScope.launch {
            try {
                api.markNotificationRead(threadId)
            } catch (e: Exception) {
                _notifications.value = before
                _error.update { e.localizedMessage ?: "Failed to mark read" }
            }
        }
    }

    fun unsubscribe(threadId: String) {
        // Optimistic local hide — same pattern as markRead, but also pull the thread
        // out of the list entirely so the user sees it disappear immediately.
        val before = _notifications.value
        _notifications.update { list -> list.filter { it.id != threadId } }
        viewModelScope.launch {
            try {
                api.unsubscribeThread(threadId)
                _actionMessage.update { "Unsubscribed from this thread" }
            } catch (e: Exception) {
                // Unsubscribe failed — restore the thread so the user can retry.
                _notifications.value = before
                _actionMessage.update { e.localizedMessage ?: "Failed to unsubscribe" }
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                api.markAllNotificationsRead()
                // Reload from cache so the local state reflects the server truth.
                load(all = currentTab.value == NotifTab.ALL)
            } catch (e: Exception) {
                // Server still says some are unread; reload rather than pretend it worked.
                load(all = currentTab.value == NotifTab.ALL)
                _error.update { e.localizedMessage ?: "Failed to mark all as read" }
            }
        }
    }

    fun clearActionMessage() { _actionMessage.value = null }

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
