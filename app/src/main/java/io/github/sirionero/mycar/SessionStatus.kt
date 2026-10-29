package io.github.sirionero.mycar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the phone UI shows about the current car session.
 *
 * [MyCarRunner] reports progress here as it moves through the handshake, so the UI reads
 * a plain state instead of parsing log lines. A failure stays on screen until the next
 * attach starts, so the reason is still there when the user picks the phone back up.
 */
object SessionStatus {

    enum class Phase { IDLE, CONNECTING, PROJECTING, FAILED, DISCONNECTED }

    data class Snapshot(val phase: Phase, val headline: String, val detail: String)

    private val flow = MutableStateFlow(
        Snapshot(
            Phase.IDLE,
            "Not connected",
            "Plug into the car's USB port, or connect wirelessly. myCar opens by itself when the car answers.",
        ),
    )

    val state: StateFlow<Snapshot> = flow

    /** The headline when the car turns the phone's certificate down. */
    const val CERT_TURNED_DOWN = "The car turned down the certificate"

    fun connecting(stage: String) {
        flow.value = Snapshot(Phase.CONNECTING, "Connecting", stage)
    }

    fun projecting() {
        flow.value = Snapshot(Phase.PROJECTING, "Connected", "myCar is on the car's screen.")
    }

    fun failed(headline: String, detail: String) {
        flow.value = Snapshot(Phase.FAILED, headline, detail)
    }

    /** Keeps an earlier failure visible: a rejection is followed by a disconnect every time. */
    fun disconnected() {
        if (flow.value.phase == Phase.FAILED) return
        flow.value = Snapshot(Phase.DISCONNECTED, "Disconnected", "The car is no longer connected.")
    }
}
