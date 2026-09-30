package dev.sumdahl.geet

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.sumdahl.geet.data.Notifier
import dev.sumdahl.geet.designsystem.GeetTheme
import dev.sumdahl.geet.ui.share.ShareSheet

/**
 * The share sheet, over whichever app shared the link. It also opens from the Quick Settings tile and the "Download
 * copied link" shortcut, which read the clipboard here: Android lets only the app in front read it.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {
    private val main: MainViewModel by viewModels()
    private var shared by mutableStateOf<String?>(null)
    private var wantsClipboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        read(intent)
        setContent {
            val settings by main.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            GeetTheme(s.theme.mode(), s.wallpaperColors) {
                ShareSheet(
                    link = shared,
                    onDismiss = ::finish,
                    onOpenApp = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .putExtra(Notifier.EXTRA_DESTINATION, Notifier.DESTINATION_DOWNLOADS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> shared = intent.dataString
            ACTION_FROM_CLIPBOARD -> wantsClipboard = true
        }
    }

    /** The clipboard can be read only once the window has focus. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && wantsClipboard) {
            wantsClipboard = false
            val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
            shared = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        }
    }

    companion object {
        const val ACTION_FROM_CLIPBOARD = "dev.sumdahl.geet.FROM_CLIPBOARD"
    }
}
