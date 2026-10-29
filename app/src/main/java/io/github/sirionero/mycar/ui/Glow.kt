package io.github.sirionero.mycar.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * A round glow (the sun, the moon's halo, the stern lantern, the light behind the flag): its
 * gradient painted once into a picture, off the GPU, and the picture stretched over the glow's
 * square. Drawn by the emulator's GPU, the gradient left bright orange specks on the sun's rim
 * as a circle, and took the emulator down as the ship sailed in as a square. [stops] go from the
 * middle (0) to the edge (1), which should be clear.
 */
internal fun DrawScope.drawGlow(center: Offset, radius: Float, vararg stops: Pair<Float, Color>) {
    if (radius <= 0f) return
    val image = glowImage(stops.toList())
    withTransform({
        translate(center.x - radius, center.y - radius)
        scale(radius * 2 / GLOW_SIZE, radius * 2 / GLOW_SIZE, pivot = Offset.Zero)
    }) { drawImage(image) }
}

/** Big enough that, stretched over the light behind the flag, the gradient stays smooth. */
private const val GLOW_SIZE = 256

/** The few glows of the intro and of the update page, each painted once. */
private val glows = HashMap<List<Pair<Float, Color>>, ImageBitmap>()

private fun DrawScope.glowImage(stops: List<Pair<Float, Color>>): ImageBitmap = glows.getOrPut(stops) {
    if (glows.size >= 8) glows.clear()
    val image = ImageBitmap(GLOW_SIZE, GLOW_SIZE)
    val half = GLOW_SIZE / 2f
    CanvasDrawScope().draw(this, layoutDirection, Canvas(image), Size(GLOW_SIZE.toFloat(), GLOW_SIZE.toFloat())) {
        drawRect(Brush.radialGradient(*stops.toTypedArray(), center = Offset(half, half), radius = half))
    }
    image
}
