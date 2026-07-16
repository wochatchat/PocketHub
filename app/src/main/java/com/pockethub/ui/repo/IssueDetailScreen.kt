package com.pockethub.ui.repo

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.pockethub.ui.markdown.MarkdownText
import java.text.SimpleDateFormat
import java.util.Locale

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
    val comments by vm.comments.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH) }

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
        if (isLoading && issue == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (issue == null && error != null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("加载失败", style = MaterialTheme.typography.titleMedium)
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.retry(owner, repo, issueNumber) }) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重试")
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            issue?.let { data ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(data.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val stateColor = if (data.state == "open") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    Box(
                        Modifier.clip(CircleShape)
                            .background(stateColor.copy(alpha = 0.12f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(data.state, style = MaterialTheme.typography.labelSmall, color = stateColor)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    data.user?.avatarUrl?.let {
                        AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        "${data.user?.login ?: "unknown"} · ${data.createdAt?.let { dateFmt.format(parseIso(it)) } ?: ""} · ${data.comments} comments",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!data.labels.isEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.labels.take(5).forEach { label ->
                            val bg = runCatching { androidx.compose.ui.graphics.Color(("FF" + (label.color ?: "888888")).toLong(16)) }.getOrDefault(MaterialTheme.colorScheme.secondaryContainer)
                            Text(
                                label.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    .background(bg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                MarkdownText(
                    markdown = data.body ?: "_No description provided._",
                    modifier = Modifier.fillMaxWidth(),
                )

                // Comments section
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Text("Comments (${comments.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (comments.isEmpty() && data.comments > 0) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else if (comments.isEmpty()) {
                    Text("No comments yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    comments.forEach { c ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                c.user?.avatarUrl?.let {
                                    AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(c.user?.login ?: "unknown", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(8.dp))
                                c.createdAt?.let {
                                    Text(dateFmt.format(parseIso(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            MarkdownText(
                                markdown = c.body.ifBlank { "_No content_" },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

/** Parse an ISO-8601 timestamp into a Date for SimpleDateFormat. */
private fun parseIso(iso: String): java.util.Date {
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(iso)
    }.getOrDefault(java.util.Date())
}
