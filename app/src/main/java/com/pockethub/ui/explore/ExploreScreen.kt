package com.pockethub.ui.explore

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.data.model.Repository

/** Trending language filter chips. */
private val LANGUAGES = listOf("All", "Kotlin", "TypeScript", "Python", "Rust", "Go", "Swift", "Java")
private val TIME_RANGES = listOf("Today", "This week", "This month")

@Composable
fun ExploreScreen(
    modifier: Modifier = Modifier,
    onNavigateToRepo: (String, String) -> Unit,
    vm: ExploreViewModel = hiltViewModel(),
) {
    val trending by vm.trending.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var selectedLang by remember { mutableStateOf("All") }
    var selectedRange by remember { mutableStateOf("Today") }

    LaunchedEffect(selectedLang, selectedRange) {
        val dateRange = when (selectedRange) {
            "This week" -> "created:>2026-07-09"
            "This month" -> "created:>2026-06-16"
            else -> "created:>2026-07-15"
        }
        val q = if (selectedLang == "All") "stars:>100 $dateRange"
               else "language:${selectedLang.lowercase()} stars:>50 $dateRange"
        vm.loadTrending(q)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Language filter chips
        item {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LANGUAGES.size) { idx ->
                    FilterChip(
                        selected = selectedLang == LANGUAGES[idx],
                        onClick = { selectedLang = LANGUAGES[idx] },
                        label = { Text(LANGUAGES[idx], style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = if (selectedLang == LANGUAGES[idx]) {{ CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) }} else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }

        // Time range chips
        item {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TIME_RANGES.size) { idx ->
                    FilterChip(
                        selected = selectedRange == TIME_RANGES[idx],
                        onClick = { selectedRange = TIME_RANGES[idx] },
                        label = { Text(TIME_RANGES[idx], style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        items(trending, key = { it.id }) { repo ->
            TrendingRepoCard(repo = repo, onClick = { onNavigateToRepo(repo.owner.login, repo.name) })
        }

        // Footer
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Showing ${trending.size} trending repositories",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun TrendingRepoCard(
    repo: Repository,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = repo.owner.avatarUrl,
                    contentDescription = repo.owner.login,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = repo.owner.login,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(" / ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (!repo.description.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Language dot
                if (repo.language != null) {
                    Box(Modifier.size(10.dp).clip(CircleShape).then(
                        Modifier.padding(0.dp) // color set in actual impl
                    ))
                    Text(repo.language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                }
                // Stars
                Icon(androidx.compose.material.icons.Icons.Outlined.Star, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text("${repo.stars}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                // Forks
                Icon(Icons.Outlined.StarOutline, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${repo.forks}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                // License
                repo.license?.let { lic ->
                    Icon(Icons.Filled.Language, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(lic.spdxId ?: lic.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
