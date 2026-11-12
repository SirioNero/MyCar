package io.github.sirionero.mycar.car

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlin.reflect.KMutableProperty0

/**
 * What the car's Back key does while a card is up over everything (What's new, the Bluetooth,
 * location and pairing cards): it answers the card, as the scrim or its gentlest button would,
 * instead of changing the screen hidden under it. Each card its own slot, read by CarUi before the
 * screens' Back: the navigator's single back slot is HomePager's and the screens'.
 */
internal object CardBack {
    var whatsNew: (() -> Unit)? = null
    var pair: (() -> Unit)? = null

    /** Cinema's question before what another app sent opens (CinemaAskCard): Back is "Not now". */
    var cinemaAsk: (() -> Unit)? = null
}

/** Holds [slot] while composed, with [action] for the car's Back; lets go when the card goes. */
@Composable
internal fun CardBackKey(slot: KMutableProperty0<(() -> Unit)?>, action: () -> Unit) {
    val latest by rememberUpdatedState(action)
    DisposableEffect(slot) {
        val mine: () -> Unit = { latest() }
        slot.set(mine)
        onDispose { if (slot.get() === mine) slot.set(null) }
    }
}
