package dev.sumdahl.geet.ui.settings

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sumdahl.geet.data.Theme
import dev.sumdahl.geet.engine.Health

/**
 * Settings, laid out like Pixel's: grouped rounded sections, the common choices up front with plain names, and every
 * other engine setting under Advanced, generated from the engine itself, so a new engine setting appears here without
 * an app change.
 */
@Composable
fun SettingsScreen(appVersion: String, viewModel: SettingsViewModel = hiltViewModel()) {
    val app by viewModel.app.collectAsStateWithLifecycle()
    val rows by viewModel.engineRows.collectAsStateWithLifecycle()
    val versions by viewModel.versions.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    var advanced by rememberSaveable { mutableStateOf(false) }
    val value = { key: String -> rows[key]?.value.orEmpty() }
    val set = { key: String, v: String? -> rows[key]?.let { viewModel.set(it.setting.env, v) } }

    Scaffold(
        topBar = { LargeFlexibleTopAppBar(title = { Text("Settings") }, scrollBehavior = scroll) },
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Section("Appearance") {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                        Theme.entries.forEachIndexed { i, t ->
                            ToggleButton(
                                checked = app.theme == t,
                                onCheckedChange = { viewModel.app { s -> s.copy(theme = t) } },
                                shapes = when (i) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    Theme.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text(if (t == Theme.Amoled) "Black" else t.name) }
                        }
                    }
                    SwitchRow("Wallpaper colours", "Use your wallpaper's colours (Material You). Off uses Geet's own.", app.wallpaperColors) { on -> viewModel.app { it.copy(wallpaperColors = on) } }
                    SwitchRow("Cover colours", "Colour the player and share sheet from the song's cover.", app.coverColors) { on -> viewModel.app { it.copy(coverColors = on) } }
                }
            }
            item {
                Section("Downloads") {
                    ChoiceRow("Format", value("format"), listOf("opus" to "Opus", "mp3" to "MP3", "flac" to "FLAC")) { set("format", it) }
                    ChoiceRow(
                        "Quality",
                        value("bitrate"),
                        listOf("" to "Best available", "320k" to "320 kbps", "256k" to "256 kbps", "192k" to "192 kbps", "128k" to "128 kbps"),
                    ) { set("bitrate", it.ifEmpty { null }) }
                    SwitchRow("Lyrics", "Save each song's lyrics with it, synced when available.", value("lyrics") != "false") { set("lyrics", it.toString()) }
                    SwitchRow("Playlist folders", "Keep each playlist's songs in a folder of its own.", value("playlist_folder") != "false") { set("playlist_folder", it.toString()) }
                    ChoiceRow(
                        "When a song is already saved",
                        value("duplicates"),
                        listOf("link" to "Reuse it", "skip" to "Skip it", "download" to "Download again"),
                    ) { set("duplicates", it) }
                    val jobs = value("jobs").toIntOrNull() ?: 3
                    ListItem(
                        headlineContent = { Text("Downloads at once: $jobs") },
                        supportingContent = {
                            Column {
                                Text("More is faster, but YouTube may start refusing sooner.")
                                Slider(value = jobs.toFloat(), onValueChange = { set("jobs", it.toInt().toString()) }, valueRange = 1f..8f, steps = 6)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
            item {
                Section("Behaviour") {
                    SwitchRow("Download on share", "Start as soon as a link is shared to Geet, without the preview's button.", app.downloadOnShare) { on -> viewModel.app { it.copy(downloadOnShare = on) } }
                    SwitchRow("Wi-Fi only", "Wait for Wi-Fi (or another unmetered network) before downloading.", app.unmeteredOnly) { on -> viewModel.app { it.copy(unmeteredOnly = on) } }
                    SwitchRow("Copied links", "Offer a link you copied when you open Geet.", app.clipboardChip) { on -> viewModel.app { it.copy(clipboardChip = on) } }
                    TextRow("Search country", "The store searched first, as two letters (US, GB, IN, NP…).", value("search.country")) { set("search.country", it.uppercase().take(2)) }
                }
            }
            item {
                Section("Maintenance") {
                    ListItem(
                        headlineContent = { Text("Update yt-dlp") },
                        supportingContent = {
                            Text(
                                when (val u = update) {
                                    Task.Idle -> "YouTube changes often; a newer yt-dlp keeps downloads working. Now ${versions?.second.orEmpty()}"
                                    Task.Running -> "Updating…"
                                    is Task.Done -> u.message
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Rounded.SystemUpdateAlt, contentDescription = null) },
                        trailingContent = { if (update == Task.Running) LoadingIndicator(Modifier.size(32.dp)) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.clickable(enabled = update != Task.Running) { viewModel.updateYtDlp() },
                    )
                    ListItem(
                        headlineContent = { Text("Check health") },
                        supportingContent = { Text("Test the tools and the services Geet uses, and say how to fix what's wrong.") },
                        leadingContent = { Icon(Icons.Rounded.HealthAndSafety, contentDescription = null) },
                        trailingContent = { if (checking) LoadingIndicator(Modifier.size(32.dp)) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.clickable(enabled = !checking) { viewModel.checkHealth() },
                    )
                }
            }
            item {
                Section("Advanced") {
                    SwitchRow("Show every engine setting", "For fine-tuning how songs are matched and saved.", advanced) { advanced = it }
                    AnimatedVisibility(advanced) {
                        Column {
                            rows.values.filter { it.setting.key !in CURATED }.sortedBy { it.setting.key }.forEach { row -> EngineRow(row, onSet = { v -> viewModel.set(row.setting.env, v) }) }
                        }
                    }
                }
            }
            item {
                Section("About") {
                    ListItem(
                        headlineContent = { Text("Geet $appVersion") },
                        supportingContent = { Text(listOfNotNull(versions?.first?.ifBlank { null }, versions?.second?.ifBlank { null }?.let { "yt-dlp $it" }).joinToString(" · ").ifEmpty { "Free and open source · GPL-3.0" }) },
                        leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                    ListItem(
                        headlineContent = { Text("Source code") },
                        supportingContent = { Text("github.com/sumdahl/geet-android") },
                        leadingContent = { Icon(Icons.Rounded.Code, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/sumdahl/geet-android".toUri())) },
                    )
                }
            }
        }
    }
    health?.let { HealthDialog(it, onDismiss = { viewModel.health.value = null }) }
}

private val CURATED = setOf("format", "bitrate", "lyrics", "playlist_folder", "duplicates", "jobs", "search.country")

/** A Pixel-style group: a label, then rows on one rounded surface. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp))
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(content = content)
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun ChoiceRow(title: String, value: String, choices: List<Pair<String, String>>, onChoose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(choices.firstOrNull { it.first == value }?.second ?: value.ifEmpty { choices.first().second }) },
        trailingContent = {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { (v, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = { if (v == value) Icon(Icons.Rounded.CheckCircle, contentDescription = "Chosen") },
                        onClick = {
                            open = false
                            onChoose(v)
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable { open = true },
    )
}

@Composable
private fun TextRow(title: String, body: String, value: String, keyboard: KeyboardType = KeyboardType.Text, onDone: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value.ifEmpty { body }) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable { editing = true },
    )
    if (editing) {
        var text by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDone(text.trim())
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
        )
    }
}

/** Any engine setting, as its type suggests: a switch, a list to pick from, or a value to type. */
@Composable
private fun EngineRow(row: SettingRow, onSet: (String?) -> Unit) {
    val s = row.setting
    val title = s.key.replace('_', ' ').replace(".", " · ").replaceFirstChar { it.uppercase() }
    when {
        s.type == "bool" -> SwitchRow(title, s.usage, row.value == "true") { onSet(it.toString()) }
        s.choices.isNotEmpty() -> ChoiceRow(title, row.value, s.choices.map { it to it }) { onSet(it) }
        else -> TextRow(title, s.usage, row.value, if (s.type == "int") KeyboardType.Number else KeyboardType.Text) { onSet(it.ifEmpty { null }) }
    }
}

@Composable
private fun HealthDialog(health: Health, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (health.healthy) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, contentDescription = null) },
        title = { Text(if (health.healthy) "All good" else "Something needs attention") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(health.checks.size) { i ->
                    val c = health.checks[i]
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            when (c.status) {
                                "ok" -> Icons.Rounded.CheckCircle
                                "warn" -> Icons.Rounded.Warning
                                "fail" -> Icons.Rounded.ErrorOutline
                                else -> Icons.Rounded.RemoveCircleOutline
                            },
                            contentDescription = c.status,
                            tint = if (c.status == "fail") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.fillMaxWidth()) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            if (c.detail.isNotBlank()) Text(c.detail, style = MaterialTheme.typography.bodySmall)
                            if (c.fix.isNotBlank()) Text(c.fix, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (health.checks.isEmpty()) item { Text("Geet couldn't run its checks. Try again in a moment.") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
