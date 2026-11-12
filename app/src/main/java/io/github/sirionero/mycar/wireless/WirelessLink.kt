package io.github.sirionero.mycar.wireless

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiNetworkSpecifier
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.StringRes
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.SessionStatus
import io.github.sirionero.mycar.Tailnet
import io.github.sirionero.mycar.Wire
import io.github.sirionero.mycar.Words
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.Socket
import java.util.UUID

/**
 * Wireless Android Auto, phone side: the start-up that hands myCar a socket instead of a USB
 * accessory. Everything after it - versions, TLS, the certificate, channels, video - is the same
 * protocol [io.github.sirionero.mycar.MyCarRunner] already speaks over USB.
 *
 *   1. Bluetooth: open RFCOMM to the car's Android Auto wireless service. The car must already be
 *      paired with the phone, as it usually is for calls.
 *   2. Over that channel, framed as [u16 length][u16 message id][protobuf]:
 *        <- VersionRequest            -> VersionResponse
 *        <- StartRequest {ip, port}   -> InfoRequest
 *        <- InfoResponse {ssid, password, bssid, security}
 *   3. Join the car's Wi-Fi as a local-only network, so the phone's internet stays on mobile data,
 *      and say so over Bluetooth before connecting:
 *        -> StartResponse {phone ip, status}    -> ConnectionStatus {status}
 *   4. TCP to {ip, port} over that network, and hand the socket to [onConnected].
 *
 * The Bluetooth channel stays open for the whole drive: the car pings over it, and some cars end
 * the session when it closes. Message layout from LIVI's head-unit side (livi-aa/src/wpp.rs) and
 * aasdk's aaw protos.
 */
object WirelessLink {

    private val log = MyCarLog.tagged("AAW")

    /** The Android Auto wireless service every head unit publishes over Bluetooth. */
    val AA_UUID: UUID = UUID.fromString("4de17a00-52cb-11e6-bdf4-0800200c9a66")

    private const val MSG_START_REQUEST = 1
    private const val MSG_INFO_REQUEST = 2
    private const val MSG_INFO_RESPONSE = 3
    /**
     * The wireless setup's status for success, shared by every message that carries one.
     * aa-proxy-rs sends 0 to real cars and they carry on; a Renault Media Nav given 1 went quiet
     * and closed the channel. (open-android-auto's table, which says 1, is marked unverified.)
     */
    private const val STATUS_SUCCESS = 0L
    private const val MSG_VERSION_REQUEST = 4
    private const val MSG_VERSION_RESPONSE = 5
    private const val MSG_CONNECTION_STATUS = 6
    private const val MSG_START_RESPONSE = 7
    private const val MSG_PING = 8
    private const val MSG_PONG = 9

    private const val LISTEN_MS = 120_000 // time to reconnect the phone from the car's Bluetooth menu
    /** Started by the car's own Bluetooth: its Android Auto can take a while to boot before it calls. */
    private const val AUTO_LISTEN_MS = 300_000
    /** After a drive with a car that calls the phone, how long the service stays published for it. */
    private const val AGAIN_LISTEN_MS = 600_000
    /** At least this many TCP tries, each up to 5 s when the car doesn't answer... */
    private const val TCP_TRIES = 12
    /** ...and for at least this long: a car's projection listener can start well after its Wi-Fi. */
    private const val TCP_WINDOW_MS = 60_000L
    /** How long the first try waits for another Wi-Fi's name, when one is in the car's address range. */
    private const val WIFI_DETAILS_WAIT_MS = 400L
    private const val WIFI_WAIT_MS = 60_000
    /** While the phone's service waits for the car, the car's own is called this often (see [waitForCar]). */
    private const val KNOCK_MS = 8_000L
    /** The same after a drive ([quiet]): the car may be gone, so less often. */
    private const val QUIET_KNOCK_MS = 15_000L
    /** How long a car's Bluetooth may be gone before an automatic start-up with nothing from it stops. */
    private const val GONE_GRACE_MS = 30_000L
    /** The listening after a drive, awake: to publish the service again for a car that calls the phone. */
    private const val QUIET_PUBLISH_MS = 10_000L
    /** ... and for one that waits to be called: a short stop. */
    private const val QUIET_AWAKE_MS = 180_000L
    /** A Bluetooth channel on which the car says nothing that leads to its Wi-Fi this long is closed. */
    private const val CHANNEL_QUIET_MS = 30_000L
    /** A start-up the car's Bluetooth began starts over this many times when a later step fails. */
    private const val AUTO_RETRIES = 3
    private const val RETRY_DELAY_MS = 3_000L

    /** Set by the activity: starts a session on the car's socket. */
    @Volatile var onConnected: ((Socket) -> Unit)? = null

    @Volatile private var rfcomm: BluetoothSocket? = null
    @Volatile private var listener: BluetoothServerSocket? = null
    @Volatile private var tcp: Socket? = null
    @Volatile private var network: ConnectivityManager.NetworkCallback? = null
    @Volatile private var worker: Thread? = null

    /** A start-up, or a session's Bluetooth channel, is under way. */
    val busy: Boolean get() = worker?.isAlive == true

    /** Only listening for a car after its drive (see [listenAgain]): a car coming back may start afresh. */
    val listeningQuietly: Boolean get() = quiet && busy && rfcomm == null && tcp == null

    /** The current start-up waits to start over ([startOver]): not over, though its thread may be. */
    val startingOver: Boolean get() = overPending == startRun

    /** The car of the current start-up, for [reachedCar]. */
    @Volatile private var car: BluetoothDevice? = null
    /** Set when Android drops the car's network mid-try, so the rest don't bind to a dead one. */
    @Volatile private var lostNetwork: Network? = null
    /** When [lostNetwork] went (elapsedRealtime). */
    @Volatile private var lostAt = 0L
    /** The network the drive's socket is bound to; null on the phone's ordinary route. */
    @Volatile private var driveNetwork: Network? = null
    /** The car network asked for or joined, until Android says it's gone or never came. */
    @Volatile private var activeSsid: String? = null
    /** Bumped on each Wi-Fi join and on stop, so an older run of TCP tries ends instead of racing the new one. */
    @Volatile private var tcpRun = 0

    /**
     * Which start-up is the current one. A Bluetooth connect or accept can't be interrupted, so a
     * start-up stopped while in one carried on once it returned: it took the channel, or the car,
     * from the start-up that replaced it.
     */
    @Volatile private var startRun = 0
    private val writeLock = Any()
    private val main = Handler(Looper.getMainLooper())

    /** The current start-up's car, to listen for again once the drive is over (see [listenAgain]). */
    @Volatile private var againFor: BluetoothDevice? = null
    /** The current start-up got as far as a drive. */
    @Volatile private var drove = false
    /** Listening for the car again after a drive: nothing on Home until the car calls. */
    @Volatile private var quiet = false
    /** The drive's service was started here, for the car's Wi-Fi, and no drive has taken it over yet. */
    @Volatile private var ownsService = false
    /** Started by the car's Bluetooth (not a tap, not the listening after a drive): it starts over by itself. */
    @Volatile private var autoStart = false
    @Volatile private var retriesLeft = 0
    /** The start-up a start-over is already on its way for, so two failures don't post two. */
    @Volatile private var retryFor = -1
    /** The start-up whose start-over is waiting its [RETRY_DELAY_MS]; -1 once the wait is over. */
    @Volatile private var overPending = -1
    /** The current start-up got as far as the car showing myCar ([reachedCar]); [drove] is only the TCP. */
    @Volatile private var pictured = false
    /** The current start-up has the car's Wi-Fi details and address, and asked for the network. */
    @Volatile private var joined = false
    /** The current start-up's failure is said already (the phone's Wi-Fi off): its channel's end says nothing more. */
    @Volatile private var saidWhy = false
    /**
     * What the current start-up, start-overs included, has seen since Android first refused myCar the
     * car's network ([VpnBlock]): when that was (elapsedRealtime, 0: never), the tries over the phone's
     * ordinary route since that the car didn't answer, and whether it answered one.
     */
    @Volatile private var vpnRefusedAt = 0L
    @Volatile private var vpnUnanswered = 0
    @Volatile private var vpnAnswered = false
    /** Said: a VPN keeps myCar off the car's Wi-Fi. Home's line for the rest of the start-up ([connecting]); null until then. */
    @Volatile private var vpnSaid: VpnSaid? = null
    /** The current start-up's channel: when it opened, whether a quick call opened it ([KnockPlan]), and the car's Wi-Fi details came on it. */
    @Volatile private var channelOpenedAt = 0L
    @Volatile private var quickChannel = false
    @Volatile private var wifiDetails = false
    /**
     * The car closed a channel at its first contact in this start-up or a start-over of it: the calls
     * to its service keep the usual step from then on (see [KnockPlan]).
     */
    @Volatile private var slowKnocks = false

