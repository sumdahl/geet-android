package dev.sumdahl.geet.data

import dev.sumdahl.geet.engine.DownloadEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressTest {
    private fun ev(stage: String, index: Int = 1, progress: Float? = null, skipped: Boolean = false) =
        DownloadEvent(
            track = "A - Song",
            stage = stage,
            index = index,
            total = 2,
            progress = progress,
            skipped = skipped,
            path = "/m/Song - A.opus"
        )

    @Test
    fun songWalksThroughTheStages() {
        var t: DownloadTrack? = null
        for (e in listOf(ev("resolved"), ev("downloading"), ev("downloading", progress = 0.4f))) t = t.apply(7, e)
        assertEquals(TrackStage.Downloading, t!!.stage)
        assertEquals(0.4f, t.progress)
        t = t.apply(7, ev("done"))
        assertEquals(TrackStage.Done, t!!.stage)
        assertEquals("/m/Song - A.opus", t.path)
    }

    @Test
    fun readingAndFatalEventsAreTheLinks() {
        assertNull(null.apply(7, DownloadEvent(stage = "reading", index = 3, total = 50)))
        val job = DownloadJob(link = "x").apply(DownloadEvent(stage = "reading", index = 3, total = 50))
        assertEquals(3, job.readingDone)
        val failed = job.apply(DownloadEvent(stage = "failed", fatal = true, error = "not a bot"))
        assertEquals("not a bot", failed.error)
    }

    @Test
    fun summaryCountsSavedExistingAndFailed() {
        val saved = null.apply(1, ev("done"))!!
        val existing = null.apply(1, ev("done", index = 2, skipped = true))!!
        val failed = null.apply(1, DownloadEvent(stage = "failed", index = 3, error = "gone"))!!
        val job = DownloadJob(link = "x").count(saved).count(existing).count(failed)
        assertEquals(Triple(1, 1, 1), Triple(job.saved, job.existing, job.failed))
        assertTrue(existing.skipped)
    }
}
