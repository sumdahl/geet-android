package dev.sumdahl.geet.ui

import dev.sumdahl.geet.ui.downloads.friendly
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlyErrorTest {
    @Test
    fun enginesReasonsReadAsPlainWords() {
        assertEquals("YouTube is limiting downloads for now. Try again in a while.", friendly("download failed: YouTube wants you to confirm you're not a bot"))
        assertEquals("Couldn't find this song on YouTube.", friendly("no YouTube result matched"))
        assertEquals("some new reason", friendly("some new reason"))
    }
}