    /**
     * Keeps the phone awake while a start-up waits for the car, until the car shows myCar (the
     * drive's service keeps it awake from there) or the start-up ends. In a pocket, with the screen
     * off, the phone went to sleep: the calls to the car every few seconds and a start-over's short
     * wait count only the time awake, so they stopped until something else woke the phone - often
     * the driver, taking it out to tap Disconnect and Connect. The listening after a drive holds it a
     * little: long enough to publish the service again for a car that calls the phone (its call wakes
     * the phone), a short stop's worth for one that waits to be called (the calls to it are its only
     * way back); and every start-over, as the car has only just answered.
     */
    @Volatile private var awake: PowerManager.WakeLock? = null

    private fun stayAwake(context: Context, ms: Long) {
        val lock = awake ?: runCatching {
            context.applicationContext.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myCar:wireless-startup")
                .apply { setReferenceCounted(false) }
        }.getOrNull()?.also { awake = it } ?: return
        // A new time replaces the one before.
        runCatching { lock.acquire(ms) }
    }

    private fun sleepAgain() {
        runCatching { awake?.takeIf { it.isHeld }?.release() }
    }

    /**
     * Takes the drive's service for start-up [mine]; false if that start-up is over already. With
     * [stop] under one lock: a stop as the channel opened or the Wi-Fi was asked for gave the service
     * back, and the start-up then took it again, for good - the phone kept awake, "Connecting…" on
     * Home, and every arrival of the car taken for a drive under way.
     */
    private fun holdService(context: Context, mine: Int): Boolean = synchronized(serviceLock) {
        if (mine != startRun) return false
        ownsService = true
        io.github.sirionero.mycar.SessionService.start(context, wireless = true, holder = io.github.sirionero.mycar.SessionService.STARTUP)
        true
    }

    /** Guards [holdService] against [stop]. */
    private val serviceLock = Any()

    /** A start-up that never got to a drive gives the drive's service back; a drive gives it back as it ends. */
    private fun giveBackService(context: Context) {
        // Not [drove]: after a drive the car can start over on the same channel, and the service
        // taken for that was never given back - left on, every arrival of the car read as a drive.
        if (!ownsService) return
        ownsService = false
        io.github.sirionero.mycar.SessionService.stop(context, io.github.sirionero.mycar.SessionService.STARTUP)
    }

    /** Guards [channelOver] and [releaseWithChannel], set from the session's end and the channel's. */
    private val ends = Any()
    /** The current start-up's Bluetooth channel is over. */
    private var channelOver = false
    /** When it ended (elapsedRealtime), 0 while open: a port given before is gone if it closed before the Wi-Fi came. */
    @Volatile private var channelClosedAt = 0L
    /** The car ended the session on Wi-Fi with the channel still open: the car's network goes with the channel. */
    private var releaseWithChannel = false

    /** Paired Bluetooth devices, cars or not: the driver picks theirs, by the name shown. Needs BLUETOOTH_CONNECT. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<BluetoothDevice> =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
            ?.sortedBy { shownName(it) }
            .orEmpty()

    /** The device's own name, for the log: an alias the driver gave it may hold a person's name. */
    @SuppressLint("MissingPermission")
    fun name(device: BluetoothDevice): String = device.name ?: device.address

    /**
     * The name the driver knows the device by, for the screen: the one given in Android's Bluetooth
     * settings (its alias), else its own. Never in the log ([name] is).
     */
    @SuppressLint("MissingPermission")
    fun shownName(device: BluetoothDevice): String = device.alias ?: device.name ?: device.address

    /**
     * [byItself]: the car came up over Bluetooth and myCar connects with no tap (see [WirelessAuto]).
     * [again]: a start-over of one of those ([startOver]), which keeps counting its tries.
     */
    fun start(context: Context, device: BluetoothDevice, byItself: Boolean = false, quiet: Boolean = false, again: Boolean = false) {
        // A start-over goes on saying "connecting": not a moment of "not connected" between two tries.
        stop(context, say = !(again && !quiet))
        againFor = device
        drove = false
        this.quiet = quiet
        // The listening after a drive too: a car that called and then failed at the Wi-Fi was left there.
        autoStart = byItself
        if (!again) {
            retriesLeft = AUTO_RETRIES
            slowKnocks = false
            forgetVpn()
        }
        pictured = false
        joined = false
        saidWhy = false
        channelOpenedAt = 0L
        quickChannel = false
        wifiDetails = false
        goneAddress = null
        synchronized(ends) {
            channelOver = false
            channelClosedAt = 0L
            releaseWithChannel = false
        }
        val app = context.applicationContext
        car = device
        // The wait for the car, then its Wi-Fi (up to a minute) and the TCP tries (a minute).
        if (!quiet) stayAwake(app, (if (byItself) AUTO_LISTEN_MS else LISTEN_MS) + 180_000L)
        else stayAwake(app, if (WirelessAuto.callsPhone(app, device.address)) QUIET_PUBLISH_MS else QUIET_AWAKE_MS)
        // Taken here, not in the thread: a start-up stopped before its thread ran is old already.
        val mine = startRun
        worker = Thread({
            // A test feature: anything unexpected ends the attempt, not the app.
            try {
                run(app, device, mine, byItself)
            } catch (e: Exception) {
                log.e("wireless start-up failed", e)
                SessionStatus.failed(Words.get(R.string.wireless_link_failed), "${e.javaClass.simpleName}: ${e.message}")
            } finally {
                // Over, unless a start-over is on its way: it keeps the phone awake for its wait.
                if (mine == startRun && retryFor != mine) sleepAgain()
            }
        }, "mycar-wireless").apply { isDaemon = true; start() }
    }

    /** The car's picture came up over Wi-Fi: it works without the cable, so it can connect by itself next time. */
    fun reachedCar(context: Context) {
        pictured = true
        // The drive's service keeps the phone awake from here.
        sleepAgain()
        car?.let { WirelessAuto.reached(context.applicationContext, it) }
    }

    /**
     * The session on [socket] is over: the car closed it, or myCar did. Marked at once, as the car's
     * end of the stream shows: a car that starts over on the same Bluetooth channel sends its Wi-Fi
     * details again within moments, and while [tcp] still named the old socket they were taken for
     * ZLink's repeat and ignored - the drive didn't come back.
     */
    fun tcpEnded(socket: Socket) {
        synchronized(ends) { if (tcp === socket) tcp = null }
    }

    /** The car's stream on [socket], marking [tcpEnded] as soon as it ends. */
    fun watched(socket: Socket): java.io.InputStream = object : java.io.FilterInputStream(socket.getInputStream()) {
        override fun read(b: ByteArray, off: Int, len: Int): Int = try {
            super.read(b, off, len).also { if (it < 0) tcpEnded(socket) }
        } catch (e: IOException) {
            tcpEnded(socket)
            throw e
        }
    }

    /**
     * The car ended the session over Wi-Fi. With the Bluetooth channel over too (its worker gone),
     * the request for the car's Wi-Fi goes as well: it kept Android looking for the car until the
     * next tap. While the channel is open the car may start over on it, so it stays.
     */
    fun sessionEnded(context: Context, socket: Socket) {
        synchronized(ends) {
            // A newer drive is on the car's network already (the car started over at once): it
            // isn't this one's to end.
            val current = tcp
            if (current != null && current !== socket) return
            tcp = null
            if (worker?.isAlive == true && !channelOver) {
                releaseWithChannel = true
                // Ended before the car showed myCar (its Android Auto not ready, a certificate
                // turned down): the channel goes too, and the start-up begins again (channelEnded).
                if (!pictured && autoStart) runCatching { rfcomm?.close() }
                return
            }
            releaseNetwork(context)
            listenAgain(context)
            if (!pictured) startOver(context, startRun, "the session ended before the car showed myCar")
        }
    }

