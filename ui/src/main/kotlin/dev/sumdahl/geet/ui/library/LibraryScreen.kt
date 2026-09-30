package dev.sumdahl.geet.ui.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sumdahl.geet.data.Song
import dev.sumdahl.geet.designsystem.component.CoverArt
import dev.sumdahl.geet.designsystem.component.MessageState
import dev.sumdahl.geet.ui.common.formatDuration
import dev.sumdahl.geet.ui.common.songs
import kotlinx.coroutines.launch

/**
 * Everything saved, by song, playlist, album or artist. Tap a song to play from there; long-press to select several;
 * delete gives a few seconds to undo. Songs saved before a reinstall need the audio permission to show, and the
 * screen offers it rather than hiding them silently.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpenPlayer: () -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LibraryTab.Songs) }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val confirmDelete = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    val groups = when (tab) {
        LibraryTab.Playlists -> state.playlists
        LibraryTab.Albums -> state.albums
        LibraryTab.Artists -> state.artists
        LibraryTab.Songs -> emptyList()
    }
    val openGroup = groups.firstOrNull { it.name == group }
    val shown: List<Song> = openGroup?.songs ?: state.songs

    BackHandler(enabled = selected.isNotEmpty() || openGroup != null || searching) {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            openGroup != null -> group = null
            else -> {
                searching = false
                viewModel.search("")
            }
        }
    }

    fun delete(songs: List<Song>) {
        selected = emptySet()
        viewModel.delete(songs) { confirmDelete.launch(IntentSenderRequest.Builder(it).build()) }
        scope.launch {
            val result = snackbar.showSnackbar(
                "Deleted ${if (songs.size == 1) songs.first().title else songs(songs.size)}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        }
    }

    Scaffold(
        topBar = {
            when {
                selected.isNotEmpty() -> TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, contentDescription = "Clear selection") } },
                    actions = {
                        IconButton(onClick = { viewModel.play(shown.filter { it.id in selected }); selected = emptySet(); onOpenPlayer() }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play selected")
                        }
                        IconButton(onClick = { delete(shown.filter { it.id in selected }) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete selected") }
                    },
                )
                searching -> TopAppBar(
                    title = {
                        val field = rememberTextFieldState(state.query)
                        LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect(viewModel::search) }
                        TextField(state = field, placeholder = { Text("Search your library") }, lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine, modifier = Modifier.fillMaxWidth())
                    },
                    navigationIcon = {
                        IconButton(onClick = { searching = false; viewModel.search("") }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search") }
                    },
                )
                else -> LargeFlexibleTopAppBar(
                    title = { Text(openGroup?.name ?: "Library") },
                    subtitle = { Text(songs(shown.size)) },
                    navigationIcon = {
                        if (openGroup != null) IconButton(onClick = { group = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                    },
                    actions = { IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, contentDescription = "Search") } },
                    scrollBehavior = scroll,
                )
            }
        },
        floatingActionButton = {
            if (shown.isNotEmpty() && selected.isEmpty() && (tab == LibraryTab.Songs || openGroup != null)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        viewModel.shuffle(shown)
                        onOpenPlayer()
                    },
                    icon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                    text = { Text("Shuffle") },
                    expanded = !scroll.state.collapsedFraction.let { it > 0.5f },
                    modifier = Modifier.padding(bottom = 88.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding())) {
            if (openGroup == null && !searching && selected.isEmpty()) {
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    LibraryTab.entries.forEach {
                        Tab(selected = tab == it, onClick = { tab = it }, text = { Text(it.label) })
                    }
                }
            }
            val hasAudioPermission = remember {
                val permission = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            AnimatedContent(targetState = Triple(tab, openGroup?.name, state.loaded), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "library") { (t, g, loaded) ->
                when {
                    !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    state.songs.isEmpty() && state.query.isNotBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        MessageState(Icons.Rounded.SearchOff, "No matches", "Nothing in your library matches \"${state.query}\".")
                    }
                    state.songs.isEmpty() -> EmptyLibrary(hasAudioPermission)
                    t == LibraryTab.Songs || g != null -> SongList(
                        songs = shown,
                        selected = selected,
                        onPlay = { i -> viewModel.play(shown, i); onOpenPlayer() },
                        onToggleSelect = { song ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selected = if (song.id in selected) selected - song.id else selected + song.id
                        },
                        selecting = selected.isNotEmpty(),
                        onPlayNext = { viewModel.playNext(it); scope.launch { snackbar.showSnackbar("Playing next: ${it.title}") } },
                        onAddToQueue = { viewModel.addToQueue(it); scope.launch { snackbar.showSnackbar("Added to queue") } },
                        onShare = { song ->
                            val share = Intent(Intent.ACTION_SEND).setType("audio/*").putExtra(Intent.EXTRA_STREAM, song.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(Intent.createChooser(share, "Share ${song.title}"))
                        },
                        onDelete = { delete(listOf(it)) },
                    )
                    else -> GroupGrid(groups, onOpen = { group = it.name }, onPlay = { viewModel.play(it.songs); onOpenPlayer() })
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(hasAudioPermission: Boolean) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MessageState(
                icon = Icons.Rounded.LibraryMusic,
                title = "Your library is empty",
                body = "Songs you download appear here, with their covers and lyrics, ready to play offline.",
            )
            if (!hasAudioPermission) {
                OutlinedButton(onClick = {
                    ask.launch(if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
                }) { Text("Show songs saved before") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongList(
    songs: List<Song>,
    selected: Set<Long>,
    selecting: Boolean,
    onPlay: (Int) -> Unit,
    onToggleSelect: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onDelete: (Song) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 180.dp)) {
        itemsIndexed(songs, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { i, song ->
            var menu by remember { mutableStateOf(false) }
            val isSelected = song.id in selected
            ListItem(
                headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        listOf(song.artist, formatDuration(song.durationMs)).filter(String::isNotBlank).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingContent = {
                    Box {
                        CoverArt(song.cover, Modifier.size(52.dp), MaterialTheme.shapes.medium)
                        if (isSelected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(32.dp))
                        }
                    }
                },
                trailingContent = {
                    if (!selecting) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${song.title}") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Play next") }, leadingIcon = { Icon(Icons.Rounded.QueuePlayNext, null) }, onClick = { menu = false; onPlayNext(song) })
                                DropdownMenuItem(text = { Text("Add to queue") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }, onClick = { menu = false; onAddToQueue(song) })
                                DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { menu = false; onShare(song) })
                                DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete(song) })
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .animateItem()
                    .combinedClickable(
                        onClick = { if (selecting) onToggleSelect(song) else onPlay(i) },
                        onLongClick = { onToggleSelect(song) },
                    ),
            )
        }
    }
}

@Composable
private fun GroupGrid(groups: List<Group>, onOpen: (Group) -> Unit, onPlay: (Group) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(groups, key = { it.name }) { g ->
            Card(onClick = { onOpen(g) }, modifier = Modifier.animateItem()) {
                Box {
                    CoverArt(g.cover, Modifier.fillMaxWidth().aspectRatio(1f), MaterialTheme.shapes.large)
                    androidx.compose.material3.FilledIconButton(
                        onClick = { onPlay(g) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${g.name}") }
                }
                Column(Modifier.padding(12.dp)) {
                    Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(songs(g.songs.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
