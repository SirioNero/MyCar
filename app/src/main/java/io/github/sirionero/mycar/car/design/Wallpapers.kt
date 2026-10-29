package io.github.sirionero.mycar.car.design

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * The wallpapers behind the app grid, the dashboard and the dock: soft glows of colour on a dark
 * ground, drawn here rather than shipped as pictures, so they're myCar's own and cost nothing in the
 * APK. Each is laid out against the whole car screen, so the dock and the screen beside it show
 * one continuous picture.
 *
 * Smooth gradients are what video compression handles worst: on the way to the head unit they
 * break into visible bands. A faint grain over the top breaks the bands up again.
 *
 * The moving ones are drawn here too, a frame at a time: glows that wander and swell, waves that
 * roll, lights that rise. Steady and smooth, nothing that flashes.
 */

/** One soft disc of colour: its centre as a fraction of the screen, its radius of the longer side. */
class Glow(val color: Color, val x: Float, val y: Float, val radius: Float, val strength: Float = 0.9f)

/** A wallpaper: [glows] on a [ground], and how it moves, or null [motion] for a still one. [name] is in the resources. */
class Wallpaper(val id: String, @StringRes val name: Int, val ground: Color, val glows: List<Glow>, val motion: Motion? = null)

/** How a moving wallpaper moves. */
sealed interface Motion {
    /** The glows wander about the screen, swelling and dimming, each on its own loop. */
    class Drift(val reach: Float, val loopSeconds: Double) : Motion

    /** Bands of colour, back to front, rolling across the lower screen like waves. */
    class Waves(val colors: List<Color>) : Motion

    /** Soft lights rising through the picture, swaying and glowing brighter and dimmer. */
    class Lights(colors: List<Color>, count: Int) : Motion {
        internal val lights = List(count) { Light.make(it, colors) }
    }
}

/** One of [Motion.Lights]: where it rises, how fast and how big, and its own timing. */
internal class Light(val x: Float, val speed: Float, val size: Float, val sway: Float, val phase: Float, val color: Color) {
    companion object {
        /** The same lights every time: seeded by their place in the list. */
        fun make(index: Int, colors: List<Color>): Light {
            val random = java.util.Random(31L * index + 7)
            return Light(
                x = random.nextFloat(),
                speed = 0.025f + random.nextFloat() * 0.035f,
                size = 0.012f + random.nextFloat() * 0.03f,
                sway = 0.01f + random.nextFloat() * 0.03f,
                phase = random.nextFloat() * 10f,
                color = colors[index % colors.size],
            )
        }
    }
}

object Wallpapers {
    /** Plain background, no wallpaper: what myCar looked like before them. */
    const val NONE = "none"
    const val DEFAULT = "aurora"

