package dev.sumdahl.geet.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sumdahl.geet.data.Song
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one app-wide player, shared by every screen and [PlaybackService] (which puts it on the lock screen, in the
 * notification, on earbuds and in the car). It plays saved songs only, so a tap starts sound at once.
 *
 * The queue and position are kept across restarts: the mini player comes back where it was, paused.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlayerHolder @Inject constructor(@ApplicationContext private val context: Context) {
    val spectrum = Spectrum()
    private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)

    private var instance: ExoPlayer? = null

    /** Created on first use, and again after [release] (the service ends when nothing plays and the app is swiped away). */
    val player: ExoPlayer get() = instance ?: create().also { instance = it }

    private fun create(): ExoPlayer {
        val renderers = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(context: Context, floatOutput: Boolean, audioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setAudioProcessors(arrayOf(spectrum)).build()
        }
        return ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                // Pause for calls, duck for navigation prompts.
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { restore(it) }
    }

    /** Plays [songs] from [start], replacing the queue. */
    fun play(songs: List<Song>, start: Int = 0, shuffle: Boolean = false) {
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(songs.map(::item), start.coerceIn(0, (songs.size - 1).coerceAtLeast(0)), 0)
        player.prepare()
        player.play()
    }

    fun playNext(song: Song) = player.addMediaItem((player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount), item(song))

    fun addToQueue(song: Song) = player.addMediaItem(item(song))

    /** Saves the queue and position; called when playback pauses and when the app goes to the background. */
    fun save() {
        if (player.mediaItemCount == 0) return
        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
        prefs.edit {
            putString(
                KEY_QUEUE,
                items.joinToString(SEP) {
                    listOf(
                        it.mediaId,
                        it.localConfiguration?.uri,
                        it.mediaMetadata.title,
                        it.mediaMetadata.artist,
                        it.mediaMetadata.albumTitle
                    ).joinToString(FIELD)
                }
            )
            putInt(KEY_INDEX, player.currentMediaItemIndex)
            putLong(KEY_POSITION, player.currentPosition)
            putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled)
            putInt(KEY_REPEAT, player.repeatMode)
        }
    }

    private fun restore(player: Player) {
        val saved = prefs.getString(KEY_QUEUE, null) ?: return
        val items = saved.split(SEP).mapNotNull { row ->
            val f = row.split(FIELD)
            if (f.size < 5) return@mapNotNull null
            MediaItem.Builder()
                .setMediaId(f[0])
                .setUri(f[1].toUri())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(f[2]).setArtist(f[3]).setAlbumTitle(f[4]).build())
                .build()
        }
        if (items.isEmpty()) return
        player.shuffleModeEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
        player.repeatMode = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
        player.setMediaItems(items, prefs.getInt(KEY_INDEX, 0).coerceIn(0, items.size - 1), prefs.getLong(KEY_POSITION, 0))
        player.prepare()
    }

    fun release() {
        val p = instance ?: return
        save()
        p.release()
        instance = null
    }

    companion object {
        /** A song as a queue item; its mediaId is the file's path, which lyrics and the library key on. */
        fun item(song: Song): MediaItem = MediaItem.Builder()
            .setMediaId(song.path)
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()

        private const val KEY_QUEUE = "queue"
        private const val KEY_INDEX = "index"
        private const val KEY_POSITION = "position"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat"
        private const val SEP = "\u001e"
        private const val FIELD = "\u001f"
    }
}
