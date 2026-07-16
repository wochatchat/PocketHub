package com.pockethub.ui.repos

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.model.Repository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val FILTERS = listOf(
    RepoFilter.ALL, RepoFilter.OWNER, RepoFilter.MEMBER,
    RepoFilter.PUBLIC, RepoFilter.PRIVATE, RepoFilter.FORKS,
)

@Composable
fun ReposScreen(
    modifier: Modifier = Modifier,
    onNavigateToRepo: (String, String) -> Unit,
    vm: ReposViewModel = hiltViewModel(),
) {
    val repos by vm.repos.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val tab by vm.currentTab.collectAsState()
    val filter by vm.currentFilter.collectAsState()
    val listState = rememberLazyListState()

    // Infinite scroll
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore()
    }

    Column(modifier) {
        // Tab selector
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SegmentedButton(selected = tab == RepoTab.MINE, onClick = { vm.switchTab(RepoTab.MINE) },
                shape = SegmentedButtonDefaults.itemShape(0, 2), label = { Text("My Repos") })
            SegmentedButton(selected = tab == RepoTab.STARRED, onClick = { vm.switchTab(RepoTab.STARRED) },
                shape = SegmentedButtonDefaults.itemShape(1, 2), label = { Text("Starred") })
        }

        // Filter chips
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FILTERS.size) { idx ->
                FilterChip(
                    selected = filter == FILTERS[idx],
                    onClick = { vm.setFilter(FILTERS[idx]) },
                    label = { Text(FILTERS[idx].name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(repos, key = { it.id }) { repo ->
                RepoCard(repo = repo, onClick = { onNavigateToRepo(repo.owner.login, repo.name) })
            }
            if (isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
        }
    }
}

@Composable
private fun RepoCard(repo: Repository, onClick: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).apply { timeZone = TimeZone.getDefault() } }
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    ) {
        // Repo name
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = repo.owner.avatarUrl, contentDescription = null, modifier = Modifier.size(16.dp).clip(CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(repo.owner.login, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(" / ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(repo.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            if (repo.private) {
                Spacer(Modifier.width(6.dp))
                Text("Private", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Description
        if (!repo.description.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(repo.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        // Stats row
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (repo.language != null) {
                val langColor = remember(repo.language) { languageColorHex(repo.language)?.let { parseColor(it) } ?: MaterialTheme.colorScheme.outline }
                Box(Modifier.size(8.dp).clip(CircleShape).background(langColor))
                Text(" ${repo.language} ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.Star, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(" ${repo.stars}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text("Forks: ${repo.forks}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text("Issues: ${repo.openIssues}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            repo.pushedAt?.let { Text("Updated ${it.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun languageColorHex(language: String): String? = when (language.lowercase()) {
    "kotlin" -> "#A97BFF"; "typescript" -> "#3178C6"; "python" -> "#3572A5"
    "rust" -> "#DEA584"; "go" -> "#00ADD8"; "swift" -> "#F05138"
    "java" -> "#B07219"; "c++" -> "#F34B7D"; "c" -> "#555555"
    "c#" -> "#178600"; "javascript" -> "#F1E05A"; "html" -> "#E34C26"
    "css" -> "#563D7C"; "php" -> "#4F5D95"; "ruby" -> "#701516"
    "dart" -> "#00B4AB"; "shell" -> "#89E051"; else -> null
}

private fun parseColor(hex: String): androidx.compose.ui.graphics.Color {
    val v = hex.removePrefix("#").toLong(16)
    return androidx.compose.ui.graphics.Color(v or 0xFF000000.toInt())
}

