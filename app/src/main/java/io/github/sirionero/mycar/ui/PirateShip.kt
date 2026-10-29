package io.github.sirionero.mycar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The big release's ship: a pirate galleon of myCar's own, drawn here like the flag (no picture
 * ships with the app). It sails in from the left on a sea that never stops moving, its sails
 * filling as it comes, rides the waves once it's in, and runs the Jolly Roger (PirateFlag's cloth)
 * up the main mast. Gulls, the sun and its glitter on the water around it. Everything moves in the
 * drawing only, as with the flag: the screen isn't composed again for every frame.
 *
 * [ink] is the page's text colour, for what's drawn against the sky (the gulls).
 */
@Composable
internal fun PirateShip(key: Any, ink: Color, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    // A light ink is a dark page: the sun becomes the moon, the sea goes deep, the lanterns glow.
    val night = ink.luminance() > 0.5f
    // Seconds since the step opened, up to the end of the arrival; then only the sea's clock runs.
    val voyage = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(120)
        voyage.animateTo(VOYAGE_S, tween((VOYAGE_S * 1000).toInt(), easing = LinearEasing))
    }
    val sea = rememberInfiniteTransition(label = "sea")
    val clock by sea.animateFloat(0f, LOOP_S, infiniteRepeatable(tween((LOOP_S * 1000).toInt(), easing = LinearEasing)), label = "clock")
    Spacer(
        modifier
            .semantics { contentDescription = "A pirate ship flying the myCar flag: 100% local. We defend our data." }
            // One layer, so the edges can fade into the page.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val k = size.width * SCALE
                val flagW = (FLAG_W * k).roundToInt().coerceAtLeast(12)
                val flagH = (flagW / 1.5f).roundToInt().coerceAtLeast(8)
                val face = ImageBitmap(flagW, flagH)
                CanvasDrawScope().draw(this, layoutDirection, Canvas(face), Size(flagW.toFloat(), flagH.toFloat())) { drawCloth(measurer) }
                val hull = hullPath()
                val sheer = sheerPath()
                val planks = plankPaths()
                val strips = (flagW / 3).coerceIn(16, 72)
                val shades = List(SHADE_LEVELS * 2 + 1) { i -> shadeFilter((i - SHADE_LEVELS).toFloat() / SHADE_LEVELS) }
                val fade = Brush.verticalGradient(0f to Color.Black, 0.86f to Color.Black, 1f to Color.Transparent)
                val sides = Brush.horizontalGradient(0f to Color.Transparent, 0.06f to Color.Black, 0.94f to Color.Black, 1f to Color.Transparent)
                onDrawBehind {
                    val t = clock
                    val v = voyage.value
                    if (night) drawStars(t)
                    drawSun(t, night)
                    drawGulls(t, ink)
                    drawWater(t, Layer.BACK, night)
                    drawGlitter(t, behind = true, night)
                    val at = drawShip(t, v, k, hull, sheer, planks, face, strips, shades, night)
                    drawWater(t, Layer.MID, night)
                    // On the water in front of the hull: its shadow and the wake it leaves.
                    withTransform({ translate(at.x, at.y); rotate(at.roll, pivot = Offset.Zero) }) {
                        drawHullShadow(k)
                        drawWake(t, k, at.speed)
                    }
                    drawWater(t, Layer.FRONT, night)
                    drawGlitter(t, behind = false, night)
                    // The sea fades into the page at the bottom and the sides.
                    drawRect(fade, blendMode = BlendMode.DstIn)
                    drawRect(sides, blendMode = BlendMode.DstIn)
                }
            },
    )
}

// --- the sea ---------------------------------------------------------------------------------

