package dev.sumdahl.geet.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sumdahl.geet.engine.Engine
import dev.sumdahl.geet.engine.LinkInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A link in the queue with its songs, as the Downloads screen groups them. */
data class JobWithTracks(val job: DownloadJob, val tracks: List<DownloadTrack>)

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val engine: Engine,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val work = WorkManager.getInstance(context)

    val jobs: Flow<List<JobWithTracks>> = combine(dao.jobs(), dao.tracks()) { jobs, tracks ->
        val byJob = tracks.groupBy { it.jobId }
        jobs.map { JobWithTracks(it, byJob[it.id].orEmpty()) }
    }

    fun job(id: Long): Flow<DownloadJob?> = dao.jobFlow(id)

    fun tracks(id: Long): Flow<List<DownloadTrack>> = dao.tracksOf(id)

    /**
     * Queues a link and returns its id at once. [info], when the share sheet already read the link, names the row
     * straight away; otherwise the link is read in the background so the row gets its name and cover within a
     * second, long before its turn to download comes.
     */
    suspend fun enqueue(link: String, info: LinkInfo? = null): Long {
        val id = dao.insert(DownloadJob(link = link))
        if (info != null) describe(id, info) else scope.launch { runCatching { engine.info(link) }.onSuccess { describe(id, it) } }
        start(id)
        return id
    }

    private suspend fun describe(id: Long, info: LinkInfo) =
        dao.describe(id, info.name.ifBlank { info.tracks.firstOrNull()?.title.orEmpty() }, info.kind, info.source, info.coverUrl, info.total)

    private suspend fun start(id: Long) {
        val unmetered = settings.settings.first().unmeteredOnly
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_JOB to id))
            .setConstraints(Constraints(requiredNetworkType = if (unmetered) NetworkType.UNMETERED else NetworkType.CONNECTED))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()
        work.enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(id: Long) {
        work.cancelUniqueWork(workName(id))
        scope.launch { if (dao.job(id)?.state == JobState.Queued) dao.setState(id, JobState.Cancelled, System.currentTimeMillis()) }
    }

    /**
     * Runs the link again. The engine skips songs already saved, so only the failed and missing ones download.
     */
    suspend fun retry(id: Long) {
        dao.setState(id, JobState.Queued)
        start(id)
    }

    suspend fun remove(id: Long) {
        cancel(id)
        dao.delete(id)
    }

    suspend fun clearFinished() {
        dao.finishedIds().forEach { dao.delete(it) }
    }

    private fun workName(id: Long) = "download-$id"

    private companion object {
        const val TAG = "download"
    }
}
