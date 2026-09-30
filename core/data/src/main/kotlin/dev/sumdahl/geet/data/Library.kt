package dev.sumdahl.geet.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A saved song, as MediaStore knows it. */
data class Song(
    val id: Long,
    val uri: Uri,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val addedAt: Long,
    /** The playlist folder it was saved into, or empty for a single song. */
    val folder: String
) {
    val cover get() = SongCover(uri)
}

/** A song's embedded cover, for Coil (see the app's SongCoverFetcher): MediaStore extracts it from the file. */
data class SongCover(val uri: Uri)

/**
 * The songs in Geet's output folder, live: MediaStore tells us when files appear or go, whoever put them there, so a
 * download, a deletion in a file manager and another app's edits all show up without a refresh.
 */
@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore
) {
    /** Follows the output folder setting: changing it shows that folder's songs. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val songs: Flow<List<Song>> = settings.engineOverrides
        .map { overrides -> relativeRoot(overrides["GEET_OUTPUT"] ?: settings.phoneDefaults().getValue("GEET_OUTPUT")) }
        .distinctUntilChanged()
        .flatMapLatest { root ->
            callbackFlow {
                val observer = object : ContentObserver(null) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(query(root))
                    }
                }
                context.contentResolver.registerContentObserver(COLLECTION, true, observer)
                send(query(root))
                awaitClose { context.contentResolver.unregisterContentObserver(observer) }
            }
        }
        .conflate()
        .flowOn(Dispatchers.IO)

    /** "Music/Geet/" for /storage/emulated/0/Music/Geet: how MediaStore names a folder. */
    private fun relativeRoot(output: String): String =
        output.removePrefix(Environment.getExternalStorageDirectory().absolutePath).trim('/') + "/"

    private fun query(root: String): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        val songs = mutableListOf<Song>()
        context.contentResolver.query(
            COLLECTION,
            projection,
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?",
            arrayOf("$root%"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                songs += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(COLLECTION, id),
                    path = c.getString(1).orEmpty(),
                    title = c.getString(2).orEmpty(),
                    artist = c.getString(3)?.takeUnless { it == MediaStore.UNKNOWN_STRING }.orEmpty(),
                    album = c.getString(4)?.takeUnless { it == MediaStore.UNKNOWN_STRING }.orEmpty(),
                    durationMs = c.getLong(5),
                    addedAt = c.getLong(6) * 1000,
                    folder = c.getString(7).orEmpty().removePrefix(root).trim('/')
                )
            }
        }
        return songs
    }

    /**
     * Deletes songs and their lyrics. Songs Geet saved itself go straight away; ones it didn't (after a reinstall,
     * Android no longer counts them as Geet's) need the user's say-so, so a [PendingIntent] for the system's
     * confirmation comes back for the caller to launch.
     */
    suspend fun delete(songs: List<Song>): PendingIntent? = withContext(Dispatchers.IO) {
        val notOurs = songs.filter { song ->
            val gone = runCatching { context.contentResolver.delete(song.uri, null, null) > 0 }.getOrDefault(false)
            if (gone) File(song.path.substringBeforeLast('.') + ".lrc").delete()
            !gone
        }
        notOurs.takeIf { it.isNotEmpty() }?.let { MediaStore.createDeleteRequest(context.contentResolver, it.map(Song::uri)) }
    }

    private companion object {
        val COLLECTION: Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    }
}
