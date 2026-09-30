package dev.sumdahl.geet.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sumdahl.geet.engine.Engine
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A song's lyrics. [synced] lines carry their start; plain ones have [Line.atMs] 0. */
data class Lyrics(val lines: List<Line>, val synced: Boolean) {
    data class Line(val atMs: Long, val text: String)

    /** The line being sung at [positionMs], or -1 before the first; a binary search, since it's asked every frame. */
    fun lineAt(positionMs: Long): Int {
        if (!synced) return -1
        var lo = 0
        var hi = lines.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].atMs <= positionMs) lo = mid + 1 else hi = mid
        }
        return lo - 1
    }

    companion object {
        private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

        /** Reads an LRC file: "[mm:ss.xx] text" lines, several stamps allowed per line; plain text if none has any. */
        fun parse(lrc: String): Lyrics {
            val synced = mutableListOf<Line>()
            val plain = mutableListOf<Line>()
            for (raw in lrc.lineSequence()) {
                val stamps = generateSequence(stamp.matchAt(raw, 0)) { m -> stamp.matchAt(raw, m.range.last + 1) }.toList()
                if (stamps.isEmpty()) {
                    val text = raw.trim()
                    // Skip metadata tags such as [ar:Artist].
                    if (text.isNotEmpty() && !(text.startsWith("[") && text.endsWith("]"))) plain += Line(0, text)
                    continue
                }
                val text = raw.substring(stamps.last().range.last + 1).trim()
                for (m in stamps) {
                    val (min, sec, frac) = m.destructured
                    val ms = frac.padEnd(3, '0').take(3).ifEmpty { "0" }.toLong()
                    synced += Line(min.toLong() * 60_000 + sec.toLong() * 1_000 + ms, text)
                }
            }
            return if (synced.isNotEmpty()) Lyrics(synced.sortedBy { it.atMs }, true) else Lyrics(plain, false)
        }
    }
}

/**
 * A saved song's lyrics. Looked for in order: the app's own copy, the .lrc beside the song (the engine writes one
 * when the folder allows), then the engine, which looks the song up by its tags (from its own cache, usually, since
 * it fetched the lyrics when downloading). What the engine finds is kept in the app, so each song is looked up once,
 * and a song with none is remembered too.
 */
@Singleton
class LyricsRepository @Inject constructor(@ApplicationContext context: Context, private val engine: Engine) {
    private val dir = File(context.filesDir, "lyrics")

    suspend fun lyrics(songPath: String): Lyrics? = withContext(Dispatchers.IO) {
        val key = MessageDigest.getInstance("SHA-1").digest(songPath.toByteArray()).joinToString("") { "%02x".format(it) }
        val kept = File(dir, "$key.lrc")
        val none = File(dir, "$key.none")
        val sidecar = File(songPath.substringBeforeLast('.') + ".lrc")
        when {
            kept.exists() -> Lyrics.parse(kept.readText())
            sidecar.canRead() -> Lyrics.parse(sidecar.readText())
            none.exists() -> null
            else -> {
                val found = runCatching { engine.lyrics(songPath) }.getOrNull()
                dir.mkdirs()
                if (found == null) {
                    none.createNewFile()
                    null
                } else {
                    val lyrics = Lyrics(found.lines.map { Lyrics.Line(it.atMs, it.text) }, found.synced)
                    kept.writeText(lyrics.toLrc())
                    lyrics
                }
            }
        }
    }

    private fun Lyrics.toLrc() = lines.joinToString("\n") { line ->
        if (!synced) {
            line.text
        } else {
            val cs = line.atMs / 10
            "[%02d:%02d.%02d]%s".format(cs / 6000, cs / 100 % 60, cs % 100, line.text)
        }
    }
}
