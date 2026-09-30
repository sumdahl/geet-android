package dev.sumdahl.geet.data

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Theme { System, Light, Dark, Amoled }

/** The app's own settings; everything about downloading is an engine setting (see [SettingsStore.engineOverrides]). */
data class AppSettings(
    val theme: Theme = Theme.System,
    /** Colours from the wallpaper (Material You) rather than Geet's own. */
    val wallpaperColors: Boolean = true,
    /** Colour the player and share sheet from the song's cover. */
    val coverColors: Boolean = true,
    /** Start downloading the moment a link is shared, without the preview's button. */
    val downloadOnShare: Boolean = false,
    /** Only download on Wi-Fi (or any unmetered network). */
    val unmeteredOnly: Boolean = false,
    /** Offer a link found on the clipboard when the app opens. */
    val clipboardChip: Boolean = true,
)

private val Context.store by preferencesDataStore("settings")
private val THEME = stringPreferencesKey("theme")
private val WALLPAPER = booleanPreferencesKey("wallpaper_colors")
private val COVER = booleanPreferencesKey("cover_colors")
private val ON_SHARE = booleanPreferencesKey("download_on_share")
private val UNMETERED = booleanPreferencesKey("unmetered_only")
private val CLIPBOARD = booleanPreferencesKey("clipboard_chip")
private const val ENGINE_PREFIX = "engine:"

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    val settings: Flow<AppSettings> = context.store.data.map { p ->
        AppSettings(
            theme = p[THEME]?.let { name -> Theme.entries.firstOrNull { it.name == name } } ?: Theme.System,
            wallpaperColors = p[WALLPAPER] ?: true,
            coverColors = p[COVER] ?: true,
            downloadOnShare = p[ON_SHARE] ?: false,
            unmeteredOnly = p[UNMETERED] ?: false,
            clipboardChip = p[CLIPBOARD] ?: true,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.store.edit { p ->
            val s = transform(settings.first())
            p[THEME] = s.theme.name
            p[WALLPAPER] = s.wallpaperColors
            p[COVER] = s.coverColors
            p[ON_SHARE] = s.downloadOnShare
            p[UNMETERED] = s.unmeteredOnly
            p[CLIPBOARD] = s.clipboardChip
        }
    }

    /** Engine settings the user changed, by environment variable (`GEET_FORMAT` → `mp3`). */
    val engineOverrides: Flow<Map<String, String>> = context.store.data.map { p ->
        p.asMap().mapNotNull { (key, value) ->
            key.name.removePrefix(ENGINE_PREFIX).takeIf { key.name.startsWith(ENGINE_PREFIX) }?.let { it to value.toString() }
        }.toMap()
    }

    /** Sets an engine setting; null goes back to Geet's default for it. */
    suspend fun setEngine(env: String, value: String?) {
        context.store.edit { p ->
            val key: Preferences.Key<String> = stringPreferencesKey(ENGINE_PREFIX + env)
            if (value == null) p.remove(key) else p[key] = value
        }
    }

    /** What every engine run gets: Geet's phone defaults, then the user's changes on top. */
    suspend fun engineEnvironment(): Map<String, String> = phoneDefaults() + engineOverrides.first()

    /**
     * Where a phone differs from the desktop engine's defaults: songs go to the shared Music/Geet folder so every
     * music app sees them, temporary files stay in the cache (the Music folder only takes audio files), fewer
     * parallel downloads suit a phone's connection and battery, and search looks in the phone's own country's store.
     */
    fun phoneDefaults(): Map<String, String> = mapOf(
        "GEET_OUTPUT" to musicFolder().absolutePath,
        "GEET_WORK_DIR" to File(context.cacheDir, "work").absolutePath,
        "GEET_JOBS" to "3",
        "GEET_RESOLVE_JOBS" to "6",
        "GEET_SEARCH_COUNTRY" to (Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"),
    )

    fun musicFolder(): File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Geet")
}
