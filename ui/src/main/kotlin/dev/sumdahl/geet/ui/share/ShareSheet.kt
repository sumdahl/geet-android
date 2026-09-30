package dev.sumdahl.geet.ui.share

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sumdahl.geet.data.JobState
import dev.sumdahl.geet.designsystem.CoverTheme
import dev.sumdahl.geet.designsystem.component.CoverArt
import dev.sumdahl.geet.designsystem.component.MessageState
import dev.sumdahl.geet.designsystem.rememberCoverSeed
import dev.sumdahl.geet.engine.LinkInfo
import dev.sumdahl.geet.ui.common.formatDuration
import dev.sumdahl.geet.ui.common.songs
import dev.sumdahl.geet.ui.downloads.friendly

/**
 * What appears over Spotify, YouTube Music or YouTube when a link is shared to Geet: a sheet that says what the link
 * is (cover, name, how many songs), in the cover's colours, with the format and a Download button. Once downloading,
 * it follows the progress and can be swiped away; the notification carries on.
 */
@Composable
fun ShareSheet(link: String?, onDismiss: () -> Unit, onOpenApp: () -> Unit, viewModel: ShareViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.runtime.LaunchedEffect(link) { viewModel.open(link) }
    val cover = (state.info as? ShareInfo.Ready)?.info?.coverUrl
    CoverTheme(rememberCoverSeed(cover.takeIf { state.coverColors })) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AnimatedContent(
                    targetState = state.info,
                    transitionSpec = { (fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioLowBouncy), initialScale = 0.9f)) togetherWith fadeOut() },
                    contentKey = { it::class },
                    label = "preview",
                ) { info ->
                    when (info) {
                        ShareInfo.Reading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(72.dp)) }
                            Column {
                                Text("Reading the link…", style = MaterialTheme.typography.titleMedium)
                                Text(state.link.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ShareInfo.NotALink -> MessageState(
                            icon = Icons.Rounded.LinkOff,
                            title = "Nothing Geet can download",
                            body = "Share a song, album or playlist from Spotify, YouTube Music or YouTube.",
                        )
                        is ShareInfo.Failed -> MessageState(icon = Icons.Rounded.ErrorOutline, title = "Couldn't read this link", body = friendly(info.message))
                        is ShareInfo.Ready -> Preview(info.info)
                    }
                }

                val job = state.job
                when {
                    job == null && state.info is ShareInfo.Ready -> {
                        FormatChoice(state.format, viewModel::setFormat)
                        DownloadButton(onDownload = viewModel::download, onDownloadAndOpen = {
                            viewModel.download()
                            onOpenApp()
                        })
                    }
                    job != null -> Progress(job.state, job.saved + job.existing + job.failed, job.total, job.readingDone, job.readingTotal, job.failed, job.error, onDismiss, onOpenApp)
                    else -> OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun Preview(info: LinkInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverArt(info.coverUrl, Modifier.size(96.dp), MaterialTheme.shapes.extraLarge)
            Column(Modifier.weight(1f)) {
                Text(info.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "${sourceName(info.source)} ${info.kind}".trim(),
                        if (info.kind == "track") info.tracks.firstOrNull()?.artists?.joinToString(", ") else songs(info.total),
                        info.tracks.sumOf { it.durationMs }.takeIf { it > 0 && info.tracks.size == info.total }?.let(::formatDuration),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (info.kind != "track" && info.tracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                info.tracks.take(3).forEach {
                    Text("${it.title} · ${it.artists.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (info.total > 3) Text("and ${info.total - 3} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun sourceName(source: String) = when (source) {
    "spotify" -> "Spotify"
    "youtube" -> "YouTube"
    "apple" -> "Apple Music"
    "deezer" -> "Deezer"
    else -> ""
}

/** Opus, MP3 or FLAC as one connected button group, with what each is good for underneath. */
@Composable
private fun FormatChoice(selected: String, onSelect: (String) -> Unit) {
    val formats = listOf("opus" to "Opus", "mp3" to "MP3", "flac" to "FLAC")
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            formats.forEachIndexed { i, (key, label) ->
                ToggleButton(
                    checked = selected == key,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(key)
                    },
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        formats.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                ) { Text(label) }
            }
        }
        Text(
            when (selected) {
                "mp3" -> "Plays on anything, from old car stereos up."
                "flac" -> "Lossless container, larger files. YouTube's audio isn't lossless, so it won't sound better than Opus."
                else -> "YouTube's own audio, kept as it is: best quality, smallest files."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadButton(onDownload: () -> Unit, onDownloadAndOpen: () -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onDownload()
            }, modifier = Modifier.height(56.dp)) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize))
                Spacer(Modifier.size(8.dp))
                Text("Download")
            }
        },
        trailingButton = {
            Box {
                SplitButtonDefaults.TrailingButton(checked = menu, onCheckedChange = { menu = it }, modifier = Modifier.height(56.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "More ways to download")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Download and open Geet") }, onClick = {
                        menu = false
                        onDownloadAndOpen()
                    })
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Progress(
    state: JobState,
    finished: Int,
    total: Int,
    readingDone: Int,
    readingTotal: Int,
    failed: Int,
    error: String?,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedContent(targetState = state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "progress") { s ->
            when (s) {
                JobState.Queued, JobState.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            s == JobState.Queued -> "Waiting for the download before it"
                            finished == 0 && readingTotal > 0 -> "Reading $readingDone of $readingTotal"
                            total > 1 -> "Downloading · $finished of $total"
                            else -> "Downloading"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (total > 1 && finished > 0) {
                        LinearWavyProgressIndicator(progress = { finished.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text("You can close this; Geet keeps going in the background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                JobState.Done -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text(if (failed > 0) "Done, but $failed couldn't be downloaded" else "Saved to your library", style = MaterialTheme.typography.titleMedium)
                }
                JobState.Failed, JobState.Cancelled -> Text(if (s == JobState.Cancelled) "Cancelled" else friendly(error), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Close") }
            Button(onClick = onOpenApp, modifier = Modifier.weight(1f)) { Text(if (state == JobState.Done) "Open library" else "Open Geet") }
        }
    }
}
