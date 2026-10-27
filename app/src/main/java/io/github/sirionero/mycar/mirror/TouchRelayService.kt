package io.github.sirionero.mycar.mirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.PointF
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.audio.CaptureConsent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Replays head-unit touches onto the phone while [PhoneMirror] is running, and answers Android's
 * question to share the phone's sound when myCar has asked it ([CaptureConsent]).
 *
 * dispatchGesture is the only public API that lets one app inject input into another, which
 * is why mirroring needs an accessibility service at all. myCar subscribes to no event
 * types, and looks at a window only while its own audio question is waiting, to press Start in
 * it - nothing else about the phone's screen is read here.
 *
 * A head unit sends a stream of DOWN/MOVE/UP, but a gesture is dispatched as a whole path, so
 * the two are bridged with continued strokes: each dispatch carries the movement since the
 * last one, and the next is only queued once the previous completes. Moves that arrive while
 * a segment is in flight collapse into the most recent point, which keeps the replayed path
 * tracking the finger instead of falling behind it.
 */
class TouchRelayService : AccessibilityService() {

    private var stroke: GestureDescription.StrokeDescription? = null
    private var last: PointF? = null

    /** A segment is in flight; further movement waits so strokes stay properly ordered. */
    private var dispatching = false

    /** Most recent unsent position, and whether the finger has already lifted. */
    private var pending: PointF? = null
    private var lifted = false

    override fun onServiceConnected() {
        instance = this
        on.value = true
        MyCarLog.i("touch relay connected")
        // Switched on while the phone was already showing in the car.
        if (PhoneMirror.active.value) holdLandscape(true)
        // Switched on while Android's audio question was up: answer it now.
        CaptureConsent.serviceReady(this)
    }

    override fun onDestroy() {
        holdLandscape(false)
        super.onDestroy()
        instance = null
        on.value = false
        reset()
    }

    /** One transparent pixel that asks Android to keep the phone on its side: see [holdLandscape]. */
    private var sideways: View? = null

    /**
     * Turns the phone landscape while [PhoneMirror] shows it in the car, whose screen is wide, so
     * apps fill it instead of standing upright between two bars. It's a one-pixel window of
     * nothing that asks for landscape, which Android puts ahead of the app on screen; removing
     * it lets the phone turn back. Fixed landscape, not the sensor's: turning the phone in its
     * holder mustn't flip the car's picture. Apps that only work upright stay upright, boxed in
     * the middle as before.
     */
    fun holdLandscape(on: Boolean) {
        val windows = getSystemService(WindowManager::class.java) ?: return
        if (!on) {
            val view = sideways ?: return
            sideways = null
            runCatching { windows.removeView(view) }
            MyCarLog.i("mirror: phone free to turn again")
            return
        }
        if (sideways != null) return
        val view = View(this)
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        runCatching { windows.addView(view, params) }
            .onSuccess {
                sideways = view
                MyCarLog.i("mirror: phone held landscape")
            }
            .onFailure { MyCarLog.w("mirror: couldn't turn the phone landscape: ${it.message}") }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = reset()

    fun down(x: Float, y: Float) {
        reset()
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val start = GestureDescription.StrokeDescription(path, 0, SEGMENT_MS, true)
        stroke = start
        last = PointF(x, y)
        send(start)
    }

    fun move(x: Float, y: Float) {
        if (stroke == null) return
        pending = PointF(x, y)
        pump()
    }

    fun up(x: Float, y: Float) {
        if (stroke == null) return
        pending = PointF(x, y)
        lifted = true
        pump()
    }

    private fun pump() {
        if (dispatching) return
        val previous = stroke ?: return
        val from = last ?: return
        val to = pending
        // Nothing new to send yet: hold the stroke open until movement or a lift arrives.
        if (to == null && !lifted) return
        val target = to ?: from

        val path = Path().apply { moveTo(from.x, from.y); lineTo(target.x, target.y) }
        val next = try {
            previous.continueStroke(path, 0, SEGMENT_MS, !lifted)
        } catch (t: Throwable) {
            MyCarLog.e("continueStroke rejected", t)
            reset()
            return
        }
        stroke = next
        last = target
        pending = null
        send(next)
    }

    private fun send(description: GestureDescription.StrokeDescription) {
        val gesture = GestureDescription.Builder().addStroke(description).build()
        dispatching = true
        val ok = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                dispatching = false
                if (lifted && pending == null) reset() else pump()
            }

            override fun onCancelled(g: GestureDescription?) {
                dispatching = false
                reset()
            }
        }, null)
        if (!ok) {
            MyCarLog.w("dispatchGesture refused - is another gesture in flight?")
            dispatching = false
            reset()
        }
    }

    private fun reset() {
        stroke = null
        last = null
        pending = null
        lifted = false
        dispatching = false
    }

    companion object {
        /**
         * How long each replayed segment is told to take. Short enough that the pointer keeps
         * up with the finger, long enough that the framework does not treat a drag as a fling.
         */
        private const val SEGMENT_MS = 32L

        @Volatile
        var instance: TouchRelayService? = null
            private set

        /** False until the user enables myCar under Settings > Accessibility. */
        val enabled: Boolean get() = instance != null

        private val on = MutableStateFlow(false)

        /** [enabled], to watch: the phone's settings show it changing as the driver comes back. */
        val switchedOn: StateFlow<Boolean> = on
    }
}
