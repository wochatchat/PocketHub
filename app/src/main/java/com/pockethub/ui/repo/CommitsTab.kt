package com.pockethub.ui.repo

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.data.remote.GitHubApi
import com.pockethub.ui.components.PhAsyncImage
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommitsTab(
    owner: String,
    repo: String,
    refreshTick: Int = 0,
    ref: String? = null,
    onNavigateToUser: (String) -> Unit = {},
    onCommitClick: (String) -> Unit = {},
    vm: CommitsViewModel = hiltViewModel(),
) {
    val commits by vm.commits.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(owner, repo, ref) { vm.loadCommits(owner, repo, ref) }
    // Pull-to-refresh on the repo detail screen bumps [refreshTick]; re-fetch so
    // the commit list actually updates (previously the spinner spun but this
    // list never reloaded — fake refresh).
    LaunchedEffect(refreshTick) { if (refreshTick > 0 && owner.isNotBlank()) vm.refresh(owner, repo, ref) }

    // Infinite scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoading) vm.loadMore(owner, repo)
    }

    Column(Modifier.fillMaxSize()) {
        when {
            isLoading && commits.isEmpty() -> com.pockethub.ui.components.SkeletonList(
                Modifier.fillMaxSize(), rows = 9, topPadding = 8.dp,
            )

            error != null && commits.isEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(error ?: stringResource(R.string.error_load_files), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { vm.refresh(owner, repo) }) {
                    Text(stringResource(R.string.action_retry))
                }
            }

            commits.isEmpty() -> com.pockethub.ui.components.EmptyStateV2(
                icon = Icons.Outlined.ListAlt,
                title = stringResource(R.string.commit_no_more),
            )

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(commits, key = { it.sha }) { commit ->
                    CommitRow(commit = commit, onNavigateToUser = onNavigateToUser, onClick = { onCommitClick(commit.sha) })
                }
                if (isLoading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitRow(
    commit: GitHubApi.Commit,
    onNavigateToUser: (String) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()) }
    // GitHub returns author = {} (empty object) for commits whose author
    // account was deleted/hidden — treat a blank login like "no GitHub user"
    // and fall back to the raw git author name.
    val authorLogin = commit.author?.login?.takeIf { it.isNotBlank() }
    val authorClick = authorLogin?.let { Modifier.clickable { onNavigateToUser(it) } } ?: Modifier

    com.pockethub.ui.components.PhCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        onClick = onClick,
        cornerRadius = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // SHA short
            Text(
                text = commit.sha.take(7),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(56.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                val message = commit.commit?.message ?: ""
                val firstLine = message.substringBefore("\n")
                Text(
                    firstLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val avatarUrl = commit.author?.avatarUrl
                    if (avatarUrl != null) {
                        PhAsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).clip(CircleShape).then(authorClick),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        authorLogin ?: commit.commit?.author?.name ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = authorClick,
                    )
                    Spacer(Modifier.width(8.dp))
                    commit.commit?.author?.date?.let { dateStr ->
                        Text(
                            dateFmt.format(parseIsoDate(dateStr)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun parseIsoDate(iso: String): Date {
    return runCatching {
        java.util.Date.from(java.time.OffsetDateTime.parse(iso.trim().replace(" ", "T")).toInstant())
    }.getOrDefault(Date())
}
