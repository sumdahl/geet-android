package dev.sumdahl.geet.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test
    fun parsesSyncedLyricsWithRepeatedStamps() {
        val l = Lyrics.parse("[ar:Someone]\n[00:01.36]Damn, love or lust\n[00:12.00][01:30.5] chorus\n")
        assertTrue(l.synced)
        assertEquals(listOf(1360L, 12_000L, 90_500L), l.lines.map { it.atMs })
        assertEquals("chorus", l.lines.last().text)
    }

    @Test
    fun plainTextStaysPlain() {
        val l = Lyrics.parse("line one\n\nline two\n")
        assertFalse(l.synced)
        assertEquals(listOf("line one", "line two"), l.lines.map { it.text })
        assertEquals(-1, l.lineAt(5_000))
    }

    @Test
    fun findsTheLineBeingSung() {
        val l = Lyrics.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c\n")
        assertEquals(-1, l.lineAt(500))
        assertEquals(0, l.lineAt(1_000))
        assertEquals(1, l.lineAt(8_999))
        assertEquals(2, l.lineAt(60_000))
    }
}
