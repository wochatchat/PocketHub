package com.pockethub.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pockethub.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

/**
 * Background worker that polls the GitHub notifications endpoint at the user-configured
 * interval and posts system notifications for new unread items.
 *
 * Managed by [NotifScheduler] — callers should never instantiate this directly.
 *
 * Dedup: thread IDs that have already been surfaced are persisted in
 * [SettingsRepository] so the same unread notification doesn't re-alert on every poll.
 */
@HiltWorker
class NotifPollWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val api: GitHubApi,
    private val authInterceptor: AuthInterceptor,
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val sessionBus: SessionEventBus,
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
            // Only alert for threads we haven't surfaced before.
            val alreadyNotified = settings.getNotifiedIds()
            val fresh = unread.filter { it.id !in alreadyNotified }
            if (fresh.isNotEmpty()) {
                ensureChannel()
                postNotification(fresh)
                settings.addNotifiedIds(fresh.map { it.id })
            }
            Result.success()
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 401) {
                // Active token is dead/revoked. Drop it so the next app launch
                // returns to login, and tell a running app to sign out via the bus.
                handleTokenInvalid()
                Result.success()
            } else if (e is HttpException && e.code() in 400..499) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun handleTokenInvalid() {
        val active = accounts.activeAccount.first()
        if (active != null) accounts.removeAccount(active.id)
        authInterceptor.token = ""
        // If the app is in the foreground, its AppStartupViewModel is collecting this
        // bus and will route back to login immediately. From background it's a no-op
        // and the next launch's start-route logic takes over.
        sessionBus.emit(SessionEventBus.Event.TokenInvalid)
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
