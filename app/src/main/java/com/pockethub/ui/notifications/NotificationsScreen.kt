package com.pockethub.ui.notifications

import com.pockethub.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CircleNotifications
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Unsubscribe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.model.GitHubNotification
import com.pockethub.data.model.NotificationReason
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToIssue: (String, String, Int) -> Unit = { _, _, _ -> },
    onNavigateToPR: (String, String, Int) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {},
    vm: NotificationsViewModel = hiltViewModel(),
) {
    val notifications by vm.notifications.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val actionMessage by vm.actionMessage.collectAsState()
    val tab by vm.currentTab.collectAsState()
    val reasonFilter by vm.reasonFilter.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingUnsubId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.clearActionMessage()
        }
    }

    val grouped = remember(notifications, reasonFilter) {
        val filtered = if (reasonFilter.apiValue == null) notifications
        else notifications.filter { it.reason == reasonFilter.apiValue }
        filtered.groupBy { it.repository?.fullName ?: "unknown" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_notifications), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = modifier.padding(padding)) {
            // Tab selector + "mark all"
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    SegmentedButton(selected = tab == NotifTab.UNREAD, onClick = { vm.switchTab(NotifTab.UNREAD) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2), label = { Text(stringResource(R.string.tab_unread)) })
                    SegmentedButton(selected = tab == NotifTab.READ, onClick = { vm.switchTab(NotifTab.READ) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2), label = { Text(stringResource(R.string.tab_read)) })
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.markAllRead() }, enabled = tab == NotifTab.UNREAD && notifications.isNotEmpty()) {
                    Text(stringResource(R.string.action_mark_all_read))
                }
            }

            // Reason filter chips — horizontal scroll, modelled after GitHub's left rail.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReasonFilter.entries.forEach { f ->
                    AssistChip(
                        onClick = { vm.switchReasonFilter(f) },
                        label = { Text(stringResource(stringResourceKey(f.labelKey))) },
                        colors = if (reasonFilter == f)
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        else AssistChipDefaults.assistChipColors(),
                        border = null,
                    )
                }
            }

            if (isLoading && notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            if (error != null && notifications.isEmpty()) {
                com.pockethub.ui.components.ErrorState(message = error!!, onRetry = { vm.refresh() })
                return@Column
            }

            if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_notifications), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                grouped.forEach { (repoFullName, items) ->
                    item(key = "header-$repoFullName") {
                        Spacer(Modifier.size(8.dp))
                        val owner = repoFullName.substringBefore('/')
                        val repoName = repoFullName.substringAfter('/')
                        val avatarUrl = items.firstOrNull { it.repository?.owner?.avatarUrl != null }?.repository?.owner?.avatarUrl
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onNavigateToRepo(owner, repoName) }.padding(vertical = 4.dp)) {
                            if (avatarUrl != null) {
                                AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(14.dp).clip(CircleShape))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = repoFullName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(items, key = { it.id }) { notif ->
                        NotificationItem(
                            notif = notif,
                            onMarkRead = { vm.markRead(notif.id) },
                            onUnsubscribe = { pendingUnsubId = notif.id },
                            onCopy = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("notification", notif.subject.title))
                                scope.launch { snackbarHostState.showSnackbar("Copied") }
                            },
                            onClick = {
                                val owner = repoFullName.substringBefore('/')
                                val repo = repoFullName.substringAfter('/')
                                val number = notif.subject.url
                                    ?.substringAfterLast('/')
                                    ?.toIntOrNull()
                                when {
                                    number != null && notif.subject.type == "Issue" ->
                                        onNavigateToIssue(owner, repo, number)
                                    number != null && notif.subject.type == "PullRequest" ->
                                        onNavigateToPR(owner, repo, number)
                                    else -> onNavigateToRepo(owner, repo)
                                }
                                if (notif.unread) vm.markRead(notif.id)
                            },
                        )
                    }
                }
                if (error != null) {
                    item(key = "error-banner") {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }

    // Unsubscribe confirmation — destructive action, ask before firing.
    pendingUnsubId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingUnsubId = null },
            title = { Text(stringResource(R.string.notif_unsub_confirm_title)) },
            text = { Text(stringResource(R.string.notif_unsub_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.unsubscribe(id)
                    pendingUnsubId = null
                }) { Text(stringResource(R.string.action_unsubscribe)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingUnsubId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun NotificationItem(
    notif: GitHubNotification,
    onMarkRead: () -> Unit,
    onUnsubscribe: () -> Unit,
    onCopy: () -> Unit,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Type icon
        val icon = when (notif.subject.type) {
            "PullRequest" -> Icons.Outlined.Merge
            "Release"     -> Icons.Outlined.NewReleases
            "Issue"       -> Icons.Outlined.Email
            else          -> Icons.Outlined.CircleNotifications
        }
        Icon(icon, contentDescription = notif.subject.type, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = notif.subject.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (notif.reason.isNotBlank()) {
                Text(
                    text = reasonLabel(notif.reason),
                    style = MaterialTheme.typography.labelSmall,
                    color = reasonTint(notif.reason),
                )
            }
        }

        // Overflow menu
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.cd_notif_actions), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (notif.unread) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_mark_read)) },
                    leadingIcon = { Icon(Icons.Outlined.Done, null, modifier = Modifier.size(18.dp)) },
                    onClick = { menuOpen = false; onMarkRead() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_unsubscribe)) },
                leadingIcon = { Icon(Icons.Outlined.Unsubscribe, null, modifier = Modifier.size(18.dp)) },
                onClick = { menuOpen = false; onUnsubscribe() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_copy)) },
                onClick = { menuOpen = false; onCopy() },
            )
        }
    }
}

