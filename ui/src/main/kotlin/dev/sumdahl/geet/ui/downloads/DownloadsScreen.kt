package dev.sumdahl.geet.ui.downloads

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sumdahl.geet.data.DownloadJob
import dev.sumdahl.geet.data.DownloadRepository
import dev.sumdahl.geet.data.DownloadTrack
import dev.sumdahl.geet.data.JobState
import dev.sumdahl.geet.data.JobWithTracks
import dev.sumdahl.geet.data.TrackStage
import dev.sumdahl.geet.designsystem.component.CoverArt
import dev.sumdahl.geet.designsystem.component.MessageState
import dev.sumdahl.geet.ui.common.ago
import dev.sumdahl.geet.ui.common.songs
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(private val repo: DownloadRepository) : ViewModel() {
    val jobs = repo.jobs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cancel(id: Long) = repo.cancel(id)

    fun retry(id: Long) = viewModelScope.launch { repo.retry(id) }

    fun remove(id: Long) = viewModelScope.launch { repo.remove(id) }

    fun clearFinished() = viewModelScope.launch { repo.clearFinished() }
}

/**
 * The queue, one card per shared link. A running card shows its wavy progress and, opened, every song's own stage;
 * a finished card sums up what happened, and a failed one says why in plain words and offers to try again.
 */
@Composable
fun DownloadsScreen(onOpenLibrary: () -> Unit, viewModel: DownloadsViewModel = hiltViewModel()) {
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Downloads") },
                subtitle = jobs?.let { list ->
                    val running = list.count { it.job.state == JobState.Running || it.job.state == JobState.Queued }
                    if (running > 0) ({ Text("$running in progress") }) else null
                },
                actions = {
                    if (jobs.orEmpty().any { it.job.state !in ACTIVE }) {
                        IconButton(onClick = {
                            viewModel.clearFinished()
                            scope.launch { snackbar.showSnackbar("Cleared finished downloads. Your songs stay in the library.") }
                        }) { Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear finished") }
                    }
                },
                scrollBehavior = scroll
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection)
    ) { padding ->
        val list = jobs
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                MessageState(
                    icon = Icons.Rounded.Download,
                    title = "Nothing downloading",
                    body = "Share a song, album or playlist to Geet from Spotify, YouTube Music or YouTube, or paste a link on Home."
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { it.job.id }) { item ->
                    JobCard(
                        item = item,
                        onCancel = { viewModel.cancel(item.job.id) },
                        onRetry = { viewModel.retry(item.job.id) },
                        onRemove = { viewModel.remove(item.job.id) },
                        onOpenLibrary = onOpenLibrary,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

private val ACTIVE = setOf(JobState.Queued, JobState.Running)

@Composable
private fun JobCard(
    item: JobWithTracks,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val job = item.job
    var open by rememberSaveable(job.id) { mutableStateOf(false) }
    ElevatedCard(onClick = { open = !open }, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CoverArt(job.coverUrl, Modifier.size(64.dp), MaterialTheme.shapes.large)
                Column(Modifier.weight(1f)) {
                    Text(job.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    AnimatedContent(targetState = status(job), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (job.state ==
                                JobState.Failed
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                when (job.state) {
                    JobState.Queued, JobState.Running -> IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                    JobState.Failed, JobState.Cancelled -> IconButton(onClick = onRetry) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Try again")
                    }
                    JobState.Done -> if (job.failed > 0) {
                        IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, contentDescription = "Retry the failed songs") }
                    } else {
                        IconButton(onClick = onOpenLibrary) { Icon(Icons.Rounded.LibraryMusic, contentDescription = "Open in library") }
                    }
                }
            }
            if (job.state == JobState.Running) {
                val finished = job.saved + job.existing + job.failed
                if (job.total > 0 && (finished > 0 || item.tracks.isNotEmpty())) {
                    LinearWavyProgressIndicator(
                        progress = {
                            (
                                finished +
                                    item.tracks.filter { it.stage == TrackStage.Downloading }.sumOf { it.progress.toDouble() }.toFloat()
                                ) /
                                job.total
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                    )
                } else {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }
            if (item.tracks.size > 1 || (item.tracks.isNotEmpty() && job.kind != "track")) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (open) "Hide songs" else "Show ${songs(item.tracks.size)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                }
            }
            AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.tracks.forEach { TrackRow(it) }
                    if (job.state !in ACTIVE) {
                        TextButton(onClick = onRemove) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Remove from the list", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: DownloadTrack) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = track.stage,
                transitionSpec = { scaleIn() + fadeIn() togetherWith fadeOut() },
                label = "stage"
            ) { stage ->
                when (stage) {
                    TrackStage.Waiting -> Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = "Waiting",
                        tint = MaterialTheme.colorScheme.outline
                    )
                    TrackStage.Resolved -> LoadingIndicator(Modifier.size(28.dp))
                    TrackStage.Downloading, TrackStage.Tagging -> CircularWavyProgressIndicator(progress = {
                        track.progress
                    }, modifier = Modifier.size(24.dp))
                    TrackStage.Done -> Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    TrackStage.Failed -> Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                track.name.substringAfter(" - "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when (track.stage) {
                    TrackStage.Waiting -> track.name.substringBefore(" - ")
                    TrackStage.Resolved -> "Found it, starting…"
                    TrackStage.Downloading -> "Downloading · ${(track.progress * 100).toInt()}%"
                    TrackStage.Tagging -> "Adding the cover and lyrics"
                    TrackStage.Done -> when {
                        track.skipped -> "Already in your library"
                        track.lyricsPath != null -> "Saved with lyrics"
                        else -> "Saved"
                    }
                    TrackStage.Failed -> friendly(track.error)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (track.stage ==
                    TrackStage.Failed
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun status(job: DownloadJob): String {
    val finished = job.saved + job.existing + job.failed
    return when (job.state) {
        JobState.Queued -> "Waiting its turn"
        JobState.Running -> when {
            finished == 0 && job.readingTotal > 0 -> "Reading ${job.readingDone} of ${job.readingTotal}"
            job.total > 1 -> "$finished of ${job.total}"
            else -> "Downloading"
        }
        JobState.Cancelled -> "Cancelled"
        JobState.Failed -> friendly(job.error)
        JobState.Done -> summary(job) + (job.finishedAt?.let { " · ${ago(it)}" } ?: "")
    }
}

private fun summary(job: DownloadJob) = buildList {
    if (job.saved > 0) add("${job.saved} saved")
    if (job.existing > 0) add("${job.existing} already had")
    if (job.failed > 0) add("${job.failed} failed")
}.joinToString(" · ").ifEmpty { "Done" }

/** The engine's reasons, in words for a phone: what happened and what to do about it. */
internal fun friendly(error: String?): String = when {
    error == null -> "Something went wrong"
    "not a bot" in error -> "YouTube is limiting downloads for now. Try again in a while."
    "age" in error && "restrict" in error -> "Age-restricted on YouTube, and no other upload could stand in."
    "no YouTube result matched" in error -> "Couldn't find this song on YouTube."
    "not a Spotify" in error || "unsupported" in error -> "That link isn't a song, album or playlist Geet can read."
    "private" in error.lowercase() -> "This playlist is private. Only public ones can be downloaded."
    listOf("no such host", "network", "timeout").any { it in error.lowercase() } ->
        "No connection. It'll work once you're back online."
    else -> error
}
