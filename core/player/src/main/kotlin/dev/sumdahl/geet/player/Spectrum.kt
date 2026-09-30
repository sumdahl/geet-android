package dev.sumdahl.geet.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * A live spectrum of what's playing, tapped from the decoded PCM on its way to the speaker, so no RECORD_AUDIO
 * permission is needed (Android's Visualizer API wants one). The audio passes through untouched.
 *
 * The audio thread only copies samples into a ring buffer; [bands] runs the FFT when a frame is drawn, so the cost is
 * paid only while the spectrum is on screen, and reuses its arrays, so drawing allocates nothing.
 */
@UnstableApi
class Spectrum : BaseAudioProcessor() {
    private val ring = FloatArray(FFT_SIZE)

    @Volatile private var written = 0L
    private var channels = 2
    private var sampleRate = 44_100

    private val re = FloatArray(FFT_SIZE)
    private val im = FloatArray(FFT_SIZE)
    private val window = FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2 * PI * it / (FFT_SIZE - 1)).toFloat() }
    private val smooth = FloatArray(BANDS)
    private var peak = MIN_PEAK

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) return AudioProcessor.AudioFormat.NOT_SET
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        val shorts = inputBuffer.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer()
        var n = written
        while (shorts.remaining() >= channels) {
            var sum = 0f
            repeat(channels) { sum += shorts.get() }
            ring[(n % FFT_SIZE).toInt()] = sum / channels / Short.MAX_VALUE
            n++
        }
        written = n
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }

    /**
     * Fills [out] (one value per band, 0..1) with the latest spectrum: log-spaced bands from 40 Hz to 16 kHz, rising
     * fast and falling slowly like a meter, with gain that follows the song's loudness. Call from the draw loop.
     */
    fun bands(out: FloatArray) {
        val end = written
        for (i in 0 until FFT_SIZE) {
            val s = ring[((end + i) % FFT_SIZE).toInt()]
            re[i] = s * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val binHz = sampleRate.toFloat() / FFT_SIZE
        var loudest = 0f
        for (b in 0 until BANDS) {
            val lo = (LOW_HZ * (HIGH_HZ / LOW_HZ).pow(b.toFloat() / BANDS) / binHz).toInt().coerceIn(1, FFT_SIZE / 2 - 1)
            val hi = max(lo + 1, (LOW_HZ * (HIGH_HZ / LOW_HZ).pow((b + 1f) / BANDS) / binHz).toInt()).coerceAtMost(FFT_SIZE / 2)
            var sum = 0f
            for (k in lo until hi) sum += hypot(re[k], im[k])
            val level = ln(1f + sum / (hi - lo))
            loudest = max(loudest, level)
            smooth[b] = if (level > smooth[b]) level else smooth[b] * FALL + level * (1 - FALL)
        }
        // Automatic gain: scale against the loudest band heard lately, decaying so quiet passages still move.
        peak = max(MIN_PEAK, max(loudest, peak * PEAK_DECAY))
        for (b in 0 until BANDS) out[b] = (smooth[b] / peak).coerceIn(0f, 1f)
    }

    override fun onReset() {
        ring.fill(0f)
        smooth.fill(0f)
        peak = MIN_PEAK
    }

    companion object {
        const val BANDS = 32
        private const val FFT_SIZE = 2048
        private const val LOW_HZ = 40f
        private const val HIGH_HZ = 16_000f
        private const val FALL = 0.82f
        private const val PEAK_DECAY = 0.995f
        private const val MIN_PEAK = 0.5f

        /** In-place radix-2 FFT; [re] and [im] must be a power of two long. */
        internal fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    re[i] = re[j].also { re[j] = re[i] }
                    im[i] = im[j].also { im[j] = im[i] }
                }
            }
            var len = 2
            while (len <= n) {
                val angle = -2 * PI / len
                val wRe = cos(angle).toFloat()
                val wIm = sin(angle).toFloat()
                for (i in 0 until n step len) {
                    var curRe = 1f
                    var curIm = 0f
                    for (k in 0 until len / 2) {
                        val a = i + k
                        val b = a + len / 2
                        val tRe = re[b] * curRe - im[b] * curIm
                        val tIm = re[b] * curIm + im[b] * curRe
                        re[b] = re[a] - tRe
                        im[b] = im[a] - tIm
                        re[a] += tRe
                        im[a] += tIm
                        val next = curRe * wRe - curIm * wIm
                        curIm = curRe * wIm + curIm * wRe
                        curRe = next
                    }
                }
                len = len shl 1
            }
        }
    }
}
