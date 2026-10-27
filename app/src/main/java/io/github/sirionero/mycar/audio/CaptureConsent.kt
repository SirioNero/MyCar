package io.github.sirionero.mycar.audio

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.mirror.TouchRelayService

/**
 * Android's question before myCar may hear what the phone plays. Android asks it again every
 * drive, remembering no answer, and only a computer (adb) can tell it to stop. So, once the driver
 * has turned myCar on under Accessibility, myCar answers it itself: while the question it has just
 * asked for is waiting, it presses Android's own Start in it. Only that question - the one that
 * names myCar, asked by myCar minutes ago at most - and only then: at no other time does myCar
 * look at the phone's screen.
 */
object CaptureConsent {

    private val main = Handler(Looper.getMainLooper())
    private val looking = Any()

    /** When myCar asked, while the answer is still to come; 0 when nothing is waiting. */
    @Volatile private var askedAt = 0L

    /** The intent for Android's question, noted as asked so it can be answered. */
    fun intent(context: Context): Intent {
        val manager = context.getSystemService(MediaProjectionManager::class.java)
        // The whole screen the only choice, so Start needs nothing picked first: the phone's
        // sound is heard either way, and "a single app" would open a list of apps instead.
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            manager.createScreenCaptureIntent()
        }
        askedAt = SystemClock.uptimeMillis()
        TouchRelayService.instance?.let(::lookFor)
        return intent
    }

    /** The answer came back, either way, or the car went: nothing more to press. */
    fun answered() {
        askedAt = 0L
        main.removeCallbacksAndMessages(looking)
    }

    /** Whether myCar answers the question itself: its accessibility service is on. */
    val canAnswer: Boolean get() = TouchRelayService.enabled

    private val waiting: Boolean
        get() = askedAt != 0L && SystemClock.uptimeMillis() - askedAt < WAIT_MS

    /** The service came on: a question may be waiting already. */
    fun serviceReady(service: AccessibilityService) {
        if (waiting) lookFor(service)
    }

    /**
     * Looks for the question twice a second while it's waiting, and presses Start once it's on
     * screen - which can be a while, if it went up behind the lock screen.
     */
    private fun lookFor(service: AccessibilityService) {
        main.removeCallbacksAndMessages(looking)
        lastSeen = ""
        MyCarLog.i("audio: waiting for Android's question, to answer it with Start")
        val tick = object : Runnable {
            override fun run() {
                if (!waiting || answer(service)) return
                main.postAtTime(this, looking, SystemClock.uptimeMillis() + LOOK_EVERY_MS)
            }
        }
        main.postAtTime(tick, looking, SystemClock.uptimeMillis() + LOOK_EVERY_MS)
    }

    /** What the last look found, logged whenever it changes: why Start wasn't pressed, if it wasn't. */
    @Volatile private var lastSeen = ""

    /** Presses Start in the question that names myCar, if it's on screen. True once pressed. */
    private fun answer(service: AccessibilityService): Boolean {
        val label = service.packageManager.getApplicationLabel(service.applicationInfo).toString()
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        val seen = StringBuilder("${windows.size} windows:")
        for (window in windows) {
            val root = window.root
            val app = root?.packageName?.toString()
            seen.append(' ').append(app ?: "(no content)")
            if (root == null || app != SYSTEM_UI) continue
            // Ours: the question names the app asking. Another app's is left alone.
            val named = root.findAccessibilityNodeInfosByText(label).isNotEmpty()
            val buttons = root.findAccessibilityNodeInfosByViewId(START_BUTTON)
            seen.append("[named=$named, start=${buttons.size}]")
            if (!named) continue
            val start = buttons.firstOrNull { it.isEnabled && it.isVisibleToUser } ?: continue
            if (start.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                MyCarLog.i("audio: answered Android's question with Start")
                answered()
                return true
            }
            seen.append("[click refused]")
        }
        val summary = seen.toString()
        if (summary != lastSeen) {
            lastSeen = summary
            MyCarLog.i("audio: looking for Android's question - $summary")
        }
        return false
    }

    /** Where Android asks: its own system interface. */
    private const val SYSTEM_UI = "com.android.systemui"

    /** The question's Start: the positive button of its dialog, from Android 12 to 16. */
    private const val START_BUTTON = "android:id/button1"

    /** How long a question stays worth answering: long enough for a phone unlocked a while later. */
    private const val WAIT_MS = 10 * 60 * 1000L
    private const val LOOK_EVERY_MS = 500L
}
