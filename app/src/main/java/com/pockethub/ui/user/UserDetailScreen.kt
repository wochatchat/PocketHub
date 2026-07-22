package com.pockethub.ui.user

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.model.FeedEvent
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    login: String,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToUser: (String) -> Unit = {},
    onBack: () -> Unit,
    vm: UserDetailViewModel = hiltViewModel(),
) {
    val user by vm.user.collectAsState()
    val repos by vm.repos.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val isFollowing by vm.isFollowing.collectAsState()
    val isSelf by vm.isSelf.collectAsState()
    val followActionInProgress by vm.followActionInProgress.collectAsState()
    val followers by vm.followers.collectAsState()
    val followingList by vm.followingList.collectAsState()
    val isLoadingFollowLists by vm.isLoadingFollowLists.collectAsState()
    val context = LocalContext.current

    // Which follow list to show in the bottom sheet: 0 = followers, 1 = following, -1 = hidden.
    var followSheetTab by remember { mutableIntStateOf(-1) }

    LaunchedEffect(login) { vm.loadUser(login) }

    // Load follow lists lazily when the sheet opens.
    LaunchedEffect(followSheetTab) {
        if (followSheetTab >= 0) vm.loadFollowLists()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@$login", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$login"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.cd_open_in_browser))
                    }
                },
            )
        },
    ) { padding ->
        if (isLoading && user == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (user == null && error != null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.loading_failed), style = MaterialTheme.typography.titleMedium)
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.action_retry)) }
            }
            return@Scaffold
        }

        val events by vm.events.collectAsState()
        val isLoadingEvents by vm.isLoadingEvents.collectAsState()
        var sectionTab by remember { mutableIntStateOf(0) }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile header card
            item {
                UserHeader(
                    user = user,
                    isSelf = isSelf,
                    isFollowing = isFollowing,
                    followInProgress = followActionInProgress,
                    onToggleFollow = { vm.toggleFollow() },
                )
            }

            // Stats row — followers / following tap through to the list sheet.
            item {
                UserStatsRow(
                    user = user,
                    onFollowersClick = { followSheetTab = 0 },
                    onFollowingClick = { followSheetTab = 1 },
                )
            }

            // Additional info
            item { UserAdditionalInfo(user) }

            // Repos / Activity segmented switch
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(R.string.user_repos_chip, R.string.user_activity_chip).forEachIndexed { idx, label ->
                            SegmentedButton(
                                selected = sectionTab == idx,
                                onClick = { sectionTab = idx },
                                shape = SegmentedButtonDefaults.itemShape(idx, 2),
                            ) {
                                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(
                            if (sectionTab == 0) Icons.Outlined.Folder else Icons.Outlined.Schedule,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (sectionTab == 0) R.string.user_repositories else R.string.user_activity),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Repos list (only shown when the user picks "Repos")
            if (sectionTab == 0) {
                if (repos.isEmpty() && !isLoading) {
                    item {
                        Text(
                            stringResource(R.string.user_no_repos),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    items(repos, key = { it.id }) { repo ->
                        UserRepoCard(
                            repo = repo,
                            onClick = { onNavigateToRepo(repo.owner.login, repo.name) },
                        )
                    }
                }
            } else {
                // Activity timeline
                if (events.isEmpty() && !isLoadingEvents) {
                    item {
                        Text(
                            stringResource(R.string.user_no_activity),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                } else if (isLoadingEvents && events.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(events, key = { it.id }) { ev ->
                        ActivityCard(event = ev, onNavigateToRepo = { full ->
                            val (o, r) = full.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                            if (r.isNotEmpty()) onNavigateToRepo(o, r)
                        })
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Followers / following bottom sheet.
    if (followSheetTab >= 0) {
        ModalBottomSheet(
            onDismissRequest = { followSheetTab = -1 },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            FollowListSheet(
                selectedTab = followSheetTab,
                onTabChange = { followSheetTab = it },
                followers = followers,
                following = followingList,
                isLoading = isLoadingFollowLists,
                onUserClick = { userLogin ->
                    followSheetTab = -1
                    if (userLogin.equals(login, ignoreCase = true)) {
                        // Tapping yourself in the list just dismisses the sheet.
                    } else {
                        onNavigateToUser(userLogin)
                    }
                },
            )
        }
    }
}

/**
 * Bottom-sheet content: a segmented followers/following switcher and the user list.
 */
@Composable
private fun FollowListSheet(
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    followers: List<com.pockethub.data.model.User>,
    following: List<com.pockethub.data.model.User>,
    isLoading: Boolean,
    onUserClick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedTab == 0,
                onClick = { onTabChange(0) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                label = { Text(stringResource(R.string.followers)) },
            )
            SegmentedButton(
                selected = selectedTab == 1,
                onClick = { onTabChange(1) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                label = { Text(stringResource(R.string.following)) },
            )
        }
        Spacer(Modifier.height(12.dp))

        val list = if (selectedTab == 0) followers else following
        when {
            isLoading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (selectedTab == 0) R.string.no_followers else R.string.not_following_anyone),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(list, key = { it.login }) { u ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onUserClick(u.login) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(model = u.avatarUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(u.name ?: "@${u.login}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("@${u.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun UserHeader(
    user: User?,
    isSelf: Boolean = true,
    isFollowing: Boolean = false,
    followInProgress: Boolean = false,
    onToggleFollow: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = user?.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(88.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                user?.name ?: user?.login ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "@${user?.login ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            // Follow / unfollow button — hidden on your own profile.
            if (!isSelf && user != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onToggleFollow,
                    enabled = !followInProgress,
                    colors = if (isFollowing) {
                        ButtonDefaults.outlinedButtonColors()
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    if (followInProgress) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(if (isFollowing) R.string.action_unfollow else R.string.action_follow),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserStatsRow(
    user: User?,
    onFollowersClick: () -> Unit = {},
    onFollowingClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatPill(stringResource(R.string.followers), user?.followers ?: 0, onClick = onFollowersClick)
        StatPill(stringResource(R.string.following), user?.following ?: 0, onClick = onFollowingClick)
        StatPill(stringResource(R.string.repos), user?.publicRepos ?: 0)
    }
}

@Composable
private fun StatPill(label: String, count: Int, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        } else Modifier,
    ) {
        Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UserAdditionalInfo(user: User?) {
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

@Composable
private fun UserRepoCard(repo: Repository, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = repo.owner.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(6.dp))
                Text(repo.owner.login, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(" / ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(repo.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(repo.description!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                repo.language?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                }
                Text("★ ${repo.stars}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text("⑂ ${repo.forks}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A single row in the user's activity feed — renders the event type, repo name,
 * and a human-friendly summary derived from [FeedEvent.type] + [FeedEvent.payload].
 */
@Composable
private fun ActivityCard(
    event: FeedEvent,
    onNavigateToRepo: (String) -> Unit,
) {
    val (icon, verb): Pair<androidx.compose.ui.graphics.vector.ImageVector, String> = when (event.type) {
        "PushEvent" -> Icons.Outlined.CloudUpload to stringResource(R.string.event_pushed)
        "WatchEvent" -> Icons.Outlined.Star to stringResource(R.string.event_starred)
        "ForkEvent" -> Icons.Outlined.ForkRight to stringResource(R.string.event_forked)
        "CreateEvent" -> Icons.Outlined.CreateNewFolder to stringResource(R.string.event_created)
        "IssueCommentEvent" -> Icons.Outlined.Comment to stringResource(R.string.event_commented)
        "IssuesEvent" -> Icons.Outlined.ErrorOutline to stringResource(R.string.event_opened_issue)
        "PullRequestEvent" -> Icons.Outlined.Merge to stringResource(R.string.event_pull_request)
        "ReleaseEvent" -> Icons.Outlined.NewReleases to stringResource(R.string.event_released)
        "DeleteEvent" -> Icons.Outlined.Delete to stringResource(R.string.event_deleted)
        "PublicEvent" -> Icons.Outlined.Public to stringResource(R.string.event_made_public)
        else -> Icons.Outlined.History to event.type.removeSuffix("Event")
    }

    val repoName = event.repo?.name ?: ""
    val summary = when (event.type) {
        "PushEvent" -> event.payload?.commits?.firstOrNull()?.message?.take(80)?.let { "→ $it" } ?: ""
        "CreateEvent" -> event.payload?.ref?.let { stringResource(R.string.event_ref_suffix, it) } ?: ""
        "DeleteEvent" -> event.payload?.ref?.let { stringResource(R.string.event_ref_suffix, it) } ?: ""
        "PullRequestEvent" -> event.payload?.pullRequest?.title?.take(80) ?: ""
        "ForkEvent" -> event.payload?.forkee?.fullName ?: ""
        "IssueCommentEvent" -> event.payload?.pullRequest?.title?.take(80) ?: ""
        "IssuesEvent" -> event.payload?.action ?: ""
        else -> ""
    }
    val createdAt = event.createdAt?.take(10) ?: ""

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { if (repoName.isNotEmpty()) onNavigateToRepo(repoName) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                "$verb ${repoName}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(createdAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
