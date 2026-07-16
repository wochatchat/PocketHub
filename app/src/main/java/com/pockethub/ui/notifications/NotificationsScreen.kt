package com.pockethub.ui.notifications

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CircleNotifications
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.data.model.GitHubNotification

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onNavigateToRepo: (String, String) -> Unit,
    onBack: () -> Unit = {},
    vm: NotificationsViewModel = hiltViewModel(),
) {
    val notifications by vm.notifications.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val tab by vm.currentTab.collectAsState()
    val grouped = remember(notifications) { notifications.groupBy { it.repository?.fullName ?: "unknown" } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = modifier.padding(padding)) {
            // Tab selector + "mark all"
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    SegmentedButton(selected = tab == NotifTab.UNREAD, onClick = { vm.switchTab(NotifTab.UNREAD) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2), label = { Text("Unread") })
                    SegmentedButton(selected = tab == NotifTab.READ, onClick = { vm.switchTab(NotifTab.READ) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2), label = { Text("Read") })
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.markAllRead() }, enabled = tab == NotifTab.UNREAD && notifications.isNotEmpty()) {
                    Text("Mark all read")
                }
            }

            if (isLoading && notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            if (notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notifications", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                grouped.forEach { (repoFullName, items) ->
                    item(key = "header-$repoFullName") {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = repoFullName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(items, key = { it.id }) { notif ->
                        NotificationItem(
                            notif = notif,
                            onMarkRead = { vm.markRead(notif.id) },
                            onClick = {
                                val owner = repoFullName.substringBefore('/')
                                val repo = repoFullName.substringAfter('/')
                                onNavigateToRepo(owner, repo)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notif: GitHubNotification,
    onMarkRead: () -> Unit,
    onClick: () -> Unit,
) {
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
                Text(notif.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Mark read
        if (notif.unread) {
            IconButton(onClick = onMarkRead) { Icon(Icons.Outlined.Done, contentDescription = "Mark read", modifier = Modifier.size(18.dp)) }
        }
    }
}
