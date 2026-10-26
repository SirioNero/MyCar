package io.github.sirionero.mycar.car.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import io.github.sirionero.mycar.R

/**
 * Renders an installed app's icon to a [px]-square bitmap that its art fills edge to edge, ready
 * for an [AppIconShape] frame. An adaptive icon is drawn from its layers without the phone's own
 * mask - otherwise a phone with round icons would leave a circle floating inside the squircle. A
 * legacy icon keeps its own shape, centred on a plain tile.
 */
fun squareAppIcon(drawable: Drawable, px: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (drawable is AdaptiveIconDrawable) {
        // Each layer is 108dp with the visible 72dp in the middle: half as big again as the
        // square, centred, puts exactly the visible part on the bitmap.
        val pad = px / 4
        listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
            layer.setBounds(-pad, -pad, px + pad, px + pad)
            layer.draw(canvas)
        }
    } else {
        canvas.drawColor(LegacyIconTile.toArgb())
        val inset = px / 8
        drawable.setBounds(inset, inset, px - inset, px - inset)
        drawable.draw(canvas)
    }
    return bitmap
}

/** Behind a legacy icon, which brings no background of its own. */
private val LegacyIconTile = Color(0xFF2C2C2F)

/** How one of myCar's own apps looks on the launcher: a white glyph on its own colour. */
class BuiltInIconLook(val color: Color, @DrawableRes val glyph: Int)

/** The look for a built-in app, by the id it has in [io.github.sirionero.mycar.car.BuiltInApps]. */
fun builtInIconLook(id: String): BuiltInIconLook = when (id) {
    "dialer" -> BuiltInIconLook(MyCarPalette.Positive, R.drawable.sym_call)
    "web" -> BuiltInIconLook(MyCarPalette.Accent, R.drawable.sym_language)
    "phone" -> BuiltInIconLook(MyCarPalette.Indigo, R.drawable.sym_screen_share)
    "vehicle" -> BuiltInIconLook(MyCarPalette.Warning, R.drawable.sym_speed)
    "settings" -> BuiltInIconLook(MyCarPalette.Graphite, R.drawable.sym_settings)
    else -> BuiltInIconLook(MyCarPalette.DarkSurface4, R.drawable.sym_directions_car)
}

/**
 * An app's icon in the squircle frame, in glass (see [glassFinish]), [size] square: an installed
 * app's own art when there is one, otherwise the built-in app [builtInId]'s glyph tile, otherwise
 * a blank tile.
 */
@Composable
fun AppIcon(icon: ImageBitmap?, builtInId: String?, size: Dp, modifier: Modifier = Modifier) {
    when {
        // The app's own art keeps its look, under a lighter coat of glass than myCar's tiles.
        icon != null -> Image(
            icon,
            contentDescription = null,
            modifier = modifier.size(size).clip(AppIconShape).glassFinish(strength = 0.75f),
            contentScale = ContentScale.Crop,
        )
        builtInId != null -> {
            val look = builtInIconLook(builtInId)
            GlassTile(look.color, look.glyph, size, modifier)
        }
        else -> Box(modifier.size(size).clip(AppIconShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).glassFinish())
    }
}
