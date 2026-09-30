package dev.sumdahl.geet.data

import dev.sumdahl.geet.engine.DownloadEvent

/** How one engine event changes a link's row and, for a song's event, that song's row. */
internal fun DownloadJob.apply(e: DownloadEvent): DownloadJob = when {
    e.stage == "reading" -> copy(readingDone = e.index, readingTotal = e.total)
    e.fatal -> copy(error = e.error)
    else -> copy(total = maxOf(total, e.total))
}

/** The song's row after [e]; null for events that aren't about one song. */
internal fun DownloadTrack?.apply(jobId: Long, e: DownloadEvent): DownloadTrack? {
    if (e.index == 0 || e.stage == "reading") return null
    val t = this ?: DownloadTrack(jobId = jobId, index = e.index, name = e.track)
    return when (e.stage) {
        "resolved" -> t.copy(stage = TrackStage.Resolved, path = e.path ?: t.path)
        // The first "downloading" event has no progress; later ones come in 10% steps.
        "downloading" -> t.copy(stage = TrackStage.Downloading, progress = e.progress ?: t.progress)
        "tagging" -> t.copy(stage = TrackStage.Tagging, progress = 1f)
        "done" -> t.copy(
            stage = TrackStage.Done,
            progress = 1f,
            path = e.path ?: t.path,
            skipped = e.skipped || e.duplicateOf != null,
            warning = e.warning,
            lyricsPath = e.lyricsPath
        )
        "failed" -> t.copy(stage = TrackStage.Failed, error = e.error)
        else -> t
    }
}

/** Counts a finished song towards its link's summary. */
internal fun DownloadJob.count(track: DownloadTrack): DownloadJob = when (track.stage) {
    TrackStage.Done -> if (track.skipped) copy(existing = existing + 1) else copy(saved = saved + 1)
    TrackStage.Failed -> copy(failed = failed + 1)
    else -> this
}
