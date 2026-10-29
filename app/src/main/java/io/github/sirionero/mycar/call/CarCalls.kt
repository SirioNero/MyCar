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
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A caller as the car shows it: whatever the contacts app knows, or just the number. */
data class Caller(val number: String, val name: String?) {
    val label: String get() = name ?: number.ifBlank { Words.get(R.string.call_unknown_number) }
}

sealed interface CallUiState {
    data object None : CallUiState
    data class Ringing(val caller: Caller, val canDecline: Boolean) : CallUiState
    data class Active(val caller: Caller?) : CallUiState
}

/**
 * Tracks the phone's call state for the car screen, and answers, declines and ends calls through
 * the doors a sideloaded app can use: [MyCarCallScreening]'s response for a call still being
 * screened, and [TelecomManager.acceptRingingCall] and [TelecomManager.endCall] - which need only
 * ANSWER_PHONE_CALLS - for one that rings or is under way.
 *
 * Call audio stays on the phone's normal voice path; nothing here sends it over the USB link,
 * which only ever carries media playback. It reaches the car's speakers over Bluetooth.
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

    /** The ringing call came while another was under way: a call waiting. */
    private var ringingDuringCall = false

    /** Who the call under way is with, as far as myCar knows. */
    private var activeCaller: Caller? = null

    /** The ringing call was answered from the car. */
    private var answeredHere = false

    /** When the phone last went idle, to tell a late number broadcast from a new call's. */
    private var idleAt = 0L
    /** The application context, kept while call state is being watched. */
    private var appContext: Context? = null

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
                appContext = context.applicationContext
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
                // Late, for a call already over (a missed call of a ring or two): kept, its name
                // went on the next call made or taken.
                if (phoneState == TelephonyManager.CALL_STATE_IDLE &&
                    android.os.SystemClock.elapsedRealtime() - idleAt < LATE_NUMBER_MS
                ) return
                val caller = Caller(number, lookUpName(context, number))
                ringingCaller = caller
                val shown = _state.value
                if (shown is CallUiState.Ringing) _state.value = shown.copy(caller = caller)
            }
        }
        // Exported because the telephony stack sends it; as a protected broadcast, no app can fake it.
        runCatching { app.registerReceiver(receiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), Context.RECEIVER_EXPORTED) }
            .onSuccess { numberWatcher = receiver }
            .onFailure { MyCarLog.w("calls: could not watch incoming numbers: ${it.message}") }
    }

    fun stop() {
        inCar = false
        val callback = watcher ?: return
        runCatching { telephony?.unregisterTelephonyCallback(callback) }
        numberWatcher?.let { receiver -> runCatching { appContext?.unregisterReceiver(receiver) } }
        numberWatcher = null
        appContext = null
        telephony = null
        watcher = null
        pendingRespond = null
        ringingCaller = null
        answerWhenRinging = false
        activeCaller = null
        answeredHere = false
        ringingDuringCall = false
        phoneState = TelephonyManager.CALL_STATE_IDLE
        _state.value = CallUiState.None
    }

    private fun onPhoneState(state: Int) {
        val previous = phoneState
        phoneState = state
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                pendingRespond = null
                ringingCaller = null
                answerWhenRinging = false
                answeredHere = false
                activeCaller = null
                idleAt = android.os.SystemClock.elapsedRealtime()
                _state.value = CallUiState.None
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                pendingRespond = null
                answerWhenRinging = false
                // Back on a call after one waited: answered or turned away, the phone's state reads
                // the same. Only an answer from the car says it's the new caller; otherwise no name,
                // rather than the one turned away while the first call goes on.
                val waited = previous == TelephonyManager.CALL_STATE_RINGING && ringingDuringCall
                activeCaller = if (waited && !answeredHere) null else ringingCaller
                _state.value = CallUiState.Active(activeCaller)
                answeredHere = false
                // Taken: a call waiting later starts from no name, not this caller's, until its
                // own number comes.
                ringingCaller = null
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // Ringing, the call is past screening, whatever myCar answered or didn't: Telecom
                // stops waiting after five seconds (a main thread held up by a page loading took
                // that long). A Decline then went to a screening nobody heard any more: the card
                // went away, and the phone rang on with no way to answer from the car.
                pendingRespond = null
                // A contact's call skips screening, so its number may not be here yet: show the
                // card now, and the number broadcast fills in who it is.
                val caller = ringingCaller ?: Caller("", null).also { ringingCaller = it }
                // Telecom rejects a ringing call on request - unless another call is under way,
                // which it would end instead.
                ringingDuringCall = previous == TelephonyManager.CALL_STATE_OFFHOOK
                val canEnd = previous != TelephonyManager.CALL_STATE_OFFHOOK && appContext?.let(::canEndCalls) == true
                _state.value = CallUiState.Ringing(caller, pendingRespond != null || canEnd)
                if (answerWhenRinging) {
                    answerWhenRinging = false
                    appContext?.let(::acceptRingingCall)
                }
            }
        }
    }

    /** Called by [MyCarCallScreening] when a call from outside the contacts comes in, before it rings. */
    fun incomingCall(context: Context, number: String?, respond: (allow: Boolean) -> Unit) {
        val caller = Caller(number.orEmpty(), number?.let { lookUpName(context, it) })
        ringingCaller = caller
        pendingRespond = respond
        _state.value = CallUiState.Ringing(caller, canDecline = true)
    }

    /** The screening window of the call [respond] answers closed by itself: it rings normally now. */
    fun screeningExpired(respond: (allow: Boolean) -> Unit) {
        // A second call came in while this one was screened: the card is the newer call's now,
        // and its Decline must keep working.
        if (pendingRespond !== respond) return
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
            if (phoneState != TelephonyManager.CALL_STATE_RINGING && pendingRespond == null && _state.value is CallUiState.Ringing) {
                MyCarLog.i("calls: the call never rang - it was hung up while being screened")
                ringingCaller = null
                answerWhenRinging = false
                _state.value = afterScreened()
            }
        }
    }

    fun decline() {
        val respond = pendingRespond
        if (respond == null) {
            // Past screening, the call rings like any other, and Telecom rejects it.
            val ringing = _state.value as? CallUiState.Ringing ?: return
            if (ringing.canDecline) appContext?.let(::endCall)
            return
        }
        pendingRespond = null
        runCatching { respond(false) }.onFailure { MyCarLog.w("calls: decline failed: ${it.message}") }
        // Rejected while screening, the call never rang: the phone stays idle, and nothing else
        // would take the card away.
        ringingCaller = null
        _state.value = afterScreened()
    }

    /**
     * What the card shows once a screened call is gone without ringing: the call under way, if one
     * is (a second call waiting on it) - not nothing, which also took the car's hang-up away.
     */
    private fun afterScreened(): CallUiState =
        if (phoneState == TelephonyManager.CALL_STATE_OFFHOOK) CallUiState.Active(activeCaller) else CallUiState.None

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
            .onSuccess { answeredHere = true }
            .onFailure { MyCarLog.w("calls: answer failed: ${it.message}") }
    }

    /** Ends the call under way, as the phone's own red button would. */
    fun hangUp(context: Context) {
        if (_state.value is CallUiState.Active) endCall(context)
    }

    fun canEndCalls(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    /** Rejects the ringing call, or ends the one under way. Emergency calls are left alone by Telecom. */
    @Suppress("DEPRECATION")
    private fun endCall(context: Context) {
        if (!canEndCalls(context)) {
            MyCarLog.w("calls: ending a call needs ANSWER_PHONE_CALLS, which was never granted")
            return
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return
        runCatching { telecom.endCall() }
            .onSuccess { ended -> if (!ended) MyCarLog.w("calls: there was no call to end") }
            .onFailure { MyCarLog.w("calls: ending the call failed: ${it.message}") }
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
    /** A number broadcast this soon after the phone went idle belongs to the call just over. */
    private const val LATE_NUMBER_MS = 2_000L
}

/** Posts to the main thread after a delay - used to give up on a screening response myCar never sent. */
internal fun mainThreadDelay(ms: Long, action: () -> Unit) {
    Handler(Looper.getMainLooper()).postDelayed(action, ms)
}
