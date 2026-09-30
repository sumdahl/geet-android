package dev.sumdahl.geet.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

enum class ThemeMode { System, Light, Dark, Amoled }

/** Geet's own colour when the wallpaper's isn't used: a warm raga rose. */
val BrandSeed = Color(0xFFE0457B)

/** Whether the surrounding theme is dark, so nested cover themes follow it. */
val LocalDarkTheme = staticCompositionLocalOf { false }
private val LocalAmoled = staticCompositionLocalOf { false }

/**
 * Material 3 Expressive, the Pixel way: wallpaper colours (Material You) by default, Geet's own palette otherwise,
 * expressive spring motion and emphasized type.
 */
@Composable
fun GeetTheme(mode: ThemeMode = ThemeMode.System, wallpaperColors: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
    }
    val amoled = mode == ThemeMode.Amoled
    val context = LocalContext.current
    val scheme = remember(dark, amoled, wallpaperColors) {
        val base = when {
            wallpaperColors && dark -> dynamicDarkColorScheme(context)
            wallpaperColors -> dynamicLightColorScheme(context)
            else -> dynamicColorScheme(BrandSeed, dark, amoled, style = PaletteStyle.Expressive, specVersion = ColorSpec.SpecVersion.SPEC_2025)
        }
        if (amoled) base.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black) else base
    }
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalAmoled provides amoled) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = GeetTypography,
            content = content,
        )
    }
}

/**
 * Re-colours [content] from a song's cover ([seed], from [rememberCoverSeed]), the way Android 16's media controls
 * do. Each change springs to the new colours instead of cutting. A null seed keeps the surrounding theme.
 */
@Composable
fun CoverTheme(seed: Color?, content: @Composable () -> Unit) {
    if (seed == null) {
        content()
        return
    }
    DynamicMaterialExpressiveTheme(
        seedColor = seed,
        isDark = LocalDarkTheme.current,
        isAmoled = LocalAmoled.current,
        style = PaletteStyle.Expressive,
        motionScheme = MotionScheme.expressive(),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        animate = true,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        content = content,
    )
}
