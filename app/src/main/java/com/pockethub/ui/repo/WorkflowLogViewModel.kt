package com.pockethub.ui.repo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pockethub.data.remote.GitHubApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime
import javax.inject.Inject

/**
 * Max log text kept in memory (~2MB). Larger logs are tail-truncated with a
 * banner — full raw output stays reachable via share/download.
 */
private const val MAX_LOG_CHARS = 2_000_000

/**
 * A job-log line starts with a UTC timestamp,
 * e.g. "2026-09-01T07:07:53.6688726Z Current runner version: …".
 */
private val LOG_TS = Regex("^(\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z)")

/**
 * One collapsible step section of a job log. [startLine]/[endLine] are indices
 * into [ParsedLog.lines] (end exclusive).
 */
data class LogSection(
    val number: Int,
    val name: String,
    val status: String?,
    val conclusion: String?,
    val startLine: Int,
    val endLine: Int,
    val durationMs: Long?,
)

data class ParsedLog(
    /** Original text (≤[MAX_LOG_CHARS] chars) — used by share/copy/download. */
    val raw: String,
    /** Display lines: raw lines with the timestamp prefix stripped. */
    val lines: List<String>,
    /** Per-line epoch millis; carried forward for lines without a timestamp. */
    val epochMs: List<Long>,
    val sections: List<LogSection>,
    val truncated: Boolean,
)

/** Thrown when the job has no log (queued, expired retention, fork-run not approved). */
private class JobLogUnavailable : Exception()

