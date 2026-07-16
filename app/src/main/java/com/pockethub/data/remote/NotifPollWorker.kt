package com.pockethub.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pockethub.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background worker that polls the GitHub notifications endpoint at the user-configured
 * interval and posts system notifications for new unread items.
 *
 * Managed by [NotifScheduler] — callers should never instantiate this directly.
 */
@HiltWorker
class NotifPollWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val api: GitHubApi,
    private val authInterceptor: AuthInterceptor,
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "notif_poll"
        const val CHANNEL_ID = "pockethub_notif"
        private const val NOTIFICATION_BASE_ID = 9001
    }

    override suspend fun doWork(): Result {
        if (authInterceptor.token.isBlank()) return Result.success()

        return try {
            val notifs = api.getNotifications(perPage = 50, all = false)
            val unread = notifs.filter { it.unread }
            if (unread.isNotEmpty()) {
                ensureChannel()
                postNotification(unread)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GitHub Notifications",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "New GitHub notifications from PocketHub"
        }
        nm.createNotificationChannel(channel)
    }

    private fun postNotification(unread: List<com.pockethub.data.model.GitHubNotification>) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        // Group by repository and build summary
        val grouped = unread.groupBy { it.repository?.fullName ?: "GitHub" }
        val repoCount = grouped.size
        val totalCount = unread.size

        // Tap action → open app to notifications screen
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val inboxStyle = NotificationCompat.InboxStyle()
        grouped.forEach { (repo, items) ->
            items.take(5).forEach { notif ->
                inboxStyle.addLine("${repo}: ${notif.subject.title}")
            }
        }
        inboxStyle.setSummaryText("$repoCount repo${if (repoCount == 1) "" else "s"} · $totalCount notification${if (totalCount == 1) "" else "s"}")

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PocketHub")
            .setContentText("$totalCount new notification${if (totalCount == 1) "" else "s"} from $repoCount repo${if (repoCount == 1) "" else "s"}")
            .setStyle(inboxStyle)
            .setNumber(totalCount)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup("pockethub_notifs")
            .build()

        nm.notify(NOTIFICATION_BASE_ID, notif)
    }
}
