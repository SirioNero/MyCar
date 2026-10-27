package io.github.sirionero.mycar.car

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.design.rememberGrain

/**
 * Behind the full player, as recent phones draw it: the cover itself, blurred far past telling
 * what it shows and darkened, so the whole screen takes its colours; darker toward the foot where
 * the controls are. A fine grain keeps the video to the car from breaking it into bands. With no
 * cover, the cover's colour would be nothing to go on, so it's the dark ground.
 */
@Composable
internal fun CoverBackdrop(art: Bitmap?, modifier: Modifier = Modifier) {
    val cover = remember(art) { art?.asImageBitmap() }
    val ground = rememberArtColor(art, MyCarPalette.DarkSurface1, darkLightness = 0.16f)
    val grain = rememberGrain()
    Box(modifier.background(ground)) {
        if (cover != null) {
            Image(
                cover,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(BLUR, BlurredEdgeTreatment.Rectangle),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.3f), 1f to Color.Black.copy(alpha = 0.6f)))
                .drawBehind { drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = GRAIN) },
        )
    }
}

private val BLUR = 90.dp
private const val GRAIN = 0.05f
