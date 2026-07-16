package com.pockethub.data.remote

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules or cancels the [NotifPollWorker] based on the user's polling interval setting.
 *
 * Called from [SettingsViewModel] when the user changes the cadence, and from
 * [AppStartupViewModel] on launch so the worker is always in sync with the persisted setting.
 */
@Singleton
class NotifScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Schedule (or cancel) the periodic notification poll according to the current setting.
     *
     * @param minutes 0 = disabled, 15+ = periodic interval in minutes.
     */
    fun schedule(minutes: Int) {
        workManager.cancelUniqueWork(NotifPollWorker.WORK_NAME)

        if (minutes < 15) return  // disabled or too frequent

        val request = PeriodicWorkRequestBuilder<NotifPollWorker>(
            minutes.toLong(), TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()

        workManager.enqueueUniquePeriodicWork(
            NotifPollWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
