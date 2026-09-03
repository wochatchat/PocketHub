package com.pockethub.ui.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    onNavigateToSearch: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFeedSources: () -> Unit,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToIssue: (String, String, Int) -> Unit,
    onNavigateToPR: (String, String, Int) -> Unit,
    onNavigateToCommit: (String, String, String) -> Unit = { _, _, _ -> },
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToHistory: () -> Unit,
    onNavigateToDownloads: () -> Unit,
) {
    val items = listOf(
        BottomNavItem("explore", R.string.tab_explore, Icons.AutoMirrored.Outlined.TrendingUp, Icons.AutoMirrored.Outlined.TrendingUp),
        BottomNavItem("repos", R.string.tab_repos, Icons.Outlined.Folder, Icons.Outlined.Folder),
        BottomNavItem("notifications", R.string.tab_notifications, Icons.Outlined.Notifications, Icons.Outlined.Notifications),
        BottomNavItem("profile", R.string.tab_profile, Icons.Outlined.Person, Icons.Outlined.Person),
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Double-tap the selected Explore tab to force-fetch its active section.
    // See [DeepNavTabGesture.pickRound].
    var lastTabClickAtMillis by rememberSaveable { mutableStateOf(0L) }
    var lastClickedTab by rememberSaveable { mutableIntStateOf(0) }
    // Bumps whenever a refresh is requested by double-tapping. Screens read it via
    // their refreshTrigger param and react with LaunchedEffect.
    var exploreRefreshTrigger by rememberSaveable { mutableIntStateOf(0) }

    // Notifications badge — the NotificationsViewModel is cheap to pull a single
    // unread-notifications page from; we just need the count for the badge dot.
    val notifVm: NotificationsViewModel = hiltViewModel()
    val notifications by notifVm.notifications.collectAsState()
    val unreadCount = notifications.count { it.unread }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = {
                    // Title crossfades+slides when switching tabs.
                    androidx.compose.animation.AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(tween(200)) +
                                androidx.compose.animation.slideInVertically(tween(220)) { it / 3 })
                                .togetherWith(
                                    androidx.compose.animation.fadeOut(tween(120)) +
                                        androidx.compose.animation.slideOutVertically(tween(160)) { -it / 3 }
                                )
                        },
                        label = "topbar_title",
                    ) { tab ->
                        Text(stringResource(items[tab].labelRes), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    when (selectedTab) {
                        0, 1 -> IconButton(onClick = { onNavigateToSearch("") }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search))
                        }
                        3 -> {
                            IconButton(onClick = onNavigateToDownloads) {
                                Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.cd_open_download))
                            }
                            IconButton(onClick = onNavigateToHistory) {
                                Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.browse_history))
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                items.forEachIndexed { index, item ->
                    val selected = selectedTab == index
                    val hapticView = androidx.compose.ui.platform.LocalView.current
                    // Selected icon pops with a spring; unselected stays quiet.
                    val iconScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (selected) 1.12f else 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.5f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                        ),
                        label = "nav_icon_scale",
                    )
                    val tint by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200),
                        label = "nav_icon_tint",
                    )
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (index == 0 && selected && lastClickedTab == index && now - lastTabClickAtMillis < 400) {
                                exploreRefreshTrigger++
                                lastTabClickAtMillis = 0L
                            } else {
                                if (index != selectedTab) {
                                    com.pockethub.ui.components.Haptics.tick(hapticView)
                                }
                                lastTabClickAtMillis = now
                                lastClickedTab = index
                                selectedTab = index
                            }
                        },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ),
                        icon = {
                            if (index == 2 && unreadCount > 0) {
                                BadgedBox(badge = { Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) } }) {
                                    Icon(
                                        item.selectedIcon, contentDescription = stringResource(item.labelRes),
                                        tint = tint,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale
                                                scaleY = iconScale
                                            },
                                    )
                                }
                            } else {
                                Icon(
                                    if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = stringResource(item.labelRes),
                                    tint = tint,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        },
                                )
                            }
                        },
                        label = { Text(stringResource(item.labelRes), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> ExploreScreen(
                modifier = Modifier.padding(innerPadding),
                onNavigateToRepo = onNavigateToRepo,
                onNavigateToUser = onNavigateToUser,
                onNavigateToFeedSources = onNavigateToFeedSources,
                refreshTrigger = exploreRefreshTrigger,
            )
            1 -> ReposScreen(
                modifier = Modifier.padding(innerPadding),
                onNavigateToRepo = onNavigateToRepo,
                onNavigateToUser = onNavigateToUser,
            )
            2 -> com.pockethub.ui.notifications.NotificationsScreen(
                modifier = Modifier.padding(innerPadding),
                onNavigateToRepo = onNavigateToRepo,
                onNavigateToIssue = onNavigateToIssue,
                onNavigateToPR = onNavigateToPR,
                showTopBar = false,
            )
            else -> com.pockethub.ui.profile.ProfileScreen(
                modifier = Modifier.padding(innerPadding),
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToUserDetail = onNavigateToUser,
                onNavigateToRepo = onNavigateToRepo,
                onNavigateToIssue = onNavigateToIssue,
                onNavigateToPR = onNavigateToPR,
                onNavigateToCommit = onNavigateToCommit,
                onBack = {},
                showTopBar = false,
            )
        }
    }
}