@HiltViewModel
class WorkflowLogViewModel @Inject constructor(
    private val api: GitHubApi,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val owner: String = savedStateHandle["owner"] ?: ""
    private val repo: String = savedStateHandle["repo"] ?: ""
    private val runId: Long = savedStateHandle["runId"] ?: 0L
    val initialJobId: Long = savedStateHandle["jobId"] ?: 0L

    /**
     * Bare client for the pre-signed blob URL. The DI client attaches a GitHub
     * Bearer token to every host (AuthInterceptor) — Azure blob storage rejects
     * signed URLs that carry a foreign Authorization header.
     */
    private val http = OkHttpClient()

    data class UiState(
        val jobs: List<GitHubApi.WorkflowJob> = emptyList(),
        val jobId: Long = 0L,
        val parsed: ParsedLog? = null,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val error: String? = null,
        val notFound: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(jobId = initialJobId))
    val state = _state.asStateFlow()

    /** Expanded step numbers — kept in the VM so it survives rotation. */
    val expandedSteps = MutableStateFlow<Set<Int>>(emptySet())

    fun toggleStep(number: Int) {
        expandedSteps.value = if (number in expandedSteps.value) expandedSteps.value - number else expandedSteps.value + number
    }

    fun setExpanded(numbers: Set<Int>) {
        expandedSteps.value = numbers
    }

    fun currentJob(): GitHubApi.WorkflowJob? = _state.value.jobs.firstOrNull { it.id == _state.value.jobId }

    init {
        load(initialJobId)
    }

    /** Load (or silently refresh) one job's log. Also refreshes the jobs list so
     *  statuses stay current while live-polling an in-progress job. */
    fun load(jobId: Long, silent: Boolean = false) = viewModelScope.launch {
        _state.update {
            it.copy(
                loading = !silent,
                refreshing = silent && it.parsed != null,
                error = null,
                notFound = false,
                jobId = jobId,
            )
        }
        try {
            val jobs = runCatching { api.getWorkflowRunJobs(owner, repo, runId).jobs }
                .getOrDefault(_state.value.jobs)
            val job = jobs.firstOrNull { it.id == jobId }
            val parsed = job?.let { fetchLog(it) }
            _state.update {
                it.copy(
                    jobs = jobs,
                    loading = false,
                    refreshing = false,
                    parsed = parsed,
                    notFound = parsed == null,
                )
            }
        } catch (_: JobLogUnavailable) {
            // Queued job (no log yet) or expired retention — the live-tail
            // polling keeps retrying while the job is unfinished.
            _state.update { it.copy(loading = false, refreshing = false, parsed = null, notFound = true) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, refreshing = false, error = e.message ?: "error") }
        }
    }

    fun refresh() = load(_state.value.jobId, silent = true)

    /** Fresh signed URL for download — the earlier one may be near expiry. */
    suspend fun freshLocation(): String? = withContext(Dispatchers.IO) {
        runCatching {
            api.getWorkflowJobLogsUrl(owner, repo, _state.value.jobId).headers()["Location"]
        }.getOrNull()
    }

    private suspend fun fetchLog(job: GitHubApi.WorkflowJob): ParsedLog? = withContext(Dispatchers.IO) {
        val resp = api.getWorkflowJobLogsUrl(owner, repo, job.id)
        val location = resp.headers()["Location"]
        if (resp.code() !in 300..399 || location.isNullOrBlank()) throw JobLogUnavailable()
        val (text, truncated) = download(location)
        parseLog(text, truncated, job)
    }

    private fun download(location: String): Pair<String, Boolean> {
        val req = Request.Builder().url(location).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw JobLogUnavailable()
            val body = resp.body ?: throw JobLogUnavailable()
            val sb = StringBuilder()
            val buf = CharArray(16_384)
            var total = 0
            var truncated = false
            body.charStream().use { reader ->
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    if (total + n > MAX_LOG_CHARS) {
                        sb.append(buf, 0, MAX_LOG_CHARS - total)
                        truncated = true
                        break
                    }
                    sb.append(buf, 0, n)
                    total += n
                }
            }
            return sb.toString() to truncated
        }
    }

    companion object {
        /**
         * Slice a raw job log into per-step sections. GitHub has no per-step log
         * endpoint — the web UI slices the same job log client-side; we do the
         * same by anchoring each step at the first log line whose timestamp is
         * ≥ the step's started_at (lines carry their own UTC timestamp prefix).
         * Lines with no timestamp stick to the previous anchor.
         */
        fun parseLog(raw: String, truncated: Boolean, job: GitHubApi.WorkflowJob): ParsedLog {
            val rawLines = raw.split("\n").map { it.removeSuffix("\r") }
            val epoch = ArrayList<Long>(rawLines.size)
            var last = 0L
            for (line in rawLines) {
                val m = LOG_TS.find(line)
                val parsed = m?.let {
                    runCatching { OffsetDateTime.parse(it.groupValues[1]).toInstant().toEpochMilli() }.getOrNull()
                }
                if (parsed != null) last = parsed
                epoch.add(last)
            }
            val display = rawLines.map { line ->
                val m = LOG_TS.find(line) ?: return@map line.removePrefix("﻿")
                line.substring(m.value.length)
            }

            val steps = job.steps
            val sections = ArrayList<LogSection>(steps.size + 1)
            if (steps.isEmpty()) {
                sections.add(LogSection(-1, "", job.status, job.conclusion, 0, display.size, jobDuration(job)))
            } else {
                // Anchors: first line at/after each step's started_at. Steps
                // without started_at (queued/skipped) become empty sections so
                // the timeline still shows them.
                val boundaries = ArrayList<Int>(steps.size)
                var cursor = 0
                for (step in steps) {
                    val startMs = step.startedAt?.let { ms(it) } ?: -1L
                    if (startMs < 0) {
                        boundaries.add(cursor)
                        continue
                    }
                    var i = cursor
                    while (i < epoch.size && epoch[i] < startMs) i++
                    cursor = i
                    boundaries.add(cursor)
                }
                for ((idx, step) in steps.withIndex()) {
                    val start = if (idx == 0) 0 else boundaries[idx]
                    val end = if (idx + 1 < steps.size) maxOf(start, boundaries[idx + 1]) else maxOf(start, display.size)
                    val duration = if (step.startedAt != null && step.completedAt != null) {
                        (ms(step.completedAt) - ms(step.startedAt)).coerceAtLeast(0)
                    } else null
                    sections.add(
                        LogSection(
                            number = step.number,
                            name = step.name,
                            status = step.status,
                            conclusion = step.conclusion,
                            startLine = start,
                            endLine = end,
                            durationMs = duration,
                        )
                    )
                }
            }
            return ParsedLog(raw, display, epoch, sections, truncated)
        }

        private fun ms(iso: String): Long =
            runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(0L)

        private fun jobDuration(job: GitHubApi.WorkflowJob): Long? =
            if (job.startedAt != null && job.completedAt != null) (ms(job.completedAt) - ms(job.startedAt)).coerceAtLeast(0) else null

        /** "12s" / "3m 05s" / "1h 04m" — compact for chips and headers. */
        fun formatMs(ms: Long?): String {
            if (ms == null) return ""
            val s = ms / 1000
            return when {
                s < 60 -> "${s}s"
                s < 3600 -> "${s / 60}m ${String.format("%02d", s % 60)}s"
                else -> "${s / 3600}h ${String.format("%02d", (s % 3600) / 60)}m"
            }
        }
    }
}
