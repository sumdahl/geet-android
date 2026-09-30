package dev.sumdahl.geet.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sumdahl.geet.data.DownloadJob
import dev.sumdahl.geet.data.DownloadRepository
import dev.sumdahl.geet.data.SettingsStore
import dev.sumdahl.geet.engine.Engine
import dev.sumdahl.geet.engine.LinkInfo
import dev.sumdahl.geet.ui.common.firstLink
import dev.sumdahl.geet.ui.home.isSupportedLink
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ShareInfo {
    data object Reading : ShareInfo
    data object NotALink : ShareInfo
    data class Failed(val message: String?) : ShareInfo
    data class Ready(val info: LinkInfo) : ShareInfo
}

data class ShareState(
    val link: String? = null,
    val info: ShareInfo = ShareInfo.Reading,
    val format: String = "opus",
    val job: DownloadJob? = null,
    val coverColors: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val engine: Engine,
    private val downloads: DownloadRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val link = MutableStateFlow<String?>(null)
    private val info = MutableStateFlow<ShareInfo>(ShareInfo.Reading)
    private val format = MutableStateFlow("opus")
    private val jobId = MutableStateFlow<Long?>(null)
    private var opened: String? = null

    val state: StateFlow<ShareState> = combine(
        link,
        info,
        format,
        jobId.flatMapLatest { id -> id?.let(downloads::job) ?: flowOf(null) },
        settings.settings,
    ) { l, i, f, j, s -> ShareState(l, i, f, j, s.coverColors) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareState())

    /** Reads what was shared: the first link in it (apps wrap links in words) if it's one Geet can read. */
    fun open(shared: String?) {
        val found = firstLink(shared)?.takeIf(::isSupportedLink)
        if (found == opened && found != null) return
        opened = found
        link.value = found
        jobId.value = null
        if (found == null) {
            info.value = ShareInfo.NotALink
            return
        }
        info.value = ShareInfo.Reading
        viewModelScope.launch {
            val env = settings.engineEnvironment()
            format.value = env["GEET_FORMAT"] ?: "opus"
            info.value = runCatching { engine.info(found, env) }.fold({ ShareInfo.Ready(it) }, { ShareInfo.Failed(it.message) })
            if (info.value is ShareInfo.Ready && settings.settings.first().downloadOnShare) download()
        }
    }

    fun setFormat(f: String) {
        format.value = f
    }

    fun download() {
        val l = link.value ?: return
        if (jobId.value != null) return
        viewModelScope.launch {
            val chosen = format.value.takeIf { it != (settings.engineEnvironment()["GEET_FORMAT"] ?: "opus") }
            jobId.value = downloads.enqueue(l, (info.value as? ShareInfo.Ready)?.info, chosen)
        }
    }
}