@Composable
private fun reasonLabel(apiValue: String): String {
    val r = NotificationReason.fromApi(apiValue)
    val resId = when (r) {
        NotificationReason.ASSIGN -> R.string.notif_reason_assign
        NotificationReason.AUTHOR -> R.string.notif_reason_author
        NotificationReason.COMMENT -> R.string.notif_reason_comment
        NotificationReason.CI_ACTIVITY -> R.string.notif_reason_ci_activity
        NotificationReason.MENTION -> R.string.notif_reason_mention
        NotificationReason.REVIEW_REQUESTED -> R.string.notif_reason_review_requested
        NotificationReason.STATE_CHANGE -> R.string.notif_reason_state_change
        NotificationReason.TEAM_MENTION -> R.string.notif_reason_team_mention
        NotificationReason.MANUAL -> R.string.notif_reason_manual
        NotificationReason.SUBSCRIBED -> R.string.notif_reason_subscribed
        NotificationReason.UNKNOWN -> return apiValue
    }
    return stringResource(resId)
}

@Composable
private fun reasonTint(apiValue: String): androidx.compose.ui.graphics.Color {
    val r = NotificationReason.fromApi(apiValue)
    return when (r) {
        NotificationReason.MENTION,
        NotificationReason.ASSIGN,
        NotificationReason.REVIEW_REQUESTED,
        NotificationReason.TEAM_MENTION -> MaterialTheme.colorScheme.primary

        NotificationReason.STATE_CHANGE,
        NotificationReason.AUTHOR -> MaterialTheme.colorScheme.secondary

        NotificationReason.CI_ACTIVITY -> MaterialTheme.colorScheme.tertiary

        NotificationReason.COMMENT,
        NotificationReason.MANUAL,
        NotificationReason.SUBSCRIBED,
        NotificationReason.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Resolve a string-key name to its resource id safely — keeps ReasonFilter free from R imports. */
private fun stringResourceKey(key: String): Int = when (key) {
    "notif_reason_all" -> R.string.notif_reason_all
    "notif_reason_assign" -> R.string.notif_reason_assign
    "notif_reason_mention" -> R.string.notif_reason_mention
    "notif_reason_review_requested" -> R.string.notif_reason_review_requested
    "notif_reason_author" -> R.string.notif_reason_author
    "notif_reason_state_change" -> R.string.notif_reason_state_change
    "notif_reason_ci_activity" -> R.string.notif_reason_ci_activity
    else -> R.string.notif_reason_all
}
