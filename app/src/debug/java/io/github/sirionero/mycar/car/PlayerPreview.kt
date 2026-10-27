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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.design.MyCarCarTheme
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.media.CarMedia
import io.github.sirionero.mycar.media.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Debug only, for CarPreviewActivity's player mode: the player docked beside a stand-in map, with
 * a made-up song and a cover drawn here, since the emulator has no music playing to show.
 */
@Composable
internal fun PlayerPreview() {
    // CarUi starts the car's services before anything reads them; without it, so must this.
    val context = LocalContext.current.applicationContext
    remember(context) { CarServices.init(context) }
    val now = remember { demoSong() }
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

/** The made-up song the previews play: the emulator has no music of its own. */
internal fun demoSong() = NowPlaying(
    title = "Midnight Drive",
    artist = "myCar Preview",
    art = previewCover(600),
    state = PlaybackState.STATE_PLAYING,
    positionMs = 72_000,
    durationMs = 214_000,
    updatedAt = SystemClock.elapsedRealtime(),
    actions = PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
)

/**
 * Debug only, for CarPreviewActivity's demoSong mode: the car's media looks as if [demoSong] were
 * playing, so the real Home shows the player beside the real map and the dock shows the song.
 * A music app on the phone (Xonos, when there) stands as the one playing it, so the full player
 * opens on it too, with the made-up song. CarMedia keeps its state private for good reason; a
 * preview reaching in by reflection is the price of leaving the app's own code alone. False until
 * CarUi has started the car's services.
 */
internal fun showDemoSong(context: android.content.Context): Boolean {
    val media = CarServices.mediaOrNull() ?: return false
    @Suppress("UNCHECKED_CAST")
    fun <T> state(name: String): MutableStateFlow<T> {
        val field = CarMedia::class.java.getDeclaredField(name).apply { isAccessible = true }
        return field.get(media) as MutableStateFlow<T>
    }
    state<NowPlaying>("_now").value = demoSong()
    state<CarMedia.Phase>("_phase").value = CarMedia.Phase.READY
    val apps = io.github.sirionero.mycar.media.MediaCatalog.installed(context)
    apps.firstOrNull { it.component.packageName == "it.xonos.ma" }?.let { app ->
        @Suppress("UNCHECKED_CAST")
        val playing = CarServices::class.java.getDeclaredField("_mediaApp").apply { isAccessible = true }
            .get(null) as MutableStateFlow<io.github.sirionero.mycar.media.MediaApp?>
        playing.value = app
    }
    return true
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
