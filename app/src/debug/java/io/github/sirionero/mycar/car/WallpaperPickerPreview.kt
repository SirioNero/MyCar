package io.github.sirionero.mycar.car

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.design.MyCarCarTheme

/**
 * Debug only, for CarPreviewActivity's wallpapers mode: the car settings' wallpaper picker on its
 * own, as the settings screen scrolls it out of a screenshot.
 */
@Composable
internal fun WallpaperPickerPreview() {
    MyCarCarTheme(dark = true) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
            WallpaperPicker()
        }
    }
}