private enum class Layer(
    val base: Float, val a1: Float, val l1: Float, val t1: Float, val a2: Float, val l2: Float, val t2: Float,
    val top: Color, val bottom: Color, val nightTop: Color, val nightBottom: Color, val foam: Float,
) {
    // Heights as parts of the scene's height, lengths of its width, periods in seconds (all divide LOOP_S).
    BACK(0.70f, 0.010f, 0.31f, 8f, 0.005f, 0.13f, 5f, Color(0xFF4A93D6), Color(0xFF1F5F9E), Color(0xFF2D5A8C), Color(0xFF14365E), 0.18f),
    MID(0.80f, 0.014f, 0.43f, 6f, 0.006f, 0.19f, 4f, Color(0xFF2B78C2), Color(0xFF103F78), Color(0xFF1F4A7E), Color(0xFF0A2547), 0.28f),
    FRONT(0.885f, 0.017f, 0.57f, 5f, 0.007f, 0.24f, 3f, Color(0xFF1C64A8), Color(0xFF0A2F5C), Color(0xFF173C69), Color(0xFF061A35), 0.36f),
}

/** The layer's surface at [x] (pixels), in pixels from the top: two swells running left. */
private fun DrawScope.crest(layer: Layer, x: Float, t: Float): Float {
    val w = size.width
    val h = size.height
    return h * layer.base +
        h * layer.a1 * sin(TWO_PI * (x / (w * layer.l1) + t / layer.t1)) +
        h * layer.a2 * sin(TWO_PI * (x / (w * layer.l2) + t / layer.t2) + 1.3f)
}

/** The slope of [layer]'s surface at [x]: what tips the ship. */
private fun DrawScope.slope(layer: Layer, x: Float, t: Float): Float {
    val dx = size.width * 0.01f
    return (crest(layer, x + dx, t) - crest(layer, x - dx, t)) / (2 * dx)
}

private fun DrawScope.drawWater(t: Float, layer: Layer, night: Boolean) {
    val w = size.width
    val steps = 48
    val surface = Path()
    val body = Path()
    for (i in 0..steps) {
        val x = w * i / steps
        val y = crest(layer, x, t)
        if (i == 0) { surface.moveTo(x, y); body.moveTo(x, y) } else { surface.lineTo(x, y); body.lineTo(x, y) }
    }
    body.lineTo(w, size.height)
    body.lineTo(0f, size.height)
    body.close()
    val top = size.height * (layer.base - layer.a1 - layer.a2)
    drawPath(
        body,
        Brush.verticalGradient(if (night) listOf(layer.nightTop, layer.nightBottom) else listOf(layer.top, layer.bottom), startY = top, endY = size.height),
    )
    // The light along the crest.
    drawPath(surface, Color.White.copy(alpha = layer.foam * (if (night) 0.6f else 1f)), style = Stroke(width = size.width * 0.0035f, cap = StrokeCap.Round))
    // Ripples riding the swell: short bright arcs that drift with it.
    for (i in 0 until 5) {
        val x = w * (((i * 0.23f + 0.07f) + t / (layer.t1 * 6f)) % 1f)
        val y = crest(layer, x, t) + size.height * (0.025f + 0.012f * (i % 3))
        val half = w * 0.022f
        drawPath(
            Path().apply { moveTo(x - half, y); quadraticTo(x, y - size.height * 0.008f, x + half, y) },
            Color.White.copy(alpha = layer.foam * 0.55f), style = Stroke(width = size.width * 0.0025f, cap = StrokeCap.Round),
        )
    }
}

/** The sun's track on the water: short bright dashes that come and go with the swell. */
private fun DrawScope.drawGlitter(t: Float, behind: Boolean, night: Boolean) {
    val w = size.width
    val h = size.height
    val x0 = w * SUN_X
    val from = if (behind) Layer.BACK.base else Layer.MID.base + 0.02f
    val to = if (behind) Layer.MID.base else 0.98f
    for (i in 0 until 9) {
        val p = i / 8f
        val y = h * (from + (to - from) * p)
        val shimmer = 0.5f + 0.5f * sin(TWO_PI * (t / 2f) + i * 1.7f)
        val half = w * (0.012f + 0.035f * p) * (0.4f + 0.6f * shimmer)
        val sway = w * 0.012f * sin(TWO_PI * (t / 5f) + i)
        drawLine(
            (if (night) Color(0xFFDDE6FF) else Color.White).copy(alpha = (if (night) 0.4f else 0.55f) * (1f - p * 0.6f) * shimmer),
            Offset(x0 + sway - half, y), Offset(x0 + sway + half, y),
            strokeWidth = h * 0.006f, cap = StrokeCap.Round,
        )
    }
}

