package io.github.sirionero.mycar.car

import android.os.SystemClock

/**
 * When the driver last touched the car's screen or turned its knob: what a page in the car may
 * answer to (see [WebSelect]) - a page can't ask by itself for a list over the car's screen.
 */
object CarInput {

    @Volatile private var lastAt = 0L

    fun touched() {
        lastAt = SystemClock.uptimeMillis()
    }

    fun touchedWithin(ms: Long): Boolean = SystemClock.uptimeMillis() - lastAt < ms
}
