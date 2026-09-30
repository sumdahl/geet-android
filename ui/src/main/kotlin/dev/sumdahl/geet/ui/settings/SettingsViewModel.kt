package dev.sumdahl.geet.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sumdahl.geet.data.AppSettings
import dev.sumdahl.geet.data.SettingsStore
import dev.sumdahl.geet.engine.Engine
import dev.sumdahl.geet.engine.EngineSetting
import dev.sumdahl.geet.engine.Health
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An engine setting with the value it has on this phone: the user's, else Geet's phone default, else the engine's. */
data class SettingRow(val setting: EngineSetting, val value: String, val changed: Boolean)

sealed interface Task {
    data object Idle : Task
    data object Running : Task
    data class Done(val message: String) : Task
}

@HiltViewModel
class SettingsViewModel @Inject constructor(private val store: SettingsStore, private val engine: Engine) : ViewModel() {
    val app: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val engineSettings = flow { emit(runCatching { engine.settings() }.getOrDefault(emptyList())) }

    /** Every engine setting a phone can use, by key. Desktop-only ones (tools, the terminal player, the clipboard watcher) are left out. */
    val engineRows: StateFlow<Map<String, SettingRow>> = combine(engineSettings, store.engineOverrides) { all, overrides ->
        val defaults = store.phoneDefaults()
        all.filterNot { s -> HIDDEN.any { s.key == it || s.key.startsWith("$it.") } }.associate { s ->
            val override = overrides[s.env]
            s.key to SettingRow(s, override ?: defaults[s.env] ?: s.default, override != null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val versions: StateFlow<Pair<String, String>?> = flow {
        emit(runCatching { engine.version() }.getOrDefault("") to (engine.ytDlpVersion() ?: ""))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val update = MutableStateFlow<Task>(Task.Idle)
    val health = MutableStateFlow<Health?>(null)
    val checking = MutableStateFlow(false)

    fun app(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { store.update(transform) }

    fun set(env: String, value: String?) = viewModelScope.launch { store.setEngine(env, value) }

    fun updateYtDlp() = viewModelScope.launch {
        update.value = Task.Running
        update.value = Task.Done(
            runCatching { engine.updateYtDlp() }.fold(
                { status -> if (status == "ALREADY_UP_TO_DATE") "yt-dlp is already the newest" else "yt-dlp updated to ${engine.ytDlpVersion()}" },
                { "Couldn't update: ${it.message}" },
            ),
        )
    }

    fun checkHealth() = viewModelScope.launch {
        checking.value = true
        health.value = runCatching { engine.doctor(store.engineEnvironment()) }.getOrNull() ?: Health(healthy = false)
        checking.value = false
    }

    private companion object {
        val HIDDEN = listOf("tools", "player", "watch", "progress", "search.picker", "search.confirm", "index_path", "work_dir", "output", "youtube.cookies_from_browser")
    }
}