/**
 * A round glow, drawn as the square around it: the gradient has faded out before the square's
 * edges, so there's no circle's edge at all. On the emulator's GPU the sun's rim, where the
 * gradient reached nothing, came out as bright orange specks.
 */
internal fun DrawScope.drawGlow(brush: Brush, center: Offset, radius: Float) =
    drawRect(brush, topLeft = center - Offset(radius, radius), size = Size(radius * 2, radius * 2))

/** The sun, or by night the moon with its halo. */
private fun DrawScope.drawSun(t: Float, night: Boolean) {
    val c = Offset(size.width * SUN_X, size.height * 0.22f)
    val breathe = 1f + 0.03f * sin(TWO_PI * t / 6f)
    if (night) {
        val r = size.width * 0.15f * breathe
        drawGlow(Brush.radialGradient(0f to Color(0x66C9D8FF), 0.35f to Color(0x22A9BFFF), 1f to Color(0x00A9BFFF), center = c, radius = r), c, r)
        val disc = size.width * 0.045f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE3E9F7)), center = c - Offset(disc * 0.3f, disc * 0.3f), radius = disc * 1.4f), radius = disc, center = c)
        // Its seas, faintly.
        for ((dx, dy, rr) in listOf(Triple(-0.3f, -0.1f, 0.22f), Triple(0.25f, 0.2f, 0.16f), Triple(0.05f, -0.35f, 0.12f))) {
            drawCircle(Color(0xFFC6CEDF).copy(alpha = 0.55f), radius = disc * rr, center = c + Offset(disc * dx, disc * dy))
        }
        return
    }
    val r = size.width * 0.15f * breathe
    // Out to a clear orange, not a clear black, which turned the edge brown.
    drawGlow(
        Brush.radialGradient(
            0f to Color(0xFFFFF1C4), 0.28f to Color(0xFFFFD27A), 0.5f to Color(0x99FFB347), 1f to Color(0x00FFB347),
            center = c, radius = r,
        ),
        c, r,
    )
}

/** A night sky's stars, twinkling slowly. */
private fun DrawScope.drawStars(t: Float) {
    for ((i, star) in STARS.withIndex()) {
        val twinkle = 0.45f + 0.55f * (0.5f + 0.5f * sin(TWO_PI * t / 4f + i * 2.3f))
        drawCircle(Color.White.copy(alpha = 0.8f * twinkle), radius = size.width * (0.0018f + 0.0012f * (i % 3)), center = Offset(size.width * star.x, size.height * star.y))
    }
}

private val STARS = listOf(
    Offset(0.06f, 0.08f), Offset(0.13f, 0.32f), Offset(0.24f, 0.06f), Offset(0.35f, 0.18f), Offset(0.41f, 0.04f),
    Offset(0.52f, 0.12f), Offset(0.6f, 0.03f), Offset(0.66f, 0.38f), Offset(0.93f, 0.08f), Offset(0.95f, 0.42f),
    Offset(0.04f, 0.5f), Offset(0.3f, 0.44f),
)

/** Two gulls gliding about, wings beating now and then. */
private fun DrawScope.drawGulls(t: Float, ink: Color) {
    val w = size.width
    val h = size.height
    for ((i, home) in listOf(Offset(0.16f, 0.17f), Offset(0.27f, 0.27f)).withIndex()) {
        val x = w * (home.x + 0.035f * sin(TWO_PI * t / 15f + i * 2f))
        val y = h * (home.y + 0.025f * sin(TWO_PI * t / 10f + i))
        val span = w * (0.028f - i * 0.006f)
        // A few beats, then a glide with the wings held up.
        val beat = sin(TWO_PI * t / 0.6f + i)
        val gliding = sin(TWO_PI * t / 6f + i * 3f) < 0f
        val lift = if (gliding) 0.35f else beat
        val tip = span * 0.45f * lift
        val gull = Path().apply {
            moveTo(x - span, y - tip)
            quadraticTo(x - span * 0.45f, y - span * 0.42f, x, y)
            quadraticTo(x + span * 0.45f, y - span * 0.42f, x + span, y - tip)
        }
        drawPath(gull, ink.copy(alpha = 0.62f), style = Stroke(width = w * 0.0045f, cap = StrokeCap.Round))
    }
}

