package io.github.sirionero.mycar

import androidx.annotation.StringRes
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

    /**
     * [headline] and [detail] are read in myCar's language each time, so a state already up (Not
     * connected, from the start) follows a change of language.
     */
    class Snapshot(val phase: Phase, private val title: () -> String, private val text: () -> String) {
        val headline: String get() = title()
        val detail: String get() = text()
    }

    private fun words(@StringRes id: Int, vararg args: Any): () -> String = { Words.get(id, *args) }

    private val flow = MutableStateFlow(
        Snapshot(Phase.IDLE, words(R.string.session_idle), words(R.string.session_idle_detail)),
    )

    val state: StateFlow<Snapshot> = flow

    /** The headline when the car turns the phone's certificate down. */
    @StringRes val CERT_TURNED_DOWN = R.string.session_cert_turned_down

    fun connecting(@StringRes stage: Int, vararg args: Any) {
        flow.value = Snapshot(Phase.CONNECTING, words(R.string.session_connecting), words(stage, *args))
    }

    fun projecting() {
        flow.value = Snapshot(Phase.PROJECTING, words(R.string.session_connected), words(R.string.session_connected_detail))
    }

    /** Words already in hand; [detail] may be one that isn't translated, like an error's own. */
    fun failed(headline: String, detail: String) {
        flow.value = Snapshot(Phase.FAILED, { headline }, { detail })
    }

    /** [args] go into [detail]. */
    fun failed(@StringRes headline: Int, @StringRes detail: Int, vararg args: Any) {
        flow.value = Snapshot(Phase.FAILED, words(headline), words(detail, *args))
    }

    /** Keeps an earlier failure visible: a rejection is followed by a disconnect every time. */
    fun disconnected() {
        if (flow.value.phase == Phase.FAILED) return
        flow.value = Snapshot(Phase.DISCONNECTED, words(R.string.session_disconnected), words(R.string.session_disconnected_detail))
    }
}