    /**
     * The Bluetooth channel is over. If the car had ended the session on Wi-Fi first, the request
     * for its network goes now: it stayed until the next tap, with Android looking for the car.
     */
    private fun channelEnded(context: Context, mine: Int, byCar: Boolean) {
        synchronized(ends) {
            if (mine != startRun) return
            channelOver = true
            channelClosedAt = SystemClock.elapsedRealtime()
            // Closed by the car within moments of opening, before its Wi-Fi details: an adapter does
            // that to a channel opened right after its Bluetooth came. The calls to its service keep
            // the usual step for the rest of the start-up, and a channel a quick call opened (maybe
            // too soon) costs no start-over: those cost the start-overs a car has.
            if (byCar && channelOpenedAt != 0L && KnockPlan.closedEarly(channelOpenedAt, channelClosedAt, wifiDetails)) {
                if (!slowKnocks && !quiet) {
                    log.i(
                        "Bluetooth: the car closed the channel ${(channelClosedAt - channelOpenedAt) / 100 / 10.0}s after it opened, " +
                            "before its Wi-Fi details - calling its service every ${KNOCK_MS / 1000}s from now on",
                    )
                }
                slowKnocks = true
                val left = KnockPlan.startOversLeft(retriesLeft, quickChannel, startsOver = autoStart, overPending = retryFor == mine)
                if (left != retriesLeft) {
                    retriesLeft = left
                    log.i("auto-connect: a quick call opened that channel - its start-over isn't counted")
                }
            }
            if (releaseWithChannel && tcp == null) releaseNetwork(context)
            // During the drive: a car that closes its channel and Wi-Fi together is switching off (the report).
            if (byCar && tcp != null) io.github.sirionero.mycar.SessionReport.carChannelClosed(channelClosedAt)
            if (tcp != null) return
            // The car's Wi-Fi asked for and not over: some cars close the channel once the phone has
            // joined, before its TCP. The join and the tries go on, and decide (onUnavailable, connectTcp).
            if (network != null) return
            giveBackService(context)
            if (pictured) {
                listenAgain(context)
                return
            }
            if (!startOver(context, mine, "the car closed the Bluetooth channel before a drive")) {
                if (quiet) {
                    // The listening after a drive said "talking to the car" when the channel opened:
                    // its last start-over over, that went too, or it stayed up for good.
                    if (SessionStatus.state.value.phase == SessionStatus.Phase.CONNECTING) SessionStatus.disconnected()
                } else if (!saidWhy && !drove) {
                    // Nothing follows: "talking to the car" stayed up for good, and only Disconnect cleared it.
                    // Only for a start that never reached a session: one on the car's Wi-Fi has said
                    // its own reason already (MyCarRunner: the certificate, the car's date), and this
                    // took its place, blaming the car's Bluetooth.
                    car?.let { SessionStatus.failed(R.string.wireless_link_bt_no_car, R.string.wireless_link_bt_no_car_detail, shownName(it)) }
                }
            }
        }
    }

    /**
     * A start-up the car's Bluetooth began went wrong after the car answered - its Wi-Fi didn't come,
     * no TCP, the channel closed - and stayed there with "couldn't connect" until the driver tapped
     * Disconnect and Connect, which then worked at once: the car had only needed a few more seconds.
     * Now it starts over by itself, a few times.
     */
    private fun startOver(context: Context, mine: Int, why: String): Boolean {
        val device = car ?: return false
        if (retryFor == mine) return true
        if (!autoStart || pictured || mine != startRun || retriesLeft <= 0) return false
        retryFor = mine
        retriesLeft--
        log.i("auto-connect: $why - starting over in ${RETRY_DELAY_MS / 1000} s, as Connect would ($retriesLeft more after this)")
        val app = context.applicationContext
        // The listening after a drive stays that: started over as a plain start-up it showed
        // "waiting for the car" on Home for 5 minutes, and ended with "the car didn't connect".
        val wasQuiet = quiet
        stayAwake(app, RETRY_DELAY_MS + 5_000L)
        overPending = mine
        main.postDelayed({
            if (overPending == mine) overPending = -1
            // Stopped, started anew or driving meanwhile (the cable, a tap, Disconnect): that one decides.
            if (mine != startRun || tcp != null) return@postDelayed
            start(app, device, byItself = true, quiet = wasQuiet, again = true)
        }, RETRY_DELAY_MS)
        return true
    }

    /**
     * The car's Bluetooth went (Android's companion watch, CarPresenceService): the listening after a
     * drive stops, since the driver has left with the car. Other start-ups go on: a Bluetooth that
     * flickers comes back within moments, and a car coming back starts them anew anyway.
     */
    fun carGone(context: Context, address: String) {
        val device = car?.takeIf { it.address.equals(address, ignoreCase = true) }
        // Every going said: an adapter restarting, or a car only waking for a moment, was seen only
        // through its coming back, or 30 s on below.
        log.i("Bluetooth: ${device?.let { name(it) } ?: "a car myCar knows (…${address.takeLast(5)})"} disconnected")
        if (device == null) return
        if (listeningQuietly) {
            log.i("Bluetooth: ${name(device)} went - no more listening for it after the drive")
            stop(context)
            return
        }
        // A start-up the car's Bluetooth began, with nothing from the car yet (no channel, no Wi-Fi):
        // gone for 30 s, the driver has left - it stopped only after 5 minutes of calling a car that
        // wasn't there, with "the car didn't connect" at the end. A car back within that keeps it.
        if (autoStart && !quiet && !pictured && rfcomm == null && tcp == null && network == null) {
            goneAddress = address
            val turn = ++goneTurn
            val run = startRun
            main.postDelayed({
                // This very going, 30 s on: an earlier one's timer doesn't act on a later going.
                if (run == startRun && turn == goneTurn && goneAddress.equals(address, ignoreCase = true) &&
                    rfcomm == null && tcp == null && network == null && !pictured
                ) {
                    log.i("Bluetooth: ${name(device)} went and didn't come back - the automatic start-up stops")
                    stop(context.applicationContext)
                }
            }, GONE_GRACE_MS)
        }
    }

    /** The car's Bluetooth back (Android's companion watch): a start-up waiting on its going goes on. */
    fun carHere(address: String) {
        arrivals[address.uppercase(java.util.Locale.ROOT)] = SystemClock.elapsedRealtime()
        if (goneAddress.equals(address, ignoreCase = true)) goneAddress = null
        // A start-up waiting for this car calls its service a few seconds on (KnockPlan), not at its next step.
        synchronized(knockWake) { knockWake.notifyAll() }
    }

    /** Wakes the calls to a car's service ([waitForCar]) when its Bluetooth comes. */
    private val knockWake = Object()

    /**
     * When each car's Bluetooth last came (Android's companion watch, every arrival, during a drive
     * too), by address in capitals, on elapsedRealtime: an adapter that restarted is back on it.
     */
    private val arrivals = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun arrivalOf(address: String): Long? = arrivals[address.uppercase(java.util.Locale.ROOT)]

    /** The car whose Bluetooth went during an automatic start-up, until it's back or the start-up stops. */
    @Volatile private var goneAddress: String? = null

    /** Bumped at each going of the car, so a timer from an earlier going doesn't act on a later one. */
    @Volatile private var goneTurn = 0

    /**
     * A drive with a car that calls the phone is over (the session and the Bluetooth channel both),
     * the car having ended it. Its Bluetooth may well stay connected - a short stop, the car not
     * yet asleep - and then Android never says the car came back, while the car, starting its
     * Android Auto again, found no service to call: the drive didn't come back until a tap. So the
     * service stays published for it a while longer, with nothing shown until the car calls.
     */
    private fun listenAgain(context: Context) {
        val device = againFor ?: return
        // A drive the car showed: one that ended before the picture starts over instead (startOver).
        if (!pictured) return
        againFor = null
        val app = context.applicationContext
        // A car that waits to be called too: its service is called now and then while it listens.
        if (!WirelessAuto.mayConnect(app, device.address)) return
        // "Use myCar in the car" off: the car is Android Auto's now.
        if (!io.github.sirionero.mycar.AppSettings.myCarEnabled(app)) return
        val run = startRun
        main.post {
            // Started or stopped meanwhile (the cable, a tap, Disconnect): that one decides.
            if (run != startRun || tcp != null) return@post
            log.i("Bluetooth: the drive is over - listening for ${name(device)} for ${AGAIN_LISTEN_MS / 60_000} minutes, in case it starts again")
            start(app, device, byItself = true, quiet = true)
        }
    }

    /**
     * "Use myCar in the car" turned off: a start-up or the listening after a drive stops, so myCar's
     * Android Auto service no longer takes the car from Android Auto. A drive that's up stays.
     */
    fun stopUnlessDriving(context: Context) {
        // A start-over waiting its 3 s is a start-up too: left alone it started again regardless.
        if (tcp != null || (!busy && network == null && !startingOver)) return
        log.i("myCar turned off - the wireless start-up stops")
        stop(context)
    }

