package io.github.sirionero.mycar.bridge

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import io.github.sirionero.mycar.MyCarLog
import okhttp3.Dns
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import kotlin.concurrent.thread

/*
 * OkHttp's way through an app's bridge, for Home Assistant (Plancia) and Music Assistant (Xonos):
 * a name the phone can't look up from outside the VPN (a MagicDNS name) is left for the app to
 * look up ([BridgeDns]), and the socket ([BridgedSocket]) is joined to the app's connection.
 * Everything else - another server, myCar inside the VPN - goes the ordinary way. Plancia's page
 * comes the same way through BridgeProxy.
 */

/** Names going through [bridge] aren't looked up here: the app does it, inside the VPN. */
class BridgeDns(private val bridge: AppBridge, private val system: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        // The name rides along with an address that's never dialled: BridgedSocket reads the name.
        if (Bridges.through(bridge, hostname)) listOf(InetAddress.getByAddress(hostname, PLACEHOLDER)) else system.lookup(hostname)
}

/** TEST-NET-1's first address, which no network has: a name [BridgeDns] left for the app. */
private val PLACEHOLDER = byteArrayOf(192.toByte(), 0, 2, 1)

/**
 * 127.0.0.1, for the listeners on the phone itself. Not InetAddress.getLoopbackAddress(), which on
 * Android is ::1: a WebView told to use a proxy at 127.0.0.1 found nothing there.
 */
internal val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

/** Sockets that go through [bridge] when they should (see [Bridges.through]), and the ordinary way otherwise. */
class BridgeSocketFactory(private val bridge: AppBridge) : SocketFactory() {
    override fun createSocket(): Socket = BridgedSocket(bridge)

    override fun createSocket(host: String, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket = createSocket(host, port)

    override fun createSocket(host: InetAddress, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port)) }

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
        createSocket(address, port)
}

/**
 * A real socket, so OkHttp - and TLS on top - work with it as with any other. Going through the
 * bridge, it's connected to a listener on the phone itself, open for that one connection, whose
 * other end is the app's: what it sends goes to the server through the app, and back.
 */
internal class BridgedSocket(private val bridge: AppBridge) : Socket() {

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        val address = endpoint as? InetSocketAddress
        val host = address?.hostString
        val context = Bridges.context
        // A name BridgeDns left for the app goes to it, whatever changed since; an address as written is decided now.
        val leftForTheApp = address?.address?.address?.contentEquals(PLACEHOLDER) == true
        if (address == null || host == null || context == null || !(leftForTheApp || Bridges.through(bridge, host))) {
            super.connect(endpoint, timeout)
            return
        }
        val tunnel = try {
            bridge.open(context, host, address.port, timeout)
        } catch (e: IOException) {
            logRefused(bridge, host, address.port, e)
            throw e
        }
        val inner = try {
            ServerSocket(0, 1, LOOPBACK).use { listener ->
                listener.soTimeout = ACCEPT_MS
                super.connect(listener.localSocketAddress, timeout)
                val accepted = listener.accept()
                // Only this socket's own connection: anything else on the phone that got in first is turned away.
                if (accepted.port != localPort) {
                    runCatching { accepted.close() }
                    throw IOException("the bridge's local connection wasn't myCar's")
                }
                accepted
            }
        } catch (e: IOException) {
            runCatching { tunnel.close() }
            bridge.closed()
            runCatching { close() }
            throw e
        }
        relay(inner, tunnel) { bridge.closed() }
    }

    private companion object {
        const val ACCEPT_MS = 5_000
    }
}

private val log = MyCarLog.tagged("BRIDGE")

/** Copies between [inner] and the app's [tunnel] both ways until one closes, then [onEnd] once. */
internal fun relay(inner: Socket, tunnel: ParcelFileDescriptor, onEnd: () -> Unit) {
    val closed = AtomicBoolean(false)
    val ends = AtomicInteger(0)
    fun closeAll() {
        if (closed.compareAndSet(false, true)) {
            runCatching { inner.close() }
            runCatching { tunnel.close() }
            onEnd()
        }
    }
    fun ended() {
        if (ends.incrementAndGet() == 2) closeAll()
    }
    thread(name = "mycar-bridge-out", isDaemon = true) {
        // From myCar to the server; once myCar has said all it will, the server hears so.
        try {
            copy(inner.getInputStream(), FileOutputStream(tunnel.fileDescriptor))
            Os.shutdown(tunnel.fileDescriptor, OsConstants.SHUT_WR)
        } catch (e: Exception) {
            closeAll()
        } finally {
            ended()
        }
    }
    thread(name = "mycar-bridge-in", isDaemon = true) {
        // From the server to myCar; once the server closes, myCar reads the end.
        try {
            copy(FileInputStream(tunnel.fileDescriptor), inner.getOutputStream())
            inner.shutdownOutput()
        } catch (e: Exception) {
            closeAll()
        } finally {
            ended()
        }
    }
}

/** Copies between two sockets both ways until one closes, then closes both and [onEnd] once. */
internal fun relay(a: Socket, b: Socket, onEnd: () -> Unit) {
    val closed = AtomicBoolean(false)
    val ends = AtomicInteger(0)
    fun closeAll() {
        if (closed.compareAndSet(false, true)) {
            runCatching { a.close() }
            runCatching { b.close() }
            onEnd()
        }
    }
    fun pipe(from: Socket, to: Socket, name: String) = thread(name = name, isDaemon = true) {
        try {
            copy(from.getInputStream(), to.getOutputStream())
            runCatching { to.shutdownOutput() }
        } catch (e: Exception) {
            closeAll()
        } finally {
            if (ends.incrementAndGet() == 2) closeAll()
        }
    }
    pipe(a, b, "mycar-bridge-a")
    pipe(b, a, "mycar-bridge-b")
}

private fun copy(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) return
        output.write(buffer, 0, n)
    }
}

/** For the log: a connection through [bridge] failed, and why. */
internal fun logRefused(bridge: AppBridge, host: String, port: Int, e: Exception) {
    log.w("through ${bridge.packageName} to $host:$port: ${e.message}")
}