    val all: List<Wallpaper> = listOf(
        Wallpaper(
            "aurora", R.string.car_wallpaper_aurora, Color(0xFF04070D),
            listOf(
                Glow(Color(0xFF00C2A8), 0.12f, 0.95f, 0.62f),
                Glow(Color(0xFF2F6BFF), 0.78f, 0.12f, 0.58f),
                Glow(Color(0xFF8A3FFC), 1.00f, 1.00f, 0.45f, 0.75f),
            ),
        ),
        Wallpaper(
            "sunset", R.string.car_wallpaper_sunset, Color(0xFF110508),
            listOf(
                Glow(Color(0xFFFF7A1A), 0.15f, 1.00f, 0.62f),
                Glow(Color(0xFFFF2E7E), 0.65f, 0.65f, 0.52f, 0.8f),
                Glow(Color(0xFF6A2BFF), 0.95f, 0.05f, 0.5f),
            ),
        ),
        Wallpaper(
            "ocean", R.string.car_wallpaper_ocean, Color(0xFF020913),
            listOf(
                Glow(Color(0xFF00A6FF), 0.85f, 0.95f, 0.66f),
                Glow(Color(0xFF2633FF), 0.10f, 0.10f, 0.55f),
                Glow(Color(0xFF00E0C6), 0.50f, 0.55f, 0.30f, 0.55f),
            ),
        ),
        Wallpaper(
            "nebula", R.string.car_wallpaper_nebula, Color(0xFF090312),
            listOf(
                Glow(Color(0xFFD432FF), 0.22f, 0.30f, 0.55f),
                Glow(Color(0xFF3A5BFF), 0.82f, 0.80f, 0.60f),
                Glow(Color(0xFFFF4F8B), 0.62f, 0.00f, 0.38f, 0.7f),
            ),
        ),
        Wallpaper(
            "ember", R.string.car_wallpaper_ember, Color(0xFF0D0503),
            listOf(
                Glow(Color(0xFFFF3B2F), 0.08f, 0.15f, 0.55f),
                Glow(Color(0xFFFFB020), 0.88f, 0.92f, 0.60f),
                Glow(Color(0xFFFF6A00), 0.55f, 0.45f, 0.35f, 0.6f),
            ),
        ),
        Wallpaper(
            "forest", R.string.car_wallpaper_forest, Color(0xFF03100A),
            listOf(
                Glow(Color(0xFF22D47B), 0.18f, 0.80f, 0.60f),
                Glow(Color(0xFFB6F23A), 0.88f, 0.10f, 0.42f, 0.7f),
                Glow(Color(0xFF00A3A3), 0.72f, 0.85f, 0.50f),
            ),
        ),
        Wallpaper(
            "graphite", R.string.car_wallpaper_graphite, Color(0xFF09090B),
            listOf(
                Glow(Color(0xFF4A4A57), 0.25f, 0.25f, 0.60f),
                Glow(Color(0xFF2E3442), 0.85f, 0.90f, 0.60f),
            ),
        ),
        // The moving ones.
        Wallpaper(
            "lava", R.string.car_wallpaper_lava, Color(0xFF120309),
            listOf(
                Glow(Color(0xFFFF2E63), 0.25f, 0.70f, 0.50f),
                Glow(Color(0xFFFF8A1F), 0.70f, 0.30f, 0.48f),
                Glow(Color(0xFF9B2CFF), 0.85f, 0.85f, 0.42f, 0.75f),
            ),
            Motion.Drift(reach = 0.24f, loopSeconds = 28.0),
        ),
        Wallpaper(
            "polar", R.string.car_wallpaper_polar, Color(0xFF020A0C),
            listOf(
                Glow(Color(0xFF14E08A), 0.20f, 0.30f, 0.50f),
                Glow(Color(0xFF00B3FF), 0.75f, 0.65f, 0.50f),
                Glow(Color(0xFF7C4DFF), 0.55f, 0.05f, 0.40f, 0.7f),
            ),
            Motion.Drift(reach = 0.2f, loopSeconds = 34.0),
        ),
        Wallpaper(
            "waves", R.string.car_wallpaper_waves, Color(0xFF010915),
            listOf(Glow(Color(0xFF1F6BFF), 0.80f, 0.05f, 0.55f, 0.7f)),
            Motion.Waves(listOf(Color(0xFF2E3DD1), Color(0xFF1A73E8), Color(0xFF00B8D9), Color(0xFF00E0B0))),
        ),
        Wallpaper(
            "fireflies", R.string.car_wallpaper_fireflies, Color(0xFF03080A),
            listOf(
                Glow(Color(0xFF0F5C4A), 0.30f, 0.90f, 0.60f, 0.8f),
                Glow(Color(0xFF1B2F6B), 0.85f, 0.15f, 0.50f, 0.8f),
            ),
            Motion.Lights(listOf(Color(0xFFFFD36B), Color(0xFFFFA94D), Color(0xFFB8FF7A)), count = 24),
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
    val motion = wallpaper.motion
    val clock = if (motion != null) rememberMotionClock() else null
    onGloballyPositioned { origin = it.positionInRoot() }.drawBehind {
        // Read here, the clock only redraws the picture: nothing recomposes as it moves.
        val seconds = clock?.let { it.value / 1000.0 } ?: 0.0
        val screen = if (!wholeScreen) size else Size(
            view.rootView.width.toFloat().takeIf { it > 0f } ?: size.width,
            view.rootView.height.toFloat().takeIf { it > 0f } ?: size.height,
        )
        val shift = if (wholeScreen) origin else Offset.Zero
        clipRect {
            translate(-shift.x, -shift.y) {
                drawRect(if (light) lightGround else wallpaper.ground, size = screen)
                val longest = max(screen.width, screen.height)
                // Softer on a light ground, so dark text stays readable over it.
                val fade = if (light) 0.45f else 1f
                wallpaper.glows.forEachIndexed { i, glow ->
                    val drift = if (motion is Motion.Drift) drift(i, seconds, motion) else Drift.NONE
                    val center = Offset((glow.x + drift.dx) * screen.width, (glow.y + drift.dy) * screen.height)
                    val radius = glow.radius * longest * drift.grow
                    val strength = glow.strength * fade * drift.glow
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
                when (motion) {
                    is Motion.Waves -> drawWaves(screen, motion, seconds, fade)
                    is Motion.Lights -> drawLights(screen, motion, seconds, fade)
                    else -> Unit
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

/** Where a glow has wandered: a fraction of the screen each way, its size and its brightness. */
private class Drift(val dx: Float, val dy: Float, val grow: Float, val glow: Float) {
    companion object {
        val NONE = Drift(0f, 0f, 1f, 1f)
    }
}

/**
 * Glow [index]'s place [seconds] into the phone's uptime: loops of half a minute or so, each glow
 * on its own pace and phase, so the picture never quite repeats. In doubles, as uptime runs to
 * millions of seconds.
 */
private fun drift(index: Int, seconds: Double, motion: Motion.Drift): Drift {
    val phase = index * 2.1
    val angle = seconds * 2 * PI / (motion.loopSeconds * (1 + index * 0.37)) + phase
    return Drift(
        dx = (sin(angle) * motion.reach).toFloat(),
        dy = (cos(angle * 0.83) * motion.reach * 0.8).toFloat(),
        grow = 1f + (sin(angle * 1.3) * 0.15).toFloat(),
        glow = 0.85f + (sin(angle * 1.7) * 0.15).toFloat(),
    )
}

/**
 * [Motion.Waves]: each band a filled curve, the nearer ones lower, taller and quicker, shading
 * off towards the bottom of the screen.
 */
private fun DrawScope.drawWaves(screen: Size, waves: Motion.Waves, seconds: Double, fade: Float) {
    val step = screen.width / WAVE_STEPS
    waves.colors.forEachIndexed { i, color ->
        val base = screen.height * (0.42f + 0.14f * i)
        val height = screen.height * (0.05f + 0.025f * i)
        val length = screen.width * (1.1f + 0.4f * i)
        val travel = seconds * screen.width * (0.035 + 0.025 * i)
        val path = Path().apply {
            moveTo(0f, screen.height)
            for (s in 0..WAVE_STEPS) {
                val x = s * step
                val angle = 2 * PI * (x + travel) / length + i * 1.7
                lineTo(x, base + height * (sin(angle) + 0.35 * sin(2.3 * angle + seconds * 0.4)).toFloat())
            }
            lineTo(screen.width, screen.height)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                0f to color.copy(alpha = 0.55f * fade),
                1f to color.copy(alpha = 0.12f * fade),
                startY = base - height,
                endY = screen.height,
            ),
        )
    }
}

/** [Motion.Lights]: each rises from just below the screen to just above it, then starts again. */
private fun DrawScope.drawLights(screen: Size, motion: Motion.Lights, seconds: Double, fade: Float) {
    val longest = max(screen.width, screen.height)
    motion.lights.forEach { light ->
        val travel = ((seconds * light.speed + light.phase) % (1 + 2 * LIGHT_MARGIN)).toFloat()
        val center = Offset(
            (light.x + light.sway * sin(seconds * 0.5 + light.phase).toFloat()) * screen.width,
            (1 + LIGHT_MARGIN - travel) * screen.height,
        )
        val radius = light.size * longest
        val alpha = 0.6f * fade * (0.55f + 0.45f * sin(seconds * 1.3 + light.phase * 3).toFloat())
        drawCircle(
            Brush.radialGradient(
                0f to light.color.copy(alpha = alpha),
                0.5f to light.color.copy(alpha = alpha * 0.45f),
                1f to light.color.copy(alpha = 0f),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

/**
 * The phone's uptime in milliseconds, moved on every [MOTION_FRAME_MS]: about 30 redraws a
 * second, for smooth motion. Every copy of the wallpaper reads uptime, so the dock and the screen
 * beside it move together - and one clock for all of them, so they're redrawn in the same frame:
 * with a clock each they ticked at their own moments, each a frame of its own (dock and grid
 * twice the frames, the settings' moving swatches nearly every frame the screen has).
 */
@Composable
private fun rememberMotionClock(): State<Long> {
    DisposableEffect(Unit) {
        MotionClock.users++
        if (MotionClock.users == 1) MotionClock.start()
        onDispose {
            MotionClock.users--
            if (MotionClock.users == 0) MotionClock.stop()
        }
    }
    return MotionClock.now
}

/** The one clock of [rememberMotionClock], running while some moving wallpaper shows. Main thread only. */
private object MotionClock {
    val now = mutableLongStateOf(SystemClock.uptimeMillis())
    var users = 0
    private var job: Job? = null

    fun start() {
        job = MainScope().launch {
            while (true) {
                delay(MOTION_FRAME_MS)
                now.longValue = SystemClock.uptimeMillis()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

/** Grey noise, tiled over the wallpaper to break up banding. The same every time: seeded. */
@Composable
internal fun rememberGrain(): ImageBitmap = remember {
    val side = 96
    val random = java.util.Random(7)
    val pixels = IntArray(side * side) {
        val v = random.nextInt(256)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(pixels, side, side, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val GRAIN_ALPHA = 0.05f
private const val MOTION_FRAME_MS = 33L
private const val WAVE_STEPS = 64
private const val LIGHT_MARGIN = 0.1f
private const val BASE_SHADE = 0.15f
