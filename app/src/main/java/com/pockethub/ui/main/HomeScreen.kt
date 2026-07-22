package com.pockethub.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
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
    onNavigateToSearch: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFeedSources: () -> Unit,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    activeAvatarUrl: String?,
) {
    val items = listOf(
        BottomNavItem("explore", R.string.tab_explore, Icons.AutoMirrored.Outlined.TrendingUp, Icons.AutoMirrored.Outlined.TrendingUp),
        BottomNavItem("repos", R.string.tab_repos, Icons.Outlined.Code, Icons.Outlined.Code),
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Search-bar inline-editing state: tapping the search icon swaps the top
    // app bar title for a real TextField so users can type a query without the
    // extra hop into a separate screen first. Submitting routes immediately.
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
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
                title = {
                    if (searchMode) {
                        val keyboard = LocalSoftwareKeyboardController.current
                        val focusRequester = remember { FocusRequester() }
                        // Auto-focus when entering search mode so users can
                        // start typing immediately.
                        androidx.compose.runtime.LaunchedEffect(searchMode) {
                            if (searchMode) focusRequester.requestFocus()
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.search_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val trimmed = query.trim()
                                    if (trimmed.isNotEmpty()) {
                                        keyboard?.hide()
                                        searchMode = false
                                        onNavigateToSearch(trimmed)
                                    }
                                },
                            ),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.action_cancel))
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            ),
                        )
                    } else {
                        Text(stringResource(items[selectedTab].labelRes))
                    }
                },
                navigationIcon = {
                    if (searchMode) {
                        IconButton(onClick = {
                            searchMode = false
                            query = ""
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                        }
                    } else {
                        IconButton(onClick = onNavigateToProfile) {
                            if (activeAvatarUrl.isNullOrBlank()) {
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.profile_avatar_fallback),
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
                    }
                },
                actions = {
                    if (!searchMode) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                        }
                        BadgedBox(badge = {
                            if (unreadCount > 0) {
                                Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                            }
                        }) {
                            IconButton(onClick = onNavigateToNotifications) {
                                Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.tab_notifications))
                            }
                        }
                        IconButton(onClick = { searchMode = true }) {
                            Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search))
                        }
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
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = onNavigateToDownloads) {
                    Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.cd_open_download))
                }
                Spacer(Modifier.height(12.dp))
                FloatingActionButton(onClick = onNavigateToHistory) {
                    Icon(Icons.Outlined.History, contentDescription = stringResource(R.string.browse_history))
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
            )
            else -> ReposScreen(
                modifier = Modifier.padding(innerPadding),
                onNavigateToRepo = onNavigateToRepo,
                onNavigateToUser = onNavigateToUser,
            )
        }
    }
}
