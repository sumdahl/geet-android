package dev.sumdahl.geet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sumdahl.geet.data.DownloadRepository
import dev.sumdahl.geet.data.JobState
import dev.sumdahl.geet.data.LibraryRepository
import dev.sumdahl.geet.data.SettingsStore
import dev.sumdahl.geet.data.Song
import dev.sumdahl.geet.engine.CatalogResult
import dev.sumdahl.geet.engine.Engine
import dev.sumdahl.geet.player.PlayerHolder
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/** What a catalog row's button shows: download it, it's on its way, or it's in the library. */
enum class RowState { Idle, Downloading, Saved, Failed }

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val engine: Engine,
    private val downloads: DownloadRepository,
    private val player: PlayerHolder,
    library: LibraryRepository,
    settings: SettingsStore,
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    val query = MutableStateFlow("")

    /** Which catalog refs were queued from here, so each row can follow its own download. */
    private val queued = MutableStateFlow<Map<String, Long>>(emptyMap())

    val trending: StateFlow<Load<List<CatalogResult>>> = refresh.mapLatest { n ->
        runCatching { engine.trending(TRENDING, refresh = n > 0, settings.engineEnvironment()) }
            .fold({ Load.Ready(it) }, { Load.Failed(it.message ?: "Couldn't read the chart") })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Load.Loading)

    val results: StateFlow<Load<List<CatalogResult>>?> = query.map { it.trim() }.distinctUntilChanged().debounce(SEARCH_DELAY_MS).mapLatest { q ->
        if (q.length < 2) {
            null
        } else {
            runCatching { engine.search(q, settings.engineEnvironment()) }
                .fold({ Load.Ready(it) }, { Load.Failed(it.message ?: "Search didn't answer") })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recent: StateFlow<List<Song>> = library.songs.map { it.take(RECENT) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The link downloading right now, for the banner at the top. */
    val active = downloads.jobs.map { list -> list.firstOrNull { it.job.state == JobState.Running } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val rowStates: StateFlow<Map<String, RowState>> = combine(queued, downloads.jobs) { refs, jobs ->
        val byId = jobs.associateBy { it.job.id }
        refs.mapValues { (_, id) ->
            when (byId[id]?.job?.state) {
                JobState.Done -> RowState.Saved
                JobState.Failed, JobState.Cancelled -> RowState.Failed
                null -> RowState.Idle
                else -> RowState.Downloading
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun retryTrending() {
        refresh.value++
    }

    fun download(ref: String) = viewModelScope.launch { queued.value += ref to downloads.enqueue(ref) }

    fun downloadLink(link: String) = viewModelScope.launch { downloads.enqueue(link) }

    fun play(songs: List<Song>, start: Int) = player.play(songs, start)

    private companion object {
        const val TRENDING = 30
        const val RECENT = 12
        const val SEARCH_DELAY_MS = 350L
    }
}

/** Links Geet can read: Spotify, YouTube and YouTube Music, Apple Music and Deezer. */
fun isSupportedLink(text: String) =
    Regex("""https?://(open\.spotify\.com|(www\.|m\.|music\.)?youtube\.com|youtu\.be|music\.apple\.com|(www\.)?deezer\.com|link\.deezer\.com)/\S+""")
        .containsMatchIn(text)

