package dev.sumdahl.geet.ui.library

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sumdahl.geet.data.LibraryRepository
import dev.sumdahl.geet.data.Song
import dev.sumdahl.geet.player.PlayerHolder
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) { Songs("Songs"), Playlists("Playlists"), Albums("Albums"), Artists("Artists") }

/** Songs gathered under a playlist folder, an album or an artist. */
data class Group(val name: String, val songs: List<Song>) {
    val cover get() = songs.first().cover
}

data class LibraryState(
    val loaded: Boolean = false,
    val songs: List<Song> = emptyList(),
    val query: String = "",
    val playlists: List<Group> = emptyList(),
    val albums: List<Group> = emptyList(),
    val artists: List<Group> = emptyList(),
)

@HiltViewModel
class LibraryViewModel @Inject constructor(private val library: LibraryRepository, private val player: PlayerHolder) : ViewModel() {
    private val query = MutableStateFlow("")

    /** Songs waiting out the undo window before they're really deleted: hidden, but still on disk. */
    private val pending = MutableStateFlow<Set<Long>>(emptySet())
    private var deletion: Job? = null
    private var pendingSongs: List<Song> = emptyList()

    val state: StateFlow<LibraryState> = combine(library.songs, query, pending) { all, q, hidden ->
        val songs = all.filter { it.id !in hidden && (q.isBlank() || q.lowercase() in "${it.title} ${it.artist} ${it.album}".lowercase()) }
        LibraryState(
            loaded = true,
            songs = songs,
            query = q,
            playlists = songs.filter { it.folder.isNotEmpty() }.groupBy { it.folder }.map { (k, v) -> Group(k.replace('-', ' '), v) }.sortedBy { it.name.lowercase() },
            albums = songs.filter { it.album.isNotBlank() }.groupBy { it.album }.map { (k, v) -> Group(k, v) }.sortedBy { it.name.lowercase() },
            artists = songs.groupBy { it.artist.substringBefore(", ").ifBlank { "Unknown artist" } }.map { (k, v) -> Group(k, v) }.sortedBy { it.name.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    fun search(q: String) = query.update { q }

    fun play(songs: List<Song>, start: Int = 0) = player.play(songs, start)

    fun shuffle(songs: List<Song>) = player.play(songs.shuffled(), 0, shuffle = true)

    fun playNext(song: Song) = player.playNext(song)

    fun addToQueue(song: Song) = player.addToQueue(song)

    /**
     * Hides [songs] now and deletes them once the undo window has passed. When some weren't saved by Geet (after a
     * reinstall), Android asks the user first; [onConfirm] receives the system's request to launch.
     */
    fun delete(songs: List<Song>, onConfirm: (PendingIntent) -> Unit) {
        commitPending()
        pendingSongs = songs
        pending.value = songs.map { it.id }.toSet()
        deletion = viewModelScope.launch {
            delay(UNDO_MS)
            library.delete(songs)?.let(onConfirm)
            pending.value = emptySet()
        }
    }

    fun undoDelete() {
        deletion?.cancel()
        pending.value = emptySet()
    }

    /** A second delete within the undo window finishes the first one straight away. */
    private fun commitPending() {
        val job = deletion ?: return
        if (job.isActive && pendingSongs.isNotEmpty()) {
            job.cancel()
            val songs = pendingSongs
            viewModelScope.launch { library.delete(songs) }
        }
    }

    private companion object {
        const val UNDO_MS = 4_000L
    }
}
