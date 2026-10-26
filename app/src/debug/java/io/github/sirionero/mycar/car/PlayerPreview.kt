package io.github.sirionero.mycar.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.session.PlaybackState
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.design.MyCarCarTheme
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.media.NowPlaying

/**
 * Debug only, for CarPreviewActivity's player mode: the player docked beside a stand-in map, with
 * a made-up song and a cover drawn here, since the emulator has no music playing to show.
 */
@Composable
internal fun PlayerPreview() {
    val now = remember {
        NowPlaying(
            title = "Midnight Drive",
            artist = "myCar Preview",
            art = previewCover(600),
            state = PlaybackState.STATE_PLAYING,
            positionMs = 72_000,
            durationMs = 214_000,
            updatedAt = SystemClock.elapsedRealtime(),
            actions = PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
        )
    }
    val navigator = remember { CarNavigator("") }
    MyCarCarTheme(dark = true) {
        CompositionLocalProvider(LocalCarNavigator provides navigator) {
            Row(Modifier.fillMaxSize().background(Color.Black)) {
                Spacer(Modifier.width(MyCarSize.DockWidth).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow))
                Box(Modifier.weight(2.1f).fillMaxHeight().background(Color(0xFFDDE3E8)), contentAlignment = Alignment.Center) {
                    Text("Map", color = Color(0xFF7A8794))
                }
                Spacer(Modifier.width(8.dp))
                GlassDockedPlayer(now, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** A cover with some life in it: a sunset of colour and a bright disc, so the glass has something to blur. */
private fun previewCover(px: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val p = px.toFloat()
    canvas.drawRect(0f, 0f, p, p, Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, p, intArrayOf(0xFF2A1B5E.toInt(), 0xFFE0457B.toInt(), 0xFFFFA24C.toInt()), null, Shader.TileMode.CLAMP)
    })
    canvas.drawCircle(p * 0.5f, p * 0.62f, p * 0.22f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(p * 0.5f, p * 0.62f, p * 0.22f, 0xFFFFF1B8.toInt(), 0xFFFFC266.toInt(), Shader.TileMode.CLAMP)
    })
    canvas.drawRect(0f, p * 0.72f, p, p, Paint().apply { color = 0xFF1B1035.toInt() })
    return bitmap
}
