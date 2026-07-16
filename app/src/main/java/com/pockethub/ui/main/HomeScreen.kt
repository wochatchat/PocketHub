package com.pockethub.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.R
import com.pockethub.ui.explore.ExploreScreen
import com.pockethub.ui.notifications.NotificationsViewModel
import com.pockethub.ui.repos.ReposScreen

/** Bottom nav item definition. */
private data class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    activeAvatarUrl: String?,
) {
    val items = listOf(
        BottomNavItem("explore", R.string.tab_explore, Icons.AutoMirrored.Outlined.TrendingUp, Icons.AutoMirrored.Outlined.TrendingUp),
        BottomNavItem("repos", R.string.tab_repos, Icons.Outlined.Code, Icons.Outlined.Code),
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Notifications badge — the NotificationsViewModel is cheap to pull a single
    // unread-notifications page from; we just need the count for the badge dot.
    val notifVm: NotificationsViewModel = hiltViewModel()
    val notifications by notifVm.notifications.collectAsState()
    val unreadCount = notifications.count { it.unread }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(items[selectedTab].labelRes)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateToProfile) {
                        if (activeAvatarUrl.isNullOrBlank()) {
                            // Fallback chip-ish ring icon when avatar isn't loaded yet
                            Box(
                                Modifier.size(28.dp).clip(CircleShape),
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                Text(
                                    "ME",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            AsyncImage(
                                model = activeAvatarUrl,
                                contentDescription = stringResource(R.string.tab_profile),
                                modifier = Modifier.size(28.dp).clip(CircleShape),
                            )
                        }
                    }
                },
                actions = {
                    BadgedBox(badge = {
                        if (unreadCount > 0) {
                            Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                        }
                    }) {
                        IconButton(onClick = onNavigateToNotifications) {
                            Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.tab_notifications))
                        }
                    }
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search))
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                items.forEachIndexed { index, item ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = stringResource(item.labelRes),
                            )
                        },
                        label = { Text(stringResource(item.labelRes), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> ExploreScreen(modifier = Modifier.padding(innerPadding), onNavigateToRepo = onNavigateToRepo)
            else -> ReposScreen(modifier = Modifier.padding(innerPadding), onNavigateToRepo = onNavigateToRepo)
        }
    }
}
