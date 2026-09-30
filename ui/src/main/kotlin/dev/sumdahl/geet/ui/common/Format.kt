package dev.sumdahl.geet.ui.common

import android.text.format.DateUtils

/** 3:07, or 1:02:45 for an hour or more. */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total / 60 % 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "12 songs", "1 song". */
fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"

/** "5 min ago", "Yesterday". */
fun ago(atMs: Long): String =
    DateUtils.getRelativeTimeSpanString(atMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** The first link in some text (a share usually wraps it in words: "Listen to X on Spotify: https://…"). */
fun firstLink(text: String?): String? = text?.let { Regex("""https?://\S+""").find(it)?.value?.trimEnd('.', ',', ')') }
