package io.github.sirionero.mycar.car

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/**
 * Where the car's Settings were scrolled to, for the rest of the drive. The screen is made anew each
 * time it comes back - Back from a screen opened over it (the Jolly key's choice, the dashboard from the
 * dock's clock), opened again from the app grid or the dock, the whole car screen made again (a new
 * encoder, screen sharing ended) - and it came back at the top, the driver's place lost: a tester saw
 * it go back up many times in the car. Forgotten when the drive ends, so the next one starts at the top.
 */
internal object SettingsScroll {

    /** How far down, in the car screen's dp: the same place after a change of its size. Main thread. */
    private var at = 0f

    fun keep(px: Int, density: Float) {
        at = px / density
    }

    fun restore(density: Float): Int = (at * density).roundToInt()

    /** At the end of the drive (CarServices.shutdown). */
    fun forget() {
        at = 0f
    }
}

/** The car Settings' scroll: where the drive left it, and kept as the screen goes. */
@Composable
internal fun rememberSettingsScroll(): ScrollState {
    val density = LocalDensity.current.density
    val scroll = rememberScrollState(SettingsScroll.restore(density))
    // The Size changed while the screen is up: its pixels are the new size's.
    val now by rememberUpdatedState(density)
    DisposableEffect(scroll) {
        onDispose { SettingsScroll.keep(scroll.value, now) }
    }
    return scroll
}
