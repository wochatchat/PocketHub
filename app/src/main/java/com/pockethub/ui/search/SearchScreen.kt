package com.pockethub.ui.search

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.pockethub.data.model.Repository
import com.pockethub.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun searchTabLabel(tab: SearchTab): String = when (tab) {
    SearchTab.REPOS -> stringResource(R.string.search_tab_repos)
    SearchTab.USERS -> stringResource(R.string.search_tab_users)
    SearchTab.CODE  -> stringResource(R.string.search_tab_code)
}

@Composable
fun SearchScreen(
    initialQuery: String = "",
    onNavigateToRepo: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    // Seed the query from the route argument on first composition.
    LaunchedEffect(Unit) {
        if (initialQuery.isNotBlank() && vm.query.value.isBlank()) {
            vm.query.value = initialQuery
            vm.search()
        }
    }
    val query by vm.query.collectAsState()
    val tab by vm.currentTab.collectAsState()
    val repos by vm.repos.collectAsState()
    val users by vm.users.collectAsState()
    val code by vm.code.collectAsState()
    val isLoading by vm.isLoading.collectAsState()

    Column {
        // Search bar
        TopAppBar(
            title = {
                TextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                    trailingIcon = {
                        IconButton(onClick = { vm.search() }) { Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search)) }
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
            },
        )

        // Tab selector
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            SearchTab.entries.forEachIndexed { idx, current ->
                SegmentedButton(
                    selected = tab == current,
                    onClick = { vm.switchTab(current) },
                    shape = SegmentedButtonDefaults.itemShape(idx, SearchTab.entries.size),
                    label = { Text(searchTabLabel(current)) },
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        when (tab) {
            SearchTab.REPOS -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(repos, key = { it.id }) { repo ->
                    Row(Modifier.fillMaxWidth().clickable { onNavigateToRepo(repo.owner.login, repo.name) }.padding(vertical = 8.dp)) {
                        AsyncImage(model = repo.owner.avatarUrl, contentDescription = null, modifier = Modifier.size(16.dp).clip(CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${repo.owner.login}/${repo.name}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!repo.description.isNullOrBlank()) Text(repo.description!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            SearchTab.USERS -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users, key = { it.login }) { user ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = user.avatarUrl, contentDescription = null, modifier = Modifier.size(24.dp).clip(CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(user.name ?: "@${user.login}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("@${user.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            SearchTab.CODE -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(code) { item ->
                    Column(Modifier.fillMaxWidth().clickable {
                        item.repository?.let { onNavigateToRepo(it.owner.login, it.name) }
                    }.padding(vertical = 8.dp)) {
                        Text(item.path, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.repository?.let { Text("${it.owner.login}/${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
