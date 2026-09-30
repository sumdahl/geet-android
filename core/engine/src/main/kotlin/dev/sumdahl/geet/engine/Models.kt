package dev.sumdahl.geet.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors of the engine's --json output (geet docs/03-communication-contract.md).
// The engine only ever adds fields, so decoding ignores unknown ones and every field has a default.

/** One NDJSON line of `geet download --json`: a track crossing a stage. */
@Serializable
data class DownloadEvent(
    val track: String = "",
    /** reading, resolved, downloading, tagging, done, failed */
    val stage: String = "",
    val error: String? = null,
    val fatal: Boolean = false,
    @SerialName("spotify_id") val id: String = "",
    val index: Int = 0,
    val total: Int = 0,
    val path: String? = null,
    @SerialName("youtube_url") val youtubeUrl: String? = null,
    val skipped: Boolean = false,
    @SerialName("duplicate_of") val duplicateOf: String? = null,
    val warning: String? = null,
    /** reading only: index, spotify or tags */
    val step: String? = null,
    val progress: Float? = null,
    @SerialName("lyrics_path") val lyricsPath: String? = null
)

/** `geet info <link> --json`: what a link is, before anything downloads. */
@Serializable
data class LinkInfo(
    /** track, album or playlist */
    val kind: String = "",
    /** spotify, youtube, apple or deezer */
    val source: String = "",
    val name: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val total: Int = 0,
    val tracks: List<InfoTrack> = emptyList()
)

@Serializable
data class InfoTrack(
    val ref: String = "",
    val title: String = "",
    val artists: List<String> = emptyList(),
    @SerialName("duration_ms") val durationMs: Long = 0,
    val explicit: Boolean = false,
    @SerialName("cover_url") val coverUrl: String? = null
)

/** A row of `geet search --json` and `geet trending --json` (which adds [rank]). */
@Serializable
data class CatalogResult(
    val rank: Int = 0,
    val ref: String = "",
    val title: String = "",
    val artists: List<String> = emptyList(),
    val album: String = "",
    val year: Int = 0,
    @SerialName("duration_ms") val durationMs: Long = 0,
    @SerialName("cover_url") val coverUrl: String? = null,
    val explicit: Boolean = false,
    val clean: Boolean = false
)

/** `geet lyrics <file> --json`. */
@Serializable
data class LyricsResult(
    val path: String = "",
    @SerialName("lrc_path") val lrcPath: String? = null,
    val synced: Boolean = false,
    val lines: List<LyricLine> = emptyList()
)

@Serializable
data class LyricLine(@SerialName("at_ms") val atMs: Long = 0, val text: String = "")

/** One entry of `geet config settings --json`: every engine setting, so the settings screen is generated. */
@Serializable
data class EngineSetting(
    val key: String,
    val env: String,
    /** string, bool, int, duration or list */
    val type: String,
    val default: String = "",
    val value: String = "",
    val secret: Boolean = false,
    val usage: String = "",
    val choices: List<String> = emptyList()
)

/** `geet doctor --json`. */
@Serializable
data class Health(val healthy: Boolean = false, val version: String = "", val checks: List<HealthCheck> = emptyList())

/** One check of [Health]: status is ok, warn, fail or skip. */
@Serializable
data class HealthCheck(
    val group: String = "",
    val name: String = "",
    val status: String = "",
    val detail: String = "",
    val fix: String = ""
)
