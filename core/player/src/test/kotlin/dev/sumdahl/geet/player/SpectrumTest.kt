package dev.sumdahl.geet.player

import androidx.media3.common.util.UnstableApi
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class SpectrumTest {
    /** A pure tone lands in the one bin it belongs to. */
    @Test
    fun fftFindsTheTone() {
        val n = 1024
        val re = FloatArray(n) { sin(2 * PI * 64 * it / n).toFloat() }
        val im = FloatArray(n)
        Spectrum.fft(re, im)
        val loudest = (0 until n / 2).maxBy { hypot(re[it], im[it]) }
        assertEquals(64, loudest)
    }
}
