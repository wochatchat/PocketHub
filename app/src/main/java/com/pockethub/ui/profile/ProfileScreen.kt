package com.pockethub.ui.profile

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.data.local.AccountEntity
import com.pockethub.data.model.User
import com.pockethub.ui.components.PhAsyncImage
import com.pockethub.ui.repos.ReposTab
import com.pockethub.ui.search.issueOwnerRepo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit,
    onNavigateToUserDetail: (String) -> Unit,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToIssue: (String, String, Int) -> Unit = { _, _, _ -> },
    onNavigateToPR: (String, String, Int) -> Unit = { _, _, _ -> },
    onNavigateToCommit: (String, String, String) -> Unit = { _, _, _ -> },
    onNavigateToUser: (String, Int) -> Unit = { _, _ -> },
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToOfflineRepos: () -> Unit = {},
    onNavigateToReposTab: (ReposTab) -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onBack: () -> Unit,
    showTopBar: Boolean = true,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val user by vm.user.collectAsState()
    val isRefreshing by vm.isLoading.collectAsState()
    val activeAccount by vm.activeAccount.collectAsState()
    val starredTotal by vm.starredTotal.collectAsState()
    val workTab by vm.workTab.collectAsState()
    val workItems by vm.workItems.collectAsState()
    val isLoadingWork by vm.isLoadingWork.collectAsState()
    val workError by vm.workError.collectAsState()

    // Unread badge for the top-right bell. When embedded in the home shell this
    // resolves to the same NotificationsViewModel the shell holds (same nav
    // entry scope); standalone it fetches its own.
    val badgeNotifVm: com.pockethub.ui.notifications.NotificationsViewModel = hiltViewModel()
    val badgeNotifications by badgeNotifVm.notifications.collectAsState()
    val unreadCount = badgeNotifications.count { it.unread }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.title_profile), fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        // App restructure: notifications live behind the top-right
                        // bell instead of a bottom tab.
                        IconButton(onClick = onNavigateToNotifications) {
                            if (unreadCount > 0) {
                                // Pull the badge left — a wide count ("50"+) anchored at
                                // the icon's top-right corner otherwise clips off-screen.
                                BadgedBox(badge = { Badge(modifier = Modifier.offset(x = (-6).dp)) { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) } }) {
                                    Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.tab_notifications))
                                }
                            } else {
                                Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.tab_notifications))
                            }
                        }
                        IconButton(onClick = { user?.login?.let { onNavigateToUserDetail(it) } }) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.cd_open_in_browser))
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    },
                )
            }
        },
    ) { padding ->
        com.pockethub.ui.components.RefreshContainer(
            isRefreshing = isRefreshing,
            onRefresh = { vm.refresh() },
            modifier = modifier.padding(padding),
        ) {
            // This page is a dashboard rather than a document feed: its sections
            // are fixed, so keep one stable layout instead of a nested LazyColumn.
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProfileHeader(user, activeAccount)
                StatsRow(
                    user,
                    starredTotal,
                    onFollowersClick = { user?.login?.let { onNavigateToUser(it, 0) } },
                    onFollowingClick = { user?.login?.let { onNavigateToUser(it, 1) } },
                    onReposClick = { onNavigateToReposTab(ReposTab.MINE) },
                    onStarredClick = { onNavigateToReposTab(ReposTab.STARRED) },
                )
                WorkListCard(
                    tab = workTab,
                    onSwitchTab = vm::switchWorkTab,
                    items = workItems,
                    isLoading = isLoadingWork,
                    error = workError,
                    onRetry = vm::refreshWorkList,
                    onOpenIssue = onNavigateToIssue,
                    onOpenPR = onNavigateToPR,
                )
                QuickAccessCard(
                    onDownloads = onNavigateToDownloads,
                    onOfflineRepos = onNavigateToOfflineRepos,
                    onHistory = onNavigateToHistory,
                    onSettings = onNavigateToSettings,
                )
                AdditionalInfo(user)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Quick-access card — settings-style text rows for the utilities that used to
 * be profile top-bar icons (downloads / history / settings) plus the offline
 * repos entry that used to live in Settings. Same visual language as the
 * Settings page: SectionHeader + PhCard + ListItem rows.
 */
@Composable
private fun QuickAccessCard(
    onDownloads: () -> Unit,
    onOfflineRepos: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        com.pockethub.ui.components.SectionHeader(stringResource(R.string.profile_quick_entries))
        com.pockethub.ui.components.PhCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.download_screen_title)) },
                    modifier = Modifier.clickable(onClick = onDownloads),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.FolderZip, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.offline_repos)) },
                    modifier = Modifier.clickable(onClick = onOfflineRepos),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.browse_history)) },
                    modifier = Modifier.clickable(onClick = onHistory),
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.settings)) },
                    modifier = Modifier.clickable(onClick = onSettings),
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: User?, activeAccount: AccountEntity?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        )
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Round avatar with a neutral surface placeholder so the circle is
            // visible even before the asynchronous image resolves.
            PhAsyncImage(
                model = user?.avatarUrl ?: activeAccount?.avatarUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.height(12.dp))
            val displayName = user?.name?.takeIf { it.isNotBlank() }
                ?: activeAccount?.name?.takeIf { it.isNotBlank() }
            val displayLogin = (user?.login ?: activeAccount?.login)?.takeIf { it.isNotBlank() }
            if (displayName != null) {
                Text(displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (displayLogin != null) {
                Text("@$displayLogin", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (displayName == null && displayLogin == null) {
                // Loading / not-yet-fetched — a single muted placeholder keeps the
                // layout stable instead of rendering two empty Text rows.
                Text(stringResource(R.string.profile_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!user?.bio.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    user!!.bio!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(
    user: User?,
    starredTotal: Int,
    onFollowersClick: (() -> Unit)? = null,
    onFollowingClick: (() -> Unit)? = null,
    onReposClick: (() -> Unit)? = null,
    onStarredClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
    ) {
        StatPill(stringResource(R.string.followers), user?.followers ?: 0, onClick = onFollowersClick)
        StatPill(stringResource(R.string.following), user?.following ?: 0, onClick = onFollowingClick)
        StatPill(
            stringResource(R.string.repos),
            (user?.publicRepos ?: 0) + (user?.totalPrivateRepos ?: 0),
            onClick = onReposClick,
        )
        StatPill(stringResource(R.string.starred), starredTotal, onClick = onStarredClick)
    }
}

@Composable
private fun StatPill(label: String, count: Int, onClick: (() -> Unit)? = null) {
    val mod = if (onClick != null) {
        Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    } else {
        Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = mod) {
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdditionalInfo(user: User?) {
    val rows: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String>> = buildList {
        user?.company?.takeIf { it.isNotBlank() }?.let { add(Triple(Icons.Outlined.Apartment, stringResource(R.string.company), it)) }
        user?.location?.takeIf { it.isNotBlank() }?.let { add(Triple(Icons.Outlined.LocationOn, stringResource(R.string.location), it)) }
        user?.blog?.takeIf { it.isNotBlank() }?.let { add(Triple(Icons.Outlined.Public, stringResource(R.string.website), it)) }
        user?.email?.takeIf { it.isNotBlank() }?.let { add(Triple(Icons.Outlined.Email, stringResource(R.string.email), it)) }
    }
    if (rows.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { (icon, label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/**
 * Work-list board — items that need this user's attention, sourced from
 * /search/issues aggregated by qualifier (Assigned / Mentioned / Created / Involved).
 *
 * Lives at the top of the Profile screen because the most frequent "open the app"
 * intent is "what's on me right now", not "let me browse my repos".
 */
@Composable
private fun WorkListCard(
    tab: com.pockethub.ui.profile.ProfileViewModel.WorkTab,
    onSwitchTab: (com.pockethub.ui.profile.ProfileViewModel.WorkTab) -> Unit,
    items: List<com.pockethub.data.model.Issue>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenIssue: (String, String, Int) -> Unit,
    onOpenPR: (String, String, Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Inbox,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.work_list_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                com.pockethub.ui.profile.ProfileViewModel.WorkTab.entries.forEach { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { onSwitchTab(t) },
                        label = { Text(workTabLabel(t), style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            when {
                isLoading -> Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(3) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.pockethub.ui.components.SkeletonBox(Modifier.size(36.dp), shape = androidx.compose.foundation.shape.CircleShape)
                            Spacer(Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth(0.5f).height(13.dp))
                                com.pockethub.ui.components.SkeletonBox(Modifier.fillMaxWidth(0.8f).height(10.dp))
                            }
                        }
                    }
                }
                error != null -> Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                items.isEmpty() -> Text(
                    stringResource(R.string.work_list_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
                else -> Column(
                    Modifier
                        .fillMaxWidth()
                        // The dashboard shell stays fixed; only a busy workbench
                        // scrolls inside its bounded card.
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items.forEach { issue ->
                        WorkListRow(
                            issue = issue,
                            onClick = {
                                // GitHub no longer returns the repository object in
                                // /search/issues — resolve owner/repo through the same
                                // fallback chain as Search (repository → repository_url
                                // → html_url). A bare `issue.repository` click silently
                                // does nothing on every result today.
                                val (owner, name) = issueOwnerRepo(issue) ?: return@WorkListRow
                                if (issue.pullRequest != null) onOpenPR(owner, name, issue.number)
                                else onOpenIssue(owner, name, issue.number)
                            },
                        )
                    }
                    if (items.size > 8) {
                        Text(
                            stringResource(R.string.work_list_more, items.size - 8),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkListRow(issue: com.pockethub.data.model.Issue, onClick: () -> Unit) {
    val isPr = issue.pullRequest != null
    // Search results carry no repository object — derive "owner/repo" from
    // repository_url / html_url so the context line stays visible.
    val repoFullName = issueOwnerRepo(issue)?.let { (o, n) -> "$o/$n" }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (isPr) Icons.Outlined.Merge else Icons.Outlined.ErrorOutline,
            null,
            modifier = Modifier.size(16.dp),
            tint = if (isPr) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                issue.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (repoFullName != null) {
                Text(
                    repoFullName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "#${issue.number}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun workTabLabel(tab: com.pockethub.ui.profile.ProfileViewModel.WorkTab): String = when (tab) {
    com.pockethub.ui.profile.ProfileViewModel.WorkTab.ASSIGNED -> stringResource(R.string.work_tab_assigned)
    com.pockethub.ui.profile.ProfileViewModel.WorkTab.MENTIONED -> stringResource(R.string.work_tab_mentioned)
    com.pockethub.ui.profile.ProfileViewModel.WorkTab.CREATED -> stringResource(R.string.work_tab_created)
    com.pockethub.ui.profile.ProfileViewModel.WorkTab.INVOLVED -> stringResource(R.string.work_tab_involved)
}
