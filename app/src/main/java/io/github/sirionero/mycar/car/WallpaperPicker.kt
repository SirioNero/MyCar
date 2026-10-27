package io.github.sirionero.mycar.car

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.Wallpaper
import io.github.sirionero.mycar.car.design.WallpaperBackground
import io.github.sirionero.mycar.car.design.WallpaperSetting
import io.github.sirionero.mycar.car.design.Wallpapers

/**
 * The car settings' wallpaper rows: a swatch of each still wallpaper, plus none, then of each
 * moving one - moving in its swatch too - in strips that scroll sideways. The chosen one is ringed
 * in the accent colour.
 */
@Composable
internal fun WallpaperPicker() {
    val current by WallpaperSetting.current.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Wallpaper", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        SwatchStrip("Still") {
            Swatch(Wallpapers.NONE, "None", current) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            Wallpapers.all.filter { it.motion == null }.forEach { WallpaperSwatch(it, current) }
        }
        SwatchStrip("Moving") {
            Wallpapers.all.filter { it.motion != null }.forEach { WallpaperSwatch(it, current) }
        }
    }
}

@Composable
private fun SwatchStrip(label: String, swatches: @Composable () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { swatches() }
}

@Composable
private fun WallpaperSwatch(wallpaper: Wallpaper, current: String) {
    Swatch(wallpaper.id, wallpaper.name, current) {
        WallpaperBackground(wallpaper, Modifier.fillMaxSize(), wholeScreen = false)
    }
}

@Composable
private fun Swatch(id: String, name: String, current: String, picture: @Composable () -> Unit) {
    val chosen = id == current
    val shape = RoundedCornerShape(MyCarRadius.Card)
    Column(
        Modifier
            .width(SWATCH_WIDTH)
            .clip(RoundedCornerShape(MyCarRadius.Card))
            .clickable { WallpaperSetting.set(id) },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(SWATCH_WIDTH)
                .height(SWATCH_HEIGHT)
                .clip(shape)
                .border(
                    if (chosen) 3.dp else 1.dp,
                    if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape,
                ),
        ) { picture() }
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val SWATCH_WIDTH = 132.dp
private val SWATCH_HEIGHT = 80.dp
