package dev.sumdahl.geet.ui

import dev.sumdahl.geet.ui.common.firstLink
import dev.sumdahl.geet.ui.home.isSupportedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTest {
    @Test
    fun recognisesTheLinksGeetReads() {
        listOf(
            "https://open.spotify.com/track/3XtbtOMVVBooqbcGz8UErp?si=x",
            "https://music.youtube.com/playlist?list=PLx",
            "https://www.youtube.com/watch?v=abc",
            "https://youtu.be/abc",
            "https://music.apple.com/us/album/x/1?i=2",
            "https://www.deezer.com/track/1"
        ).forEach { assertTrue(it, isSupportedLink(it)) }
        assertFalse(isSupportedLink("https://example.com/song"))
    }

    @Test
    fun findsTheLinkInSharedText() {
        assertEquals(
            "https://open.spotify.com/track/abc?si=1",
            firstLink("Listen to Blinding Lights on Spotify: https://open.spotify.com/track/abc?si=1.")
        )
    }
}
