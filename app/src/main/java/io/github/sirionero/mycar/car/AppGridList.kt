package io.github.sirionero.mycar.car

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app grid as one list (car Settings > App grid > List): its rows one under the other, swiped up
 * and down instead of turned sideways a page at a time, as Android Auto's are - easier for some
 * drivers than a sideways swipe. The rows are the pages' own, so icons, columns and gaps stay the same.
 * No dots: a thin bar in the [gutter] right of the rows says how far down the list is, and that there's
 * more, as the dots did.
 */
@Composable
internal fun AppGridList(gutter: Dp, rows: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .scrollBar(scroll, MaterialTheme.colorScheme.onSurface, gutter)
            .verticalScroll(scroll),
        content = rows,
    )
}

/**
 * The bar beside a list scrolled up and down: a dim track as tall as the list and a solid piece of it
 * where the screen is, in the dots' two colours. Drawn only, so scrolling redraws it without composing
 * anything again, and only when there's more than the screen holds.
 */
private fun Modifier.scrollBar(state: ScrollState, color: Color, gutter: Dp): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    // Not measured yet (Int.MAX_VALUE), or nothing to scroll.
    if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
    val width = BAR_WIDTH.toPx()
    val x = size.width + (gutter.toPx() - width) / 2
    val height = size.height
    val thumb = (height * height / (height + max)).coerceIn(minOf(BAR_MIN.toPx(), height), height)
    val top = (height - thumb) * state.value / max
    val round = CornerRadius(width / 2)
    drawRoundRect(color.copy(alpha = TRACK_ALPHA), Offset(x, 0f), Size(width, height), round)
    drawRoundRect(color, Offset(x, top), Size(width, thumb), round)
}

private val BAR_WIDTH = 4.dp
private val BAR_MIN = 32.dp

/** As the dots of the pages that aren't in sight. */
private const val TRACK_ALPHA = 0.3f
