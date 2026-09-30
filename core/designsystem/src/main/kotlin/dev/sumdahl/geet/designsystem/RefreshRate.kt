package dev.sumdahl.geet.designsystem

import android.app.Activity
import android.os.Build
import android.view.Display

/**
 * Asks for the display's fastest refresh mode at the current resolution (e.g. 120 Hz).
 * Many OEM "smart refresh" policies keep apps at 60 Hz unless the window votes for more,
 * which makes scrolling look jittery on high-refresh screens.
 */
fun Activity.preferHighestRefreshRate() = preferMode { modes -> modes.maxByOrNull { it.refreshRate } }

/**
 * For video: TV is 25–50 fps, so 60 Hz shows every frame while the panel draws far fewer
 * refreshes than at 120/144 Hz, which saves battery for the whole time something is playing.
 */
fun Activity.preferVideoRefreshRate() = preferMode { modes ->
    modes.filter { it.refreshRate >= VIDEO_MIN_HZ }.minByOrNull { it.refreshRate } ?: modes.minByOrNull { it.refreshRate }
}

private const val VIDEO_MIN_HZ = 59f

/** Picks among the modes at the current resolution, so switching rate never changes resolution. */
private inline fun Activity.preferMode(choose: (List<Display.Mode>) -> Display.Mode?) {
    val display = currentDisplay() ?: return
    val current = display.mode
    val sameResolution = display.supportedModes.filter {
        it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
    }
    val mode = choose(sameResolution) ?: return
    // Always re-send: OEM refresh managers can drop the vote (e.g. mid-rotation after video),
    // while the window still reports the old id, so a "same id, skip" check would never recover.
    window.attributes = window.attributes.apply { preferredDisplayModeId = mode.modeId }
}

/** Fastest refresh rate the current display offers at its current resolution. */
fun Activity.maxRefreshRate(): Float? {
    val display = currentDisplay() ?: return null
    val current = display.mode
    return display.supportedModes
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .maxOfOrNull { it.refreshRate }
}

/** Refresh rate the display is running at right now. */
fun Activity.currentRefreshRate(): Float? = currentDisplay()?.refreshRate

@Suppress("DEPRECATION")
private fun Activity.currentDisplay(): Display? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
