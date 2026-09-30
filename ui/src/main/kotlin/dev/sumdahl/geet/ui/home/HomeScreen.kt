package dev.sumdahl.geet.ui.home

import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberSearchBarState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.sumdahl.geet.data.JobWithTracks
import dev.sumdahl.geet.designsystem.component.CoverArt
import dev.sumdahl.geet.designsystem.component.MessageState
import dev.sumdahl.geet.engine.CatalogResult
import dev.sumdahl.geet.ui.common.firstLink
import dev.sumdahl.geet.ui.common.formatDuration
import kotlinx.coroutines.launch

/**
 * The start screen: search the catalogs, paste or pick up a copied link, see what's trending, get back to what was
 * saved lately, and follow the download running now.
 */
@Composable
fun HomeScreen(
    clipboardChip: Boolean,
    onOpenDownloads: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val trending by viewModel.trending.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val rowStates by viewModel.rowStates.collectAsStateWithLifecycle()
    val searchState = rememberSearchBarState()
    val field = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { viewModel.query.value = it } }

    val clipboardLink = rememberClipboardLink(enabled = clipboardChip)
    var dismissedClip by rememberSaveable { mutableStateOf<String?>(null) }
    val download: (String) -> Unit = { ref ->
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        viewModel.download(ref)
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = field,
            searchBarState = searchState,
            onSearch = {},
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        AppBarWithSearch(state = searchState, inputField = inputField, modifier = Modifier.statusBarsPadding())
        ExpandedFullScreenSearchBar(state = searchState, inputField = inputField) {
            val results by viewModel.results.collectAsStateWithLifecycle()
            SearchResults(results, rowStates, onDownload = download)
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "active") {
                AnimatedVisibility(active != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    active?.let { ActiveBanner(it, onOpenDownloads) }
                }
            }
            item(key = "clip") {
                val link = clipboardLink?.takeIf { it != dismissedClip }
                AnimatedVisibility(link != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    link?.let {
                        ClipboardCard(it, onDownload = {
                            download(it)
                            dismissedClip = it
                            onOpenDownloads()
                        }, onDismiss = { dismissedClip = it })
                    }
                }
            }
            item(key = "paste") {
                PasteLink(onSubmit = { link ->
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    viewModel.downloadLink(link)
                    onOpenDownloads()
                })
            }
            if (recent.isNotEmpty()) {
                item(key = "recent-title") { SectionTitle("Recently added") }
                item(key = "recent") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(recent, key = { _, s -> s.id }) { i, song ->
                            Column(
                                Modifier.width(132.dp).clickable {
                                    viewModel.play(recent, i)
                                    onOpenPlayer()
                                },
                            ) {
                                CoverArt(song.cover, Modifier.size(132.dp), MaterialTheme.shapes.large)
                                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item(key = "trending-title") { SectionTitle("Trending now") }
            when (val t = trending) {
                Load.Loading -> item(key = "trending-loading") {
                    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                is Load.Failed -> item(key = "trending-failed") {
                    MessageState(
                        icon = Icons.Rounded.CloudOff,
                        title = "Couldn't load the chart",
                        body = "Check your connection and try again.",
                        action = "Try again",
                        onAction = viewModel::retryTrending,
                    )
                }
                is Load.Ready -> {
                    item(key = "carousel") { TrendingCarousel(t.value.take(CAROUSEL), onDownload = download) }
                    items(t.value.drop(CAROUSEL), key = { "t-" + it.ref }) { r ->
                        ResultRow(r, rowStates[r.ref], onDownload = { download(r.ref) }, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
    LaunchedEffect(searchState.currentValue) {
        if (searchState.currentValue == SearchBarValue.Collapsed) scope.launch { field.setTextAndPlaceCursorAtEnd("") }
    }
}

private const val CAROUSEL = 10

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun ActiveBanner(item: JobWithTracks, onOpen: () -> Unit) {
    val job = item.job
    val finished = job.saved + job.existing + job.failed
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverArt(job.coverUrl, Modifier.size(48.dp), MaterialTheme.shapes.medium)
            Column(Modifier.weight(1f)) {
                Text("Downloading", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(job.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (job.total > 1) Text("$finished of ${job.total}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (job.total > 0) {
                    LinearWavyProgressIndicator(progress = { finished.toFloat() / job.total }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun ClipboardCard(link: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Download the link you copied?", style = MaterialTheme.typography.titleSmall)
                Text(link, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onDismiss) { Text("Not now") }
            FilledIconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, contentDescription = "Download it") }
        }
    }
}

@Composable
private fun PasteLink(onSubmit: (String) -> Unit) {
    val field = rememberTextFieldState()
    val link = firstLink(field.text.toString())
    val valid = link != null && isSupportedLink(link)
    OutlinedTextField(
        state = field,
        placeholder = { Text("Paste a Spotify, YouTube Music or YouTube link") },
        leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(valid, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
                FilledIconButton(onClick = {
                    link?.let(onSubmit)
                    field.setTextAndPlaceCursorAtEnd("")
                }) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Download") }
            }
        },
        supportingText = if (field.text.isNotBlank() && !valid) ({ Text("That isn't a link Geet can read yet") }) else null,
        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        onKeyboardAction = { if (valid) link?.let(onSubmit) },
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun TrendingCarousel(results: List<CatalogResult>, onDownload: (String) -> Unit) {
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { results.size },
        preferredItemWidth = 220.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(260.dp),
    ) { i ->
        val r = results[i]
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .clickable { onDownload(r.ref) },
        ) {
            CoverArt(r.coverUrl, Modifier.fillMaxSize(), MaterialTheme.shapes.extraLarge)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text("#${r.rank}", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Text(r.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.artists.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SearchResults(results: Load<List<CatalogResult>>?, states: Map<String, RowState>, onDownload: (String) -> Unit) {
    AnimatedContent(targetState = results, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "results") { r ->
        when (r) {
            null -> MessageState(Icons.Rounded.Search, "Find any song", "Search Apple Music's and Deezer's catalogs, then download in one tap.")
            Load.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            is Load.Failed -> MessageState(Icons.Rounded.CloudOff, "Search didn't answer", "Check your connection and try again.")
            is Load.Ready -> if (r.value.isEmpty()) {
                MessageState(Icons.Rounded.SearchOff, "No results", "Try the song's title with the artist's name.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(r.value, key = { it.ref }) { item -> ResultRow(item, states[item.ref], onDownload = { onDownload(item.ref) }) }
                }
            }
        }
    }
}

/** A catalog song with a button that becomes its download's progress, then a tick. */
@Composable
private fun ResultRow(r: CatalogResult, state: RowState?, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.explicit) ExplicitBadge()
            }
        },
        supportingContent = {
            Text(
                listOf(r.artists.joinToString(", "), r.album, r.year.takeIf { it > 0 }?.toString(), formatDuration(r.durationMs))
                    .filterNot { it.isNullOrBlank() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                CoverArt(r.coverUrl, Modifier.size(52.dp), MaterialTheme.shapes.medium)
                if (r.rank > 0) Text("${r.rank}", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))
            }
        },
        trailingContent = {
            AnimatedContent(targetState = state ?: RowState.Idle, transitionSpec = { scaleIn() + fadeIn() togetherWith fadeOut() }, label = "row") { s ->
                when (s) {
                    RowState.Idle -> IconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, contentDescription = "Download ${r.title}") }
                    RowState.Downloading -> LoadingIndicator(Modifier.size(40.dp))
                    RowState.Saved -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Saved", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                    RowState.Failed -> IconButton(onClick = onDownload) { Icon(Icons.Rounded.ErrorOutline, contentDescription = "Failed, try again", tint = MaterialTheme.colorScheme.error) }
                }
            }
        },
        modifier = modifier.clickable(enabled = state == null || state == RowState.Idle, onClick = onDownload),
    )
}

@Composable
private fun ExplicitBadge() {
    Surface(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.padding(start = 6.dp),
    ) {
        Text("E", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/**
 * A supported link on the clipboard, read each time the screen comes to the front (Android only lets the app in
 * front read it, and shows a note when it does, so it's never read in the background).
 */
@Composable
private fun rememberClipboardLink(enabled: Boolean): String? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var link by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            link = firstLink(text)?.takeIf(::isSupportedLink)
        }
    }
    return link
}
