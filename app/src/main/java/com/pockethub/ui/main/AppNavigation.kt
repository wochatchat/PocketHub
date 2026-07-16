package com.pockethub.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.ui.auth.LoginScreen
import com.pockethub.ui.repo.RepoDetailScreen
import com.pockethub.ui.settings.SettingsScreen
import com.pockethub.ui.theme.PocketHubTheme
import com.pockethub.ui.theme.ThemeMode
import javax.inject.Inject

/** All top-level and detail routes used by the navigation graph. */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    // Top-level destinations reachable from home's top corners.
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"

    // Detail destinations
    const val SEARCH = "search?query={query}"
    const val SETTINGS = "settings"
    const val REPO_DETAIL = "repo/{owner}/{repo}"
    const val ISSUE_DETAIL = "repo/{owner}/{repo}/issues/{number}"
    const val USER_DETAIL = "user/{login}"

    fun repoDetail(owner: String, repo: String) = "repo/$owner/$repo"
    fun issueDetail(owner: String, repo: String, number: Int) = "repo/$owner/$repo/issues/$number"
    fun search(query: String = "") = "search?query=${java.net.URLEncoder.encode(query.ifBlank { " " }, "UTF-8")}"
    fun userDetail(login: String) = "user/$login"
}

/**
 * Root composable that decides between login and main content.
 *
 * Reads the active account token from the Room database on startup; if present,
 * seeds the global [AuthInterceptor] and starts at HOME; otherwise starts at LOGIN.
 */
@Composable
fun PocketHubApp(
    themeMode: ThemeMode,
) {
    val navController = rememberNavController()

    // Injected globals — used to seed the auth interceptor at startup.
    val appVm: AppStartupViewModel = hiltViewModel()
    val startRoute by appVm.startRoute.collectAsState()
    val signedOut by appVm.signedOut.collectAsState()

    // Observe signedOut to empty the nav stack back to Login when the user signs out.
    androidx.compose.runtime.LaunchedEffect(signedOut) {
        if (signedOut) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(Routes.HOME) { inclusive = true }
            }
            appVm.clearSignedOut()
        }
    }

    // After login success: the token has already been persisted in the LoginViewModel;
    // re-seed the interceptor (belt-and-suspenders — MainActivity also does it on startup).
    fun onLoginSuccess() {
        appVm.syncAuthInterceptor()
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.LOGIN) { inclusive = true }
        }
    }

    PocketHubTheme(mode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val route = startRoute
            if (route == null) {
                // Splash/loading — a neutral box so the theme can render behind it
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {}
                return@Surface
            }

            NavHost(
                navController = navController,
                startDestination = route,
                enterTransition = { fadeIn(animationSpec = tween(200)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) },
                popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)) },
            ) {
                composable(Routes.LOGIN) {
                    LoginScreen(onLoginSuccess = ::onLoginSuccess)
                }

                composable(Routes.HOME) {
                    val activeAccount by appVm.activeAccount.collectAsState()
                    HomeScreen(
                        activeAvatarUrl = activeAccount?.avatarUrl,
                        onNavigateToSearch = { navController.navigate(Routes.search()) },
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onNavigateToUser = { login -> navController.navigate(Routes.userDetail(login)) },
                        onNavigateToNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                        onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                    )
                }

                composable(Routes.PROFILE) {
                    com.pockethub.ui.profile.ProfileScreen(
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.NOTIFICATIONS) {
                    com.pockethub.ui.notifications.NotificationsScreen(
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.SEARCH,
                    arguments = listOf(
                        navArgument("query") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) { backStackEntry ->
                    val initialQuery = backStackEntry.arguments?.getString("query").orEmpty().trim()
                    com.pockethub.ui.search.SearchScreen(
                        initialQuery = initialQuery,
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onNavigateToUser = { login -> navController.navigate(Routes.userDetail(login)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onSignOut = { appVm.signOut() },
                    )
                }

                composable(
                    Routes.REPO_DETAIL,
                    arguments = listOf(navArgument("owner") { type = NavType.StringType }, navArgument("repo") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: return@composable
                    val repo = backStackEntry.arguments?.getString("repo") ?: return@composable
                    RepoDetailScreen(
                        owner = owner,
                        repo = repo,
                        onNavigateToIssue = { n -> navController.navigate(Routes.issueDetail(owner, repo, n)) },
                        onNavigateToRepo = { o, r -> navController.navigate(Routes.repoDetail(o, r)) },
                        onNavigateToUser = { login -> navController.navigate(Routes.userDetail(login)) },
                        onNavigateToSearch = { query -> navController.navigate(Routes.search(query)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.ISSUE_DETAIL,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("number") { type = NavType.IntType },
                    ),
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: return@composable
                    val repo = backStackEntry.arguments?.getString("repo") ?: return@composable
                    val number = backStackEntry.arguments?.getInt("number") ?: return@composable
                    com.pockethub.ui.repo.IssueDetailScreen(
                        owner = owner,
                        repo = repo,
                        issueNumber = number,
                        onNavigateToRepo = { o, r -> navController.navigate(Routes.repoDetail(o, r)) },
                        onNavigateToUser = { login -> navController.navigate(Routes.userDetail(login)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.USER_DETAIL,
                    arguments = listOf(navArgument("login") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val login = backStackEntry.arguments?.getString("login") ?: return@composable
                    com.pockethub.ui.user.UserDetailScreen(
                        login = login,
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
