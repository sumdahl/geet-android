package dev.sumdahl.geet.data

import android.content.Context
import android.media.MediaScannerConnection
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sumdahl.geet.engine.DownloadEvent
import dev.sumdahl.geet.engine.Engine
import dev.sumdahl.geet.engine.EngineException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Downloads one link by running the engine and folding its events into the database, as a foreground service with a
 * live notification. Links run one at a time ([engineLock]): the engine already downloads a playlist's songs in
 * parallel, and two engines at once would race on its download index and trip YouTube's bot check sooner.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: Engine,
    private val settings: SettingsStore,
    private val dao: DownloadDao,
    private val notifier: Notifier
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_JOB, -1)
        val queued = dao.job(id) ?: return Result.success()
        if (queued.state == JobState.Cancelled) return Result.success()
        setForeground(notifier.progress(queued, "Waiting for the download before it", this.id))
        return engineLock.withLock { run(id) }
    }

    private suspend fun run(id: Long): Result {
        // Re-read: while this link waited its turn, reading it may have named it.
        val start =
            dao.job(id)?.copy(state = JobState.Running, saved = 0, existing = 0, failed = 0, error = null) ?: return Result.success()
        dao.clearTracks(id)
        dao.progress(start)
        val run = Run(start)
        var job = try {
            val env = settings.engineEnvironment() + listOfNotNull(start.format?.let { "GEET_FORMAT" to it })
            engine.download(start.link, env).collect { run.record(it) }
            run.job.copy(state = if (run.job.failed > 0 && run.job.saved + run.job.existing == 0) JobState.Failed else JobState.Done)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { dao.progress(run.job.copy(state = JobState.Cancelled, finishedAt = System.currentTimeMillis())) }
            throw e
        } catch (e: EngineException) {
            run.job.copy(state = JobState.Failed, error = run.job.error ?: e.message)
        }
        job = job.copy(finishedAt = System.currentTimeMillis())
        dao.progress(job)
        // New files are in MediaStore already (the Music folder is MediaProvider's), but their tags are read lazily;
        // scanning now makes titles and covers appear in the library, and in every music app, straight away.
        if (run.saved.isNotEmpty()) MediaScannerConnection.scanFile(applicationContext, run.saved.toTypedArray(), null, null)
        dao.job(id)?.let(notifier::finished)
        return Result.success()
    }

    /** One link's run: folds each engine event into the database and, now and then, the notification. */
    private inner class Run(var job: DownloadJob) {
        val saved = mutableListOf<String>()
        private var current: String? = null
        private var lastNotified = 0L

        suspend fun record(e: DownloadEvent) {
            job = job.apply(e)
            val before = dao.track(job.id, e.index)
            before.apply(job.id, e)?.let { track ->
                dao.upsert(track)
                if (track.stage != before?.stage && track.stage in FINAL) {
                    job = job.count(track)
                    if (track.stage == TrackStage.Done && !track.skipped) track.path?.let(saved::add)
                }
                if (track.stage == TrackStage.Downloading) current = track.name.substringAfter(" - ")
            }
            dao.progress(job)
            // Android drops notification updates beyond a few a second.
            val now = System.currentTimeMillis()
            if (now - lastNotified > NOTIFY_EVERY_MS) {
                lastNotified = now
                dao.job(job.id)?.let { setForeground(notifier.progress(it, current, id)) }
            }
        }
    }

    companion object {
        const val KEY_JOB = "job"
        private const val NOTIFY_EVERY_MS = 500L
        private val FINAL = setOf(TrackStage.Done, TrackStage.Failed)
        private val engineLock = Mutex()
    }
}