// --- the ship ----------------------------------------------------------------------------------

/**
 * The ship in its own units: the waterline at y = 0, the bow to the right (x up to 44 with the
 * bowsprit), the masts up to y = -66. [k] pixels to a unit.
 */
/** Where the ship is this frame, for what's drawn on the water around it. */
private class Placement(val x: Float, val y: Float, val roll: Float, val speed: Float)

private fun DrawScope.drawShip(
    t: Float, v: Float, k: Float,
    hull: Path, sheer: Path, planks: List<Path>,
    face: ImageBitmap, strips: Int, shades: List<ColorFilter>, night: Boolean,
): Placement {
    val w = size.width
    // Sailing in from beyond the left edge, slowing to its place.
    val arrive = ARRIVE.transform((v / ARRIVE_S).coerceIn(0f, 1f))
    val x = w * (-0.42f + (SHIP_X + 0.42f) * arrive)
    // Riding the middle swell: up and down with it, tipped by its slope, and a slow roll of its own.
    val y = crest(Layer.MID, x, t) - k * 0.6f
    val roll = -atan(slope(Layer.MID, x, t)) * (180f / PI.toFloat()) * 0.45f + 1.4f * sin(TWO_PI * t / 5f)
    // The sails fill as it comes in; then they breathe with the wind.
    val fill = (0.35f + 0.65f * ARRIVE.transform((v / (ARRIVE_S * 0.9f)).coerceIn(0f, 1f))) * (1f + 0.18f * sin(TWO_PI * t / 3f))
    val hoist = HOIST.transform(((v - HOIST_AT) / HOIST_S).coerceIn(0f, 1f))
    // Faster at first: a longer wake while it's still coming in.
    val speed = 1f - arrive * 0.75f
    withTransform({
        translate(x, y)
        rotate(roll, pivot = Offset.Zero)
    }) {
        withTransform({ scale(k, k, pivot = Offset.Zero) }) {
            drawRigging(night)
            drawMasts()
            drawSails(t, fill)
            drawHull(hull, sheer, planks, night)
            drawSpray(t)
        }
        if (hoist > 0f) drawFlag(t, k, hoist, face, strips, shades)
    }
    return Placement(x, y, roll, speed)
}

/** The hull's shadow on the water, just under it. */
private fun DrawScope.drawHullShadow(k: Float) {
    val c = Offset(1f * k, 2.2f * k)
    drawOval(
        Brush.radialGradient(listOf(Color(0x55041A33), Color.Transparent), center = c, radius = 36f * k),
        topLeft = Offset(c.x - 36f * k, c.y - 3.2f * k), size = Size(72f * k, 6.4f * k),
    )
}

private fun hullPath() = Path().apply {
    moveTo(-34f, -15f)
    lineTo(-31.5f, -15.4f)
    cubicTo(-33.2f, -8f, -32.3f, -2f, -29f, 2f)
    cubicTo(-22f, 8.6f, 18f, 9.2f, 28.5f, 1.5f)
    cubicTo(32f, -1f, 34.6f, -6f, 35.6f, -11.6f)
    lineTo(33.6f, -12.1f)
    lineTo(27f, -10.6f)
    cubicTo(18f, -8f, -8f, -7f, -18f, -9f)
    lineTo(-24f, -11f)
    lineTo(-24.6f, -14.2f)
    close()
}

/** The gunwale, stern to bow, gilded. */
private fun sheerPath() = Path().apply {
    moveTo(-34f, -15f)
    lineTo(-24.6f, -14.2f)
    lineTo(-24f, -11f)
    lineTo(-18f, -9f)
    cubicTo(-8f, -7f, 18f, -8f, 27f, -10.6f)
    lineTo(33.6f, -12.1f)
    lineTo(35.6f, -11.6f)
}

/** The planks' seams, following the hull's curve. */
private fun plankPaths() = listOf(-1.2f, 1.6f, 4.2f, 6.6f).map { d ->
    Path().apply {
        moveTo(-34f, -6f + d)
        cubicTo(-15f, -3.6f + d * 1.05f, 15f, -3.6f + d * 1.05f, 36f, -6.5f + d)
    }
}

