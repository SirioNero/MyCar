package io.github.sirionero.mycar.car

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.design.MyCarMotion

/*
 * The player's controls as recent phones draw them: plain glyphs with no button under them, and a
 * thin line to seek along instead of a bar. Whatever sits under the player shows through.
 */

/**
 * A glyph to tap, in [size] of room: nothing behind it at rest, a soft disc while pressed, and it
 * sinks a little under the finger. [lit] marks a toggle that's on (shuffle, repeat) with a soft
 * rounded square, as recent phones do.
 */
@Composable
internal fun PlainButton(
    onClick: () -> Unit,
    description: String,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lit: Boolean = false,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) PRESSED_SCALE else 1f,
        tween(MyCarMotion.PressMs, easing = MyCarMotion.Decelerate),
        label = "plainPress",
    )
    val tint = LocalContentColor.current
    val shape = if (lit) RoundedCornerShape(size * 0.26f) else CircleShape
    Box(
        modifier
            .size(size)
            .semantics { contentDescription = description }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else DISABLED_ALPHA
            }
            .clip(shape)
            .background(if (lit) tint.copy(alpha = LIT_ALPHA) else Color.Transparent)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * A thin line to seek along: the played part solid, the rest faint, thickening while dragged. A
 * tap jumps to that point; a drag seeks once the finger lifts, so the song doesn't stutter along
 * behind it.
 */
@Composable
internal fun ThinSeekBar(fraction: Float, onSeek: (Float) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeek)
    val shown = (dragged ?: fraction).coerceIn(0f, 1f)
    val thickness by animateDpAsState(
        if (dragged != null) LINE_DRAGGED else LINE,
        tween(MyCarMotion.PressMs, easing = MyCarMotion.Decelerate),
        label = "seekLine",
    )
    val tint = LocalContentColor.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(TOUCH_BAND)
            .then(
                if (!enabled) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { seek((it.x / size.width).coerceIn(0f, 1f)) }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = (it.x / size.width).coerceIn(0f, 1f) },
                            onDragEnd = {
                                dragged?.let { seek(it) }
                                dragged = null
                            },
                            onDragCancel = { dragged = null },
                        ) { change, _ ->
                            change.consume()
                            dragged = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(thickness).clip(CircleShape).background(tint.copy(alpha = TRACK_ALPHA)))
        Box(Modifier.width(maxWidth * shown).height(thickness).clip(CircleShape).background(tint))
    }
}

private const val PRESSED_SCALE = 0.88f
private const val DISABLED_ALPHA = 0.35f
private const val LIT_ALPHA = 0.2f
private const val TRACK_ALPHA = 0.25f
private val LINE = 6.dp
private val LINE_DRAGGED = 10.dp
private val TOUCH_BAND = 32.dp
