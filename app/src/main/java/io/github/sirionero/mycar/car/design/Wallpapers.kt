package io.github.sirionero.mycar.car.design

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import io.github.sirionero.mycar.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

/*
 * The wallpapers behind the app grid, the dashboard and the dock: soft glows of colour on a dark
 * ground, drawn here rather than shipped as pictures, so they're myCar's own and cost nothing in the
 * APK. Each is laid out against the whole car screen, so the dock and the screen beside it show
 * one continuous picture.
 *
 * Smooth gradients are what video compression handles worst: on the way to the head unit they
 * break into visible bands. A faint grain over the top breaks the bands up again.
 */

/** One soft disc of colour: its centre as a fraction of the screen, its radius of the longer side. */
class Glow(val color: Color, val x: Float, val y: Float, val radius: Float, val strength: Float = 0.9f)

class Wallpaper(val id: String, val name: String, val ground: Color, val glows: List<Glow>)

object Wallpapers {
    /** Plain background, no wallpaper: what myCar looked like before them. */
    const val NONE = "none"
    const val DEFAULT = "aurora"

    val all: List<Wallpaper> = listOf(
        Wallpaper(
            "aurora", "Aurora", Color(0xFF04070D),
            listOf(
                Glow(Color(0xFF00C2A8), 0.12f, 0.95f, 0.62f),
                Glow(Color(0xFF2F6BFF), 0.78f, 0.12f, 0.58f),
                Glow(Color(0xFF8A3FFC), 1.00f, 1.00f, 0.45f, 0.75f),
            ),
        ),
        Wallpaper(
            "sunset", "Sunset", Color(0xFF110508),
            listOf(
                Glow(Color(0xFFFF7A1A), 0.15f, 1.00f, 0.62f),
                Glow(Color(0xFFFF2E7E), 0.65f, 0.65f, 0.52f, 0.8f),
                Glow(Color(0xFF6A2BFF), 0.95f, 0.05f, 0.5f),
            ),
        ),
        Wallpaper(
            "ocean", "Ocean", Color(0xFF020913),
            listOf(
                Glow(Color(0xFF00A6FF), 0.85f, 0.95f, 0.66f),
                Glow(Color(0xFF2633FF), 0.10f, 0.10f, 0.55f),
                Glow(Color(0xFF00E0C6), 0.50f, 0.55f, 0.30f, 0.55f),
            ),
        ),
        Wallpaper(
            "nebula", "Nebula", Color(0xFF090312),
            listOf(
                Glow(Color(0xFFD432FF), 0.22f, 0.30f, 0.55f),
                Glow(Color(0xFF3A5BFF), 0.82f, 0.80f, 0.60f),
                Glow(Color(0xFFFF4F8B), 0.62f, 0.00f, 0.38f, 0.7f),
            ),
        ),
        Wallpaper(
            "ember", "Ember", Color(0xFF0D0503),
            listOf(
                Glow(Color(0xFFFF3B2F), 0.08f, 0.15f, 0.55f),
                Glow(Color(0xFFFFB020), 0.88f, 0.92f, 0.60f),
                Glow(Color(0xFFFF6A00), 0.55f, 0.45f, 0.35f, 0.6f),
            ),
        ),
        Wallpaper(
            "forest", "Forest", Color(0xFF03100A),
            listOf(
                Glow(Color(0xFF22D47B), 0.18f, 0.80f, 0.60f),
                Glow(Color(0xFFB6F23A), 0.88f, 0.10f, 0.42f, 0.7f),
                Glow(Color(0xFF00A3A3), 0.72f, 0.85f, 0.50f),
            ),
        ),
        Wallpaper(
            "graphite", "Graphite", Color(0xFF09090B),
            listOf(
                Glow(Color(0xFF4A4A57), 0.25f, 0.25f, 0.60f),
                Glow(Color(0xFF2E3442), 0.85f, 0.90f, 0.60f),
            ),
        ),
    )

    fun find(id: String): Wallpaper? = all.find { it.id == id }
}

/** The driver's wallpaper, kept with the rest of myCar's settings. */
object WallpaperSetting {
    private const val KEY = "car_wallpaper"
    private var app: Context? = null
    private val flow = MutableStateFlow(Wallpapers.DEFAULT)
    val current: StateFlow<String> = flow

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        flow.value = AppSettings.getString(app, KEY, Wallpapers.DEFAULT)
    }

    fun set(id: String) {
        flow.value = id
        app?.let { AppSettings.putString(it, KEY, id) }
    }
}

/** The wallpaper the driver chose, or null for none. */
@Composable
fun rememberWallpaper(): Wallpaper? {
    val id by WallpaperSetting.current.collectAsState()
    return Wallpapers.find(id)
}

/**
 * Draws [wallpaper] as the part of the whole car screen this element covers, so neighbours line
 * up - or, with [wholeScreen] false, the whole of it shrunk to this element, for a swatch. [dim]
 * darkens it for what sits on top: the dock's glass, say. On a light theme the glows go softer on
 * a light ground, so dark text stays readable over them.
 */
@Composable
fun WallpaperBackground(wallpaper: Wallpaper, modifier: Modifier = Modifier, dim: Float = 0f, wholeScreen: Boolean = true) {
    Spacer(modifier.wallpaperBackground(wallpaper, dim, wholeScreen))
}

/** [WallpaperBackground] as a background behind an element's own content. */
fun Modifier.wallpaperBackground(wallpaper: Wallpaper, dim: Float = 0f, wholeScreen: Boolean = true): Modifier = composed {
    val view = LocalView.current
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val lightGround = MaterialTheme.colorScheme.background
    var origin by remember { mutableStateOf(Offset.Zero) }
    val grain = rememberGrain()
    onGloballyPositioned { origin = it.positionInRoot() }.drawBehind {
        val screen = if (!wholeScreen) size else Size(
            view.rootView.width.toFloat().takeIf { it > 0f } ?: size.width,
            view.rootView.height.toFloat().takeIf { it > 0f } ?: size.height,
        )
        val shift = if (wholeScreen) origin else Offset.Zero
        clipRect {
            translate(-shift.x, -shift.y) {
                drawRect(if (light) lightGround else wallpaper.ground, size = screen)
                val longest = max(screen.width, screen.height)
                wallpaper.glows.forEach { glow ->
                    val center = Offset(glow.x * screen.width, glow.y * screen.height)
                    val radius = glow.radius * longest
                    val strength = if (light) glow.strength * 0.45f else glow.strength
                    drawCircle(
                        Brush.radialGradient(
                            0f to glow.color.copy(alpha = strength),
                            0.45f to glow.color.copy(alpha = strength * 0.5f),
                            1f to glow.color.copy(alpha = 0f),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
            drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = GRAIN_ALPHA)
            // A touch darker overall, so white labels stay clear over the brightest glow.
            val shade = if (light) 0f else BASE_SHADE
            val total = 1f - (1f - shade) * (1f - dim)
            if (total > 0f) drawRect(Color.Black.copy(alpha = total))
        }
    }
}

/** Grey noise, tiled over the wallpaper to break up banding. The same every time: seeded. */
@Composable
private fun rememberGrain(): ImageBitmap = remember {
    val side = 96
    val random = java.util.Random(7)
    val pixels = IntArray(side * side) {
        val v = random.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(pixels, side, side, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val GRAIN_ALPHA = 0.05f
private const val BASE_SHADE = 0.15f
