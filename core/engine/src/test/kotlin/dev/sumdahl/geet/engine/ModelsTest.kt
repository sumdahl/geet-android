package dev.sumdahl.geet.engine

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decodes real geet output (captured into src/test/resources), including fields added after this app was built. */
class ModelsTest {
    private fun resource(name: String) = checkNotNull(javaClass.classLoader).getResource(name).readText()

    @Test
    fun downloadEvents() {
        val events = resource("download.ndjson").lines().filter(String::isNotBlank).map { engineJson.decodeFromString<DownloadEvent>(it) }
        assertEquals(listOf("reading", "resolved", "downloading", "done", "failed"), events.map { it.stage })
        assertEquals(0.4f, events[2].progress)
        assertEquals("/music/Monkeys Spinning Monkeys - Kevin MacLeod, Kevin.lrc", events[3].lyricsPath)
        assertTrue(events[4].fatal)
    }

    @Test
    fun linkInfo() {
        val info = engineJson.decodeFromString<LinkInfo>(resource("info.json"))
        assertEquals("track", info.kind)
        assertEquals("spotify", info.source)
        assertEquals("Monkeys Spinning Monkeys", info.tracks.single().title)
        assertEquals(125_000L, info.tracks.single().durationMs)
    }

    @Test
    fun settingsCarryChoices() {
        val settings = engineJson.decodeFromString<List<EngineSetting>>(resource("settings.json"))
        assertEquals(listOf("opus", "flac", "mp3"), settings.first { it.key == "format" }.choices)
        assertTrue(settings.any { it.key == "lyrics" && it.type == "bool" })
    }
}