    private fun releaseNetwork(context: Context) {
        network?.let { cb ->
            runCatching { context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        }
        network = null
        activeSsid = null
    }

    /**
     * Closes the Bluetooth channel, the socket and the Wi-Fi request. Safe to call any time. [say]
     * puts Home's card back to "not connected" if a start-up was under way.
     */
    fun stop(context: Context, say: Boolean = true) {
        tcpRun++
        // With the service: a start-up taking it right now either sees itself stopped or gives it back here.
        synchronized(serviceLock) {
            startRun++
            giveBackService(context)
        }
        againFor = null
        sleepAgain()
        val starting = worker != null && SessionStatus.state.value.phase == SessionStatus.Phase.CONNECTING
        worker?.interrupt()
        worker = null
        runCatching { listener?.close() }
        listener = null
        runCatching { rfcomm?.close() }
        rfcomm = null
        runCatching { tcp?.close() }
        tcp = null
        network?.let { cb ->
            runCatching { context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        }
        network = null
        activeSsid = null
        // Stopped before the car's session began, so nothing else resets Home's card.
        if (starting) {
            log.i("wireless start-up stopped")
            if (say) SessionStatus.disconnected()
        }
    }

    /** A stable id for this phone as this app sees it, standing in for the serial a real phone sends. */
    @SuppressLint("HardwareIds")
    private fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "mycar"

    @SuppressLint("MissingPermission")
    private fun run(context: Context, device: BluetoothDevice, mine: Int, byItself: Boolean) {
        val name = name(device)
        // On the phone's screen, the car's name as the driver knows it.
        val shown = shownName(device)
        // The car's Bluetooth address only in the debug build: logs get shared, in public too.
        log.i("--- wireless start-up with $name (${if (BuildConfig.DEBUG) device.address else "…" + device.address.takeLast(5)}) ---")
        if (!quiet) connecting(R.string.wireless_link_bt_reaching, shown)
        val listenMs = when {
            quiet -> AGAIN_LISTEN_MS
            byItself -> AUTO_LISTEN_MS
            else -> LISTEN_MS
        }
        // A car that called the phone last time does it again as soon as its Bluetooth is up: the
        // service is published at once rather than after a try at the car's own, which took a few
        // seconds to fail - long enough for the car to look, find nothing, and give up.
        val callsUs = WirelessAuto.callsPhone(context, device.address)
        var inbound = callsUs
        // A quick call to the car's service opened the channel (KnockPlan).
        var quick = false
        // The car's Bluetooth arrival known before the first call: one while it runs is new to the wait.
        val arrivedBefore = arrivalOf(device.address)
        val firstCallAt = SystemClock.elapsedRealtime()
        val socket = if (callsUs) {
            log.i("Bluetooth: $name called the phone last time - waiting for it at once")
            val accepted = waitForCar(context, device, name, mine, listenMs)
            if (accepted == null) {
                // It didn't, this time: next time the phone tries the car first again. Not when the
                // car's Bluetooth woke it or after a drive, when the car may simply not have started.
                if (mine == startRun && !quiet && !byItself) WirelessAuto.rememberCalls(context, device.address, false)
                return
            }
            inbound = accepted.inbound
            accepted.socket
        } else try {
            // A running scan slows the connect, but stopping one needs BLUETOOTH_SCAN on Android
            // 12+, which myCar doesn't ask for. myCar never scans, so skipping it is fine.
            runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter?.cancelDiscovery() }
            device.createRfcommSocketToServiceRecord(AA_UUID).also { it.connect() }
        } catch (e: SecurityException) {
            log.e("Bluetooth: permission refused while opening the channel on $name", e)
            SessionStatus.failed(
                R.string.wireless_link_bt_permission,
                R.string.wireless_link_bt_permission_detail,
                shown,
            )
            return
        } catch (e: IOException) {
            // Most factory head units publish no service of their own: they look for the phone's
            // and connect to it, so wait for the car instead.
            log.i("Bluetooth: $name has no Android Auto service to call (${e.message}); waiting for it to call us")
            // Replaced while its connect hung: the newer start-up does the waiting.
            if (mine != startRun) return
            // Failed at once: the car's Bluetooth is there, its service not yet. Without the car it takes ~5 s.
            val carThere = SystemClock.elapsedRealtime() - firstCallAt < KnockPlan.FAST_FAIL_MS
            val got = waitForCar(context, device, name, mine, listenMs, carThere, arrivedBefore) ?: return
            inbound = got.inbound
            quick = got.quick
            got.socket
        }
        if (mine != startRun) {
            log.i("Bluetooth: a newer start-up took over - closing this one's channel")
            runCatching { socket.close() }
            return
        }
        rfcomm = socket
        if (inbound != callsUs) WirelessAuto.rememberCalls(context, device.address, inbound)
        // The car's Wi-Fi is handed only to an app in front or with a foreground service. With the
        // phone in a pocket - connecting by itself, or after a stop - the screen isn't in front, and
        // the drive's service only came once the picture was up: the join was refused.
        if (!holdService(context, mine)) {
            log.i("Bluetooth: stopped as the channel opened - closing it")
            runCatching { socket.close() }
            if (rfcomm === socket) rfcomm = null
            return
        }
        log.i("Bluetooth: channel open")
        channelOpenedAt = SystemClock.elapsedRealtime()
        quickChannel = quick
        if (mine == startRun) connecting(R.string.wireless_link_bt_talking, shown)
        // A car whose Android Auto isn't ready yet can take the channel and say nothing: the start-up
        // stayed on "talking to the car" for good, holding the drive's service, and every arrival of
        // the car after it was taken for a drive under way. Closed, it starts over (channelEnded).
        main.postDelayed({
            if (mine == startRun && rfcomm === socket && !joined) {
                log.w("Bluetooth: $name said nothing that leads to its Wi-Fi in ${CHANNEL_QUIET_MS / 1000}s - closing the channel")
                runCatching { socket.close() }
            }
        }, CHANNEL_QUIET_MS)

        val input = DataInputStream(socket.inputStream)
        val output = socket.outputStream
        var target: Pair<String, Int>? = null
        // Wi-Fi details that came before the address, joined once the StartRequest brings it.
        var waitingJoin: ((String, Int) -> Unit)? = null
        // The car closed the channel (not myCar).
        var byCar = false
        // The car's VersionRequest answered, whether with the Wi-Fi channel type, and whether the car went
        // on after it (its StartRequest or Wi-Fi details): one that didn't may want the type (WirelessVersion).
        var answered = false
        var gaveChannelType = false
        var wentOn = false
        try {
            while (!Thread.currentThread().isInterrupted) {
                val length = input.readUnsignedShort()
                val id = input.readUnsignedShort()
                val body = ByteArray(length).also { input.readFully(it) }
                when (id) {
                    MSG_VERSION_REQUEST -> {
                        log.i("<- VersionRequest")
                        log.hex("   version", body)
                        val f = Wire.fields(body)
                        // The Wi-Fi channel type back only to a car that went quiet without it (WirelessVersion).
                        val withType = WirelessAuto.wantsChannelType(context, device.address) && Wire.varint(f, 3) != null
                        val reply = WirelessVersion.reply(body, deviceId(context), UUID.randomUUID().toString(), withType)
                        send(output, MSG_VERSION_RESPONSE, reply)
                        answered = true
                        gaveChannelType = withType
                        log.i("-> VersionResponse")
                        // With the phone's id (its serial, twice) only in the debug build: logs get shared.
                        if (BuildConfig.DEBUG) log.hex("   reply", reply)
                        else log.i(
                            "   reply: version ${Wire.varint(f, 1) ?: 1}.${Wire.varint(f, 2) ?: 0}, status $STATUS_SUCCESS" +
                                (if (withType) ", Wi-Fi channel type ${Wire.varint(f, 3)}" else "") + " (the phone's id left out)",
                        )
                    }
                    MSG_START_REQUEST -> {
                        wentOn = true
                        val f = Wire.fields(body)
                        val ip = f.firstOrNull { it.number == 1 && it.wireType == 2 }?.bytes?.decodeToString().orEmpty()
                        val port = Wire.varint(f, 2)?.toInt() ?: 0
                        log.i("<- StartRequest: projection at $ip:$port")
                        target = ip to port
                        val join = waitingJoin
                        if (join != null) {
                            waitingJoin = null
                            join(ip, port)
                        } else {
                            send(output, MSG_INFO_REQUEST, ByteArray(0))
                            log.i("-> InfoRequest")
                        }
                    }
                    MSG_INFO_RESPONSE -> {
                        wifiDetails = true
                        wentOn = true
                        val f = Wire.fields(body)
                        fun text(n: Int) = f.firstOrNull { it.number == n && it.wireType == 2 }?.bytes?.decodeToString().orEmpty()
                        val ssid = text(1)
                        val password = text(2)
                        val bssid = text(3)
                        val security = Wire.varint(f, 4) ?: -1
                        // The car's Wi-Fi address (BSSID) only in the debug build: logs get shared, and it
                        // places the car on maps of networks. Whether there's one is what setBssid needs.
                        val shownBssid = when {
                            BuildConfig.DEBUG -> bssid
                            bssid.isEmpty() -> "none"
                            bssid == "00:00:00:00:00:00" -> "zeros"
                            else -> "(set)"
                        }
                        log.i("<- InfoResponse: ssid=\"$ssid\" bssid=$shownBssid security=$security password=${if (password.isEmpty()) "none" else "(${password.length} chars)"}")
                        // ZLink head units send the Wi-Fi details again once the stream is up. Asking
                        // Android for the network again releases the one carrying the stream, which
                        // aborts it a second in; a phone running Android Auto carries on. A car
                        // starting over once its session ended still has the network asked for anew.
                        if (ssid == activeSsid && tcp != null) {
                            log.i("InfoResponse again for \"$ssid\", which the drive is on - ignoring it")
                            continue
                        }
                        val join = { ip: String, port: Int -> joinWifi(context, output, shown, ssid, password, bssid, ip, port, mine) }
                        val known = target
                        if (known != null) {
                            join(known.first, known.second)
                        } else {
                            // Some dongles send the Wi-Fi details first; the address follows a moment later.
                            log.i("InfoResponse before StartRequest: waiting for the address")
                            waitingJoin = join
                        }
                    }
                    MSG_PING -> send(output, MSG_PONG, body)
                    MSG_CONNECTION_STATUS, MSG_START_RESPONSE -> {
                        log.i("<- message $id: status=${Wire.varint(Wire.fields(body), if (id == MSG_START_RESPONSE) 3 else 1)}")
                    }
                    else -> {
                        log.i("<- unknown message $id (${body.size} bytes)")
                        log.hex("   msg", body)
                    }
                }
            }
        } catch (e: IOException) {
            if (rfcomm != null) {
                log.w("Bluetooth: channel closed (${e.message})")
                byCar = true
            }
        } finally {
            // Nothing more from the car after the VersionResponse: a strict car (Gearslip: Toyota's) may
            // wait for the Wi-Fi channel type in it. It gets it from the next try on, a start-over too.
            if (mine == startRun && WirelessVersion.wantsChannelType(answered, wentOn, byCar, gaveChannelType, afterDrive = quiet)) {
                WirelessAuto.rememberWantsChannelType(context, device.address)
                log.i("Bluetooth: $name said nothing more after the VersionResponse - from now on it gets the Wi-Fi channel type in it")
            }
            channelEnded(context, mine, byCar)
        }
    }

    /** The car's Bluetooth channel, whether the car opened it ([inbound]) or the phone did, and with a quick call ([KnockPlan]). */
    private class Channel(val socket: BluetoothSocket, val inbound: Boolean, val quick: Boolean = false)

    /** A wait that has ended: a channel the phone's call gets after it is closed, not left open. */
    private object WaitOver

    /**
     * Publishes the Android Auto service on the phone, the way Android Auto itself does, and waits
     * for the car to connect to it. A car looks for the service when the phone connects over
     * Bluetooth, so the driver may have to reconnect the phone from the car's menu.
     *
     * Meanwhile the car's own service is called every [KNOCK_MS]. A car still booting has none yet
     * when its Bluetooth comes up, so the first call failed and the phone only waited, for a car
     * that waits to be called: nothing happened until Disconnect and Connect, which then worked at
     * once. Not while listening after a drive ([quiet]), with the car maybe gone. With the car's
     * Bluetooth there ([carThere]: the first call failed at once), coming while the first call ran
     * (after [arrivedBefore]) or coming during the wait, quicker at first ([KnockPlan]).
     */
    @SuppressLint("MissingPermission")
    private fun waitForCar(
        context: Context, device: BluetoothDevice, name: String, mine: Int, listenMs: Int,
        carThere: Boolean = false, arrivedBefore: Long? = arrivalOf(device.address),
    ): Channel? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        if (mine != startRun) return null
        val server = try {
            adapter.listenUsingRfcommWithServiceRecord("Android Auto", AA_UUID)
        } catch (e: Exception) {
            log.e("Bluetooth: couldn't publish the Android Auto service", e)
            if (!quiet) SessionStatus.failed(Words.get(R.string.wireless_link_bt_publish_failed), "${e.javaClass.simpleName}: ${e.message}")
            return null
        }
        listener = server
        // Stopped in the moment it took to publish: stop() found nothing to close yet.
        if (mine != startRun) {
            runCatching { server.close() }
            if (listener === server) listener = null
            return null
        }
        if (!quiet) connecting(R.string.wireless_link_bt_waiting, shownName(device))
        log.i("Bluetooth: Android Auto service published, waiting ${listenMs / 1000}s for $name")
        // The first channel either way wins; the other is closed. [WaitOver] once the wait has ended.
        val won = java.util.concurrent.atomic.AtomicReference<Any?>(null)
        val knockEvery = if (quiet) QUIET_KNOCK_MS else KNOCK_MS
        // Quick calls only for a car that waits to be called, not after a drive (the usual 15 s), and
        // not once the car closed a channel at its first contact in this start-up.
        val quickOk = !quiet && !slowKnocks && !WirelessAuto.callsPhone(context, device.address)
        val knocker = Thread({
            var knocks = 0
            var lastEnd = SystemClock.elapsedRealtime()
            // The latest arrival of the car's Bluetooth seen, and the one the quick calls count from:
            // null while the car's Bluetooth isn't known to be there.
            var seen = arrivalOf(device.address)
            var arrival = KnockPlan.firstArrival(carThere, arrivedBefore, seen)
            if (!carThere && arrival != null && quickOk) {
                log.i("Bluetooth: $name's Bluetooth came during the first call - calling its service ${KnockPlan.AFTER_ARRIVAL_MS / 1000}s after")
            }
            while (true) {
                try {
                    synchronized(knockWake) {
                        while (true) {
                            if (mine != startRun || listener !== server || won.get() != null) return@Thread
                            // The car's Bluetooth came (again) during the wait (carHere): from it.
                            val latest = arrivalOf(device.address)
                            if (latest != null && latest != seen) {
                                seen = latest
                                if (quickOk) {
                                    arrival = latest
                                    log.i("Bluetooth: $name's Bluetooth came - calling its service ${KnockPlan.AFTER_ARRIVAL_MS / 1000}s after")
                                }
                            }
                            val wait = KnockPlan.next(lastEnd, arrival, quickOk, knockEvery).at - SystemClock.elapsedRealtime()
                            if (wait <= 0) break
                            knockWake.wait(wait)
                        }
                    }
                } catch (e: InterruptedException) {
                    return@Thread
                }
                val quickCall = KnockPlan.next(lastEnd, arrival, quickOk, knockEvery).quick
                knocks++
                val socket = runCatching { device.createRfcommSocketToServiceRecord(AA_UUID) }.getOrNull()
                if (socket == null) {
                    lastEnd = SystemClock.elapsedRealtime()
                    continue
                }
                val calledAt = SystemClock.elapsedRealtime()
                try {
                    socket.connect()
                } catch (e: IOException) {
                    runCatching { socket.close() }
                    lastEnd = SystemClock.elapsedRealtime()
                    // Slow to fail: the car's Bluetooth isn't there (any more), and quick calls would
                    // only page for it - the usual step until it comes again.
                    if (lastEnd - calledAt >= KnockPlan.FAST_FAIL_MS) arrival = null
                    if (knocks == 1) {
                        val again = if (KnockPlan.next(lastEnd, arrival, quickOk, knockEvery).quick) {
                            "every ${KnockPlan.QUICK_EVERY_MS / 1000}s until ${KnockPlan.QUICK_FOR_MS / 1000}s after its Bluetooth came, then every ${knockEvery / 1000}s"
                        } else {
                            "every ${knockEvery / 1000}s"
                        }
                        log.i("Bluetooth: $name's own service doesn't answer yet (${e.message}) - calling it again $again")
                    }
                    continue
                }
                if (won.compareAndSet(null, Channel(socket, inbound = false, quick = quickCall))) {
                    log.i("Bluetooth: $name's own Android Auto service answered (call ${knocks + 1})")
                    // Ends the wait below.
                    runCatching { server.close() }
                } else {
                    runCatching { socket.close() }
                }
                return@Thread
            }
        }, "mycar-wireless-knock").apply { isDaemon = true; start() }
        return try {
            val accepted = server.accept(listenMs)
            val from = runCatching { accepted.remoteDevice.name }.getOrNull() ?: "a device"
            log.i("Bluetooth: $from connected to the Android Auto service")
            val ours = Channel(accepted, inbound = true)
            if (won.compareAndSet(null, ours)) {
                ours
            } else {
                // The phone's own call got through in the same moment: that one is the channel.
                runCatching { accepted.close() }
                won.get() as Channel
            }
        } catch (e: IOException) {
            // The car's own service answered the phone's call, which closed the wait. Claimed at
            // once: a call that gets through after this finds the wait over and hangs up.
            (won.getAndSet(WaitOver) as? Channel)?.let { return it }
            // Stopped, or replaced by a newer start-up: that one says what's going on.
            if (mine != startRun || listener !== server) return null
            log.w("Bluetooth: no car connected within ${listenMs / 1000}s (${e.message})")
            if (quiet) return null
            SessionStatus.failed(
                R.string.wireless_link_bt_no_car,
                R.string.wireless_link_bt_no_car_detail,
                shownName(device),
            )
            null
        } finally {
            // However the wait ended, a call that gets through from now on hangs up.
            won.compareAndSet(null, WaitOver)
            knocker.interrupt()
            runCatching { server.close() }
            // Only this one's: a newer start-up may have published its own by now.
            if (listener === server) listener = null
        }
    }

