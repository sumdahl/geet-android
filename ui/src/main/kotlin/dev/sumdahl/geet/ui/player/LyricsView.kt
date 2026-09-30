package dev.sumdahl.geet.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.sumdahl.geet.data.Lyrics
import dev.sumdahl.geet.designsystem.LyricLineStyle
import dev.sumdahl.geet.designsystem.component.MessageState

/**
 * Lyrics that follow the song, like Spotify's and YouTube Music's: the line being sung is lit and kept in the upper
 * third, lines already sung dim, the edges fade, and tapping a line jumps there. Lyrics without timings scroll as plain
 * text. The view recomposes only when the line changes, not every frame.
 */
@Composable
fun LyricsView(state: LyricsState, position: State<Long>, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    when (state) {
        LyricsState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        LyricsState.None -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MessageState(
                icon = Icons.Rounded.Lyrics,
                title = "No lyrics for this one",
                body = "Nobody has shared the words for this song yet. Enjoy the music."
            )
        }
        is LyricsState.Ready -> SyncedLyrics(state.lyrics, position, onSeek, modifier)
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics, position: State<Long>, onSeek: (Long) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    val current by remember(lyrics) { derivedStateOf { lyrics.lineAt(position.value) } }
    val density = LocalDensity.current
    LaunchedEffect(current) {
        if (current >= 0) {
            val third = with(density) { list.layoutInfo.viewportSize.height / 3 }
            list.animateScrollToItem(current, scrollOffset = -third)
        }
    }
    LazyColumn(
        state = list,
        contentPadding = PaddingValues(vertical = 160.dp),
        modifier = modifier
            .fillMaxSize()
            // Fade the top and bottom edges so lines drift in and out rather than being cut.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.15f to Color.Black, 0.85f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        itemsIndexed(lyrics.lines, key = { i, _ -> i }) { i, line ->
            val lit = !lyrics.synced || i == current
            val past = lyrics.synced && i < current
            val color by animateColorAsState(
                when {
                    lit -> MaterialTheme.colorScheme.onSurface
                    past -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                },
                label = "line colour"
            )
            val scale by animateFloatAsState(
                if (lit || !lyrics.synced) 1f else 0.92f,
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "line scale"
            )
            Text(
                text = line.text.ifBlank { "♪" },
                style = LyricLineStyle,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = lyrics.synced) { onSeek(line.atMs) }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
            )
        }
    }
}
