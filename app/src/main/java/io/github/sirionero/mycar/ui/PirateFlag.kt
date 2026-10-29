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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import io.github.sirionero.mycar.car.design.InterFamily
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The intro's flag, where a band of words used to scroll: a Jolly Roger of myCar's own, drawn
 * here (no picture ships with the app) - skull and crossbones, "100% LOCAL" under them and what
 * that means at the hem - flying from its pole in the wind. Hoisted up the pole when the step
 * opens. The cloth is drawn once into a picture, then laid out in thin strips, each lifted by the
 * wave and lit or shaded by its slope.
 */
@Composable
internal fun PirateFlag(key: Any, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    // Read while drawing, like the wind: the screen isn't composed again for every frame, and the
    // cloth isn't painted again either.
    val hoist = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(250)
        hoist.animateTo(1f, tween(1_200, easing = LinearEasing))
    }
    val wind = rememberInfiniteTransition(label = "wind")
    val phase by wind.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(2_300, easing = LinearEasing)), label = "wave")
    val gust by wind.animateFloat(0f, TWO_PI, infiniteRepeatable(tween(7_100, easing = LinearEasing)), label = "gust")
    Spacer(
        modifier
            .semantics { contentDescription = "A pirate flag: 100% local. We defend our data." }
            // The strips' light and shade fall on the cloth only (SrcAtop), not on what's behind;
            // and a flag on its way up shows only inside its own place.
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
            }
            .drawWithCache {
                val pole = (size.width * 0.022f).coerceAtLeast(3f)
                val knob = pole * 1.7f
                val amp = size.height * 0.085f
                val flagLeft = pole * 0.5f
                val flagTop = knob * 2.2f + amp
                val flagWidth = size.width - flagLeft - amp
                val flagHeight = minOf(flagWidth / 1.5f, size.height - flagTop - amp * 1.6f)
                val face = ImageBitmap(flagWidth.toInt().coerceAtLeast(2), flagHeight.toInt().coerceAtLeast(2))
                CanvasDrawScope().draw(this, layoutDirection, Canvas(face), Size(face.width.toFloat(), face.height.toFloat())) {
                    drawCloth(measurer)
                }
                val strips = (face.width / 4).coerceIn(24, 140)
                val poleBrush = Brush.horizontalGradient(listOf(Color(0xFF6E6E73), Color(0xFFD6D6DB), Color(0xFF5A5A5F)), 0f, pole)
                onDrawBehind {
                    val shown = HOIST.transform(hoist.value.coerceIn(0f, 1f))
                    // Up the pole from below as it's hoisted.
                    val lift = (1f - shown) * (size.height - flagTop)
                    val strength = 0.78f + 0.22f * sin(gust)
                    for (i in 0 until strips) {
                        val x0 = i * face.width / strips
                        val x1 = (i + 1) * face.width / strips
                        val u = x0.toFloat() / face.width
                        // The wave runs away from the pole, and the free end flies the most.
                        val angle = phase - u * 2.2f * PI.toFloat()
                        val reach = 0.1f + 0.9f * u
                        val dy = (sin(angle) * amp * strength * reach + lift).toInt()
                        val top = (flagTop + dy).toInt()
                        drawImage(
                            face,
                            srcOffset = IntOffset(x0, 0),
                            srcSize = IntSize(x1 - x0, face.height),
                            dstOffset = IntOffset((flagLeft + x0).toInt(), top),
                            dstSize = IntSize(x1 - x0, face.height),
                            alpha = shown,
                        )
                        val slope = cos(angle) * reach * strength
                        drawRect(
                            if (slope > 0f) Color.White else Color.Black,
                            topLeft = Offset(flagLeft + x0, top.toFloat()),
                            size = Size((x1 - x0).toFloat(), face.height.toFloat()),
                            alpha = abs(slope) * 0.2f * shown,
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                    // The pole over the cloth's sleeve, and its gilded knob.
                    drawRoundRect(poleBrush, topLeft = Offset(0f, knob), size = Size(pole, size.height - knob), cornerRadius = CornerRadius(pole / 2))
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE08A), Color(0xFFC08A1E)), center = Offset(pole / 2 - knob * 0.3f, knob * 0.7f), radius = knob * 1.4f), radius = knob, center = Offset(pole / 2, knob))
                }
            },
    )
}