    private fun send(output: OutputStream, id: Int, body: ByteArray) {
        val frame = ByteArray(4 + body.size)
        frame[0] = (body.size shr 8).toByte(); frame[1] = body.size.toByte()
        frame[2] = (id shr 8).toByte(); frame[3] = id.toByte()
        body.copyInto(frame, 4)
        synchronized(writeLock) { output.write(frame); output.flush() }
    }

    /**
     * Asks Android for the car's network. The first time, Android shows a "Connect to device"
     * prompt on the phone; the driver has to accept it.
     */
    private fun joinWifi(
        context: Context, output: OutputStream, name: String,
        ssid: String, password: String, bssid: String, ip: String, port: Int, mine: Int,
    ) {
        // Stopped or replaced meanwhile: its service and network request would be no one's.
        if (mine != startRun) return
        joined = true
        // The phone's Wi-Fi off: no app can turn it on, so the car's network never came - a minute's
        // wait, a message about a prompt or a password, and minutes of start-overs. Said at once.
        // Off for good only: one still turning on (a routine that turns it on with the car's
        // Bluetooth) gets its minute, as before.
        val wifiState = context.getSystemService(android.net.wifi.WifiManager::class.java)?.wifiState
        if (wifiState == android.net.wifi.WifiManager.WIFI_STATE_DISABLED || wifiState == android.net.wifi.WifiManager.WIFI_STATE_DISABLING) {
            log.w("Wi-Fi: the phone's Wi-Fi is off - the car's network can't come")
            runCatching { send(output, MSG_CONNECTION_STATUS, Protobuf.varintField(1, -1)) }
            SessionStatus.failed(R.string.wireless_link_wifi_off, R.string.wireless_link_wifi_off_detail)
            // Over, without starting over (it would fail the same way) or saying anything else: the
            // channel's end gives the drive's service back.
            saidWhy = true
            retriesLeft = 0
            runCatching { rfcomm?.close() }
            return
        }
        if (mine == startRun) connecting(R.string.wireless_link_wifi_joining, name)
        val spec = WifiNetworkSpecifier.Builder().setSsid(ssid).apply {
            runCatching { MacAddress.fromString(bssid) }.getOrNull()
                ?.takeIf { it != MacAddress.fromString("00:00:00:00:00:00") }
                ?.let { setBssid(it) }
            if (password.isNotEmpty()) setWpa2Passphrase(password)
        }.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(spec)
            .build()
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(net: Network) {
                log.i("Wi-Fi: joined \"$ssid\"")
                // Real phones report the join over Bluetooth before opening TCP, and some cars
                // only start listening once they hear it.
                val phoneIp = manager.getLinkProperties(net)?.linkAddresses
                    ?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress.orEmpty()
                runCatching {
                    send(output, MSG_START_RESPONSE, Protobuf.stringField(1, phoneIp) + Protobuf.varintField(3, STATUS_SUCCESS))
                    send(output, MSG_CONNECTION_STATUS, Protobuf.varintField(1, STATUS_SUCCESS))
                    log.i("-> StartResponse (phone at $phoneIp), ConnectionStatus: success")
                }.onFailure { log.w("Bluetooth: couldn't report the Wi-Fi join (${it.message})") }
                val run = ++tcpRun
                val cb = this
                Thread({ connectTcp(context, output, name, net, ip, port, run, mine, cb, ssid, bssid) }, "mycar-wireless-tcp").start()
            }

            override fun onUnavailable() {
                if (activeSsid == ssid) activeSsid = null
                if (network === this && tcp == null) giveBackService(context)
                log.w("Wi-Fi: couldn't join \"$ssid\" (declined, out of range, or wrong password)")
                runCatching { send(output, MSG_CONNECTION_STATUS, Protobuf.varintField(1, -1)) }
                // Said only when nothing follows: before a start-over Home turned red for 3 s, as
                // if it were over, and Disconnect went from it.
                if (!startOver(context, mine, "the car's Wi-Fi didn't come")) {
                    SessionStatus.failed(
                        R.string.wireless_link_wifi_failed,
                        R.string.wireless_link_wifi_failed_detail,
                        name,
                    )
                }
            }

            // Android can hold an app's traffic back on a network it has joined (battery or data
            // limits, local-network rules); the packets are then dropped without an error.
            override fun onBlockedStatusChanged(net: Network, blocked: Boolean) {
                log.i("Wi-Fi: Android ${if (blocked) "BLOCKS" else "allows"} myCar's traffic on \"$ssid\"")
            }

            private var capsLogged = false
            override fun onCapabilitiesChanged(net: Network, caps: NetworkCapabilities) {
                if (capsLogged) return
                capsLogged = true
                log.i("Wi-Fi: \"$ssid\" capabilities $caps")
            }

            override fun onLost(net: Network) {
                // The time first: the tries read it once they see the network gone.
                lostAt = SystemClock.elapsedRealtime()
                lostNetwork = net
                if (activeSsid == ssid) activeSsid = null
                // "Picking it by hand in the Wi-Fi list does this" was said at the end of every drive,
                // the car switched off: the tries say it where it can be so (the car's Wi-Fi left).
                log.w("Wi-Fi: lost \"$ssid\" - the car's Wi-Fi went away")
                // Under the drive: for the session report, which tells a car switching off from a drop.
                if (tcp != null && driveNetwork == net) io.github.sirionero.mycar.SessionReport.carWifiLost(lostAt)
            }
        }
        // In the foreground for the request (see [run]): the service started with the channel, given
        // a moment to be up.
        if (!holdService(context, mine)) return
        val until = System.currentTimeMillis() + 3_000
        while (!io.github.sirionero.mycar.SessionService.running && System.currentTimeMillis() < until) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
        if (mine != startRun) return
        network?.let { runCatching { manager.unregisterNetworkCallback(it) } }
        network = callback
        activeSsid = ssid
        // The car started over on the channel: this network is for the new session.
        synchronized(ends) { releaseWithChannel = false }
        manager.requestNetwork(request, callback, WIFI_WAIT_MS)
    }

    /**
     * Run once when the first TCP try fails, to tell "the car ignores us" from "wrong port". A
     * refused port answers at once, so it proves the car is reachable; a timeout on every port
     * means the car drops the phone's traffic, usually because it is still waiting for a step.
     */
    @SuppressLint("MissingPermission")
    private fun probe(context: Context, net: Network, ip: String, port: Int) {
        runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val link = manager?.getLinkProperties(net)
            log.i("probe: phone ${link?.linkAddresses?.joinToString()} on ${link?.interfaceName}, routes ${link?.routes?.joinToString()}")
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            val calls = adapter?.getProfileConnectionState(BluetoothProfile.HEADSET)
            val audio = adapter?.getProfileConnectionState(BluetoothProfile.A2DP)
            log.i("probe: Bluetooth (any device) calls ${profileState(calls)}, media audio ${profileState(audio)}")
            log.i(
                "probe: background data ${manager?.restrictBackgroundStatus} (1 off, 2 allowed, 3 restricted), " +
                    "active network ${manager?.activeNetwork}, car network $net, VPN ${if (vpnActive(context)) "on" else "off"}",
            )
        }.onFailure { log.w("probe: couldn't read the link (${it.message})") }
        // Never the car's own port: a car that just got ready would take the probe for the phone,
        // see it hang up at once, and some stop listening.
        for (p in listOf(5288, 5277, 80, 443).filter { it != port }) {
            val result = try {
                net.socketFactory.createSocket().use { it.connect(InetSocketAddress(ip, p), 1_500) }
                "open"
            } catch (e: SocketTimeoutException) {
                "no answer"
            } catch (e: ConnectException) {
                if (e.message?.contains("ECONNREFUSED") == true) "refused (car is reachable)" else "failed: ${e.message}"
            } catch (e: Exception) {
                "failed: ${e.javaClass.simpleName} ${e.message}"
            }
            log.i("probe: $ip:$p $result")
        }
    }

    /** Whether a VPN carries this app's traffic. Android then refuses to bind sockets to the car's network (EPERM). */
    private fun vpnActive(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)

    private fun profileState(state: Int?) = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "connected"
        BluetoothProfile.STATE_CONNECTING -> "connecting"
        BluetoothProfile.STATE_DISCONNECTED -> "not connected"
        else -> "unknown"
    }

    /**
     * Another Wi-Fi network the phone has with an address in the car's /24, or null: only a Wi-Fi
     * can be the car's, joined by hand. Whether it is, [judgeOther] says.
     */
    private fun otherOnCarSubnet(context: Context, ip: String, except: Network): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val prefix = ip.substringBeforeLast('.') + "."
        return manager.allNetworks.firstOrNull { candidate ->
            if (candidate == except) return@firstOrNull false
            val caps = manager.getNetworkCapabilities(candidate) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                manager.getLinkProperties(candidate)?.linkAddresses
                    ?.any { it.address is java.net.Inet4Address && it.address.hostAddress?.startsWith(prefix) == true } == true
        }
    }

    /**
     * Whether [other], a Wi-Fi of the phone in the car's address range, is the car's own network
     * ([CarNetwork]), said once in the log. Its name only as the car's or another: the driver's home
     * Wi-Fi has no place in logs that get shared.
     */
    private fun judgeOther(context: Context, other: Network, carSsid: String, carBssid: String): CarNetwork.Verdict {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val validated = runCatching {
            manager?.getNetworkCapabilities(other)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrNull()
        val wifi = manager?.let { wifiDetails(it, other) }
        val name = CarNetwork.name(wifi?.ssid, wifi?.bssid, carSsid, carBssid)
        val verdict = CarNetwork.judge(validated, name)
        val internet = when (validated) {
            true -> "reaches the Internet"
            false -> "no Internet"
            null -> "Internet unknown"
        }
        val named = when (name) {
            CarNetwork.Name.SAME -> "the car's name"
            CarNetwork.Name.OTHER -> "another name"
            CarNetwork.Name.UNKNOWN -> "name hidden from myCar"
        }
        log.i(
            "TCP: the phone has another Wi-Fi in the car's address range ($internet, $named) - " + when (verdict) {
                CarNetwork.Verdict.CAR -> "it's the car's, joined by hand: the tries start on it"
                CarNetwork.Verdict.MAYBE -> "maybe the car's: tried every other time, after the car's own network"
                CarNetwork.Verdict.NOT_CAR -> "not the car's: the tries go over the car's own network only"
            },
        )
        return verdict
    }

    /**
     * [network]'s Wi-Fi details as Android shows them to myCar: through a listening request that
     * asks for them (FLAG_INCLUDE_LOCATION_INFO), whose answers for the networks already up come
     * within moments. Name and address are hidden without precise location for myCar or with
     * location off; null if nothing came in time.
     */
    private fun wifiDetails(manager: ConnectivityManager, network: Network): WifiInfo? {
        val found = java.util.concurrent.atomic.AtomicReference<WifiInfo?>(null)
        val came = java.util.concurrent.CountDownLatch(1)
        val listen = object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(net: Network, caps: NetworkCapabilities) {
                if (net != network) return
                found.set(caps.transportInfo as? WifiInfo)
                came.countDown()
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        if (runCatching { manager.registerNetworkCallback(request, listen) }.isFailure) return null
        runCatching { came.await(WIFI_DETAILS_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS) }
        runCatching { manager.unregisterNetworkCallback(listen) }
        return found.get()
    }

    /** Home's line once a VPN is known to keep myCar off the car's Wi-Fi: [detail], with the car's name. */
    private class VpnSaid(@StringRes val detail: Int, val car: String)

    /** A new start-up, or a drive on the car's Wi-Fi: what the last one saw of a VPN no longer holds. */
    private fun forgetVpn() {
        vpnRefusedAt = 0L
        vpnUnanswered = 0
        vpnAnswered = false
        vpnSaid = null
    }

    /**
     * The start-up's stage under "Connecting", on Home and in the drive service's notification - or,
     * once a VPN is known to keep myCar off the car's Wi-Fi ([vpnSaid]), that, for the rest of the
     * start-up: a car that asks for its Wi-Fi again put "joining" back over it within seconds.
     */
    private fun connecting(@StringRes stage: Int, vararg args: Any) {
        val vpn = vpnSaid
        if (vpn != null) SessionStatus.connecting(vpn.detail, vpn.car) else SessionStatus.connecting(stage, *args)
    }

    /** The words for a VPN that keeps myCar off the car's Wi-Fi: Tailscale by its name (Tailnet). */
    @StringRes
    private fun vpnDetail(context: Context): Int =
        if (Tailnet.vpnIsTailscale(context)) R.string.wireless_link_tailscale_detail else R.string.wireless_link_vpn_detail

    /**
     * A try over the phone's ordinary route failed, Android having refused myCar the car's network.
     * Once that route is known not to reach the car ([VpnBlock]), Home and the drive service's
     * notification say a VPN keeps myCar off the car's Wi-Fi, and what to do: once, for the rest of
     * the start-up. The tries go on - they get through as soon as the VPN lets myCar go.
     */
    private fun ordinaryTryFailed(context: Context, error: String?, verdict: CarNetwork.Verdict?, name: String, run: Int, mine: Int) {
        // An older start-up's tries, ending: not this one's to count.
        if (mine != startRun) return
        val carWifi = verdict == CarNetwork.Verdict.CAR || verdict == CarNetwork.Verdict.MAYBE
        if (VpnBlock.answered(error, carWifi)) vpnAnswered = true else vpnUnanswered++
        val refusedAt = vpnRefusedAt
        val since = if (refusedAt == 0L) null else SystemClock.elapsedRealtime() - refusedAt
        if (run != tcpRun || !VpnBlock.due(since, vpnUnanswered, vpnAnswered, said = vpnSaid != null)) return
        val said = VpnSaid(vpnDetail(context), name)
        vpnSaid = said
        log.w(
            "TCP: Android keeps myCar off the car's network and the ordinary route doesn't reach the car " +
                "($vpnUnanswered tries unanswered in ${(since ?: 0L) / 1000}s) - " +
                "${if (said.detail == R.string.wireless_link_tailscale_detail) "Tailscale" else "a VPN"} carries myCar: telling the driver",
        )
        SessionStatus.connecting(said.detail, said.car)
    }

    private fun connectTcp(
        context: Context, output: OutputStream, name: String, net: Network, ip: String, port: Int, run: Int, mine: Int,
        cb: ConnectivityManager.NetworkCallback, carSsid: String, carBssid: String,
    ) {
        connecting(R.string.wireless_link_wifi_reaching, name)
        lostNetwork = null
        val joinedAt = SystemClock.elapsedRealtime()
        // The first of the car's refusals in a row on its own network, 0 without one (TcpTries.stalePort).
        var refusedSince = 0L
        // The car refused the port it gave: it's reachable, and the port is gone.
        var stale = false
        var last: Exception? = null
        // Set once Android refuses to bind to the car's network, which a VPN causes. Plain sockets
        // then go through the VPN, and VPNs that let local traffic past still reach the car.
        var bindRefused = false
        // The phone's other Wi-Fi networks in the car's address range met so far, and whether each is the car's.
        val judged = HashMap<Network, CarNetwork.Verdict>()
        // Why the tries ended before their minute (TcpTries); null: the minute is up.
        var cut: String? = null
        var saidAfterLoss = false
        // For about a minute, not only twelve tries: a port no one listens on yet is refused at
        // once, so the tries a second apart were over in some 12 s, and the car, listening moments
        // later, found no one - Disconnect and Connect then worked at once.
        val until = SystemClock.elapsedRealtime() + TCP_WINDOW_MS
        var attempt = -1
        while (++attempt < TCP_TRIES || SystemClock.elapsedRealtime() < until) {
            if (run != tcpRun) {
                log.i("TCP: stopping the tries on the old network - the car started over")
                return
            }
            // The VPN that kept myCar off the car's network no longer carries it - turned off, or myCar
            // left out of it, as the driver was told: the car's network again, not the ordinary route
            // for the rest of the minute.
            if (bindRefused && !vpnActive(context)) {
                bindRefused = false
                log.i("TCP: no VPN carries myCar any more - back to the car's network")
            }
            // The phone's own Wi-Fi on the car's network wins when it is the car's (CarNetwork): that
            // is how Android Auto itself connects, and a Pixel 6 passed pings but no TCP over the side
            // connection Android opens for myCar's request. One that may be the car's is tried every
            // other time, after the car's own network; a home or office Wi-Fi with addresses like the
            // car's (both 192.168.1.x) never: the first try went there, and an adapter that wants the
            // phone within moments of its Wi-Fi had closed it by the second.
            val other = runCatching { otherOnCarSubnet(context, ip, net) }.getOrNull()
            val verdict = other?.let { judged.getOrPut(it) { judgeOther(context, it, carSsid, carBssid) } }
            // A network Android has dropped refuses every bind (EPERM). If the driver joined the car's
            // Wi-Fi by hand, it's left, and the tries go over it; behind a VPN the ordinary route
            // goes on, as before. Anything else doesn't lead to the car: the tries spent the rest of
            // their minute on the home Wi-Fi or mobile data, and the start-up now starts over.
            val lost = lostNetwork == net
            if (lost) {
                val channelOpen = synchronized(ends) { !channelOver }
                val next = TcpTries.afterLoss(
                    carLeft = verdict == CarNetwork.Verdict.CAR, bindRefused = bindRefused,
                    newerJoin = network !== cb, channelOpen = channelOpen,
                    sinceLossMs = SystemClock.elapsedRealtime() - lostAt,
                )
                when (next) {
                    TcpTries.AfterLoss.GO_ON -> if (!saidAfterLoss && !bindRefused) {
                        saidAfterLoss = true
                        log.i(
                            "TCP: Android dropped the car's network, but the phone is on the car's Wi-Fi itself - " +
                                "picking it by hand in the Wi-Fi list does this; the remaining tries go over it",
                        )
                    }
                    TcpTries.AfterLoss.WAIT -> {
                        if (!saidAfterLoss) {
                            saidAfterLoss = true
                            log.i("TCP: the car's network went with its Bluetooth channel open - giving the car a moment to ask for it again")
                        }
                        // Not a try: the car's network is gone, and a try on the ordinary route takes up to 5 s.
                        attempt--
                        Thread.sleep(250)
                        continue
                    }
                    TcpTries.AfterLoss.YIELD -> {
                        log.i("TCP: the car asked for its Wi-Fi again - the tries on the network it dropped stop")
                        return
                    }
                    TcpTries.AfterLoss.STOP -> {
                        cut = "the car's Wi-Fi went before any TCP"
                        log.w("TCP: Android dropped the car's network and nothing else leads to the car - stopping the tries")
                        break
                    }
                }
            }
            // An adapter that restarted while the phone joined its Wi-Fi refuses the port it gave
            // before: a new start-up gets a new one, instead of a minute of refusals on the road.
            val now = SystemClock.elapsedRealtime()
            val carBack = car?.address?.let { arrivalOf(it) }
            val closedAt = channelClosedAt.takeIf { it != 0L }
            if (!bindRefused && TcpTries.stalePort(closedAt, joinedAt, refusedSince.takeIf { it != 0L }, carBack, now)) {
                stale = true
                cut = "the car refuses the port it gave before it restarted"
                val since = if (carBack != null && closedAt != null && carBack > closedAt) ", its Bluetooth back ${(carBack - closedAt) / 1000}s after" else ""
                log.w(
                    "TCP: $ip:$port refused for ${(now - refusedSince) / 1000}s, and the car closed its Bluetooth channel " +
                        "before the Wi-Fi came$since - that port is gone, starting over for a new one",
                )
                break
            }
            // The try goes over the ordinary route because Android refused the car's network (VpnBlock).
            val behindVpn = bindRefused
            // The try goes over the car's own network, or its Wi-Fi joined by hand: a refusal there is the car's.
            var viaCar = false
            try {
                val viaOther = when (verdict) {
                    CarNetwork.Verdict.CAR -> lost || attempt % 2 == 0
                    CarNetwork.Verdict.MAYBE -> !lost && attempt % 2 == 1
                    else -> false
                }
                // Once Android has refused a bind - a VPN that apps can't get past, like Tailscale,
                // refuses one to the main Wi-Fi as well - the ordinary route is the only one left.
                // The network the try is bound to; null: the phone's ordinary route.
                val via = when {
                    bindRefused -> null
                    viaOther && other != null -> other
                    lost -> null
                    else -> net
                }
                val socket = via?.socketFactory?.createSocket() ?: Socket()
                viaCar = via != null && (via == net || verdict == CarNetwork.Verdict.CAR)
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(ip, port), 5_000)
                // Stopped while it connected (the cable went in meanwhile): handing it on would
                // close the session that just started on the cable.
                if (run != tcpRun) {
                    log.i("TCP: connected after a stop - closing it")
                    socket.close()
                    return
                }
                synchronized(ends) {
                    driveNetwork = via
                    tcp = socket
                    drove = true
                    // The drive takes the service over: the screen stops it when the drive ends.
                    ownsService = false
                }
                // Through, the VPN let go or never in the way: a later join of this start-up starts afresh.
                forgetVpn()
                log.i("TCP: connected to $ip:$port (try ${attempt + 1})")
                val start = onConnected
                if (start == null) {
                    log.w("TCP: connected, but nothing is waiting for the socket")
                    socket.close()
                    io.github.sirionero.mycar.SessionService.stop(context, io.github.sirionero.mycar.SessionService.STARTUP)
                } else {
                    start(socket)
                }
                return
            } catch (e: Exception) {
                last = e
                // Refused at once, the tries can be dozens: the first few and every tenth say so.
                if (attempt < 3 || attempt % 10 == 9) log.w("TCP: try ${attempt + 1} to $ip:$port failed: ${e.message}")
                if (!bindRefused && e.message?.contains("EPERM") == true) {
                    bindRefused = true
                    if (mine == startRun && vpnRefusedAt == 0L) vpnRefusedAt = SystemClock.elapsedRealtime()
                    log.i("TCP: Android won't let myCar use the car's network directly; trying the phone's ordinary route")
                }
                if (behindVpn) ordinaryTryFailed(context, e.message, verdict, name, run, mine)
                if (viaCar) {
                    refusedSince = if (e.message?.contains("ECONNREFUSED") != true) 0L
                    else refusedSince.takeIf { it != 0L } ?: SystemClock.elapsedRealtime()
                }
                if (attempt == 0) probe(context, net, ip, port)
                Thread.sleep(1_000)
            }
        }
        if (cut == null) log.e("TCP: gave up on $ip:$port", last)
        // Stopped meanwhile (Disconnect, the cable, a tap), or a newer join that will say its own.
        if (run != tcpRun) return
        giveBackService(context)
        // Nothing follows: the car's network goes, or the phone stayed on it (off its home Wi-Fi)
        // and the channel's end, finding the request still there, kept it until the next tap.
        // Said first: the channel's end then adds nothing over the reason.
        fun endJoin(noStartOver: Boolean) = synchronized(ends) {
            if (network !== cb) return@synchronized
            if (noStartOver) retriesLeft = 0
            saidWhy = true
            releaseNetwork(context)
        }
        // A car that refused the port is reachable: neither a VPN nor Android's local-network rule is in the way.
        if (!stale && vpnActive(context)) {
            SessionStatus.failed(
                R.string.wireless_link_vpn,
                vpnSaid?.detail ?: vpnDetail(context),
                name,
            )
            endJoin(noStartOver = true)
            return
        }
        // Android 17 keeps myCar off local networks until it's allowed, the car's Wi-Fi among them:
        // every try timed out, and no start-over mends that. Said as it is, with what to allow.
        if (!stale && !io.github.sirionero.mycar.LocalNetwork.allowed(context)) {
            log.w("TCP: Android doesn't let myCar reach local networks (Nearby devices) - the car's Wi-Fi is one")
            SessionStatus.failed(R.string.wireless_link_no_connection, R.string.wireless_link_local_network_detail)
            endJoin(noStartOver = true)
            return
        }
        // Not behind a VPN, which no start-over gets past. Said only when nothing follows.
        if (startOver(context, mine, cut ?: "no TCP to the car")) return
        SessionStatus.failed(
            R.string.wireless_link_no_connection,
            R.string.wireless_link_no_connection_detail,
            name, "$ip:$port",
        )
        endJoin(noStartOver = false)
    }
}
