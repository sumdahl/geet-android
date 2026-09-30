package dev.sumdahl.geet.ui.player

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import dev.sumdahl.geet.designsystem.CoverTheme
import dev.sumdahl.geet.designsystem.component.MessageState
import dev.sumdahl.geet.designsystem.component.MorphingCover
import dev.sumdahl.geet.designsystem.rememberCoverSeed

/**
 * The full player, coloured from the song's cover and springing to the next song's colours on a skip. The cover
 * morphs between shapes as playback starts and stops; below it are the title, the wavy seek bar, the live spectrum
 * and a floating toolbar of controls. Lyrics take the cover's place with one tap.
 */
@Composable
fun PlayerScreen(onCollapse: () -> Unit, coverColors: Boolean, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val song = state.current
    val seed = rememberCoverSeed(song?.cover.takeIf { coverColors })
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val position = rememberPosition(viewModel.player, state.playing)
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    CoverTheme(seed) {
        val colors = MaterialTheme.colorScheme
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(colors.primaryContainer, colors.surfaceContainerLowest, colors.surfaceContainerLowest))),
        ) {
            if (song == null) {
                MessageState(
                    icon = Icons.Rounded.MusicNote,
                    title = "Nothing playing",
                    body = "Pick a song from your library and it plays straight away.",
                    modifier = Modifier.align(Alignment.Center),
                )
                return@Box
            }
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player") }
                    Text(
                        if (showLyrics) "Lyrics" else "Now playing",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        color = colors.onSurfaceVariant,
                    )
                    IconButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, song.uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(share, "Share ${song.title}"))
                    }) { Icon(Icons.Rounded.Share, contentDescription = "Share song") }
                }

                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        (fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy), initialScale = 0.92f)) togetherWith fadeOut()
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "cover or lyrics",
                ) { lyricsShown ->
                    if (lyricsShown) {
                        LyricsView(lyrics, position, onSeek = viewModel::seekTo)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            MorphingCover(
                                model = song.cover,
                                playing = state.playing,
                                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().aspectRatio(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
                Text(
                    listOf(song.artist, song.album).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().basicMarquee(),
                )
                Spacer(Modifier.height(12.dp))
                SeekBar(position, state.durationMs, state.playing, onSeek = viewModel::seekTo, modifier = Modifier.fillMaxWidth())
                SpectrumBars(viewModel.spectrum, state.playing, Modifier.fillMaxWidth().height(40.dp))
                Spacer(Modifier.height(16.dp))

                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    leadingContent = {
                        FilledTonalIconToggleButton(checked = state.shuffle, onCheckedChange = { viewModel.toggleShuffle() }) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = if (state.shuffle) "Shuffle on" else "Shuffle off")
                        }
                    },
                    trailingContent = {
                        FilledTonalIconToggleButton(checked = state.repeat != Player.REPEAT_MODE_OFF, onCheckedChange = { viewModel.cycleRepeat() }) {
                            Icon(
                                if (state.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                contentDescription = when (state.repeat) {
                                    Player.REPEAT_MODE_ONE -> "Repeating this song"
                                    Player.REPEAT_MODE_ALL -> "Repeating the queue"
                                    else -> "Repeat off"
                                },
                            )
                        }
                    },
                ) {
                    IconButton(onClick = viewModel::previous) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous") }
                    FilledIconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            viewModel.toggle()
                        },
                        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.playing) "Pause" else "Play",
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                        )
                    }
                    IconButton(onClick = viewModel::next) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next") }
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    FilledTonalIconToggleButton(checked = showLyrics, onCheckedChange = { showLyrics = it }) {
                        Icon(Icons.Rounded.Lyrics, contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics")
                    }
                    FilledTonalIconToggleButton(checked = showQueue, onCheckedChange = { showQueue = it }) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Queue")
                    }
                }
            }
        }
        if (showQueue) QueueSheet(state, viewModel, onDismiss = { showQueue = false })
    }
}
