package io.github.sirionero.mycar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.min
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/** The splash itself, with the theme's colours handed in: what [UpdateSplash] shows. */
@Composable
internal fun UpdateSplashContent(
    number: String,
    tag: String,
    leaving: Boolean,
    onGone: () -> Unit,
    surface: Color, ink: Color, quiet: Color, accent: Color, glow: Color, chip: Color, onChip: Color,
    onTap: () -> Unit,
) {
    val fade by animateFloatAsState(
        if (leaving) 0f else 1f, tween(if (leaving) 380 else 0), label = "leave",
        finishedListener = { if (leaving) onGone() },
    )
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade; scaleX = 0.96f + 0.04f * fade; scaleY = 0.96f + 0.04f * fade }
            .background(surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    ) {
        val w = maxWidth
        val h = maxHeight
        fun cq(n: Float): Dp = w * (n / 100f)
        val density = LocalDensity.current
        fun cqSp(n: Float): TextUnit = with(density) { cq(n).toSp() }
        val flagW = min(cq(86f), h * 0.36f * 1.62f)
        val flagH = flagW / 1.62f
        // The flag and the words under it, together a little above the middle.
        val flagTop = ((h - flagH - cq(46f)) * 0.42f).coerceAtLeast(h * 0.05f)

        // A soft light behind the flag, breathing with the wind.
        val air = rememberInfiniteTransition(label = "air")
        val breath by air.animateFloat(0f, 1f, infiniteRepeatable(tween(4_000, easing = LinearEasing)), label = "breath")
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, (flagTop + flagH * 0.55f).toPx())
            val r = size.width * (0.62f + 0.04f * sin(2 * PI.toFloat() * breath))
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.85f), glow.copy(alpha = 0.25f), Color.Transparent), center = center, radius = r), radius = r, center = center)
        }
        Sparkles(accent, Modifier.fillMaxSize(), flagTop, flagH)

        PirateFlag("update", Modifier.align(Alignment.TopCenter).padding(top = flagTop).size(flagW, flagH))

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = flagTop + flagH + cq(7f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Updated to", color = quiet, style = TextStyle(fontSize = cqSp(4.4f), fontWeight = FontWeight.SemiBold),
                modifier = Modifier.up(entrance("update", 900), cq(6f)),
            )
            Text(
                number, color = accent,
                style = TextStyle(fontSize = cqSp(19f), fontWeight = FontWeight.Black, letterSpacing = cqSp(-0.9f)),
                modifier = Modifier.pop(entrance("update", 1_050, 900)),
            )
            if (tag.isNotEmpty()) {
                Text(
                    tag, color = onChip, style = TextStyle(fontSize = cqSp(3.8f), fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = cq(2f)).up(entrance("update", 1_400), cq(6f))
                        .clip(RoundedCornerShape(50)).background(chip).padding(horizontal = cq(4.2f), vertical = cq(1.8f)),
                )
            }
        }
        val hint = entrance("update", 2_200, 600)
        Text(
            "Tap anywhere to go on", color = quiet.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = cq(8f))
                .graphicsLayer { alpha = hint },
        )
    }
}

/** Little four-pointed stars around the flag, each twinkling in its own time. */
@Composable
private fun Sparkles(color: Color, modifier: Modifier, flagTop: Dp, flagHeight: Dp) {
    val twinkle = rememberInfiniteTransition(label = "twinkle")
    val t by twinkle.animateFloat(0f, 12f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "t")
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(1_300)
        shown.animateTo(1f, tween(900, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
    }
    Canvas(modifier) {
        val top = flagTop.toPx()
        val fh = flagHeight.toPx()
        for ((i, s) in SPARKS.withIndex()) {
            val on = 0.5f + 0.5f * sin(2 * PI.toFloat() * (t / s.period) + i)
            val a = shown.value * on
            if (a <= 0.02f) continue
            val c = Offset(size.width * s.x, top + fh * s.y)
            val r = size.width * s.size * 1.4f * (0.6f + 0.4f * on)
            val star = Path().apply {
                moveTo(c.x, c.y - r)
                quadraticTo(c.x, c.y, c.x + r, c.y)
                quadraticTo(c.x, c.y, c.x, c.y + r)
                quadraticTo(c.x, c.y, c.x - r, c.y)
                quadraticTo(c.x, c.y, c.x, c.y - r)
                close()
            }
            drawPath(star, color.copy(alpha = 0.75f * a))
        }
    }
}

private class Spark(val x: Float, val y: Float, val size: Float, val period: Float)

/** Where the sparkles are: x as a part of the width, y of the flag's height from its top. */
private val SPARKS = listOf(
    Spark(0.1f, -0.15f, 0.022f, 3f), Spark(0.86f, -0.25f, 0.018f, 4f), Spark(0.93f, 0.55f, 0.026f, 2.4f),
    Spark(0.06f, 0.8f, 0.016f, 3f), Spark(0.22f, 1.25f, 0.014f, 6f), Spark(0.8f, 1.2f, 0.02f, 4f),
    Spark(0.5f, -0.3f, 0.012f, 2.4f), Spark(0.97f, 1.0f, 0.012f, 6f),
)