private fun DrawScope.drawHull(hull: Path, sheer: Path, planks: List<Path>, night: Boolean) {
    drawPath(hull, Brush.verticalGradient(listOf(Color(0xFF7A4B2A), Color(0xFF4A2C17), Color(0xFF2A170C)), startY = -15f, endY = 9f))
    clipPath(hull) {
        // A warm highlight along the upper strakes, the curve of the hull catching the sun.
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), startY = -12f, endY = -5f),
            topLeft = Offset(-36f, -16f), size = Size(74f, 12f),
        )
        for (p in planks) drawPath(p, Color(0xFF1E0F06).copy(alpha = 0.55f), style = Stroke(width = 0.28f))
        // The wale: a gilded band along the side.
        drawPath(
            Path().apply { moveTo(-34f, -6f); cubicTo(-15f, -3.4f, 15f, -3.4f, 36f, -6.6f) },
            Color(0xFFD9A441).copy(alpha = 0.85f), style = Stroke(width = 0.7f),
        )
        // The gun ports, each with a gilded frame.
        for (px in listOf(-13f, -7f, -1f, 5f, 11f, 17f, 23f)) {
            val py = -6.6f + (px / 36f) * (px / 36f) * 1.6f
            drawRect(Color(0xFFD9A441), topLeft = Offset(px - 0.95f, py - 0.95f), size = Size(1.9f, 1.9f))
            drawRect(Color(0xFF120903), topLeft = Offset(px - 0.7f, py - 0.7f), size = Size(1.4f, 1.4f))
        }
        // The stern windows, lit from inside.
        for (wx in listOf(-32.4f, -30.2f, -28f)) {
            drawRoundRect(Color(0xFFFFC66B), topLeft = Offset(wx - 0.7f, -12.6f), size = Size(1.4f, 2.2f), cornerRadius = CornerRadius(0.5f))
        }
        drawRect(Color(0xFFD9A441).copy(alpha = 0.8f), topLeft = Offset(-34f, -10.1f), size = Size(8f, 0.45f))
    }
    drawPath(sheer, Color(0xFFE2B04E), style = Stroke(width = 0.75f, cap = StrokeCap.Round))
    drawPath(hull, Color(0xFF1A0D05).copy(alpha = 0.6f), style = Stroke(width = 0.3f))
    // The stern lantern, glowing; more so by night.
    drawLine(Color(0xFF3A2414), Offset(-33.6f, -15f), Offset(-33.6f, -17.6f), strokeWidth = 0.35f)
    val glow = if (night) 5f else 2.6f
    drawGlow(Brush.radialGradient(listOf(Color(0xCCFFD27A), Color(0x00FFD27A)), center = Offset(-33.6f, -18.4f), radius = glow), Offset(-33.6f, -18.4f), glow)
    drawCircle(Color(0xFFFFE3A0), radius = 0.7f, center = Offset(-33.6f, -18.4f))
    // The bowsprit.
    drawLine(Color(0xFF5E3B1F), Offset(31f, -10.5f), Offset(44f, -20f), strokeWidth = 1.1f, cap = StrokeCap.Round)
}

private fun DrawScope.drawMasts() {
    val wood = Brush.horizontalGradient(listOf(Color(0xFF5E3B1F), Color(0xFF9A6A3E), Color(0xFF5E3B1F)))
    for ((mx, top, thick) in listOf(Triple(MAIN_X, MAIN_TOP, 1.5f), Triple(FORE_X, FORE_TOP, 1.3f), Triple(MIZZEN_X, MIZZEN_TOP, 1.1f))) {
        withTransform({ translate(mx - thick / 2, 0f) }) {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF5E3B1F), Color(0xFF9A6A3E), Color(0xFF5E3B1F)), 0f, thick), topLeft = Offset(0f, top), size = Size(thick, -8f - top))
        }
        drawCircle(Color(0xFF3A2414), radius = thick * 0.55f, center = Offset(mx, top))
    }
    // The crow's nest on the main mast.
    drawRoundRect(wood, topLeft = Offset(MAIN_X - 2.6f, -43.6f), size = Size(5.2f, 1.9f), cornerRadius = CornerRadius(0.4f))
    drawLine(Color(0xFF3A2414), Offset(MAIN_X - 2.6f, -42.2f), Offset(MAIN_X + 2.6f, -42.2f), strokeWidth = 0.25f)
}

