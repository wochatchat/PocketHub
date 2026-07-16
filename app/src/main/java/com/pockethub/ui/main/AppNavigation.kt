package com.pockethub.ui.main

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pockethub.ui.auth.LoginScreen
import com.pockethub.ui.auth.LoginViewModel
import com.pockethub.ui.repo.RepoDetailScreen
import com.pockethub.ui.settings.SettingsScreen
import com.pockethub.ui.theme.PocketHubTheme
import com.pockethub.ui.theme.ThemeMode

/** All top-level and detail routes used by the navigation graph. */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    // Tab destinations (within home)
    const val TAB_EXPLORE = "tab/explore"
    const val TAB_REPOS = "tab/repos"
    const val TAB_NOTIFICATIONS = "tab/notifications"
    const val TAB_PROFILE = "tab/profile"

    // Detail destinations
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val REPO_DETAIL = "repo/{owner}/{repo}"
    const val ISSUE_DETAIL = "repo/{owner}/{repo}/issues/{number}"

    fun repoDetail(owner: String, repo: String) = "repo/$owner/$repo"
    fun issueDetail(owner: String, repo: String, number: Int) = "repo/$owner/$repo/issues/$number"
}

/**
 * Root composable that decides between login and main content.
 */
@Composable
fun PocketHubApp(
    themeMode: ThemeMode,
) {
    val navController = rememberNavController()

    // Determine start destination based on account state
    var startRoute by remember { mutableStateOf<String?>(null) }
    val loginVm: LoginViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        // Start destination will be set by the SplashAccountCheck composable
        // For now, default to login — the HomeScreen will handle re-validation
        startRoute = Routes.LOGIN
    }

    PocketHubTheme(mode = themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (startRoute == null) return@Surface // loading

            NavHost(
                navController = navController,
                startDestination = startRoute!!,
                enterTransition = { fadeIn(animationSpec = tween(200)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) },
                popEnterTransition = { fadeIn(animationSpec = tween(200)) },
                popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(200)) },
            ) {
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToSearch = { navController.navigate(Routes.SEARCH) },
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                    )
                }

                composable(Routes.SEARCH) {
                    com.pockethub.ui.search.SearchScreen(
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
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
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
