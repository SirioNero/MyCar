package io.github.sirionero.mycar.car.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sirionero.mycar.R

/*
 * myCar's design system: every colour, radius, spacing, size, type style and timing the car UI
 * uses, in one place. Screens mostly read it through MaterialTheme - colours, type and shapes are
 * set up by [MyCarCarTheme] at the root of the car UI - and reach for these objects directly only
 * for what Material has no slot for (the dock, the app grid, app tile colours, motion).
 *
 * The car sees all of this as a compressed video stream, so the palette avoids subtle gradients,
 * blur and thin low-contrast lines: they band or vanish on the way to the head unit.
 */

/** The raw palette. A black canvas, a few greys for depth, and a handful of saturated accents. */
object MyCarPalette {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)

    // Dark: the default.
    val DarkSurface1 = Color(0xFF111113)
    val DarkSurface2 = Color(0xFF1C1C1E)
    val DarkSurface3 = Color(0xFF2A2A2D)
    val DarkSurface4 = Color(0xFF3A3A3C)
    val DarkTextSecondary = Color(0xFF9A9AA0)

    // Light.
    val LightBackground = Color(0xFFF2F2F5)
    val LightSurface2 = Color(0xFFFFFFFF)
    val LightSurface3 = Color(0xFFE5E5EA)
    val LightSurface4 = Color(0xFFD1D1D6)
    val LightTextSecondary = Color(0xFF6C6C72)

    // Accents: the same few hues on either background.
    val Accent = Color(0xFF3D8BFF)
    val AccentOnLight = Color(0xFF0A6CFF)
    val Positive = Color(0xFF32D46B)
    val Negative = Color(0xFFFF4D45)
    val Warning = Color(0xFFFFB020)
    val Indigo = Color(0xFF6C63FF)
    val Graphite = Color(0xFF6B6B72)
}

/** Corner radii. App icons use [Icon] as a fraction of their side, the rest are fixed. */
object MyCarRadius {
    /** How round an app icon's squircle is: see [SquircleShape]. */
    const val Icon = 5f
    val Small = 10.dp
    val Button = 14.dp
    val Card = 16.dp
    val Sheet = 22.dp
}

/** The spacing scale. Nothing in the car UI should need a value between these. */
object MyCarSpace {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}

/** Fixed sizes: touch targets, the dock, app icons. */
object MyCarSize {
    /** The smallest thing a driver should have to hit. */
    val TouchTarget = 64.dp
    val DockWidth = 96.dp
    val DockIcon = 52.dp
    /** The largest an app icon gets on the launcher grid; smaller screens shrink it to fit. */
    val GridIconMax = 80.dp
    /** A launcher grid cell's smallest width and height, which decide how many fit on a page. */
    val GridCellMinWidth = 150.dp
    val GridCellMinHeight = 170.dp
}

