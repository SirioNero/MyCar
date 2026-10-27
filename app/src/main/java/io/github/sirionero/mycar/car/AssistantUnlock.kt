package io.github.sirionero.mycar.car

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.car.app.CarToast
import io.github.sirionero.mycar.MyCarLog

/**
 * Opens the phone for the assistant the driver asked for in the car. On a locked phone an
 * assistant won't listen - Claude's, like most, wants the phone open - and the lock screen is the
 * one thing Android lets no app get past. So with the phone locked or asleep, the microphone
 * lights the phone's screen and asks Android to open it: at once where nothing needs typing (no
 * lock, or the phone kept open by a trusted device such as the car's Bluetooth), otherwise behind
 * the face, fingerprint or PIN the phone asks for. Once it's open, the assistant starts listening.
 *
 * Android lets an app in the background open a screen only in a few cases, one of them an
 * accessibility service it has bound, which is how myCar can do this: with myCar off under
 * Accessibility the screen may never open, and the car says to unlock the phone first.
 */
internal object AssistantUnlock {
    private val main = Handler(Looper.getMainLooper())
    private val watching = Any()

    /** When [AssistantUnlockActivity] last had the phone opened, for the start it then asks for. */
    @Volatile private var openedAt = 0L

    /** The phone is locked or asleep: the assistant would start where nobody can talk to it. */
    fun needed(context: Context): Boolean {
        // Just opened for this very touch: the lock may take a moment longer to say it's gone.
        if (openedAt != 0L && SystemClock.uptimeMillis() - openedAt < JUST_OPENED_MS) {
            openedAt = 0L
            return false
        }
        val locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val asleep = context.getSystemService(PowerManager::class.java)?.isInteractive == false
        return locked || asleep
    }

    /** Opens the phone, then starts the chosen assistant through [CarAssistant] once more. */
    fun start(context: Context, name: String) {
        // Whether opening takes the driver's face, finger or PIN, rather than nothing.
        if (context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true) {
            CarToasts.show(this, "Unlock the phone: $name starts as soon as it's open", CarToast.LENGTH_LONG)
        }
        main.removeCallbacksAndMessages(watching)
        runCatching {
            context.startActivity(
                Intent(context, AssistantUnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }.onFailure { MyCarLog.w("assistant: couldn't open the phone: ${it.message}") }
        // Android refuses a screen from the background without a word: if none came, say so.
        main.postDelayed({
            MyCarLog.w("assistant: Android didn't let myCar light the phone - is myCar on under Accessibility?")
            CarToasts.show(this, "Unlock the phone, then touch the microphone again", CarToast.LENGTH_LONG)
        }, watching, SHOWN_WITHIN_MS)
    }

    internal fun shown() = main.removeCallbacksAndMessages(watching)

    /** The phone is open: the assistant starts once the lock screen has finished going. */
    internal fun opened() {
        main.postDelayed({
            openedAt = SystemClock.uptimeMillis()
            CarAssistant.start()
        }, LOCK_GONE_MS)
    }

    internal fun stayedLocked(why: String) {
        MyCarLog.i("assistant: the phone stayed locked ($why)")
        CarToasts.show(this, "The phone stayed locked", CarToast.LENGTH_SHORT)
    }

    private const val SHOWN_WITHIN_MS = 2_500L
    private const val LOCK_GONE_MS = 300L
    private const val JUST_OPENED_MS = 5_000L
}

/**
 * See-through, over the lock screen: lights the screen, has Android open the phone, and hands
 * back to [AssistantUnlock], gone as soon as that's done.
 */
class AssistantUnlockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AssistantUnlock.shown()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            finishWith { AssistantUnlock.opened() }
            return
        }
        MyCarLog.i("assistant: opening the phone first")
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = finishWith { AssistantUnlock.opened() }
            override fun onDismissCancelled() = finishWith { AssistantUnlock.stayedLocked("the driver backed out") }
            override fun onDismissError() = finishWith { AssistantUnlock.stayedLocked("Android couldn't ask") }
        })
        // Nobody opened it: don't stay over the lock screen for good.
        handler.postDelayed({ finishWith { AssistantUnlock.stayedLocked("nobody opened it") } }, GIVE_UP_MS)
    }

    private fun finishWith(then: () -> Unit) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        finish()
        then()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val GIVE_UP_MS = 60_000L
    }
}
