package io.github.sirionero.mycar.car.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The glass look of myCar's icons: light catching the top edge, a soft reflection over the upper
 * half and a little shade at the foot, laid over whatever the icon shows. Small and bold on
 * purpose - an icon is a few dozen pixels in the car's video, where fine detail is lost and
 * faint gradients turn to bands.
 */

/**
 * Lays the glass over this element's content: meant to follow a clip to [shape], which keeps the
 * rim's outer half and the reflection inside the icon. [strength] scales it all, lighter for an
 * app's own artwork than for myCar's flat tiles.
 */
fun Modifier.glassFinish(shape: Shape = AppIconShape, strength: Float = 1f): Modifier = drawWithContent {
    drawContent()
    // The reflection: the bottom of a wide oval, bright at the top and gone by the middle.
    drawOval(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.32f * strength),
            1f to Color.White.copy(alpha = 0f),
            startY = 0f,
            endY = size.height * 0.5f,
        ),
        topLeft = Offset(-size.width * 0.3f, -size.height * 0.6f),
        size = Size(size.width * 1.6f, size.height * 1.12f),
    )
    // A little shade at the foot, so the icon reads as a thing with depth rather than a sticker.
    drawRect(
        Brush.verticalGradient(
            0.55f to Color.Black.copy(alpha = 0f),
            1f to Color.Black.copy(alpha = 0.18f * strength),
        ),
    )
    // The rim: light along the top edge, fading round the sides, and caught again at the foot,
    // as light passing through glass comes out at the far edge.
    drawOutline(
        shape.createOutline(size, layoutDirection, this),
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.7f * strength),
            0.4f to Color.White.copy(alpha = 0.12f * strength),
            0.8f to Color.White.copy(alpha = 0.12f * strength),
            1f to Color.White.copy(alpha = 0.42f * strength),
        ),
        style = Stroke(width = RIM.toPx()),
    )
}

/**
 * One of myCar's own icons in glass: [glyph] in white on [color], the colour a shade lighter at
 * the top than the foot, in the squircle frame.
 */
@Composable
fun GlassTile(
    color: Color,
    @DrawableRes glyph: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    glyphSize: Dp = size * 0.56f,
) {
    Box(
        modifier
            .size(size)
            .clip(AppIconShape)
            .background(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.2f), lerp(color, Color.Black, 0.12f))))
            .glassFinish(),
        contentAlignment = Alignment.Center,
    ) {
        val painter = painterResource(glyph)
        // A faint shadow a hair below the glyph lifts it off the glass.
        Icon(painter, contentDescription = null, modifier = Modifier.size(glyphSize).offset(y = 1.dp), tint = Color.Black.copy(alpha = 0.22f))
        Icon(painter, contentDescription = null, modifier = Modifier.size(glyphSize), tint = Color.White)
    }
}

private val RIM = 1.5.dp
