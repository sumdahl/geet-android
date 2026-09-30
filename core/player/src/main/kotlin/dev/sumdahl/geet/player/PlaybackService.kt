package dev.sumdahl.geet.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Puts the shared player on the lock screen, in the media notification, on earbuds, watches and in the car. */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var holder: PlayerHolder

    private var session: MediaSession? = null

    private val saver = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) holder.save()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = holder.save()
    }

    override fun onCreate() {
        super.onCreate()
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        holder.player.addListener(saver)
        session = MediaSession.Builder(this, holder.player)
            .apply { launch?.let(::setSessionActivity) }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    /** Swiping Geet away keeps the music going; with nothing playing, the service goes too. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        holder.player.removeListener(saver)
        session?.release()
        session = null
        holder.release()
        super.onDestroy()
    }
}
