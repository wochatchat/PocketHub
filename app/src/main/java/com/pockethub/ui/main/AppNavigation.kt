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
import android.net.Uri
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.pockethub.data.remote.AccountRepository
import com.pockethub.data.remote.AuthInterceptor
import com.pockethub.ui.auth.LoginScreen
import com.pockethub.ui.repo.RepoDetailScreen
import com.pockethub.ui.settings.SettingsScreen
import com.pockethub.ui.theme.AppStyle
import com.pockethub.ui.theme.PocketHubTheme
import com.pockethub.ui.theme.ThemeMode
import javax.inject.Inject

/** All top-level and detail routes used by the navigation graph. */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"

    const val SEARCH = "search?query={query}"
    const val SETTINGS = "settings"
    const val FEED_SOURCES = "feed_sources"
    const val REPO_DETAIL = "repo/{owner}/{repo}?tab={tab}"
    const val CREATE_ISSUE = "create_issue/{owner}/{repo}"
    const val ISSUE_DETAIL = "repo/{owner}/{repo}/issues/{number}"
    const val PR_DETAIL = "repo/{owner}/{repo}/pulls/{number}"
    const val COMMIT_DETAIL = "repo/{owner}/{repo}/commits/{sha}"
    const val WORKFLOW_RUN_DETAIL = "repo/{owner}/{repo}/actions/runs/{runId}"
    const val USER_DETAIL = "user/{login}?followTab={followTab}"
    const val HISTORY = "history"
    const val DOWNLOADS = "downloads?tab={tab}"
    const val IMAGE_PREVIEW = "image_preview?url={url}&gallery={gallery}&index={index}"
    const val FILE_VIEWER = "repo/{owner}/{repo}/file?path={path}&ref={ref}"

    fun downloads(tab: String = "active") = "downloads?tab=$tab"
    fun imagePreview(url: String, gallery: List<String> = emptyList(), startIndex: Int = 0) =
        "image_preview?url=" + java.net.URLEncoder.encode(url, "UTF-8") +
            "&gallery=" + java.net.URLEncoder.encode(gallery.joinToString("\n"), "UTF-8") +
            "&index=$startIndex"

    fun repoDetail(owner: String, repo: String, tab: String? = null) =
        if (tab.isNullOrBlank()) "repo/$owner/$repo" else "repo/$owner/$repo?tab=$tab"
    fun fileViewer(owner: String, repo: String, path: String, ref: String? = null) =
        "repo/$owner/$repo/file?path=" + java.net.URLEncoder.encode(path, "UTF-8") +
            (ref?.let { "&ref=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
    fun createIssue(owner: String, repo: String) = "create_issue/$owner/$repo"
    fun issueDetail(owner: String, repo: String, number: Int) = "repo/$owner/$repo/issues/$number"
    fun prDetail(owner: String, repo: String, number: Int) = "repo/$owner/$repo/pulls/$number"
    fun commitDetail(owner: String, repo: String, sha: String) = "repo/$owner/$repo/commits/$sha"
    fun workflowRunDetail(owner: String, repo: String, runId: Long) = "repo/$owner/$repo/actions/runs/$runId"

    fun search(query: String = "") = "search?query=${java.net.URLEncoder.encode(query, "UTF-8")}"
    fun userDetail(login: String, followTab: Int = -1) = if (followTab < 0) "user/$login" else "user/$login?followTab=$followTab"

    // ── Deep-link URI mappings (scheme pockethub://) ────────────────────────
    // Used by intent-filters in AndroidManifest and NavHost deepLinks to land
    // directly on a screen when the app is opened via the launcher icon from a
    // notification or shared GitHub link.
    const val DEEP_LINK_SCHEME = "pockethub"
    const val DEEP_LINK_NOTIFICATIONS = "pockethub://notifications"
    const val DEEP_LINK_SETTINGS = "pockethub://settings"
    const val DEEP_LINK_REPO = "pockethub://repo/{owner}/{repo}"
    const val DEEP_LINK_ISSUE = "pockethub://repo/{owner}/{repo}/issues/{number}"
    const val DEEP_LINK_PR = "pockethub://repo/{owner}/{repo}/pulls/{number}"
    const val DEEP_LINK_COMMIT = "pockethub://repo/{owner}/{repo}/commits/{sha}"
    const val DEEP_LINK_USER = "pockethub://user/{login}"
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
    appStyle: AppStyle? = null,
    forceDark: Boolean = false,
    deepLinkUri: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    // Injected globals — used to seed the auth interceptor at startup.
    val appVm: AppStartupViewModel = hiltViewModel()
    val startRoute by appVm.startRoute.collectAsState()
    val signedOut by appVm.signedOut.collectAsState()

    // In-app update check (auto on launch; manual from Settings).
    val updateVm: UpdateViewModel = hiltViewModel()
    val updateState by updateVm.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Severe-issue diagnostics: record every navigation so a crash/ANR digest
    // can answer "which screen was the user on?".
    androidx.compose.runtime.LaunchedEffect(navController) {
        val reporter = (context.applicationContext as? com.pockethub.PocketHubApp)?.issueReporter
        navController.addOnDestinationChangedListener { _, dest, _ ->
            reporter?.breadcrumb("→ ${dest.route ?: dest.label ?: "?"}")
        }
    }

    // Run the throttled auto-check once on launch — the ViewModel handles the
    // 24h interval and the "ignored version" gates.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Delay the auto-check so the home screen has a chance to render fully
        // before any in-flight network work competes for resources. 2s is long
        // enough to skip the cold-start critical path; short enough to surface
        // the update dialog within a single session even for short visits.
        kotlinx.coroutines.delay(2_000)
        updateVm.maybeAutoCheck()
    }

    // Observe signedOut and always reset the whole authenticated navigation stack.
    // Use the graph's actual start-destination id rather than the HOME route string:
    // logout can be initiated from profile/settings/account-picker, where HOME may
    // not be the current graph entry anymore.
    androidx.compose.runtime.LaunchedEffect(signedOut) {
        if (signedOut) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
                restoreState = false
            }
            appVm.clearSignedOut()
        }
    }

    // Handle pockethub:// deep links forwarded by MainActivity. We only navigate
    // when the user is actually logged in (Home is the current destination) — if
    // not, we discard the link so we don't drop the user into a screen they
    // can't leave to log in first.
    androidx.compose.runtime.LaunchedEffect(deepLinkUri, startRoute) {
        val uri = deepLinkUri ?: return@LaunchedEffect
        if (startRoute != Routes.HOME) {
            // User not ready — drop the link to avoid landing on a guarded screen.
            onDeepLinkConsumed()
            return@LaunchedEffect
        }
        // Build the Compose Navigation route from the URI so the NavController
        // routes to the matching composable. We strip the scheme:// prefix and
        // treat the rest as the route pattern (which already matches because
        // Routes.DEEP_LINK_* mirrors the Routes.*_DETAIL patterns).
        val route = uri.host + uri.path?.let { if (it.isBlank()) "" else it }
        if (route.isNotBlank()) {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
        onDeepLinkConsumed()
    }

    // After login success: the token has already been persisted in the LoginViewModel;
    // re-seed the interceptor (belt-and-suspenders — MainActivity also does it on startup).
    fun onLoginSuccess() {
        appVm.syncAuthInterceptor()
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.LOGIN) { inclusive = true }
        }
    }

    PocketHubTheme(mode = themeMode, styleOverride = appStyle, forceDark = forceDark) {
        val imagePreviewOpener = remember<(List<String>, Int) -> Unit> {
            { urls, start ->
                val first = urls.getOrNull(start) ?: urls.firstOrNull()
                if (first != null) {
                    navController.navigate(Routes.imagePreview(first, urls, start))
                }
            }
        }
        CompositionLocalProvider(
            com.pockethub.ui.components.LocalImagePreviewer provides imagePreviewOpener,
        ) {
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
                enterTransition = {
                    fadeIn(tween(240)) + slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    ) { it / 4 }
                },
                exitTransition = { fadeOut(tween(180)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = {
                    fadeOut(tween(200)) + slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    ) { it / 4 }
                },
            ) {
                composable(Routes.LOGIN) {
                    LoginScreen(onLoginSuccess = ::onLoginSuccess)
                }

                composable(Routes.HOME) {
                    HomeScreen(
                        onNavigateToSearch = { q -> navController.navigate(Routes.search(q)) },
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigateToFeedSources = { navController.navigate(Routes.FEED_SOURCES) },
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onNavigateToIssue = { owner, repo, number -> navController.navigate(Routes.issueDetail(owner, repo, number)) },
                        onNavigateToPR = { owner, repo, number -> navController.navigate(Routes.prDetail(owner, repo, number)) },
                        onNavigateToCommit = { o, r, sha -> navController.navigate(Routes.commitDetail(o, r, sha)) },
                        onNavigateToUser = { login -> navController.navigate(Routes.userDetail(login)) },
                        onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                        onNavigateToDownloads = { navController.navigate(Routes.downloads("done")) },
                    )
                }

                composable(Routes.PROFILE) {
                    com.pockethub.ui.profile.ProfileScreen(
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                        onNavigateToUserDetail = { login -> navController.navigate(Routes.userDetail(login)) },
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onNavigateToIssue = { o, r, n -> navController.navigate(Routes.issueDetail(o, r, n)) },
                        onNavigateToPR = { o, r, n -> navController.navigate(Routes.prDetail(o, r, n)) },
                        onNavigateToCommit = { o, r, sha -> navController.navigate(Routes.commitDetail(o, r, sha)) },
                        onNavigateToUser = { login, followTab -> navController.navigate(Routes.userDetail(login, followTab)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.NOTIFICATIONS,
                    deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_NOTIFICATIONS }),
                ) {
                    com.pockethub.ui.notifications.NotificationsScreen(
                        modifier = Modifier.fillMaxSize(),
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onNavigateToIssue = { o, r, n -> navController.navigate(Routes.issueDetail(o, r, n)) },
                        onNavigateToPR = { o, r, n -> navController.navigate(Routes.prDetail(o, r, n)) },
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
                        onNavigateToIssue = { owner, repo, n -> navController.navigate(Routes.issueDetail(owner, repo, n)) },
                        onNavigateToPR = { owner, repo, n -> navController.navigate(Routes.prDetail(owner, repo, n)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.SETTINGS,
                    deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_SETTINGS }),
                ) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToFeedSources = { navController.navigate(Routes.FEED_SOURCES) },
                        onSignOut = { appVm.signOut() },
                    )
                }

                composable(Routes.FEED_SOURCES) {
                    com.pockethub.ui.settings.FeedSourcesScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.REPO_DETAIL,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_REPO }),
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: return@composable
                    val repo = backStackEntry.arguments?.getString("repo") ?: return@composable
                    RepoDetailScreen(
                        owner = owner,
                        repo = repo,
                        initialTab = backStackEntry.arguments?.getString("tab"),
                        onNavigateToIssue = { n -> navController.navigate(Routes.issueDetail(owner, repo, n)) },
                        onNavigateToRepoTab = { o, r, tab -> navController.navigate(Routes.repoDetail(o, r, tab)) },
                        onNavigateToFile = { o, r, path, ref -> navController.navigate(Routes.fileViewer(o, r, path, ref)) },
                        onNavigateToIssueFull = { o, r, n -> navController.navigate(Routes.issueDetail(o, r, n)) },
                        onNavigateToPRFull = { o, r, n -> navController.navigate(Routes.prDetail(o, r, n)) },
                        onNavigateToPR = { n -> navController.navigate(Routes.prDetail(owner, repo, n)) },
                        onNavigateToCommit = { sha -> navController.navigate(Routes.commitDetail(owner, repo, sha)) },
                        onNavigateToCreateIssue = { o, r -> navController.navigate(Routes.createIssue(o, r)) },
                        onNavigateToRepo = { o, r -> navController.navigate(Routes.repoDetail(o, r)) },
                        onNavigateToUser = { login -> navController.navigate(Routes.userDetail(login)) },
                        onNavigateToSearch = { query -> navController.navigate(Routes.search(query)) },
                        onNavigateToDownloads = { tab -> navController.navigate(Routes.downloads(tab)) },
                        onNavigateToWorkflowRun = { runId -> navController.navigate(Routes.workflowRunDetail(owner, repo, runId)) },
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
                    deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_ISSUE }),
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
                        onNavigateToRepoTab = { o, r, tab -> navController.navigate(Routes.repoDetail(o, r, tab)) },
                        onNavigateToFile = { o, r, path, ref -> navController.navigate(Routes.fileViewer(o, r, path, ref)) },
                        onNavigateToIssue = { o, r, n -> navController.navigate(Routes.issueDetail(o, r, n)) },
                        onNavigateToPR = { o, r, n -> navController.navigate(Routes.prDetail(o, r, n)) },
                        onNavigateToCommit = { o, r, sha -> navController.navigate(Routes.commitDetail(o, r, sha)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.PR_DETAIL,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("number") { type = NavType.IntType },
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_PR }),
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: return@composable
                    val repo = backStackEntry.arguments?.getString("repo") ?: return@composable
                    val number = backStackEntry.arguments?.getInt("number") ?: return@composable
                    com.pockethub.ui.repo.PullRequestDetailScreen(
                        owner = owner,
                        repo = repo,
                        prNumber = number,
                        onNavigateToRepo = { o, r -> navController.navigate(Routes.repoDetail(o, r)) },
                        onNavigateToUser = { login -> navController.navigate(Routes.userDetail(login)) },
                        onNavigateToRepoTab = { o, r, tab -> navController.navigate(Routes.repoDetail(o, r, tab)) },
                        onNavigateToFile = { o, r, path, ref -> navController.navigate(Routes.fileViewer(o, r, path, ref)) },
                        onNavigateToIssue = { o, r, n -> navController.navigate(Routes.issueDetail(o, r, n)) },
                        onNavigateToPR = { o, r, n -> navController.navigate(Routes.prDetail(o, r, n)) },
                        onNavigateToCommit = { o, r, sha -> navController.navigate(Routes.commitDetail(o, r, sha)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.CREATE_ISSUE,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                    ),
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: return@composable
                    val repo = backStackEntry.arguments?.getString("repo") ?: return@composable
                    com.pockethub.ui.repo.CreateIssueScreen(
                        owner = owner,
                        repo = repo,
                        onBack = { navController.popBackStack() },
                        onIssueCreated = { n -> navController.navigate(Routes.issueDetail(owner, repo, n)) },
                    )
                }

                composable(Routes.HISTORY) {
                    com.pockethub.ui.history.HistoryScreen(
                        onNavigateToRepo = { o, r -> navController.navigate(Routes.repoDetail(o, r)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.DOWNLOADS,
                    arguments = listOf(
                        navArgument("tab") { type = NavType.StringType; defaultValue = "active" },
                    ),
                ) { backStackEntry ->
                    val tabArg = backStackEntry.arguments?.getString("tab") ?: "active"
                    val initialTab = if (tabArg == "done")
                        com.pockethub.ui.download.DownloadTab.DONE
                    else com.pockethub.ui.download.DownloadTab.ACTIVE
                    com.pockethub.ui.download.DownloadScreen(
                        initialTab = initialTab,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.COMMIT_DETAIL,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("sha") { type = NavType.StringType },
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_COMMIT }),
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: return@composable
                    val repo = backStackEntry.arguments?.getString("repo") ?: return@composable
                    val sha = backStackEntry.arguments?.getString("sha") ?: return@composable
                    com.pockethub.ui.repo.CommitDetailScreen(
                        owner = owner,
                        repo = repo,
                        sha = sha,
                        onNavigateToUser = { login -> navController.navigate(Routes.userDetail(login)) },
                        onNavigateToRepo = { o, r -> navController.navigate(Routes.repoDetail(o, r)) },
                        onNavigateToRepoTab = { o, r, tab -> navController.navigate(Routes.repoDetail(o, r, tab)) },
                        onNavigateToFile = { o, r, path, ref -> navController.navigate(Routes.fileViewer(o, r, path, ref)) },
                        onNavigateToIssue = { o, r, n -> navController.navigate(Routes.issueDetail(o, r, n)) },
                        onNavigateToPR = { o, r, n -> navController.navigate(Routes.prDetail(o, r, n)) },
                        onNavigateToCommit = { o, r, s -> navController.navigate(Routes.commitDetail(o, r, s)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.FILE_VIEWER,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("path") { type = NavType.StringType },
                        navArgument("ref") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: return@composable
                    val repo = backStackEntry.arguments?.getString("repo") ?: return@composable
                    com.pockethub.ui.repo.FileViewerScreen(
                        owner = owner,
                        repo = repo,
                        path = backStackEntry.arguments?.getString("path").orEmpty(),
                        ref = backStackEntry.arguments?.getString("ref"),
                        onNavigateToRepo = { o, r, tab -> navController.navigate(Routes.repoDetail(o, r, tab)) },
                        onNavigateToFile = { o, r, path, ref -> navController.navigate(Routes.fileViewer(o, r, path, ref)) },
                        onNavigateToIssue = { o, r, n -> navController.navigate(Routes.issueDetail(o, r, n)) },
                        onNavigateToPR = { o, r, n -> navController.navigate(Routes.prDetail(o, r, n)) },
                        onNavigateToCommit = { o, r, sha -> navController.navigate(Routes.commitDetail(o, r, sha)) },
                        onNavigateToUser = { login -> navController.navigate(Routes.userDetail(login)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.WORKFLOW_RUN_DETAIL,
                    arguments = listOf(
                        navArgument("owner") { type = NavType.StringType },
                        navArgument("repo") { type = NavType.StringType },
                        navArgument("runId") { type = NavType.LongType },
                    ),
                ) { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: return@composable
                    val repo = backStackEntry.arguments?.getString("repo") ?: return@composable
                    val runId = backStackEntry.arguments?.getLong("runId") ?: return@composable
                    com.pockethub.ui.repo.WorkflowRunDetailScreen(
                        owner = owner,
                        repo = repo,
                        runId = runId,
                        onBack = { navController.popBackStack() },
                    )
                }

                 composable(
                    Routes.USER_DETAIL,
                    arguments = listOf(
                        navArgument("login") { type = NavType.StringType },
                        navArgument("followTab") {
                            type = NavType.IntType
                            defaultValue = -1
                        },
                    ),
                    deepLinks = listOf(navDeepLink { uriPattern = Routes.DEEP_LINK_USER }),
                ) { backStackEntry ->
                    val login = backStackEntry.arguments?.getString("login") ?: return@composable
                    val initialFollowTab = backStackEntry.arguments?.getInt("followTab") ?: -1
                    com.pockethub.ui.user.UserDetailScreen(
                        login = login,
                        initialFollowTab = initialFollowTab,
                        onNavigateToRepo = { owner, repo -> navController.navigate(Routes.repoDetail(owner, repo)) },
                        onNavigateToUser = { l -> navController.navigate(Routes.userDetail(l)) },
                        onNavigateToIssue = { o, r, n -> navController.navigate(Routes.issueDetail(o, r, n)) },
                        onNavigateToPR = { o, r, n -> navController.navigate(Routes.prDetail(o, r, n)) },
                        onNavigateToCommit = { o, r, sha -> navController.navigate(Routes.commitDetail(o, r, sha)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    Routes.IMAGE_PREVIEW,
                    arguments = listOf(
                        navArgument("url") { type = NavType.StringType },
                        navArgument("gallery") { type = NavType.StringType; defaultValue = "" },
                        navArgument("index") { type = NavType.IntType; defaultValue = 0 },
                    ),
                ) { backStackEntry ->
                    val url = backStackEntry.arguments?.getString("url") ?: return@composable
                    val gallery = backStackEntry.arguments?.getString("gallery")
                        ?.split("\n")?.filter { it.isNotBlank() }
                        ?.takeIf { it.isNotEmpty() } ?: listOf(url)
                    val index = backStackEntry.arguments?.getInt("index") ?: 0
                    com.pockethub.ui.components.ImagePreviewScreen(
                        imageUrls = gallery,
                        initialIndex = index,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        // Update dialog — surfaced on top of the nav graph whenever a newer
        // non-ignored release is detected. Auto-check runs on launch; Settings
        // offers a manual trigger via the same flow.
        val updateDownload by updateVm.download.collectAsState()
        when (val s = updateState) {
            is UpdateViewModel.State.UpdateAvailable -> {
                UpdateDialog(
                    info = s.info,
                    downloadState = updateDownload,
                    onDownload = { updateVm.startDownload(s.info) },
                    onCancel = { updateVm.cancelDownload() },
                    onInstall = { path -> updateVm.install(context, path) },
                    onRetry = { updateVm.startDownload(s.info) },
                    onIgnore = { updateVm.ignoreVersion(s.info.latestVersionName) },
                    onLater = { updateVm.dismiss() },
                )
            }
            else -> Unit
        }
        }
    }
}
