package dev.sumdahl.geet.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Download notifications: a live one per running link (the worker's foreground service), and a summary when done. */
@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Downloading", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while songs download"
                setShowBadge(false)
            }
        )
        system.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a song, album or playlist has finished downloading"
            }
        )
    }

    fun progress(job: DownloadJob, current: String?, workId: UUID): ForegroundInfo {
        val finished = job.saved + job.existing + job.failed
        val text = when {
            job.readingTotal > 0 && finished == 0 && current == null -> "Reading ${job.readingDone} of ${job.readingTotal}"
            job.total > 1 -> listOfNotNull("$finished of ${job.total}", current).joinToString(" · ")
            else -> current ?: "Starting…"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_geet)
            .setContentTitle(job.name)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openDownloads())
            .apply {
                if (job.total > 0 && finished < job.total) setProgress(job.total, finished, false) else setProgress(0, 0, true)
            }
            .addAction(0, "Cancel", WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()
        return ForegroundInfo(progressId(job.id), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    fun finished(job: DownloadJob) {
        if (!manager.areNotificationsEnabled()) return
        val (title, text) = summary(job)
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_geet)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openDownloads())
            .build()
        @Suppress("MissingPermission") // checked by areNotificationsEnabled
        manager.notify(doneId(job.id), notification)
    }

    private fun summary(job: DownloadJob): Pair<String, String> {
        val parts = buildList {
            if (job.saved > 0) add("${job.saved} saved")
            if (job.existing > 0) add("${job.existing} already had")
            if (job.failed > 0) add("${job.failed} failed")
        }
        return when (job.state) {
            JobState.Failed ->
                "Couldn't download ${job.name}" to
                    (job.error ?: parts.joinToString(" · ").ifEmpty { "Something went wrong" })
            else -> job.name to parts.joinToString(" · ").ifEmpty { "Saved" }
        }
    }

    private fun openDownloads(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_DESTINATION, DESTINATION_DOWNLOADS)
            ?.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL_PROGRESS = "downloads"
        const val CHANNEL_DONE = "finished"
        const val EXTRA_DESTINATION = "destination"
        const val DESTINATION_DOWNLOADS = "downloads"
        private fun progressId(job: Long) = (job * 2).toInt()
        private fun doneId(job: Long) = (job * 2 + 1).toInt()
    }
}
