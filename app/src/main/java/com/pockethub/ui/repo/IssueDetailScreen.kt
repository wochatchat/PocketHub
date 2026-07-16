package com.pockethub.ui.repo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pockethub.ui.markdown.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    owner: String,
    repo: String,
    issueNumber: Int,
    onBack: () -> Unit,
    vm: IssueDetailViewModel = hiltViewModel(),
) {
    val issue by vm.issue.collectAsState()

    LaunchedEffect(owner, repo, issueNumber) { vm.loadIssue(owner, repo, issueNumber) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("#$issueNumber", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            issue?.let { data ->
                Text(data.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "By ${data.user?.login ?: "unknown"} · ${data.state}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                MarkdownText(
                    markdown = data.body ?: "_No description provided._",
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: CircularProgressIndicator()
        }
    }
}
