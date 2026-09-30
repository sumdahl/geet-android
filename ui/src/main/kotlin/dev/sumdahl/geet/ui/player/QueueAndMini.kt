package dev.sumdahl.geet.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sumdahl.geet.designsystem.CoverTheme
import dev.sumdahl.geet.designsystem.component.CoverArt
import dev.sumdahl.geet.designsystem.rememberCoverSeed
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Up next: drag to reorder, swipe the × to drop a song, tap to jump to it. */
@Composable
fun QueueSheet(state: PlayerState, viewModel: PlayerViewModel, onDismiss: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.index.coerceAtLeast(0))
    val reorder = rememberReorderableLazyListState(list) { from, to ->
        viewModel.move(from.index, to.index)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Up next", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(state.queue, key = { i, song -> "${song.path}#$i" }) { i, song ->
                ReorderableItem(reorder, key = "${song.path}#$i") { dragging ->
                    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "lift")
                    Surface(shadowElevation = elevation) {
                        ListItem(
                            headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                if (i == state.index) {
                                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    CoverArt(song.cover, Modifier.size(40.dp), MaterialTheme.shapes.small)
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = {
                                        viewModel.remove(i)
                                    }) { Icon(Icons.Rounded.Close, contentDescription = "Remove from queue") }
                                    Icon(
                                        Icons.Rounded.DragHandle,
                                        contentDescription = "Reorder",
                                        modifier = Modifier.draggableHandle().padding(12.dp)
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (i ==
                                    state.index
                                ) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                }
                            ),
                            modifier = Modifier.clickable { viewModel.playAt(i) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The mini player above the navigation bar: the cover, the song, play/pause and next, with a thin wavy line of
 * progress. Tapping it opens the full player. It takes the song's colours too, so it's clear what's playing.
 */
@Composable
fun MiniPlayer(onOpen: () -> Unit, coverColors: Boolean, modifier: Modifier = Modifier, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val song = state.current ?: return
    val position = rememberPosition(viewModel.player, state.playing)
    CoverTheme(rememberCoverSeed(song.cover.takeIf { coverColors })) {
        Surface(
            onClick = onOpen,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp,
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Row(
                    Modifier.padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CoverArt(song.cover, Modifier.size(44.dp), MaterialTheme.shapes.medium)
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = viewModel::toggle) {
                        Icon(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.playing) "Pause" else "Play"
                        )
                    }
                    IconButton(onClick = viewModel::next) { Icon(Icons.Rounded.SkipNext, contentDescription = "Next") }
                }
                LinearWavyProgressIndicator(
                    progress = { if (state.durationMs > 0) (position.value.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                    amplitude = { if (state.playing) 1f else 0f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp).height(8.dp)
                )
            }
        }
    }
}
