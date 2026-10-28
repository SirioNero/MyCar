package io.github.sirionero.mycar.car

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * While something of a screen's own is [open] over it - a search, a "Call …?" question - the
 * car's Back key closes that ([close]) instead of the whole screen under it.
 */
@Composable
internal fun BackCloses(open: Boolean, close: () -> Unit) {
    val navigator = LocalCarNavigator.current
    val latest by rememberUpdatedState(close)
    DisposableEffect(open) {
        if (!open) return@DisposableEffect onDispose { }
        val back: () -> Boolean = {
            latest()
            true
        }
        navigator.backInterceptor = back
        onDispose { if (navigator.backInterceptor === back) navigator.backInterceptor = null }
    }
}
