package io.github.sirionero.mycar.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.sirionero.mycar.BuildConfig
import kotlinx.coroutines.delay

/**
 * After every update, the first time myCar opens: the flag run up its pole and flying, and the
 * version it came with. A tap anywhere, Back, or a few seconds go on. A release big enough for an
 * intro gets the ship there instead (IntroScreen), not this as well.
 */
object UpdateFlag {
    private const val PREFS = "update_flag"
    private const val KEY = "version_code"

    /**
     * True once after each update. A new install only notes its version, and so does a big
     * release whose intro is showing ([introDue]). Before 0.5.21 nothing was noted: then it's an
     * update if myCar had been used.
     */
    fun due(context: Context, introDue: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getInt(KEY, -1)
        val now = BuildConfig.VERSION_CODE
        if (seen == now) return false
        prefs.edit().putInt(KEY, now).apply()
        if (introDue) return false
        if (seen == -1) return Intro.usedBefore(context)
        return seen < now
    }
}

@Composable
internal fun UpdateSplash(onDone: () -> Unit) {
    val c = introColors()
    var leaving by remember { mutableStateOf(false) }
    BackHandler { leaving = true }
    LaunchedEffect(Unit) {
        delay(STAY_MS)
        leaving = true
    }
    val number = BuildConfig.VERSION_NAME.substringBefore('-')
    val tag = BuildConfig.VERSION_NAME.substringAfter('-', "")
    UpdateSplashContent(
        number, tag, leaving, onDone,
        surface = c.surface, ink = c.onSurface, quiet = c.onVariant, accent = c.primary, glow = c.primaryC,
        chip = c.tertiaryC, onChip = c.onTertiaryC,
    ) { leaving = true }
}

private const val STAY_MS = 7_000L
