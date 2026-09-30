package dev.sumdahl.geet.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Where a shared link is in the queue. */
enum class JobState { Queued, Running, Done, Failed, Cancelled }

/** Where one song of a link is: the engine's stages, plus [Waiting] before it has reached any. */
enum class TrackStage { Waiting, Resolved, Downloading, Tagging, Done, Failed }

/** One shared or pasted link, and everything the Downloads screen shows about it. */
@Entity(tableName = "job")
data class DownloadJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val link: String,
    /** Until the link has been read, the link itself. */
    val name: String = link,
    /** track, album or playlist; empty until read */
    val kind: String = "",
    /** spotify, youtube, apple or deezer; empty until read */
    val source: String = "",
    val coverUrl: String? = null,
    val total: Int = 0,
    val state: JobState = JobState.Queued,
    /** While the engine reads a big playlist before downloading: how far it is ("Reading 12 of 50"). */
    val readingDone: Int = 0,
    val readingTotal: Int = 0,
    val saved: Int = 0,
    /** Already in the library, so nothing was downloaded. */
    val existing: Int = 0,
    val failed: Int = 0,
    /** Why the whole link failed (a bad link, YouTube's bot check…). */
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

@Entity(tableName = "track", primaryKeys = ["jobId", "index"])
data class DownloadTrack(
    val jobId: Long,
    val index: Int,
    /** "Artists - Title", as the engine names it. */
    val name: String,
    val stage: TrackStage = TrackStage.Waiting,
    val progress: Float = 0f,
    val path: String? = null,
    val error: String? = null,
    val warning: String? = null,
    val skipped: Boolean = false,
    val lyricsPath: String? = null,
)

class Converters {
    @TypeConverter fun jobState(s: JobState) = s.name

    @TypeConverter fun jobState(s: String) = JobState.valueOf(s)

    @TypeConverter fun stage(s: TrackStage) = s.name

    @TypeConverter fun stage(s: String) = TrackStage.valueOf(s)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM job ORDER BY createdAt DESC")
    fun jobs(): Flow<List<DownloadJob>>

    @Query("SELECT * FROM track ORDER BY jobId, `index`")
    fun tracks(): Flow<List<DownloadTrack>>

    @Query("SELECT * FROM job WHERE id = :id")
    suspend fun job(id: Long): DownloadJob?

    @Query("SELECT * FROM job WHERE id = :id")
    fun jobFlow(id: Long): Flow<DownloadJob?>

    @Query("SELECT * FROM track WHERE jobId = :jobId ORDER BY `index`")
    fun tracksOf(jobId: Long): Flow<List<DownloadTrack>>

    @Query("SELECT * FROM track WHERE jobId = :jobId AND `index` = :index")
    suspend fun track(jobId: Long, index: Int): DownloadTrack?

    @Insert suspend fun insert(job: DownloadJob): Long

    @Upsert suspend fun upsert(job: DownloadJob)

    @Upsert suspend fun upsert(track: DownloadTrack)

    @Query("DELETE FROM track WHERE jobId = :jobId")
    suspend fun clearTracks(jobId: Long)

    @Transaction
    suspend fun delete(jobId: Long) {
        clearTracks(jobId)
        deleteJob(jobId)
    }

    @Query("DELETE FROM job WHERE id = :jobId")
    suspend fun deleteJob(jobId: Long)

    @Query("SELECT id FROM job WHERE state IN ('Done', 'Failed', 'Cancelled')")
    suspend fun finishedIds(): List<Long>

    /** What reading the link says. Its own query, so it never overwrites the worker's counts, or they it. */
    @Query("UPDATE job SET name = :name, kind = :kind, source = :source, coverUrl = :cover, total = MAX(total, :total) WHERE id = :id")
    suspend fun describe(id: Long, name: String, kind: String, source: String, cover: String?, total: Int)

    @Query(
        """UPDATE job SET state = :state, readingDone = :readingDone, readingTotal = :readingTotal, total = MAX(total, :total),
           saved = :saved, existing = :existing, failed = :failed, error = :error, finishedAt = :finishedAt WHERE id = :id""",
    )
    suspend fun progress(
        id: Long,
        state: JobState,
        readingDone: Int,
        readingTotal: Int,
        total: Int,
        saved: Int,
        existing: Int,
        failed: Int,
        error: String?,
        finishedAt: Long?,
    )

    @Query("UPDATE job SET state = :state, finishedAt = :finishedAt WHERE id = :id")
    suspend fun setState(id: Long, state: JobState, finishedAt: Long? = null)
}

/** Writes the worker's view of a job without touching what reading the link filled in. */
internal suspend fun DownloadDao.progress(j: DownloadJob) =
    progress(j.id, j.state, j.readingDone, j.readingTotal, j.total, j.saved, j.existing, j.failed, j.error, j.finishedAt)

@Database(entities = [DownloadJob::class, DownloadTrack::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GeetDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // ponytail: destructive migration while the schema is young; the queue is history, and the songs themselves
    // stay on disk. Add real migrations once the schema settles.
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GeetDatabase =
        Room.databaseBuilder(context, GeetDatabase::class.java, "geet.db").fallbackToDestructiveMigration(dropAllTables = true).build()

    @Provides
    fun downloads(db: GeetDatabase): DownloadDao = db.downloads()
}
