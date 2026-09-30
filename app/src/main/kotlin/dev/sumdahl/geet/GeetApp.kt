package dev.sumdahl.geet

import android.app.Application
import android.graphics.Bitmap
import android.util.Size
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.request.crossfade
import coil3.size.pxOrElse
import dagger.hilt.android.HiltAndroidApp
import dev.sumdahl.geet.data.SongCover
import dev.sumdahl.geet.engine.Engine
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

@HiltAndroidApp
class GeetApp :
    Application(),
    SingletonImageLoader.Factory,
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var engine: Engine

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Python and ffmpeg unpack once per install (a few seconds); doing it now means the first share doesn't wait.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { engine.prepare() } }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(SongCoverFetcher.Factory()) }
            .crossfade(true)
            .build()
}

/** A saved song's embedded cover, which Android's media store extracts from the file (and caches). */
private class SongCoverFetcher(private val cover: SongCover, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val px = options.size.width.pxOrElse { DEFAULT_PX }.coerceAtMost(MAX_PX)
        val bitmap: Bitmap = runInterruptible(Dispatchers.IO) {
            options.context.contentResolver.loadThumbnail(cover.uri, Size(px, px), null)
        }
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<SongCover> {
        override fun create(data: SongCover, options: Options, imageLoader: ImageLoader) = SongCoverFetcher(data, options)
    }

    private companion object {
        const val DEFAULT_PX = 512
        const val MAX_PX = 1024
    }
}
