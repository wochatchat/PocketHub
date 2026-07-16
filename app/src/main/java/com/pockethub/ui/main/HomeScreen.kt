package com.pockethub.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.pockethub.R
import com.pockethub.ui.explore.ExploreScreen
import com.pockethub.ui.notifications.NotificationsScreen
import com.pockethub.ui.profile.ProfileScreen
import com.pockethub.ui.repos.ReposScreen
import com.pockethub.ui.theme.ThemeMode

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
) {
    val items = listOf(
        BottomNavItem("explore", R.string.tab_explore, Icons.Filled.Home, Icons.Outlined.Home),
        BottomNavItem("repos", R.string.tab_repos, Icons.Outlined.Code, Icons.Outlined.Code),
        BottomNavItem("notifications", R.string.tab_notifications, Icons.Filled.Notifications, Icons.Outlined.Notifications),
        BottomNavItem("profile", R.string.tab_profile, Icons.Outlined.Person, Icons.Outlined.Person),
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(items[selectedTab].labelRes)) },
                actions = {
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
            1 -> ReposScreen(modifier = Modifier.padding(innerPadding), onNavigateToRepo = onNavigateToRepo)
            2 -> NotificationsScreen(modifier = Modifier.padding(innerPadding), onNavigateToRepo = onNavigateToRepo)
            3 -> ProfileScreen(
                modifier = Modifier.padding(innerPadding),
                onNavigateToSettings = onNavigateToSettings,
            )
        }
    }
}
