package io.github.sirionero.mycar.call

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import io.github.sirionero.mycar.MyCarLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A caller as the car shows it: whatever the contacts app knows, or just the number. */
data class Caller(val number: String, val name: String?) {
    val label: String get() = name ?: number.ifBlank { "Unknown number" }
}

sealed interface CallUiState {
    data object None : CallUiState
    data class Ringing(val caller: Caller, val canDecline: Boolean) : CallUiState
    data class Active(val caller: Caller?) : CallUiState
}

/**
 * Tracks the phone's call state for the car screen, and answers or declines through the two
 * doors a sideloaded app can use: [android.telecom.TelecomManager.acceptRingingCall] to answer,
 * and [MyCarCallScreening]'s response to decline one that is still ringing.
 *
 * Ending a call already answered needs MODIFY_PHONE_STATE, a privileged permission MyCar
 * cannot hold, so a call taken here is finished the way it would be without MyCar at all -
 * from the phone itself, a paired headset, or the car's own Bluetooth if it has any. Call audio
 * stays on the phone's normal voice path too; nothing here sends it over the USB link, which
 * only ever carries media playback.
 */
object CarCalls {

    private val _state = MutableStateFlow<CallUiState>(CallUiState.None)
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    /** True while the car screen is up. Away from the car, screening lets calls ring at once. */
    var inCar = false
        private set

    private var telephony: TelephonyManager? = null
    private var watcher: TelephonyCallback? = null
    private var pendingRespond: ((allow: Boolean) -> Unit)? = null
    private var ringingCaller: Caller? = null
    /** The phone's last call state, as [TelephonyCallback] reported it. */
    private var phoneState = TelephonyManager.CALL_STATE_IDLE
    private var numberWatcher: BroadcastReceiver? = null
    /** Answer was tapped while the call was still being screened: pick it up once it rings. */
    private var answerWhenRinging = false
    private var watcherContext: Context? = null

    /** [inCar] is true from the car screen, false from the phone's setup guide. */
    fun start(context: Context, inCar: Boolean = false) {
        if (inCar) this.inCar = true
        if (watcher != null) return
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(TelephonyManager::class.java) ?: return
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) = onPhoneState(state)
        }
        runCatching { manager.registerTelephonyCallback(context.mainExecutor, callback) }
            .onSuccess {
                telephony = manager
                watcher = callback
                watchNumbers(context.applicationContext)
            }
            .onFailure { MyCarLog.w("calls: could not watch call state: ${it.message}") }
    }

    /**
     * The screening service only sees calls from numbers outside the contacts, so a contact's
     * number comes from the phone-state broadcast instead. Android adds the number to it for
     * apps holding READ_CALL_LOG, which setup asks for with the rest of the phone permissions.
     */
    private fun watchNumbers(app: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING) return
                @Suppress("DEPRECATION")
                val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (number.isNullOrBlank() || number == ringingCaller?.number) return
                val caller = Caller(number, lookUpName(context, number))
                ringingCaller = caller
                val shown = _state.value
                if (shown is CallUiState.Ringing) _state.value = shown.copy(caller = caller)
            }
        }
        // Exported because the telephony stack sends it; as a protected broadcast, no app can fake it.
        runCatching { app.registerReceiver(receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), Context.RECEIVER_EXPORTED) }
            .onSuccess { numberWatcher = receiver; watcherContext = app }
            .onFailure { MyCarLog.w("calls: could not watch incoming numbers: ${it.message}") }
    }

    fun stop() {
        inCar = false
        val callback = watcher ?: return
        runCatching { telephony?.unregisterTelephonyCallback(callback) }
        numberWatcher?.let { receiver -> runCatching { watcherContext?.unregisterReceiver(receiver) } }
        numberWatcher = null
        watcherContext = null
        telephony = null
        watcher = null
        pendingRespond = null
        ringingCaller = null
        answerWhenRinging = false
        phoneState = TelephonyManager.CALL_STATE_IDLE
        _state.value = CallUiState.None
    }

    private fun onPhoneState(state: Int) {
        phoneState = state
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                pendingRespond = null
                ringingCaller = null
                answerWhenRinging = false
                _state.value = CallUiState.None
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                pendingRespond = null
                answerWhenRinging = false
                _state.value = CallUiState.Active(ringingCaller)
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // A contact's call skips screening, so its number may not be here yet: show the
                // card now, and the number broadcast fills in who it is.
                val caller = ringingCaller ?: Caller("", null).also { ringingCaller = it }
                _state.value = CallUiState.Ringing(caller, pendingRespond != null)
                if (answerWhenRinging) {
                    answerWhenRinging = false
                    watcherContext?.let(::acceptRingingCall)
                }
            }
        }
    }

    /** Called by [MyCarCallScreening] the moment a call comes in - the only place with a number. */
    fun incomingCall(context: Context, number: String?, respond: (allow: Boolean) -> Unit) {
        val caller = Caller(number.orEmpty(), number?.let { lookUpName(context, it) })
        ringingCaller = caller
        pendingRespond = respond
        _state.value = CallUiState.Ringing(caller, canDecline = true)
    }

    /** The screening window closed by itself - the call rings normally now, and can't be declined from here. */
    fun screeningExpired() {
        pendingRespond = null
        val caller = ringingCaller ?: return
        if (_state.value is CallUiState.Ringing) _state.value = CallUiState.Ringing(caller, canDecline = false)
        expectRinging()
    }

    /**
     * A call let through screening starts ringing within moments. One that hasn't by then was hung
     * up first, and the phone never left idle - so no state change will come to clear its card.
     */
    private fun expectRinging() {
        mainThreadDelay(RING_GRACE_MS) {
            if (phoneState == TelephonyManager.CALL_STATE_IDLE && pendingRespond == null && _state.value is CallUiState.Ringing) {
                MyCarLog.i("calls: the call never rang - it was hung up while being screened")
                ringingCaller = null
                answerWhenRinging = false
                _state.value = CallUiState.None
            }
        }
    }

    fun decline() {
        val respond = pendingRespond ?: return
        pendingRespond = null
        runCatching { respond(false) }.onFailure { MyCarLog.w("calls: decline failed: ${it.message}") }
        // Rejected while screening, the call never rang: the phone stays idle, and nothing else
        // would take the card away.
        ringingCaller = null
        _state.value = CallUiState.None
    }

    fun answer(context: Context) {
        // Still being screened, the call hasn't rung yet and there is nothing to pick up: let it
        // through now, and pick it up the moment it rings.
        val respond = pendingRespond
        if (respond != null) {
            pendingRespond = null
            answerWhenRinging = true
            runCatching { respond(true) }.onFailure { MyCarLog.w("calls: letting the call through failed: ${it.message}") }
            expectRinging()
            return
        }
        acceptRingingCall(context)
    }

    private fun acceptRingingCall(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            MyCarLog.w("calls: answer needs ANSWER_PHONE_CALLS, which was never granted")
            return
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        runCatching { telecom.acceptRingingCall() }
            .onFailure { MyCarLog.w("calls: answer failed: ${it.message}") }
    }

    private fun lookUpName(context: Context, number: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    private const val RING_GRACE_MS = 3_000L
}

/** Posts to the main thread after a delay - used to give up on a screening response MyCar never sent. */
internal fun mainThreadDelay(ms: Long, action: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed(action, ms)
}
