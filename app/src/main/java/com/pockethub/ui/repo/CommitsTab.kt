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
import coil.compose.AsyncImage
import com.pockethub.data.remote.GitHubApi
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommitsTab(
    owner: String,
    repo: String,
    vm: CommitsViewModel = hiltViewModel(),
) {
    val commits by vm.commits.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(owner, repo) { vm.loadCommits(owner, repo) }

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
            isLoading && commits.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

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

            commits.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.commit_no_more), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(commits, key = { it.sha }) { commit ->
                    CommitRow(commit = commit)
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
private fun CommitRow(commit: GitHubApi.Commit) {
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()) }

    Column(
        Modifier.fillMaxWidth()
            .clickable { /* TODO: navigate to commit detail */ }
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
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
                    commit.author?.avatarUrl?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        commit.author?.login ?: commit.commit?.author?.name ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        androidx.compose.material3.HorizontalDivider()
    }
}

private fun parseIsoDate(iso: String): Date {
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(iso)
    }.getOrDefault(Date())
}