/** Timing. Short and decelerating: the car shows every frame a beat late, so long motion feels slow. */
object MyCarMotion {
    const val PressMs = 130
    const val ScreenMs = 220
    const val PressedScale = 0.94f
    val Decelerate = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** Inter, the only typeface in the car UI, in the four weights shipped in res/font. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

fun myCarDarkColors(): ColorScheme = with(MyCarPalette) {
    darkColorScheme(
        primary = Accent,
        onPrimary = White,
        primaryContainer = Color(0xFF15345F),
        onPrimaryContainer = Color(0xFFD7E6FF),
        inversePrimary = AccentOnLight,
        secondary = Color(0xFFB8B8BE),
        onSecondary = Black,
        secondaryContainer = Color(0xFF243A5E),
        onSecondaryContainer = Color(0xFFDCE8FF),
        tertiary = Positive,
        onTertiary = Black,
        tertiaryContainer = Color(0xFF173F27),
        onTertiaryContainer = Color(0xFFC6F4D6),
        error = Negative,
        onError = White,
        errorContainer = Color(0xFF5C1A17),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Black,
        onBackground = White,
        surface = Black,
        onSurface = White,
        surfaceVariant = DarkSurface3,
        onSurfaceVariant = DarkTextSecondary,
        surfaceTint = White,
        inverseSurface = Color(0xFFE5E5EA),
        inverseOnSurface = Black,
        outline = Color(0xFF48484C),
        outlineVariant = DarkSurface3,
        scrim = Black,
        surfaceBright = DarkSurface3,
        surfaceDim = Black,
        surfaceContainerLowest = Black,
        surfaceContainerLow = DarkSurface1,
        surfaceContainer = DarkSurface2,
        surfaceContainerHigh = Color(0xFF242427),
        surfaceContainerHighest = Color(0xFF2C2C2F),
    )
}

fun myCarLightColors(): ColorScheme = with(MyCarPalette) {
    lightColorScheme(
        primary = AccentOnLight,
        onPrimary = White,
        primaryContainer = Color(0xFFD6E5FF),
        onPrimaryContainer = Color(0xFF002A66),
        inversePrimary = Accent,
        secondary = Color(0xFF55555B),
        onSecondary = White,
        secondaryContainer = Color(0xFFDCE7FA),
        onSecondaryContainer = Color(0xFF0B2A55),
        tertiary = Color(0xFF1FA653),
        onTertiary = White,
        tertiaryContainer = Color(0xFFCDF3DA),
        onTertiaryContainer = Color(0xFF0A3A1C),
        error = Color(0xFFE5332B),
        onError = White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF5C1A17),
        background = LightBackground,
        onBackground = Black,
        surface = LightBackground,
        onSurface = Black,
        surfaceVariant = LightSurface3,
        onSurfaceVariant = LightTextSecondary,
        surfaceTint = Black,
        inverseSurface = DarkSurface2,
        inverseOnSurface = White,
        outline = Color(0xFFB0B0B6),
        outlineVariant = LightSurface3,
        scrim = Black,
        surfaceBright = White,
        surfaceDim = LightSurface3,
        surfaceContainerLowest = White,
        surfaceContainerLow = Color(0xFFF7F7FA),
        surfaceContainer = White,
        surfaceContainerHigh = Color(0xFFECECF0),
        surfaceContainerHighest = LightSurface3,
    )
}

/**
 * Material's type scale, set in Inter. The sizes stay Material's, which every screen was laid out
 * against; the tracking tightens a little, as Inter sets loose at Material's defaults.
 */
fun myCarTypography(): Typography {
    val base = Typography()
    fun TextStyle.inter(tracking: Float) = copy(fontFamily = InterFamily, letterSpacing = tracking.sp)
    return Typography(
        displayLarge = base.displayLarge.inter(-0.5f),
        displayMedium = base.displayMedium.inter(-0.4f),
        displaySmall = base.displaySmall.inter(-0.3f),
        headlineLarge = base.headlineLarge.inter(-0.3f),
        headlineMedium = base.headlineMedium.inter(-0.2f),
        headlineSmall = base.headlineSmall.inter(-0.2f),
        titleLarge = base.titleLarge.inter(-0.2f),
        titleMedium = base.titleMedium.inter(-0.1f),
        titleSmall = base.titleSmall.inter(0f),
        bodyLarge = base.bodyLarge.inter(0f),
        bodyMedium = base.bodyMedium.inter(0f),
        bodySmall = base.bodySmall.inter(0f),
        labelLarge = base.labelLarge.inter(0f),
        labelMedium = base.labelMedium.inter(0.1f),
        labelSmall = base.labelSmall.inter(0.2f),
    )
}

fun myCarShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(MyCarRadius.Small),
    medium = RoundedCornerShape(MyCarRadius.Button),
    large = RoundedCornerShape(MyCarRadius.Card),
    extraLarge = RoundedCornerShape(MyCarRadius.Sheet),
)

/** The car UI's Material theme: myCar's colours (dark unless [dark] is false), Inter, and its shapes. */
@Composable
fun MyCarCarTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) myCarDarkColors() else myCarLightColors(),
        typography = myCarTypography(),
        shapes = myCarShapes(),
        content = content,
    )
}
