package io.github.sirionero.mycar.car

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.theme.GsIconBox
import io.github.sirionero.mycar.car.theme.GsIconButton

/** The layouts a driver's page can take, in the order they're offered. */
internal val DriverLayouts = listOf(
    PageLayout.FULL,
    PageLayout.HALVES,
    PageLayout.GRID,
    PageLayout.HALF_QUARTERS,
    PageLayout.QUARTERS_HALF,
    PageLayout.STRIP_QUARTERS,
    PageLayout.QUARTERS_STRIP,
)

/** What a layout is called where the driver picks one: short, as the picture beside it says the rest. */
@get:StringRes
internal val PageLayout.label: Int
    get() = when (this) {
        PageLayout.FULL -> R.string.car_pages_layout_full
        PageLayout.HALVES -> R.string.car_pages_layout_halves
        PageLayout.GRID -> R.string.car_pages_layout_grid
        PageLayout.HALF_QUARTERS -> R.string.car_pages_layout_half_left
        PageLayout.QUARTERS_HALF -> R.string.car_pages_layout_half_right
        PageLayout.STRIP_QUARTERS -> R.string.car_pages_layout_strip_top
        PageLayout.QUARTERS_STRIP -> R.string.car_pages_layout_strip_below
        PageLayout.MAP -> R.string.car_pages_layout_map
    }

/**
 * A small picture of [layout]: its parts as rounded blocks in [color], laid out as the page lays
 * them out, so the picture can't disagree with the page.
 */
@Composable
internal fun LayoutDiagram(layout: PageLayout, color: Color, modifier: Modifier, gap: Dp = 2.dp) {
    val block: @Composable (Modifier) -> Unit = { m -> Box(m.clip(RoundedCornerShape(gap + 1.dp)).background(color)) }
    val across = Arrangement.spacedBy(gap)
    when (layout) {
        PageLayout.FULL, PageLayout.MAP -> block(modifier)
        PageLayout.HALVES -> Row(modifier, horizontalArrangement = across) {
            block(Modifier.weight(1f).fillMaxHeight())
            block(Modifier.weight(1f).fillMaxHeight())
        }
        PageLayout.GRID -> Column(modifier, verticalArrangement = across) {
            repeat(2) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = across) {
                    block(Modifier.weight(1f).fillMaxHeight())
                    block(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        PageLayout.HALF_QUARTERS -> Row(modifier, horizontalArrangement = across) {
            block(Modifier.weight(1f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = across) {
                block(Modifier.weight(1f).fillMaxWidth())
                block(Modifier.weight(1f).fillMaxWidth())
            }
        }
        PageLayout.QUARTERS_HALF -> Row(modifier, horizontalArrangement = across) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = across) {
                block(Modifier.weight(1f).fillMaxWidth())
                block(Modifier.weight(1f).fillMaxWidth())
            }
            block(Modifier.weight(1f).fillMaxHeight())
        }
        PageLayout.STRIP_QUARTERS -> Column(modifier, verticalArrangement = across) {
            block(Modifier.weight(1f).fillMaxWidth())
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = across) {
                block(Modifier.weight(1f).fillMaxHeight())
                block(Modifier.weight(1f).fillMaxHeight())
            }
        }
        PageLayout.QUARTERS_STRIP -> Column(modifier, verticalArrangement = across) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = across) {
                block(Modifier.weight(1f).fillMaxHeight())
                block(Modifier.weight(1f).fillMaxHeight())
            }
            block(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** In the bar of a page being changed: its layout's picture, and tapped, the list of layouts. */
@Composable
internal fun LayoutButton(layout: PageLayout, onClick: () -> Unit) {
    val description = stringResource(R.string.car_pages_layout_now, stringResource(layout.label))
    GsIconBox(
        onClick = onClick,
        modifier = Modifier.height(MyCarSize.TouchTarget).semantics { contentDescription = description },
    ) {
        Row(Modifier.padding(horizontal = MyCarSpace.M), verticalAlignment = Alignment.CenterVertically) {
            LayoutDiagram(layout, LocalContentColor.current, Modifier.size(width = 40.dp, height = 26.dp))
            Spacer(Modifier.width(MyCarSpace.S))
            Text(stringResource(R.string.car_pages_layout), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * How a page can be cut up, as big tiles with a picture of each. What the page shows stays, in
 * order, as far as the new layout has room ([withLayout]).
 */
@Composable
internal fun LayoutChooser(current: PageLayout, onChoose: (PageLayout) -> Unit, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(MyCarSpace.L)
                .clip(RoundedCornerShape(MyCarRadius.Sheet))
                .background(scheme.surfaceContainerHigh)
                // Taps on the sheet itself stay on it.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(MyCarSpace.L),
            verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.car_pages_layout),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                GsIconButton(Icons.Filled.Close, stringResource(R.string.common_close), onClose, size = MyCarSize.TouchTarget)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(ChoiceWidth),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
            ) {
                items(DriverLayouts) { layout ->
                    Choice(stringResource(layout.label), layout == current, onClick = { onChoose(layout) }) {
                        LayoutDiagram(layout, LocalContentColor.current, Modifier.size(width = 72.dp, height = 44.dp), gap = 3.dp)
                    }
                }
            }
            Text(
                stringResource(R.string.car_pages_layout_keeps),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
