package dev.sumdahl.geet.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import coil3.compose.AsyncImage

/** A song's cover, fading in over a tonal placeholder with a note, so a missing cover still looks deliberate. */
@Composable
fun CoverArt(model: Any?, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
        if (model != null) {
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * The player's cover: a soft cookie shape while paused that springs into a circle while playing, like Pixel's media
 * player. The morph is drawn from the shape's outline, so it animates in the draw phase without recomposing.
 */
@Composable
fun MorphingCover(model: Any?, playing: Boolean, modifier: Modifier = Modifier) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val progress by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cover shape"
    )
    CoverArt(model, modifier, shape = MorphShape(morph, progress))
}

/** A [Shape] partway through [morph] (0 = its start, 1 = its end), scaled from the unit square to the size drawn. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        morph.asCubics(progress).forEachIndexed { i, c ->
            if (i == 0) path.moveTo(c.anchor0X * size.width, c.anchor0Y * size.height)
            path.cubicTo(
                c.control0X * size.width,
                c.control0Y * size.height,
                c.control1X * size.width,
                c.control1Y * size.height,
                c.anchor1X * size.width,
                c.anchor1Y * size.height
            )
        }
        path.close()
        return Outline.Generic(path)
    }
}
