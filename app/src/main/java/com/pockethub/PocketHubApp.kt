package com.pockethub

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pockethub.data.reporting.IssueReportScheduler
import com.pockethub.data.reporting.IssueReporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Provides WorkManager configuration using [HiltWorkerFactory]
 * so that [com.pockethub.data.remote.NotifPollWorker] and
 * [com.pockethub.data.reporting.IssueReportWorker] can receive injected dependencies.
 *
 * Also bootstraps the severe-issue reporter (crash + ANR hooks) immediately on
 * startup so we capture early crashes too, and reschedules the periodic email
 * report worker to keep alarms in sync with persisted settings.
 */
@HiltAndroidApp
class PocketHubApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var issueReporter: IssueReporter
    @Inject lateinit var issueReportScheduler: IssueReportScheduler
    @Inject lateinit var cacheDao: com.pockethub.data.local.CacheDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Install the crash + ANR watch-dog as the very first thing, so any
        // crash during the rest of onCreate is itself captured.
        issueReporter.install()
        // Reschedule issue report work from persisted settings.
        appScope.launch { issueReportScheduler.rescheduleFromSettings() }
        // Startup housekeeping: the API-response cache had no eviction path and
        // grew unbounded — drop entries older than 7 days on each launch.
        appScope.launch {
            runCatching {
                cacheDao.evictOlderThan(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
            }
        }
    }
}