private fun DrawScope.drawRigging(night: Boolean) {
    val rope = if (night) Color(0xFFCDBB9C).copy(alpha = 0.45f) else Color(0xFF2B1B10).copy(alpha = 0.55f)
    val stroke = 0.22f
    fun line(a: Offset, b: Offset) = drawLine(rope, a, b, strokeWidth = stroke)
    line(Offset(FORE_X, FORE_TOP), Offset(44f, -20f))
    line(Offset(MAIN_X, MAIN_TOP + 1f), Offset(FORE_X, FORE_TOP + 2f))
    line(Offset(MAIN_X, MAIN_TOP + 1f), Offset(-33.5f, -15f))
    line(Offset(MIZZEN_X, MIZZEN_TOP), Offset(-33.5f, -15f))
    line(Offset(MIZZEN_X, MIZZEN_TOP + 1f), Offset(MAIN_X, MAIN_TOP + 8f))
    // Shrouds down to the gunwale either side of each mast.
    for ((mx, at) in listOf(MAIN_X to -40f, FORE_X to -35f, MIZZEN_X to -32f)) {
        for (d in listOf(-5.5f, -3f, 3f, 5.5f)) line(Offset(mx, at), Offset(mx + d, -8.5f - abs(d) * 0.1f))
    }
}

/** Square sails on the fore and main masts, a jib and a spanker; [fill] how full of wind they are. */
private fun DrawScope.drawSails(t: Float, fill: Float) {
    val b = 1.7f * fill
    jib(b)
    spanker(b)
    for (s in SQUARE) {
        val sway = 0.25f * sin(TWO_PI * t / 3f + s.left * 0.1f)
        squareSail(s.left, s.right, s.top, s.bottom, b + sway, emblem = s.emblem)
        // The yard it hangs from.
        drawRoundRect(Color(0xFF5E3B1F), topLeft = Offset(s.left - 1.2f, s.top - 0.55f), size = Size(s.right - s.left + 2.4f, 1.1f), cornerRadius = CornerRadius(0.5f))
    }
}

private class Square(val left: Float, val right: Float, val top: Float, val bottom: Float, val emblem: Boolean = false)

private val SQUARE = listOf(
    Square(FORE_X - 14f, FORE_X + 14f, -35f, -16f),
    Square(FORE_X - 11f, FORE_X + 11f, -49f, -37f),
    Square(MAIN_X - 17f, MAIN_X + 17f, -40f, -17.5f, emblem = true),
    Square(MAIN_X - 13f, MAIN_X + 13f, -57f, -45f),
)

private val CANVAS_LIGHT = Color(0xFFFFF8E8)
private val CANVAS_SHADE = Color(0xFFD5C39D)

private fun DrawScope.squareSail(left: Float, right: Float, top: Float, bottom: Float, b: Float, emblem: Boolean) {
    val mid = (top + bottom) / 2
    val sail = Path().apply {
        moveTo(left, top)
        lineTo(right, top)
        quadraticTo(right + b * 0.9f, mid, right - b * 0.25f, bottom)
        quadraticTo((left + right) / 2, bottom + b * 1.3f, left + b * 0.25f, bottom)
        quadraticTo(left - b * 0.9f, mid, left, top)
        close()
    }
    // Lit from the left, the belly in shade towards the wind's side.
    drawPath(sail, Brush.horizontalGradient(listOf(CANVAS_LIGHT, Color(0xFFF1E4C5), CANVAS_SHADE), startX = left, endX = right + b))
    clipPath(sail) {
        drawRect(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.06f)), startY = top, endY = bottom + b),
            topLeft = Offset(left - b, top), size = Size(right - left + 2 * b, bottom - top + 2 * b),
        )
        // Seams, bowed like the cloth.
        for (i in 1..3) {
            val sx = left + (right - left) * i / 4f
            drawPath(
                Path().apply { moveTo(sx, top); quadraticTo(sx + b * (i - 2) * 0.35f, mid, sx, bottom + b) },
                Color(0xFF8A7650).copy(alpha = 0.22f), style = Stroke(width = 0.18f),
            )
        }
        if (emblem) emblem(Offset((left + right) / 2 + b * 0.2f, mid + 0.6f), 3.1f)
    }
    drawPath(sail, Color(0xFF9C8A66).copy(alpha = 0.55f), style = Stroke(width = 0.22f))
}

