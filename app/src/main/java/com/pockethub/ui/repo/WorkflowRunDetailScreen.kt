package com.pockethub.ui.repo

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.pockethub.R
import com.pockethub.data.remote.GitHubApi
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowRunDetailScreen(
    owner: String,
    repo: String,
    runId: Long,
    onBack: () -> Unit,
    vm: WorkflowRunDetailViewModel = hiltViewModel(),
) {
    val run by vm.run.collectAsState()
    val jobs by vm.jobs.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val error by vm.error.collectAsState()
    val actionMessage by vm.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearActionMessage()
        }
    }
    LaunchedEffect(owner, repo, runId) {
        vm.loadRun(owner, repo, runId)
    }

    fun open(url: String?) {
        url ?: return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("#$runId — Actions Run", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { open(run?.htmlUrl) }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = stringResource(R.string.cd_open_in_browser))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (isLoading && run == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (run == null && error != null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.loading_failed), style = MaterialTheme.typography.titleMedium)
                Text(error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { vm.retry(owner, repo, runId) }) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_retry))
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                RunHeaderCard(run = run, dateFmt = dateFmt)
            }

            // Action toolbar
            item {
                val r = run
                val isActive = r?.status == "queued" || r?.status == "in_progress"
                val isCompleted = r?.status == "completed"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (isActive) {
                        OutlinedButton(
                            onClick = { vm.cancelRun() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.StopCircle, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_cancel_run))
                        }
                    }
                    if (isCompleted) {
                        OutlinedButton(
                            onClick = { vm.rerunRun() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.Cached, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.action_rerun_run))
                        }
                    }
                    OutlinedButton(
                        onClick = { open(r?.htmlUrl) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_open_logs))
                    }
                }
            }

            item { HorizontalDivider() }

            item {
                Text(
                    "${jobs.size} jobs",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (jobs.isEmpty() && error != null) {
                item {
                    Text(error ?: "No jobs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(jobs, key = { it.id }) { job ->
                    JobCard(job = job, dateFmt = dateFmt, onOpenLogs = { open(job.htmlUrl) })
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun RunHeaderCard(run: GitHubApi.WorkflowRun?, dateFmt: DateFormat) {
    val r = run ?: return
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(r.name.ifBlank { r.event ?: "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(status = r.status, conclusion = r.conclusion)
            Spacer(Modifier.width(10.dp))
            Text("run #${r.runNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoPill(label = "branch", value = r.headBranch ?: "—")
            Spacer(Modifier.width(10.dp))
            InfoPill(label = "event", value = r.event ?: "—")
        }
        Text(
            "SHA ${r.headSha?.take(7) ?: "—"}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        r.createdAt?.let {
            Text(
                "Triggered ${dateFmt.format(parseIso(it))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JobCard(
    job: GitHubApi.WorkflowJob,
    dateFmt: DateFormat,
    onOpenLogs: () -> Unit,
) {
    var expanded by remember(job.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { expanded = !expanded }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(status = job.status, conclusion = job.conclusion)
            Spacer(Modifier.width(8.dp))
            Text(
                job.name.ifBlank { "job ${job.id}" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onOpenLogs, modifier = Modifier.size(24.dp)) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(16.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            InfoPill(label = "runner", value = job.runnerName ?: "—")
            Spacer(Modifier.width(10.dp))
            job.startedAt?.let {
                Text(
                    "Started ${dateFmt.format(parseIso(it))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            val durationMins = jobDurationMinutes(job)
            if (durationMins > 0) {
                Text(
                    "Duration ${"%.1f".format(durationMins)} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            if (job.steps.isEmpty()) {
                Text("No step metadata", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    job.steps.forEach { step ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StepStatusIcon(status = step.status, conclusion = step.conclusion)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "#${step.number} ${step.name}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                step.conclusion ?: step.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = stepConclusionColor(step.conclusion),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun jobDurationMinutes(job: GitHubApi.WorkflowJob): Double {
    val start = job.startedAt?.let { parseIsoSafe(it) } ?: return 0.0
    val end = job.completedAt?.let { parseIsoSafe(it) ?: return 0.0 }
        ?: Date()
    return (end.time - start.time).toDouble() / 60_000.0
}

@Composable
private fun StatusBadge(status: String?, conclusion: String?) {
    val color = when (conclusion ?: status) {
        "success", "completed" -> Color(0xFF2EA043)
        "failure", "cancelled", "timed_out", "neutral" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val label = conclusion ?: status ?: "—"
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StepStatusIcon(status: String?, conclusion: String?) {
    val (icon, tint) = when (conclusion ?: status) {
        "success" -> Icons.Outlined.CheckCircle to Color(0xFF2EA043)
        "failure", "cancelled" -> Icons.Outlined.Close to MaterialTheme.colorScheme.error
        "skipped", "neutral" -> Icons.Outlined.Pending to MaterialTheme.colorScheme.onSurfaceVariant
        "in_progress", "queued" -> Icons.Outlined.Pending to MaterialTheme.colorScheme.primary
        else -> Icons.Outlined.Pending to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(icon, null, modifier = Modifier.size(12.dp), tint = tint)
}

private fun stepConclusionColor(c: String?): Color {
    return when (c) {
        "success" -> Color(0xFF2EA043)
        "failure", "cancelled", "timed_out" -> Color(0xFFD73A49)
        "skipped", "neutral" -> Color(0xFF959DA5)
        null -> Color(0xFF2188FF)
        else -> Color(0xFF959DA5)
    }
}

private fun parseIso(iso: String): Date {
    return parseIsoSafe(iso) ?: Date()
}

private fun parseIsoSafe(iso: String): Date? = runCatching {
    java.util.Date.from(java.time.OffsetDateTime.parse(iso.trim().replace(" ", "T")).toInstant())
}.getOrNull()

@Composable
private fun InfoPill(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
