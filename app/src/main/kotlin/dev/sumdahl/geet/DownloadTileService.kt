package dev.sumdahl.geet

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/** A Quick Settings tile: copy a link anywhere, pull down the shade, tap Geet, and the share sheet opens with it. */
class DownloadTileService : TileService() {
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, ShareActivity::class.java)
            .setAction(ShareActivity.ACTION_FROM_CLIPBOARD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            // The only way before Android 14, and still honoured there.
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
