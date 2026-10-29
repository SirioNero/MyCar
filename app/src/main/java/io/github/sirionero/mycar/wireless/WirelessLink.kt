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
import android.net.wifi.WifiNetworkSpecifier
import android.provider.Settings
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.SessionStatus
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
    private const val TCP_TRIES = 12 // about a minute: a car's projection listener can start well after its Wi-Fi
    private const val WIFI_WAIT_MS = 60_000

    /** Set by the activity: starts a session on the car's socket. */
    @Volatile var onConnected: ((Socket) -> Unit)? = null

    @Volatile private var rfcomm: BluetoothSocket? = null
    @Volatile private var listener: BluetoothServerSocket? = null
    @Volatile private var tcp: Socket? = null
    @Volatile private var network: ConnectivityManager.NetworkCallback? = null
    @Volatile private var worker: Thread? = null

    /** A start-up, or a session's Bluetooth channel, is under way. */
    val busy: Boolean get() = worker?.isAlive == true

    /** The car of the current start-up, for [reachedCar]. */
    @Volatile private var car: BluetoothDevice? = null
    /** Set when Android drops the car's network mid-try, so the rest don't bind to a dead one. */
    @Volatile private var lostNetwork: Network? = null
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

    /** Guards [channelOver] and [releaseWithChannel], set from the session's end and the channel's. */
    private val ends = Any()
    /** The current start-up's Bluetooth channel is over. */
    private var channelOver = false
    /** The car ended the session on Wi-Fi with the channel still open: the car's network goes with the channel. */
    private var releaseWithChannel = false

    /** Paired Bluetooth devices, cars or not: the driver picks theirs. Needs BLUETOOTH_CONNECT. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<BluetoothDevice> =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
            ?.sortedBy { it.name ?: it.address }
            .orEmpty()

    @SuppressLint("MissingPermission")
    fun name(device: BluetoothDevice): String = device.name ?: device.address

    /** [byItself]: the car came up over Bluetooth and myCar connects with no tap (see [WirelessAuto]). */
    fun start(context: Context, device: BluetoothDevice, byItself: Boolean = false) {
        stop(context)
        synchronized(ends) {
            channelOver = false
            releaseWithChannel = false
        }
        val app = context.applicationContext
        car = device
        // Taken here, not in the thread: a start-up stopped before its thread ran is old already.
        val mine = startRun
        worker = Thread({
            // A test feature: anything unexpected ends the attempt, not the app.
            try {
                run(app, device, mine, byItself)
            } catch (e: Exception) {
                log.e("wireless start-up failed", e)
                SessionStatus.failed(Words.get(R.string.wireless_link_failed), "${e.javaClass.simpleName}: ${e.message}")
            }
        }, "mycar-wireless").apply { isDaemon = true; start() }
    }

    /** The car's picture came up over Wi-Fi: it works without the cable, so it can connect by itself next time. */
    fun reachedCar(context: Context) {
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
                return
            }
            releaseNetwork(context)
        }
    }

    /**
     * The Bluetooth channel is over. If the car had ended the session on Wi-Fi first, the request
     * for its network goes now: it stayed until the next tap, with Android looking for the car.
     */
    private fun channelEnded(context: Context, mine: Int) {
        synchronized(ends) {
            if (mine != startRun) return
            channelOver = true
            if (releaseWithChannel && tcp == null) releaseNetwork(context)
        }
    }

    private fun releaseNetwork(context: Context) {
        network?.let { cb ->
            runCatching { context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(cb) }
        }
        network = null
        activeSsid = null
    }

    /** Closes the Bluetooth channel, the socket and the Wi-Fi request. Safe to call any time. */
    fun stop(context: Context) {
        tcpRun++
        startRun++
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
            SessionStatus.disconnected()
        }
    }

    /** A stable id for this phone as this app sees it, standing in for the serial a real phone sends. */
    @SuppressLint("HardwareIds")
    private fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "mycar"

    @SuppressLint("MissingPermission")
    private fun run(context: Context, device: BluetoothDevice, mine: Int, byItself: Boolean) {
        val name = name(device)
        log.i("--- wireless start-up with $name (${device.address}) ---")
        SessionStatus.connecting(R.string.wireless_link_bt_reaching, name)
        val listenMs = if (byItself) AUTO_LISTEN_MS else LISTEN_MS
        // A car that called the phone last time does it again as soon as its Bluetooth is up: the
        // service is published at once rather than after a try at the car's own, which took a few
        // seconds to fail - long enough for the car to look, find nothing, and give up.
        val callsUs = WirelessAuto.callsPhone(context, device.address)
        var inbound = callsUs
        val socket = if (callsUs) {
            log.i("Bluetooth: $name called the phone last time - waiting for it at once")
            val accepted = waitForCar(context, name, mine, listenMs)
            if (accepted == null) {
                // It didn't, this time: next time the phone tries the car first again.
                if (mine == startRun) WirelessAuto.rememberCalls(context, device.address, false)
                return
            }
            accepted
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
                name,
            )
            return
        } catch (e: IOException) {
            // Most factory head units publish no service of their own: they look for the phone's
            // and connect to it, so wait for the car instead.
            log.i("Bluetooth: $name has no Android Auto service to call (${e.message}); waiting for it to call us")
            // Replaced while its connect hung: the newer start-up does the waiting.
            if (mine != startRun) return
            inbound = true
            waitForCar(context, name, mine, listenMs) ?: return
        }
        if (mine != startRun) {
            log.i("Bluetooth: a newer start-up took over - closing this one's channel")
            runCatching { socket.close() }
            return
        }
        rfcomm = socket
        if (inbound != callsUs) WirelessAuto.rememberCalls(context, device.address, inbound)
        log.i("Bluetooth: channel open")
        SessionStatus.connecting(R.string.wireless_link_bt_talking, name)

        val input = DataInputStream(socket.inputStream)
        val output = socket.outputStream
        var target: Pair<String, Int>? = null
        // Wi-Fi details that came before the address, joined once the StartRequest brings it.
        var waitingJoin: ((String, Int) -> Unit)? = null
        try {
            while (!Thread.currentThread().isInterrupted) {
                val length = input.readUnsignedShort()
                val id = input.readUnsignedShort()
                val body = ByteArray(length).also { input.readFully(it) }
                when (id) {
                    MSG_VERSION_REQUEST -> {
                        log.i("<- VersionRequest")
                        log.hex("   version", body)
                        // Laid out the way a real phone's reply is (aa-proxy-rs and LIVI both
                        // parse it from real phones): 1 and 2 the version, echoing the car's;
                        // 3 a serial; 4 the status; 6 the device's ids.
                        val f = Wire.fields(body)
                        val serial = deviceId(context)
                        val ids = Protobuf.stringField(1, serial) + Protobuf.stringField(2, UUID.randomUUID().toString())
                        val reply = Protobuf.varintField(1, Wire.varint(f, 1) ?: 1) +
                            Protobuf.varintField(2, Wire.varint(f, 2) ?: 0) +
                            Protobuf.stringField(3, serial) +
                            Protobuf.varintField(4, STATUS_SUCCESS) +
                            Protobuf.varint(((6 shl 3) or 2).toLong()) + Protobuf.varint(ids.size.toLong()) + ids
                        send(output, MSG_VERSION_RESPONSE, reply)
                        log.i("-> VersionResponse")
                        log.hex("   reply", reply)
                    }
                    MSG_START_REQUEST -> {
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
                        val f = Wire.fields(body)
                        fun text(n: Int) = f.firstOrNull { it.number == n && it.wireType == 2 }?.bytes?.decodeToString().orEmpty()
                        val ssid = text(1)
                        val password = text(2)
                        val bssid = text(3)
                        val security = Wire.varint(f, 4) ?: -1
                        log.i("<- InfoResponse: ssid=\"$ssid\" bssid=$bssid security=$security password=${if (password.isEmpty()) "none" else "(${password.length} chars)"}")
                        // ZLink head units send the Wi-Fi details again once the stream is up. Asking
                        // Android for the network again releases the one carrying the stream, which
                        // aborts it a second in; a phone running Android Auto carries on. A car
                        // starting over once its session ended still has the network asked for anew.
                        if (ssid == activeSsid && tcp != null) {
                            log.i("InfoResponse again for \"$ssid\", which the drive is on - ignoring it")
                            continue
                        }
                        val join = { ip: String, port: Int -> joinWifi(context, output, name, ssid, password, bssid, ip, port) }
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
            if (rfcomm != null) log.w("Bluetooth: channel closed (${e.message})")
        } finally {
            channelEnded(context, mine)
        }
    }

    /**
     * Publishes the Android Auto service on the phone, the way Android Auto itself does, and waits
     * for the car to connect to it. A car looks for the service when the phone connects over
     * Bluetooth, so the driver may have to reconnect the phone from the car's menu.
     */
    @SuppressLint("MissingPermission")
    private fun waitForCar(context: Context, name: String, mine: Int, listenMs: Int): BluetoothSocket? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        if (mine != startRun) return null
        val server = try {
            adapter.listenUsingRfcommWithServiceRecord("Android Auto", AA_UUID)
        } catch (e: Exception) {
            log.e("Bluetooth: couldn't publish the Android Auto service", e)
            SessionStatus.failed(Words.get(R.string.wireless_link_bt_publish_failed), "${e.javaClass.simpleName}: ${e.message}")
            return null
        }
        listener = server
        // Stopped in the moment it took to publish: stop() found nothing to close yet.
        if (mine != startRun) {
            runCatching { server.close() }
            if (listener === server) listener = null
            return null
        }
        SessionStatus.connecting(R.string.wireless_link_bt_waiting, name)
        log.i("Bluetooth: Android Auto service published, waiting ${listenMs / 1000}s for $name")
        return try {
            server.accept(listenMs).also {
                val from = runCatching { it.remoteDevice.name }.getOrNull() ?: "a device"
                log.i("Bluetooth: $from connected to the Android Auto service")
            }
        } catch (e: IOException) {
            // Stopped, or replaced by a newer start-up: that one says what's going on.
            if (mine != startRun || listener !== server) return null
            log.w("Bluetooth: no car connected within ${listenMs / 1000}s (${e.message})")
            SessionStatus.failed(
                R.string.wireless_link_bt_no_car,
                R.string.wireless_link_bt_no_car_detail,
                name,
            )
            null
        } finally {
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
        ssid: String, password: String, bssid: String, ip: String, port: Int,
    ) {
        SessionStatus.connecting(R.string.wireless_link_wifi_joining, name)
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
                Thread({ connectTcp(context, output, name, net, ip, port, run) }, "mycar-wireless-tcp").start()
            }

            override fun onUnavailable() {
                if (activeSsid == ssid) activeSsid = null
                log.w("Wi-Fi: couldn't join \"$ssid\" (declined, out of range, or wrong password)")
                runCatching { send(output, MSG_CONNECTION_STATUS, Protobuf.varintField(1, -1)) }
                SessionStatus.failed(
                    R.string.wireless_link_wifi_failed,
                    R.string.wireless_link_wifi_failed_detail,
                    name,
                )
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
                lostNetwork = net
                if (activeSsid == ssid) activeSsid = null
                log.w(
                    "Wi-Fi: lost \"$ssid\" - Android dropped the car's network. Picking it by hand " +
                        "in the Wi-Fi list does this; the remaining tries go over the phone's own Wi-Fi",
                )
            }
        }
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

    /** Another network the phone has with an address in the car's /24, or null. */
    private fun mainOnCarNetwork(context: Context, ip: String, except: Network): Network? {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val prefix = ip.substringBeforeLast('.') + "."
        return manager.allNetworks.firstOrNull { candidate ->
            candidate != except && manager.getLinkProperties(candidate)?.linkAddresses
                ?.any { it.address is java.net.Inet4Address && it.address.hostAddress?.startsWith(prefix) == true } == true
        }
    }

    private fun connectTcp(context: Context, output: OutputStream, name: String, net: Network, ip: String, port: Int, run: Int) {
        SessionStatus.connecting(R.string.wireless_link_wifi_reaching, name)
        lostNetwork = null
        var last: Exception? = null
        // Set once Android refuses to bind to the car's network, which a VPN causes. Plain sockets
        // then go through the VPN, and VPNs that let local traffic past still reach the car.
        var bindRefused = false
        repeat(TCP_TRIES) { attempt ->
            if (run != tcpRun) {
                log.i("TCP: stopping the tries on the old network - the car started over")
                return
            }
            try {
                // A network Android has dropped refuses every bind (EPERM). If the driver joined the
                // car's Wi-Fi by hand, the phone's ordinary route reaches it, so use that instead.
                // The phone's main Wi-Fi on the car's network wins: that is how Android Auto itself
                // connects, and a Pixel 6 passed pings but no TCP over the side connection Android
                // opens for myCar's request.
                val main = mainOnCarNetwork(context, ip, net)
                if (main != null && attempt == 0) log.i("TCP: the phone's main Wi-Fi is on the car's network, using it")
                // Once Android has refused a bind - a VPN that apps can't get past, like Tailscale,
                // refuses one to the main Wi-Fi as well - the ordinary route is the only one left.
                // Every other try goes over the car's own network all the same: a home Wi-Fi with
                // addresses like the car's (both 192.168.1.x) looked like the car's to the test above,
                // and every try went there.
                val socket = when {
                    lostNetwork == net || bindRefused -> Socket()
                    main != null && attempt % 2 == 0 -> main.socketFactory.createSocket()
                    else -> net.socketFactory.createSocket()
                }
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(ip, port), 5_000)
                // Stopped while it connected (the cable went in meanwhile): handing it on would
                // close the session that just started on the cable.
                if (run != tcpRun) {
                    log.i("TCP: connected after a stop - closing it")
                    socket.close()
                    return
                }
                synchronized(ends) { tcp = socket }
                log.i("TCP: connected to $ip:$port (try ${attempt + 1})")
                val start = onConnected
                if (start == null) {
                    log.w("TCP: connected, but nothing is waiting for the socket")
                    socket.close()
                } else {
                    start(socket)
                }
                return
            } catch (e: Exception) {
                last = e
                log.w("TCP: try ${attempt + 1} to $ip:$port failed: ${e.message}")
                if (!bindRefused && e.message?.contains("EPERM") == true) {
                    bindRefused = true
                    log.i("TCP: Android won't let myCar use the car's network directly; trying the phone's ordinary route")
                }
                if (attempt == 0) probe(context, net, ip, port)
                Thread.sleep(1_000)
            }
        }
        log.e("TCP: gave up on $ip:$port", last)
        if (vpnActive(context)) {
            SessionStatus.failed(
                R.string.wireless_link_vpn,
                R.string.wireless_link_vpn_detail,
                name,
            )
            return
        }
        SessionStatus.failed(
            R.string.wireless_link_no_connection,
            R.string.wireless_link_no_connection_detail,
            name, "$ip:$port",
        )
    }
}