/** Skull and crossbones painted on the main course. */
private fun DrawScope.emblem(c: Offset, s: Float) {
    val ink = Color(0xFF1C1C21).copy(alpha = 0.82f)
    for (turn in listOf(35f, -35f)) {
        rotate(turn, pivot = Offset(c.x, c.y + s * 0.55f)) {
            drawRoundRect(ink, topLeft = Offset(c.x - s * 1.7f, c.y + s * 0.42f), size = Size(s * 3.4f, s * 0.26f), cornerRadius = CornerRadius(s * 0.13f))
        }
    }
    drawCircle(ink, radius = s * 0.78f, center = c)
    drawRoundRect(ink, topLeft = Offset(c.x - s * 0.47f, c.y + s * 0.3f), size = Size(s * 0.94f, s * 0.6f), cornerRadius = CornerRadius(s * 0.18f))
    val cloth = CANVAS_LIGHT
    for (side in listOf(-1f, 1f)) drawCircle(cloth, radius = s * 0.2f, center = Offset(c.x + side * s * 0.3f, c.y - s * 0.02f))
    drawCircle(cloth, radius = s * 0.08f, center = Offset(c.x, c.y + s * 0.3f))
}

private fun DrawScope.jib(b: Float) {
    val sail = Path().apply {
        moveTo(FORE_X + 2f, FORE_TOP + 3f)
        quadraticTo(34f + b * 0.6f, -33f - b * 0.4f, 43f, -20.5f)
        quadraticTo(36f, -17f + b * 0.3f, 29.5f, -16.5f)
        quadraticTo(22f + b, -32f, FORE_X + 2f, FORE_TOP + 3f)
        close()
    }
    drawPath(sail, Brush.linearGradient(listOf(CANVAS_LIGHT, CANVAS_SHADE), start = Offset(20f, -45f), end = Offset(40f, -18f)))
    drawPath(sail, Color(0xFF9C8A66).copy(alpha = 0.55f), style = Stroke(width = 0.22f))
}

private fun DrawScope.spanker(b: Float) {
    val sail = Path().apply {
        moveTo(MIZZEN_X - 0.4f, -37f)
        lineTo(-31f, -40.5f)
        quadraticTo(-33f - b * 0.8f, -27f, -33.4f, -16.2f)
        lineTo(MIZZEN_X - 0.4f, -15.6f)
        quadraticTo(MIZZEN_X - 1.2f - b * 0.5f, -26f, MIZZEN_X - 0.4f, -37f)
        close()
    }
    drawPath(sail, Brush.horizontalGradient(listOf(CANVAS_SHADE, CANVAS_LIGHT), startX = -34f, endX = MIZZEN_X))
    drawPath(sail, Color(0xFF9C8A66).copy(alpha = 0.55f), style = Stroke(width = 0.22f))
    drawLine(Color(0xFF5E3B1F), Offset(MIZZEN_X, -37.2f), Offset(-31.4f, -40.8f), strokeWidth = 0.7f, cap = StrokeCap.Round)
    drawLine(Color(0xFF5E3B1F), Offset(MIZZEN_X, -15.4f), Offset(-34f, -16f), strokeWidth = 0.7f, cap = StrokeCap.Round)
}

/** White water thrown up at the bow, rising and falling. */
private fun DrawScope.drawSpray(t: Float) {
    for (i in 0 until 7) {
        val p = ((t / 1.2f + i / 7f) % 1f)
        val x = 34.5f + p * (4f + i % 3)
        val y = -1.5f - sin(PI.toFloat() * p) * (2.6f + (i % 2) * 1.2f)
        drawCircle(Color.White.copy(alpha = 0.75f * (1f - p)), radius = 0.42f + (i % 3) * 0.12f, center = Offset(x, y))
    }
}

