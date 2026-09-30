package dev.sumdahl.geet

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sumdahl.geet.data.AppSettings
import dev.sumdahl.geet.data.DownloadRepository
import dev.sumdahl.geet.data.JobState
import dev.sumdahl.geet.data.Notifier
import dev.sumdahl.geet.data.SettingsStore
import dev.sumdahl.geet.data.Theme
import dev.sumdahl.geet.designsystem.GeetTheme
import dev.sumdahl.geet.designsystem.ThemeMode
import dev.sumdahl.geet.designsystem.preferHighestRefreshRate
import dev.sumdahl.geet.player.PlaybackService
import dev.sumdahl.geet.ui.downloads.DownloadsScreen
import dev.sumdahl.geet.ui.home.HomeScreen
import dev.sumdahl.geet.ui.library.LibraryScreen
import dev.sumdahl.geet.ui.player.MiniPlayer
import dev.sumdahl.geet.ui.player.PlayerScreen
import dev.sumdahl.geet.ui.settings.SettingsScreen
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey

@Serializable data object Library : NavKey

@Serializable data object Downloads : NavKey

@Serializable data object Settings : NavKey

@Serializable data object Player : NavKey

private enum class Tab(val key: NavKey, val label: String, val icon: ImageVector) {
    HomeTab(Home, "Home", Icons.Rounded.Home),
    LibraryTab(Library, "Library", Icons.Rounded.LibraryMusic),
    DownloadsTab(Downloads, "Downloads", Icons.Rounded.Download),
    SettingsTab(Settings, "Settings", Icons.Rounded.Settings)
}

@HiltViewModel
class MainViewModel @Inject constructor(store: SettingsStore, downloads: DownloadRepository) : ViewModel() {
    /** Null until DataStore answers; the splash stays up until then, so the first frame has the right theme. */
    val settings = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeDownloads = downloads.jobs.map { jobs -> jobs.count { it.job.state == JobState.Queued || it.job.state == JobState.Running } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var controller: ListenableFuture<MediaController>? = null

    /** A tab asked for by a notification or a shortcut, taken once navigation is ready. */
    private var pending by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition { viewModel.settings.value == null }
            // The logo swells and fades as the app appears behind it.
            setOnExitAnimationListener { splash ->
                val icon: View = splash.iconView
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.4f),
                        ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.4f),
                        ObjectAnimator.ofFloat(splash.view, View.ALPHA, 1f, 0f)
                    )
                    duration = SPLASH_EXIT_MS
                    doOnEnd { splash.remove() }
                    start()
                }
            }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pending = intent.getStringExtra(Notifier.EXTRA_DESTINATION)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            GeetTheme(s.theme.mode(), s.wallpaperColors) {
                GeetShell(viewModel, s, pending, onConsumed = { pending = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_DESTINATION)?.let { pending = it }
    }

    override fun onStart() {
        super.onStart()
        preferHighestRefreshRate()
        // Binding a controller starts the playback service, which keeps music going with the app in the background.
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        controller?.let(MediaController::releaseFuture)
        controller = null
        super.onStop()
    }

    private companion object {
        const val SPLASH_EXIT_MS = 320L
    }
}

internal fun Theme.mode() = when (this) {
    Theme.System -> ThemeMode.System
    Theme.Light -> ThemeMode.Light
    Theme.Dark -> ThemeMode.Dark
    Theme.Amoled -> ThemeMode.Amoled
}

private fun AnimatorSet.doOnEnd(action: () -> Unit) = addListener(object : android.animation.AnimatorListenerAdapter() {
    override fun onAnimationEnd(animation: android.animation.Animator) = action()
})

@Composable
private fun GeetShell(viewModel: MainViewModel, settings: AppSettings, pending: String?, onConsumed: () -> Unit) {
    val backStack = rememberNavBackStack(Home)
    val active by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val top = backStack.lastOrNull()
    val onTab = Tab.entries.any { it.key == top }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Notifications show download progress, so ask once, up front (Android 13+).
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(pending) {
        when (pending) {
            "downloads" -> backStack.switchTo(Downloads)
            "library" -> backStack.switchTo(Library)
            "player" -> if (top != Player) backStack.add(Player)
        }
        if (pending != null) onConsumed()
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = top == tab.key,
                    onClick = { backStack.switchTo(tab.key) },
                    icon = {
                        if (tab == Tab.DownloadsTab && active > 0) {
                            BadgedBox(badge = { Badge { Text("$active") } }) { Icon(tab.icon, contentDescription = null) }
                        } else {
                            Icon(tab.icon, contentDescription = null)
                        }
                    },
                    label = { Text(tab.label) }
                )
            }
        },
        layoutType = if (onTab) {
            NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
        } else {
            NavigationSuiteType.None
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                // Tabs fade through; the player rises from the mini player and sinks back.
                transitionSpec = {
                    if (targetState.key == Player) {
                        slideInVertically(tween(420)) { it } + fadeIn() togetherWith fadeOut(tween(200))
                    } else {
                        (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(280), initialScale = 0.96f)) togetherWith fadeOut(tween(120))
                    }
                },
                popTransitionSpec = {
                    if (initialState.key == Player) {
                        fadeIn(tween(220)) togetherWith slideOutVertically(tween(360)) { it } + fadeOut(tween(300))
                    } else {
                        fadeIn(tween(220)) togetherWith (fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 0.96f))
                    }
                },
                predictivePopTransitionSpec = {
                    fadeIn(tween(220)) togetherWith (fadeOut(tween(200)) + scaleOut(tween(240), targetScale = 0.9f))
                },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                entryProvider = entryProvider {
                    entry<Home> {
                        HomeScreen(
                            clipboardChip = settings.clipboardChip,
                            onOpenDownloads = { backStack.switchTo(Downloads) },
                            onOpenPlayer = { backStack.add(Player) }
                        )
                    }
                    entry<Library> { LibraryScreen(onOpenPlayer = { backStack.add(Player) }) }
                    entry<Downloads> { DownloadsScreen(onOpenLibrary = { backStack.switchTo(Library) }) }
                    entry<Settings> { SettingsScreen(appVersion = BuildConfig.VERSION_NAME) }
                    entry<Player> { PlayerScreen(onCollapse = { backStack.removeLastOrNull() }, coverColors = settings.coverColors) }
                }
            )
            if (onTab) {
                MiniPlayer(
                    onOpen = { backStack.add(Player) },
                    coverColors = settings.coverColors,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

/** Tabs replace the stack, so back from any tab leaves the app instead of cycling through tabs. */
private fun NavBackStack<NavKey>.switchTo(key: NavKey) {
    if (lastOrNull() == key && size == 1) return
    clear()
    add(key)
}
