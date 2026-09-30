package dev.sumdahl.geet.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sumdahl.geet.data.Lyrics
import dev.sumdahl.geet.data.LyricsRepository
import dev.sumdahl.geet.data.SongCover
import dev.sumdahl.geet.player.PlayerHolder
import dev.sumdahl.geet.player.Spectrum
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A song in the queue, as the player screens show it. */
data class QueueSong(val path: String, val uri: Uri?, val title: String, val artist: String, val album: String) {
    val cover get() = uri?.let(::SongCover)
}

data class PlayerState(
    val queue: List<QueueSong> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF
) {
    val current: QueueSong? get() = queue.getOrNull(index)
}

sealed interface LyricsState {
    data object Loading : LyricsState
    data object None : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(private val holder: PlayerHolder, lyrics: LyricsRepository) : ViewModel() {
    val player: Player get() = holder.player
    val spectrum: Spectrum get() = holder.spectrum

    val state: StateFlow<PlayerState> = callbackFlow {
        val p = holder.player
        fun snapshot() = PlayerState(
            queue = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).toQueueSong() },
            index = p.currentMediaItemIndex.takeIf { p.mediaItemCount > 0 } ?: -1,
            playing = p.isPlaying,
            durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            shuffle = p.shuffleModeEnabled,
            repeat = p.repeatMode
        )
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                trySend(snapshot())
            }
        }
        p.addListener(listener)
        send(snapshot())
        awaitClose { p.removeListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerState())

    val lyrics: StateFlow<LyricsState> = state.map { it.current?.path }.distinctUntilChanged().flatMapLatest { path ->
        flow {
            if (path == null) return@flow emit(LyricsState.None)
            emit(LyricsState.Loading)
            emit(lyrics.lyrics(path)?.takeIf { it.lines.isNotEmpty() }?.let(LyricsState::Ready) ?: LyricsState.None)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsState.Loading)

    fun toggle() = if (player.isPlaying) player.pause() else player.play()

    fun next() = player.seekToNext()

    /** Back to the start of the song, or to the one before when it has only just begun (as every player does). */
    fun previous() = player.seekToPrevious()

    fun seekTo(ms: Long) = player.seekTo(ms)

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playAt(index: Int) {
        player.seekTo(index, 0)
        player.play()
    }

    fun move(from: Int, to: Int) = player.moveMediaItem(from, to)

    fun remove(index: Int) = player.removeMediaItem(index)

    private fun MediaItem.toQueueSong() = QueueSong(
        path = mediaId,
        uri = localConfiguration?.uri,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty()
    )
}