/** The wake behind the stern, in pixels: longer while the ship is still coming in. */
private fun DrawScope.drawWake(t: Float, k: Float, speed: Float) {
    for (i in 0 until 3) {
        val length = k * (18f + 22f * speed) * (1f - i * 0.22f)
        val y = k * (1.2f + i * 1.4f)
        val drift = k * 1.5f * sin(TWO_PI * t / 2.4f + i)
        val wake = Path().apply {
            moveTo(-29f * k, y)
            quadraticTo(-29f * k - length * 0.5f, y + k * 1.2f + drift * 0.4f, -29f * k - length, y + drift)
        }
        drawPath(wake, Color.White.copy(alpha = 0.42f - i * 0.1f), style = Stroke(width = k * (0.7f - i * 0.15f), cap = StrokeCap.Round))
    }
}

/** The Jolly Roger at the main masthead, in pixels: run up from the crow's nest, then flying. */
private fun DrawScope.drawFlag(t: Float, k: Float, hoist: Float, face: ImageBitmap, strips: Int, shades: List<ColorFilter>) {
    val left = (MAIN_X + 0.75f) * k
    val top = (-42f + (MAIN_TOP + 1.2f + 42f) * hoist) * k
    val amp = k * 0.85f
    val strength = 0.8f + 0.2f * sin(TWO_PI * t / 6f)
    val phase = TWO_PI * t / 1.2f
    for (i in 0 until strips) {
        val x0 = i * face.width / strips
        val x1 = (i + 1) * face.width / strips
        val u = x0.toFloat() / face.width
        val angle = phase - u * 2.4f * PI.toFloat()
        val reach = 0.08f + 0.92f * u
        val dy = sin(angle) * amp * strength * reach
        val level = (cos(angle) * reach * strength * SHADE_LEVELS).roundToInt().coerceIn(-SHADE_LEVELS, SHADE_LEVELS)
        drawImage(
            face,
            srcOffset = IntOffset(x0, 0),
            srcSize = IntSize(x1 - x0, face.height),
            dstOffset = IntOffset((left + x0).roundToInt(), (top + dy).roundToInt()),
            dstSize = IntSize(x1 - x0, face.height),
            alpha = hoist.coerceAtMost(1f),
            colorFilter = shades[level + SHADE_LEVELS],
        )
    }
    // The halyard it ran up on.
    drawLine(Color(0xFF2B1B10).copy(alpha = 0.6f), Offset(left, top), Offset(left, -42f * k), strokeWidth = k * 0.18f)
}

/** Lighter where the cloth faces the sun, darker where it turns away: [d] from -1 to 1. */
private fun shadeFilter(d: Float): ColorFilter {
    val shade = 0.22f * d
    return if (shade >= 0f) {
        ColorFilter.lighting(Color.White, Color(shade, shade, shade))
    } else {
        ColorFilter.lighting(Color(1f + shade, 1f + shade, 1f + shade), Color.Black)
    }
}

private const val TWO_PI = (2 * PI).toFloat()
/** The sea's clock loops every two minutes: every period above divides it, so it never jumps. */
private const val LOOP_S = 120f
private const val VOYAGE_S = 5f
private const val ARRIVE_S = 2.6f
private const val HOIST_AT = 2.1f
private const val HOIST_S = 1.4f
/** A unit of the ship, as a part of the scene's width. */
private const val SCALE = 0.0074f
private const val SHIP_X = 0.45f
private const val SUN_X = 0.8f
private const val FLAG_W = 16.5f
private const val SHADE_LEVELS = 10
private const val MAIN_X = -1f
private const val MAIN_TOP = -71f
private const val FORE_X = 15f
private const val FORE_TOP = -52f
private const val MIZZEN_X = -20f
private const val MIZZEN_TOP = -46f

private val ARRIVE = CubicBezierEasing(0.16f, 0.84f, 0.3f, 1f)
private val HOIST = CubicBezierEasing(0.25f, 0.9f, 0.3f, 1f)
