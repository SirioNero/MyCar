package io.github.sirionero.mycar.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import io.github.sirionero.mycar.MyCarLog
import kotlin.system.measureTimeMillis

/**
 * Loads the phone's web engine before the car's screen is up. The first web page of a session
 * held the car's screen still while Android loaded the engine - for seconds, in the emulator.
 * Loaded while myCar and the car are still shaking hands, nobody sees it, and Plancia's page and
 * the browser start straight away.
 */
internal object WebViewWarmUp {
    @Volatile private var done = false

    fun start(context: Context) {
        if (done) return
        done = true
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            val took = measureTimeMillis { runCatching { WebSettings.getDefaultUserAgent(app) } }
            MyCarLog.i("web: the engine loaded in ${took}ms, before the car's screen")
        }
    }
}
