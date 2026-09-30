package dev.sumdahl.geet.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.sumdahl.geet.designsystem.tabular
import dev.sumdahl.geet.player.Spectrum
import dev.sumdahl.geet.ui.common.formatDuration

/**
 * The playback position, fresh every frame while playing (lyrics and the seek bar follow it smoothly), and read once
 * when paused. Readers should read it in a lambda or a derivedStateOf, so a frame doesn't recompose them.
 */
@Composable
fun rememberPosition(player: Player, playing: Boolean): State<Long> {
    val position = remember { mutableLongStateOf(player.currentPosition) }
    LaunchedEffect(player, playing) {
        position.longValue = player.currentPosition
        while (playing) {
            withFrameMillis { position.longValue = player.currentPosition }
        }
    }
    return position
}

/**
 * The seek bar: Material's wavy line, waving while the song plays and lying flat when paused, like Android 16's media
 * controls. Drag or tap to seek; the times follow the finger, and a tick of haptics confirms the jump.
 */
@Composable
fun SeekBar(position: State<Long>, durationMs: Long, playing: Boolean, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val amplitude by animateFloatAsState(if (playing && !dragging) 1f else 0f, label = "wave")
    val fraction = { if (dragging) dragFraction else if (durationMs > 0) (position.value.toFloat() / durationMs).coerceIn(0f, 1f) else 0f }
    val shownSeconds by remember(durationMs) { derivedStateOf { (fraction() * durationMs / 1000).toLong() } }
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .semantics { contentDescription = "Seek, ${formatDuration(shownSeconds * 1000)} of ${formatDuration(durationMs)}" }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSeek((offset.x / size.width * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            onSeek((dragFraction * durationMs).toLong())
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
        ) {
            LinearWavyProgressIndicator(
                progress = fraction,
                amplitude = { amplitude },
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.labelMedium.tabular()
            Text(formatDuration(shownSeconds * 1000), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(durationMs), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The live spectrum: rounded bars mirrored from the middle, drawn every frame from [Spectrum] while the song plays and
 * settling to rest when paused. It draws without recomposing and reuses one array, so it costs nothing per frame
 * beyond the FFT.
 */
@Composable
fun SpectrumBars(spectrum: Spectrum, playing: Boolean, modifier: Modifier = Modifier) {
    val bands = remember { FloatArray(Spectrum.BANDS) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        if (!playing) {
            bands.fill(0f)
            frame++
            return@LaunchedEffect
        }
        while (true) {
            withFrameMillis {
                spectrum.bands(bands)
                frame = it
            }
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = "Spectrum" }) {
        frame // read in the draw phase: each frame redraws, nothing recomposes
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (bands.size - 1)) / bands.size
        val radius = CornerRadius(w / 2, w / 2)
        bands.forEachIndexed { i, level ->
            val h = (size.height * (0.06f + 0.94f * level)).coerceAtLeast(w)
            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.65f * level),
                topLeft = Offset(i * (w + gap), (size.height - h) / 2),
                size = Size(w, h),
                cornerRadius = radius,
            )
        }
    }
}
