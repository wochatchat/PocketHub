package com.pockethub.data.reporting

import android.content.Context
import android.os.Build
import android.os.Looper
import com.pockethub.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private val reporterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Severe-issue logging facility for PocketHub.
 *
 * Captures only critical events:
 * - JVM uncaught exceptions (crashes)
 * - ANR (Application Not Responding) detected via main-thread heartbeat watch-dog
 *
 * Events are persisted as a ring of JSON lines in [logFile] (max [MAX_EVENTS] entries).
 * Anything finer than Throwable or ANR is intentionally NOT captured —
 * this is a quality alarm, not a usage log.
 */
@Singleton
class IssueReporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _events = MutableSharedFlow<IssueEvent>(extraBufferCapacity = 32)
    /** Observers can subscribe to live severe events (rarely useful for UI, mainly for tests). */
    val events: SharedFlow<IssueEvent> = _events.asSharedFlow()
    private val mutex = Mutex()
    private val installedCrashHook = AtomicBoolean(false)

    /** What the user was doing right before the event: screens visited and
     *  network calls made, newest last. Kept in memory only; attached to
     *  crash/ANR events so each digest entry answers "where did this happen
     *  and what had the app just done?". */
    private val breadcrumbs = java.util.concurrent.CopyOnWriteArrayList<String>()

    fun breadcrumb(message: String) {
        val ts = formatter.format(Date())
        breadcrumbs.add("$ts $message")
        while (breadcrumbs.size > MAX_BREADCRUMBS) breadcrumbs.removeAt(0)
    }

    private fun breadcrumbsSnapshot(): String = breadcrumbs.joinToString("\n")
    private val watchDogRunning = AtomicBoolean(false)
    /** Current main-thread heartbeat runnable; null once the watchdog stops. */
    @Volatile private var anrTicker: Runnable? = null
    private val heartbeatMs = AtomicLong(System.currentTimeMillis())
    private val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    private val logFile: File get() = File(context.filesDir, "issue_log.jsonl")

    /**
     * Install the global uncaught-exception handler and ANR watch-dog.
     * Safe to call multiple times — guards prevent double-install. Should
     * be called once from [com.pockethub.PocketHubApp.onCreate].
     *
     * Also prunes any legacy `error`-kind entries from earlier builds (the
     * digest now only carries crash/ANR), so the settings counter reflects
     * the current policy after upgrade.
     */
    fun install() {
        pruneLegacyErrors()
        installCrashHook()
        installAnrWatchDog()
    }

    /**
     * Diagnostic hook from catch blocks. By default it does NOT record
     * anything — the severe-issues digest only carries crashes and ANRs.
     * Expected failures (offline, 404, rate limits…) were flooding the
     * digest and drowned the signal, so regular exceptions are logged to
     * logcat only. Pass [severe] = true for a failure that genuinely
     * corrupts app state and cannot be surfaced in the UI.
     */
    fun reportError(
        screen: String,
        where: String,
        error: Throwable?,
        extra: Map<String, String> = emptyMap(),
        severe: Boolean = false,
    ) {
        if (!severe) {
            android.util.Log.d("IssueReporter", "non-severe $screen/$where: $error")
            return
        }
        reporterScope.launch {
            runCatching {
                report(
                    kind = IssueKind.ERROR,
                    subject = "[$screen] $where: ${error?.javaClass?.simpleName ?: "Error"}${error?.message?.let { ": $it" } ?: ""}",
                    stackTrace = error?.stackTraceToString() ?: "",
                    extra = extra,
                )
            }
        }
    }

    /**
     * Emit a custom severe event (e.g. ANR detected by the watch-dog, or
     * a critical state from app code that callers want persisted).
     */
    suspend fun report(
        kind: IssueKind,
        subject: String,
        stackTrace: String = "",
        threadName: String = Thread.currentThread().name,
        extra: Map<String, String> = emptyMap(),
    ) {
        val event = IssueEvent(
            kind = kind,
            ts = System.currentTimeMillis(),
            isoTs = formatter.format(Date()),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            appVariant = BuildConfig.BUILD_TYPE,
            sdkInt = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            subject = subject.take(MAX_SUBJECT),
            stackTrace = stackTrace.take(MAX_STACK),
            threadName = threadName,
            extra = extra,
        )
        appendToLogFile(event)
        _events.tryEmit(event)
    }

    /** Read all persisted events, oldest first. The ring buffer cap still applies. */
    suspend fun readLog(): List<IssueEvent> = mutex.withLock {
        if (!logFile.exists()) return@withLock emptyList()
        runCatching {
            logFile.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> runCatching {
                    val obj = JSONObject(line)
                    IssueEvent(
                        kind = IssueKind.from(obj.optString("kind")),
                        ts = obj.optLong("ts"),
                        isoTs = obj.optString("isoTs"),
                        appVersionName = obj.optString("appVersionName"),
                        appVersionCode = obj.optInt("appVersionCode"),
                        appVariant = obj.optString("appVariant"),
                        sdkInt = obj.optInt("sdkInt"),
                        deviceModel = obj.optString("deviceModel"),
                        subject = obj.optString("subject"),
                        stackTrace = obj.optString("stackTrace"),
                        threadName = obj.optString("threadName"),
                        extra = runCatching {
                            val arr = obj.optJSONArray("extra")
                            if (arr != null && arr.length() > 0) {
                                (0 until arr.length()).associate {
                                    arr.getJSONObject(it).optString("k") to arr.getJSONObject(it).optString("v")
                                }
                            } else emptyMap()
                        }.getOrDefault(emptyMap()),
                    )
                }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    /** Wipe the persisted log. Called by the report-worker after a successful send. */
    suspend fun clearLog() = mutex.withLock {
        runCatching { logFile.delete() }
        Unit
    }

    private fun installCrashHook() {
        if (!installedCrashHook.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            // Persist synchronously — process is about to die.
            val stack = runCatching {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                sw.toString()
            }.getOrDefault(e.toString())
            try {
                val event = IssueEvent(
                    kind = IssueKind.CRASH,
                    ts = System.currentTimeMillis(),
                    isoTs = formatter.format(Date()),
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    appVariant = BuildConfig.BUILD_TYPE,
                    sdkInt = Build.VERSION.SDK_INT,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    subject = "${e.javaClass.name}: ${e.message ?: "<no message>"}",
                    stackTrace = stack.take(MAX_STACK),
                    threadName = t.name,
                    extra = mapOf(BKEY_BREADCRUMBS to breadcrumbsSnapshot()),
                )
                appendToFileSync(event)
            } catch (_: Throwable) { /* never let the hook throw while already crashing */ }
            previous?.uncaughtException(t, e)
        }
    }

    private fun installAnrWatchDog() {
        if (!watchDogRunning.compareAndSet(false, true)) return
        // Watch-dog thread: pings the main looper every [HEARTBEAT_TICK_MS];
        // if the main thread hasn't broadcast a tick in [ANR_THRESHOLD_MS],
        // emit an ANR event.
        val mainThread = Looper.getMainLooper().thread
        reporterScope.launch {
            // Heartbeat ticker on the main thread. Re-posts itself ONLY while
            // [anrTicker] still points at it — a cancelled watchdog coroutine
            // stops the chain instead of leaving a runnable waking the main
            // thread every 2s for the rest of the process (idle battery drain).
            val ticker = object : Runnable {
                override fun run() {
                    heartbeatMs.set(System.currentTimeMillis())
                    anrTicker?.let { mainThreadHandler.postDelayed(it, HEARTBEAT_TICK_MS) }
                }
            }
            anrTicker = ticker
            mainThreadHandler.postDelayed(ticker, HEARTBEAT_TICK_MS)
            try {
                while (true) {
                    kotlinx.coroutines.delay(HEARTBEAT_TICK_MS)
                    val last = heartbeatMs.get()
                    val lag = System.currentTimeMillis() - last
                    if (lag > ANR_THRESHOLD_MS) {
                        // Build a synthetic stack trace of the main thread for diagnostics.
                        val mainStack = mainThread.stackTrace
                            .joinToString("\n")
                            .take(MAX_STACK)
                        report(
                            kind = IssueKind.ANR,
                            subject = "Main thread blocked for ${lag}ms (threshold ${ANR_THRESHOLD_MS}ms)",
                            stackTrace = mainStack,
                            threadName = mainThread.name,
                            extra = mapOf(
                                "threadState" to mainThread.state.name,
                                "lagMs" to lag.toString(),
                                BKEY_BREADCRUMBS to breadcrumbsSnapshot(),
                            ),
                        )
                        // Back-off so we don't emit a flood of dupes for the same stall.
                        kotlinx.coroutines.delay(ANR_COOLDOWN_MS)
                        heartbeatMs.set(System.currentTimeMillis())
                    }
                }
            } finally {
                // Watchdog shut down (scope cancelled): stop the main-thread
                // heartbeat chain and allow a future reinstall.
                anrTicker = null
                mainThreadHandler.removeCallbacks(ticker)
                watchDogRunning.set(false)
            }
        }
    }

    private val mainThreadHandler by lazy {
        android.os.Handler(Looper.getMainLooper())
    }

    /**
     * Drop pre-policy `error` entries so the digest is crash/ANR-only.
     *
     * Runs ONCE per install (flag in SharedPreferences): without the gate,
     * every process start wiped ALL `error` entries — including severe ones
     * written by the previous session via [reportError] — so a severe error
     * never survived a restart (issue #13 regression window).
     */
    private fun pruneLegacyErrors() {
        val prefs = context.getSharedPreferences("issue_reporter", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_ERRORS_PRUNED, false)) return
        reporterScope.launch {
            runCatching {
                mutex.withLock {
                    if (logFile.exists()) {
                        val lines = logFile.readLines()
                        val kept = lines.filter { line ->
                            line.isBlank() || runCatching {
                                JSONObject(line).optString("kind") != IssueKind.ERROR.id
                            }.getOrDefault(true)
                        }
                        when {
                            kept.isEmpty() -> logFile.delete()
                            kept.size != lines.size -> logFile.writeText(kept.joinToString("\n") + "\n")
                        }
                    }
                    // Set only after a successful pass — a crashed prune retries next start.
                    prefs.edit().putBoolean(KEY_LEGACY_ERRORS_PRUNED, true).apply()
                }
            }
        }
    }

    private suspend fun appendToLogFile(event: IssueEvent) = mutex.withLock {
        appendToFileSync(event)
    }

    @Synchronized
    private fun appendToFileSync(event: IssueEvent) {
        if (!logFile.parentFile.exists()) logFile.parentFile.mkdirs()
        val jsonObject = JSONObject().apply {
            put("kind", event.kind.id)
            put("ts", event.ts)
            put("isoTs", event.isoTs)
            put("appVersionName", event.appVersionName)
            put("appVersionCode", event.appVersionCode)
            put("appVariant", event.appVariant)
            put("sdkInt", event.sdkInt)
            put("deviceModel", event.deviceModel)
            put("subject", event.subject)
            put("stackTrace", event.stackTrace)
            put("threadName", event.threadName)
            if (event.extra.isNotEmpty()) {
                val arr = JSONArray()
                event.extra.forEach { (k, v) -> arr.put(JSONObject().put("k", k).put("v", v)) }
                put("extra", arr)
            }
        }
        logFile.appendText(jsonObject.toString() + "\n")
        trimRingBuffer()
    }

    /** Trim oldest lines until we're at [MAX_EVENTS]. */
    private fun trimRingBuffer() {
        val lines = logFile.readLines()
        if (lines.size > MAX_EVENTS) {
            logFile.writeText(lines.takeLast(MAX_EVENTS).joinToString("\n") + "\n")
        }
    }

    companion object {
        /** extra[] key carrying the breadcrumb trail of a crash/ANR event. */
        const val BKEY_BREADCRUMBS = "breadcrumbs"
        private const val MAX_BREADCRUMBS = 20
        private const val MAX_EVENTS = 200
        private const val MAX_SUBJECT = 400
        private const val MAX_STACK = 8_000
        private const val HEARTBEAT_TICK_MS = 2_000L
        private const val ANR_THRESHOLD_MS = 5_000L
        private const val ANR_COOLDOWN_MS = 15_000L
        private const val KEY_LEGACY_ERRORS_PRUNED = "legacy_errors_pruned"
    }
}

/** Issue type. [id] is the value stored in the JSON log line. */
enum class IssueKind(val id: String) {
    CRASH("crash"),
    ANR("anr"),
    ERROR("error");
    companion object {
        fun from(id: String?): IssueKind = entries.firstOrNull { it.id == id } ?: ERROR
    }
}

/** Persistent record of one severe event. */
@Serializable
data class IssueEvent(
    @SerialName("kind") val kind: IssueKind,
    @SerialName("ts") val ts: Long,
    @SerialName("isoTs") val isoTs: String,
    @SerialName("appVersionName") val appVersionName: String,
    @SerialName("appVersionCode") val appVersionCode: Int,
    @SerialName("appVariant") val appVariant: String,
    @SerialName("sdkInt") val sdkInt: Int,
    @SerialName("deviceModel") val deviceModel: String,
    @SerialName("subject") val subject: String,
    @SerialName("stackTrace") val stackTrace: String,
    @SerialName("threadName") val threadName: String,
    @SerialName("extra") val extra: Map<String, String> = emptyMap(),
)
