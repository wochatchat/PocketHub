package com.pockethub.ui.profile

import com.pockethub.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import coil.compose.AsyncImage
import com.pockethub.data.local.AccountEntity
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit,
    onNavigateToRepo: (String, String) -> Unit,
    onNavigateToIssue: (String, String, Int) -> Unit = { _, _, _ -> },
    onNavigateToPR: (String, String, Int) -> Unit = { _, _, _ -> },
    onBack: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val user by vm.user.collectAsState()
    val allAccounts by vm.allAccounts.collectAsState()
    val activeAccount by vm.activeAccount.collectAsState()
    val topRepos by vm.topRepos.collectAsState()
    val isLoadingRepos by vm.isLoadingRepos.collectAsState()
    val starredTotal by vm.starredTotal.collectAsState()
    val workTab by vm.workTab.collectAsState()
    val workItems by vm.workItems.collectAsState()
    val isLoadingWork by vm.isLoadingWork.collectAsState()
    val workError by vm.workError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_profile), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile header card
            item { ProfileHeader(user, activeAccount) }

            // Quick stats row (followers / following / repos)
            item { StatsRow(user, starredTotal) }

            // Work-list — Assigned / Mentioned / Created / Involved items needing attention.
            // Placed right under the stats so opening the app shows "what's on me" first.
            item { WorkListCard(
                tab = workTab,
                onSwitchTab = vm::switchWorkTab,
                items = workItems,
                isLoading = isLoadingWork,
                error = workError,
                onRetry = vm::refreshWorkList,
                onOpenIssue = onNavigateToIssue,
                onOpenPR = onNavigateToPR,
            ) }

            // Contact / extra info
            item { AdditionalInfo(user) }

            // Pinned / top repositories
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.top_repositories), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }

            if (isLoadingRepos && topRepos.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (topRepos.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.profile_no_repos_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(topRepos, key = { it.id }) { repo ->
                    RepoMiniCard(
                        repo = repo,
                        onClick = { onNavigateToRepo(repo.owner.login, repo.name) },
                    )
                }
            }

            // Multi-account section
            if (allAccounts.size > 1) {
                item {
                    Text(
                        stringResource(R.string.accounts),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
                items(allAccounts, key = { it.id }) { account ->
                    AccountRow(
                        account = account,
                        isActive = account.isActive,
                        onSwitch = { vm.switchAccount(account.id) },
                        onRemove = { vm.removeAccount(account.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProfileHeader(user: User?, activeAccount: AccountEntity?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = user?.avatarUrl ?: activeAccount?.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(88.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                user?.name ?: activeAccount?.name ?: "",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "@${user?.login ?: activeAccount?.login ?: ""}",
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
        }
    }
}

@Composable
private fun StatsRow(user: User?, starredTotal: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatPill(stringResource(R.string.followers), user?.followers ?: 0)
        StatPill(stringResource(R.string.following), user?.following ?: 0)
        StatPill(stringResource(R.string.repos), user?.publicRepos ?: 0)
        StatPill(stringResource(R.string.starred), starredTotal)
    }
}

@Composable
private fun StatPill(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
private fun RepoMiniCard(repo: Repository, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
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

@Composable
private fun AccountRow(
    account: AccountEntity,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = account.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(28.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("@${account.login}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        if (isActive) {
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.active)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        } else {
            IconButton(onClick = onSwitch) { Icon(Icons.Outlined.SwapHoriz, contentDescription = stringResource(R.string.action_switch)) }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_remove)) }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                com.pockethub.ui.profile.ProfileViewModel.WorkTab.entries.forEach { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { onSwitchTab(t) },
                        label = { Text(workTabLabel(t), style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.take(8).forEach { issue ->
                        WorkListRow(
                            issue = issue,
                            onClick = {
                                val repo = issue.repository
                                val owner = repo?.owner?.login
                                val name = repo?.name
                                if (owner != null && name != null) {
                                    if (issue.pullRequest != null) onOpenPR(owner, name, issue.number)
                                    else onOpenIssue(owner, name, issue.number)
                                }
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
    val repoFullName = issue.repository?.fullName
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
