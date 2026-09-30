package dev.sumdahl.geet.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colour a cover suggests for theming (Material's own quantizer and scorer), or null while it loads or when the
 * cover is too grey to theme from, which leaves the surrounding colours in place. A 112 px copy is plenty to pick a
 * colour from and keeps this cheap on a skip.
 */
@Composable
fun rememberCoverSeed(cover: Any?): Color? {
    val context = LocalContext.current
    val seed by produceState<Color?>(null, cover) {
        if (cover == null) {
            value = null
            return@produceState
        }
        val request = ImageRequest.Builder(context).data(cover).size(Size(SEED_PX, SEED_PX)).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@produceState
        value = withContext(Dispatchers.Default) {
            result.image.asDrawable(context.resources).toBitmap().asImageBitmap().themeColorOrNull()
        }
    }
    return seed
}

private const val SEED_PX = 112