/** The cloth, flat: black with a torn fly, the skull and crossbones, and the words. */
private fun DrawScope.drawCloth(measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    val cloth = Path().apply {
        moveTo(0f, 0f)
        lineTo(w * 0.97f, 0f)
        // The fly, torn by the wind: notches down the free edge.
        TORN.forEach { (y, x) -> lineTo(w * x, h * y) }
        lineTo(w * 0.95f, h)
        lineTo(0f, h)
        close()
    }
    drawPath(cloth, Brush.verticalGradient(listOf(Color(0xFF232327), Color(0xFF0C0C0E))))
    drawPath(cloth, Color.White.copy(alpha = 0.12f), style = Stroke(width = h * 0.012f))
    // The sleeve along the pole.
    drawRect(Color(0xFF2C2C31), size = Size(w * 0.045f, h))

    val bone = Color(0xFFF2EEE3)
    val dark = Color(0xFF111114)
    val s = h * 0.15f
    val cx = w * 0.52f
    val cy = h * 0.32f

    // Crossbones behind the skull: two bones, a knuckle on each side of each end.
    val crossing = Offset(cx, cy + s * 0.55f)
    for (turn in listOf(35f, -35f)) {
        rotate(turn, pivot = crossing) {
            val length = s * 3.6f
            val thick = s * 0.32f
            drawRoundRect(bone, topLeft = Offset(crossing.x - length / 2, crossing.y - thick / 2), size = Size(length, thick), cornerRadius = CornerRadius(thick / 2))
            for (end in listOf(-1f, 1f)) {
                for (side in listOf(-1f, 1f)) {
                    drawCircle(bone, radius = thick * 0.62f, center = Offset(crossing.x + end * length / 2, crossing.y + side * thick * 0.45f))
                }
            }
        }
    }
    // The skull: crown and jaw, the sockets and nose cut out in the cloth's black, teeth.
    drawCircle(bone, radius = s, center = Offset(cx, cy))
    drawRoundRect(bone, topLeft = Offset(cx - s * 0.6f, cy + s * 0.35f), size = Size(s * 1.2f, s * 0.85f), cornerRadius = CornerRadius(s * 0.25f))
    for (side in listOf(-1f, 1f)) {
        drawOval(dark, topLeft = Offset(cx + side * s * 0.42f - s * 0.26f, cy - s * 0.18f), size = Size(s * 0.52f, s * 0.6f))
    }
    val nose = Path().apply {
        moveTo(cx, cy + s * 0.38f)
        lineTo(cx - s * 0.13f, cy + s * 0.62f)
        lineTo(cx + s * 0.13f, cy + s * 0.62f)
        close()
    }
    drawPath(nose, dark)
    for (k in -1..1) {
        val x = cx + k * s * 0.24f
        drawLine(dark, Offset(x, cy + s * 0.88f), Offset(x, cy + s * 1.18f), strokeWidth = s * 0.07f)
    }

    // The words: big and bold under the bones, then what they mean along the hem.
    val local = measurer.measure(
        "100% LOCAL",
        TextStyle(color = bone, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = (h * 0.15f).toSp(), letterSpacing = 0.06.em),
    )
    drawText(local, topLeft = Offset(cx - local.size.width / 2f, h * 0.62f))
    val motto = measurer.measure(
        "WE DEFEND OUR DATA",
        TextStyle(color = bone.copy(alpha = 0.85f), fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = (h * 0.078f).toSp(), letterSpacing = 0.2.em),
    )
    drawText(motto, topLeft = Offset(cx - motto.size.width / 2f, h * 0.81f))
}

/** The torn fly, from the top down: (how far down, how far out) as parts of the cloth. */
private val TORN = listOf(
    0.1f to 0.91f, 0.2f to 0.995f, 0.33f to 0.93f, 0.45f to 1f, 0.58f to 0.915f, 0.7f to 0.985f, 0.83f to 0.905f, 0.93f to 0.97f,
)

private const val TWO_PI = (2 * PI).toFloat()

/** Hoisting: quick off the ground, slowing to the top. */
private val HOIST = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
