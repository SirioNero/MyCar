package io.github.sirionero.mycar.call

import android.telecom.Call
import android.telecom.CallScreeningService
import io.github.sirionero.mycar.MyCarLog

/**
 * Sees every incoming call before it rings, so the car screen can show who is calling and
 * decline it in the same window a spam-blocker would use. Inert until the driver sets myCar
 * as the phone's caller ID and spam app - the same on-device consent Truecaller-style apps ask
 * for, not a privileged permission.
 *
 * The system gives an app a few seconds to answer before it lets the call ring on its own, so a
 * response is sent either when the driver taps a button or when that window is about to close,
 * whichever comes first. Answering a call that has already passed this point still works, since
 * [CarCalls.answer] goes through the ordinary ringing-call API rather than through screening.
 */
class MyCarCallScreening : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Calls the driver places to a number outside the contacts come through here too. They
        // need no answer, and showing them would cover the car screen with "Incoming call".
        if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) return
        // Away from the car there is no screen to show the call on: let it ring at once, instead of
        // holding every unknown caller for the whole screening window.
        if (!CarCalls.inCar) {
            runCatching { respondToCall(callDetails, CallResponse.Builder().build()) }
                .onFailure { MyCarLog.w("calls: screening response failed: ${it.message}") }
            return
        }
        val number = callDetails.handle?.schemeSpecificPart
        var responded = false

        val respond: (Boolean) -> Unit = { allow ->
            if (!responded) {
                responded = true
                runCatching {
                    respondToCall(
                        callDetails,
                        CallResponse.Builder()
                            .setDisallowCall(!allow)
                            .setRejectCall(!allow)
                            .setSkipNotification(false)
                            .build(),
                    )
                }.onFailure { MyCarLog.w("calls: screening response failed: ${it.message}") }
            }
        }

        CarCalls.incomingCall(applicationContext, number, respond)

        // Answer for the system if nobody has by the time its own patience runs out, so a call
        // myCar is slow to show never gets silently blocked.
        mainThreadDelay(SCREENING_WINDOW_MS) {
            // Already answered or declined from the car: that call is settled, and the card may
            // belong to a newer one by now.
            if (responded) return@mainThreadDelay
            respond(true)
            CarCalls.screeningExpired(respond)
        }
    }

    private companion object {
        const val SCREENING_WINDOW_MS = 3_500L
    }
}
