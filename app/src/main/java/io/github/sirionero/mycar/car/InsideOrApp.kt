package io.github.sirionero.mycar.car

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.service.media.MediaBrowserService
import androidx.car.app.CarAppService
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.Tailnet
import io.github.sirionero.mycar.bridge.Bridges
import io.github.sirionero.mycar.home.HaConnection
import io.github.sirionero.mycar.home.HomeAssistant
import io.github.sirionero.mycar.xonos.Xonos
import io.github.sirionero.mycar.xonos.data.Connection
import io.github.sirionero.mycar.xonos.data.Problem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plancia and Xonos inside myCar, or the Plancia and Xonos apps in their place: inside myCar while
 * they reach Home Assistant and Music Assistant; once they can't, and the app is on the phone, the
 * app, for the rest of the drive ([TakeOver]). What it's for above all: to connect wirelessly with
 * Tailscale on, the driver leaves myCar out of it - inside a VPN, Android keeps myCar off the car's
 * Wi-Fi - and then a Tailscale address is out of myCar's reach, not of the apps', inside Tailscale.
 * A wrong address or token in myCar's settings ends the same way: the car keeps a Plancia and a
 * Xonos that work. The flags it sets are [HomeAssistant.appTakesOver] and [Xonos.appTakesOver].
 */
internal object InsideOrApp {

    private val log = MyCarLog.tagged("APP")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    /** The drive started: watched from now until [stop]. */
    @Synchronized
    fun start(context: Context) {
        job?.cancel()
        // A new drive starts with the ones inside myCar, whatever the last one ended with.
        HomeAssistant.appTakesOver.value = false
        Xonos.appTakesOver.value = false
        val app = context.applicationContext
        Xonos.refresh(app)
        val plancia = TakeOver()
        val xonos = TakeOver()
        var appsCheckedAt = 0L
        var planciaThere = false
        var xonosThere = false
        fun check() {
            if (HomeAssistant.appTakesOver.value && Xonos.appTakesOver.value) return
            val now = SystemClock.elapsedRealtime()
            if (appsCheckedAt == 0L || now - appsCheckedAt >= APPS_EVERY_MS) {
                planciaThere = planciaAppThere(app)
                xonosThere = xonosAppThere(app)
                appsCheckedAt = now
            }
            val online = online(app)

            val home = HomeAssistant.config.value
            val ha = HomeAssistant.connection.value
            val haUp = ha is HaConnection.Connected
            // Asked of Android only when it can matter. Out of reach for myCar, but not through the
            // app's bridge, when the app has one: then Plancia inside myCar connects through it.
            val haOut = !haUp && home.isComplete && planciaThere && Tailnet.outOfReach(app, home.urls) &&
                !Bridges.plancia.available(app)
            if (!HomeAssistant.appTakesOver.value &&
                plancia.update(now, home.isComplete, planciaThere, haUp, online, haOut, (ha as? HaConnection.Failed)?.tokenRefused == true)
            ) {
                log.i("the Plancia app takes Plancia's place for this drive ($ha; ${Tailnet.describe(app)})")
                HomeAssistant.appTakesOver.value = true
            }

            val ma = Xonos.repo.connection.value
            val maUp = ma is Connection.Connected
            val maOut = !maUp && Xonos.setUp.value && xonosThere && Tailnet.outOfReach(app, Xonos.settings.load().urls) &&
                !Bridges.xonos.available(app)
            if (!Xonos.appTakesOver.value &&
                xonos.update(now, Xonos.setUp.value, xonosThere, maUp, online, maOut, (ma as? Connection.Failed)?.problem in MISSET)
            ) {
                log.i("the Xonos app takes Xonos's place for this drive ($ma; ${Tailnet.describe(app)})")
                Xonos.appTakesOver.value = true
                scope.launch(Dispatchers.Main) { CarServices.xonosAppTakesOver() }
            }
        }
        // Once now: the drive's music is picked right after, and must not be the Xonos that can't connect.
        runCatching { check() }.onFailure { log.w("couldn't tell Plancia's and Xonos's places: ${it.message}") }
        job = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                runCatching { check() }
            }
        }
    }

    /** The drive's over: the next one starts with Plancia and Xonos inside myCar again. */
    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        HomeAssistant.appTakesOver.value = false
        Xonos.appTakesOver.value = false
    }

    /** Whether the Plancia app is on the phone, as a car app myCar can open in Plancia's place. */
    fun planciaAppThere(context: Context): Boolean = installed(context, CarAppService.SERVICE_INTERFACE, PLANCIA_PACKAGE)

    /** Whether the Xonos app is on the phone, as a music app myCar can play in Xonos's place. */
    fun xonosAppThere(context: Context): Boolean = installed(context, MediaBrowserService.SERVICE_INTERFACE, Xonos.APP_PACKAGE)

    private fun installed(context: Context, action: String, packageName: String): Boolean =
        runCatching { context.packageManager.queryIntentServices(Intent(action).setPackage(packageName), 0).isNotEmpty() }
            .getOrDefault(false)

    /** The phone reaches the internet; through a VPN counts, as Android doesn't check those. */
    private fun online(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull() ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
    }

    /** What Music Assistant answers when myCar's settings are wrong: no retry mends these during a drive. */
    private val MISSET = setOf(Problem.BAD_ADDRESS, Problem.NOT_MUSIC_ASSISTANT, Problem.BAD_TOKEN, Problem.DENIED)

    private const val TICK_MS = 2_000L
    private const val APPS_EVERY_MS = 30_000L
}
