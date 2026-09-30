package dev.sumdahl.geet.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The engine only ever adds fields, so unknown ones are ignored and nulls fall back to defaults. */
internal val engineJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** The engine stopped with a fatal error (exit code 2): a bad link, a missing tool, a failed read. */
class EngineException(val exitCode: Int, message: String) : Exception(message)

/**
 * Runs the bundled geet CLI (`libgeet.so`) and decodes its --json output.
 *
 * geet shells out to yt-dlp and ffmpeg. On Android those come from youtubedl-android: Python plus the yt-dlp
 * zipapp, ffmpeg, ffprobe and QuickJS (which yt-dlp needs for YouTube's JavaScript challenges), all shipped as
 * native libraries. [prepare] unpacks them once; [environment] points geet at them the way youtubedl-android
 * itself launches Python.
 *
 * [settings] are engine settings as `GEET_*` environment variables (from `geet config settings --json`),
 * which override the engine's defaults.
 */
@Singleton
class Engine @Inject constructor(@ApplicationContext private val context: Context) {
    private val json = engineJson
    private val prepared = Mutex()
    private var ready = false

    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val geet = File(nativeDir, "libgeet.so")

    /** Unpacks Python and ffmpeg on first use (a few seconds, once per install or update). */
    suspend fun prepare() = prepared.withLock {
        if (ready) return@withLock
        withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
        }
        ready = true
    }

    /** Fetches the newest yt-dlp release. YouTube breaks old ones often, so this matters more than app updates. */
    suspend fun updateYtDlp(): String? = withContext(Dispatchers.IO) {
        prepare()
        YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)?.name
    }

    fun ytDlpVersion(): String? = YoutubeDL.getInstance().version(context)

    private fun environment(settings: Map<String, String>): Map<String, String> {
        val base = File(context.noBackupFilesDir, "youtubedl-android")
        val python = File(base, "packages/python/usr").absolutePath
        val ffmpegLibs = File(base, "packages/ffmpeg/usr/lib").absolutePath
        val cache = context.cacheDir.absolutePath
        val lib = { name: String -> File(nativeDir, name).absolutePath }
        val ytDlpArgs = "--js-runtimes quickjs:${lib("libqjs.so")} --ffmpeg-location ${lib("libffmpeg.so")} --cache-dir $cache/yt-dlp"
        return mapOf(
            "LD_LIBRARY_PATH" to "$python/lib:$ffmpegLibs",
            "SSL_CERT_FILE" to "$python/etc/tls/cert.pem",
            "PYTHONHOME" to python,
            "HOME" to python,
            "TMPDIR" to cache,
            "PATH" to System.getenv("PATH").orEmpty() + ":" + nativeDir.absolutePath,
            "XDG_CACHE_HOME" to cache,
            "XDG_DATA_HOME" to File(context.filesDir, "data").absolutePath,
            "GEET_CONFIG" to File(context.filesDir, "config.toml").absolutePath,
            "GEET_TOOLS_YT_DLP" to lib("libpython.so"),
            "GEET_TOOLS_YT_DLP_ARGS" to File(base, "yt-dlp/yt-dlp").absolutePath,
            "GEET_TOOLS_FFMPEG" to lib("libffmpeg.so"),
            "GEET_TOOLS_FFPROBE" to lib("libffprobe.so"),
            "GEET_PROGRESS" to "never"
        ) + settings + ("GEET_YOUTUBE_EXTRA_ARGS" to listOfNotNull(ytDlpArgs, settings["GEET_YOUTUBE_EXTRA_ARGS"]).joinToString(" "))
    }

    /**
     * Runs geet and emits its stdout lines as they come. Cancelling the collector stops geet with SIGTERM, which it
     * handles like Ctrl+C (it cleans up its work files); it's killed if it hasn't exited 3 s later. Exit codes 0 and
     * 1 (a partial failure, no lyrics, an unhealthy check) end the flow normally; anything else throws
     * [EngineException] with geet's last stderr lines.
     */
    fun lines(args: List<String>, settings: Map<String, String> = emptyMap()): Flow<String> = channelFlow {
        prepare()
        val process = ProcessBuilder(listOf(geet.absolutePath) + args)
            .directory(context.cacheDir)
            .apply { environment().putAll(environment(settings)) }
            .start()
        val stderr = ArrayDeque<String>()
        launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().forEachLine { line ->
                synchronized(stderr) {
                    stderr.addLast(line)
                    if (stderr.size > STDERR_LINES) stderr.removeFirst()
                }
            }
        }
        val stdout = launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().forEachLine { trySendBlocking(it) }
        }
        try {
            stdout.join()
            val code = runInterruptible(Dispatchers.IO) { process.waitFor() }
            if (code > 1) {
                val why = synchronized(stderr) {
                    stderr.lastOrNull { it.startsWith("geet: ") }?.removePrefix("geet: ")
                        ?: stderr.joinToString("\n")
                }
                throw EngineException(code, why.ifBlank { "geet exited with code $code" })
            }
        } finally {
            if (process.isAlive) {
                process.destroy()
                if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
            }
        }
    }

    private suspend fun output(args: List<String>, settings: Map<String, String> = emptyMap()): String =
        lines(args, settings).toList().joinToString("\n")

    /** Downloads a link, emitting each track's stage changes (NDJSON), per the settings. */
    fun download(link: String, settings: Map<String, String>): Flow<DownloadEvent> =
        lines(listOf("download", "--json", link), settings).mapNotNull { line ->
            runCatching { json.decodeFromString<DownloadEvent>(line) }.getOrNull()
        }

    suspend fun info(link: String, settings: Map<String, String> = emptyMap()): LinkInfo =
        json.decodeFromString(output(listOf("info", "--json", link), settings))

    suspend fun search(query: String, settings: Map<String, String> = emptyMap()): List<CatalogResult> =
        json.decodeFromString(output(listOf("search", "--json", query), settings))

    suspend fun trending(limit: Int, refresh: Boolean, settings: Map<String, String> = emptyMap()): List<CatalogResult> =
        json.decodeFromString(
            output(listOfNotNull("trending", "--json", "--limit", limit.toString(), "--refresh".takeIf { refresh }), settings)
        )

    /** Finds a saved song's lyrics and writes its .lrc; null when the song has none anywhere. */
    suspend fun lyrics(file: String): LyricsResult? =
        json.decodeFromString<LyricsResult>(output(listOf("lyrics", "--json", file))).takeIf { it.lines.isNotEmpty() }

    suspend fun settings(): List<EngineSetting> = json.decodeFromString(output(listOf("config", "settings", "--json")))

    suspend fun doctor(settings: Map<String, String>): Health = json.decodeFromString(output(listOf("doctor", "--json"), settings))

    suspend fun version(): String = output(listOf("version")).trim()

    private companion object {
        const val STDERR_LINES = 20
    }
}
