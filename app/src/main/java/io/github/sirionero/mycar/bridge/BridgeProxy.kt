package io.github.sirionero.mycar.bridge

import android.os.SystemClock
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import io.github.sirionero.mycar.MyCarLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executor
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * Home Assistant's page through the Plancia app, for Plancia's WebView, whose connections OkHttp
 * doesn't make. The WebView is told (androidx.webkit's ProxyController) to reach Home Assistant -
 * its addresses only, every other site as before - through a SOCKS5 proxy on the phone itself
 * (127.0.0.1, a port of its own), and each connection the proxy is asked for goes through the
 * app's bridge when it should ([Bridges.through]), the ordinary way otherwise. The page stays on
 * Home Assistant's own address: its TLS and certificate, and the name a proxy in front of it
 * answers to, all as they are.
 *
 * The proxy opens Home Assistant's addresses as written in Plancia, nothing else. Any app on the
 * phone could reach its port while it's open, and through it Home Assistant's: as any app inside
 * Tailscale - by default, all of them - already can.
 */
object BridgeProxy {

    private val log = MyCarLog.tagged("BRIDGE")

    /** The WebView's answers, taken on whichever thread they come. */
    private val direct = Executor { it.run() }

    /** An address of Home Assistant's: a host as a connection names it, and a port. */
    private data class Way(val host: String, val port: Int)

    /** What the proxy opens, and through which app: read by its threads. */
    @Volatile private var ways: Set<Way> = emptySet()
    @Volatile private var bridge: AppBridge? = null

    /** The last connection the app turned down: for which host, why, and when. */
    private class Refusal(val host: String, val why: String, val at: Long)

    @Volatile private var lastRefusal: Refusal? = null

    // The rest on the main thread.
    private val turns = Mutex()
    private var listener: ServerSocket? = null

    /** The addresses the WebView sends to the proxy, as last asked of it (also while it's still applying). */
    private var applied: Set<Way> = emptySet()
    private var epoch = 0
    private var toldUnsupported = false

    /**
     * The page's way to Home Assistant at [urls]: through [bridge] when [bridged], as it is otherwise.
     * Returns once the WebView has it, so a page loaded next goes that way.
     */
    suspend fun route(bridge: AppBridge, urls: List<String>, bridged: Boolean): Unit = turns.withLock {
        val wanted = if (bridged) urls.mapNotNull(::wayOf).toSet() else emptySet()
        if (wanted.isEmpty()) {
            if (applied.isNotEmpty()) {
                applied = emptySet()
                withTimeoutOrNull(APPLY_MS) { clearOverride() }
                log.i("Home Assistant's page goes the ordinary way again")
            }
            ways = emptySet()
            runCatching { listener?.close() }
            listener = null
            return@withLock
        }
        if (!supported()) {
            if (!toldUnsupported) log.w("this WebView can't send only Home Assistant's page through a proxy: no bridge for the page")
            toldUnsupported = true
            return@withLock
        }
        val mine = epoch
        val open = listener?.takeUnless { it.isClosed }
            ?: withContext(Dispatchers.IO) { runCatching { listen() }.onFailure { log.w("no proxy for the page: ${it.message}") }.getOrNull() }
            ?: return@withLock
        if (mine != epoch) {
            // The drive ended meanwhile.
            if (open !== listener) runCatching { open.close() }
            return@withLock
        }
        listener = open
        this.bridge = bridge
        ways = wanted
        if (wanted == applied) return@withLock
        // Counted as applied from now: a drive that ends meanwhile clears it after.
        applied = wanted
        try {
            if (withTimeoutOrNull(APPLY_MS) { setOverride(wanted, open.localPort) } == null) {
                log.w("the WebView didn't confirm the proxy for the page")
            }
            log.i("Home Assistant's page (${wanted.joinToString { "${it.host}:${it.port}" }}) through ${bridge.packageName}")
        } catch (e: IllegalArgumentException) {
            if (mine == epoch) applied = emptySet()
            log.w("the WebView turned the proxy for the page down: ${e.message}")
        }
    }

    /** The drive's over: the WebView back to no proxy, and the proxy closed. On the main thread. */
    fun stop() {
        epoch++
        ways = emptySet()
        bridge = null
        lastRefusal = null
        runCatching { listener?.close() }
        listener = null
        if (applied.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            applied = emptySet()
            runCatching { ProxyController.getInstance().clearProxyOverride(direct) {} }
        }
    }

    /** Why the app turned down a connection to [host] just now, in myCar's words; null when it didn't. */
    fun refusal(host: String?): String? {
        val last = lastRefusal ?: return null
        if (host == null || normal(host) != last.host || SystemClock.elapsedRealtime() - last.at > FRESH_MS) return null
        return last.why
    }

    private fun supported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS)

    /** Only Home Assistant's addresses to the proxy - "reverse bypass" - and every other one as before. */
    private suspend fun setOverride(wanted: Set<Way>, port: Int) = suspendCancellableCoroutine<Unit> { done ->
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) ||
            !WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE_REVERSE_BYPASS)
        ) {
            done.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val config = ProxyConfig.Builder()
            .addProxyRule("socks5://127.0.0.1:$port")
            .apply { wanted.forEach { addBypassRule(if (':' in it.host) "[${it.host}]:${it.port}" else "${it.host}:${it.port}") } }
            .setReverseBypassEnabled(true)
            .build()
        ProxyController.getInstance().setProxyOverride(config, direct) { if (done.isActive) done.resume(Unit) }
    }

    private suspend fun clearOverride() = suspendCancellableCoroutine<Unit> { done ->
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            done.resume(Unit)
            return@suspendCancellableCoroutine
        }
        ProxyController.getInstance().clearProxyOverride(direct) { if (done.isActive) done.resume(Unit) }
    }

    private fun listen(): ServerSocket {
        val server = ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())
        thread(name = "mycar-bridge-proxy", isDaemon = true) {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                thread(name = "mycar-bridge-proxy-conn", isDaemon = true) { serve(client) }
            }
        }
        log.i("a proxy for Home Assistant's page at 127.0.0.1:${server.localPort}")
        return server
    }

    /** One connection the page asks for: SOCKS5 (RFC 1928) without sign-in, CONNECT only. */
    private fun serve(client: Socket) {
        var handed = false
        try {
            client.soTimeout = HANDSHAKE_MS
            client.tcpNoDelay = true
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()
            // The greeting: version 5 and the ways to sign in the page knows; none is the one taken.
            if (input.readUnsignedByte() != VERSION) return
            val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            if (NO_SIGN_IN.toByte() !in methods) {
                output.write(byteArrayOf(VERSION.toByte(), NO_METHOD.toByte()))
                return
            }
            output.write(byteArrayOf(VERSION.toByte(), NO_SIGN_IN.toByte()))
            // The request: what to do, and where to.
            if (input.readUnsignedByte() != VERSION) return
            val command = input.readUnsignedByte()
            input.readUnsignedByte()
            val named = when (input.readUnsignedByte()) {
                IPV4 -> InetAddress.getByAddress(ByteArray(4).also { input.readFully(it) }).hostAddress
                NAME -> String(ByteArray(input.readUnsignedByte()).also { input.readFully(it) }, Charsets.US_ASCII)
                IPV6 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) }).hostAddress
                else -> null
            } ?: return
            val host = normal(named)
            val port = input.readUnsignedShort()
            if (command != CONNECT) return reply(output, NOT_SUPPORTED)
            val through = bridge
            val context = Bridges.context
            if (Way(host, port) !in ways || through == null || context == null) {
                log.w("the page asked for $host:$port, which isn't Home Assistant's: turned down")
                return reply(output, NOT_ALLOWED)
            }
            client.soTimeout = 0
            if (Bridges.through(through, host)) {
                val tunnel = try {
                    through.open(context, host, port, CONNECT_MS)
                } catch (e: IOException) {
                    logRefused(through, host, port, e)
                    lastRefusal = Refusal(host, e.message.orEmpty(), SystemClock.elapsedRealtime())
                    return reply(output, REFUSED)
                }
                try {
                    reply(output, SUCCEEDED)
                } catch (e: IOException) {
                    runCatching { tunnel.close() }
                    through.closed()
                    return
                }
                handed = true
                relay(client, tunnel) { through.closed() }
            } else {
                val server = Socket()
                try {
                    server.connect(InetSocketAddress(host, port), CONNECT_MS)
                    server.tcpNoDelay = true
                    reply(output, SUCCEEDED)
                } catch (e: IOException) {
                    runCatching { server.close() }
                    runCatching { reply(output, UNREACHABLE) }
                    return
                }
                handed = true
                relay(client, server) {}
            }
        } catch (e: IOException) {
            // The page went away mid-way, or spoke something else.
        } finally {
            if (!handed) runCatching { client.close() }
        }
    }

    private fun reply(output: OutputStream, code: Int) {
        // Bound to 0.0.0.0:0: the page doesn't need to know.
        output.write(byteArrayOf(VERSION.toByte(), code.toByte(), 0, IPV4.toByte(), 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun wayOf(url: String): Way? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.let(::normal)?.ifEmpty { null } ?: return null
        val tls = uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("wss", ignoreCase = true)
        return Way(host, if (uri.port > 0) uri.port else if (tls) 443 else 80)
    }

    /** A host as the page and Plancia's settings both write it: lower case, no brackets, no final dot. */
    private fun normal(host: String): String = host.removeSurrounding("[", "]").lowercase().trimEnd('.')

    private const val VERSION = 5
    private const val NO_SIGN_IN = 0
    private const val NO_METHOD = 0xFF
    private const val CONNECT = 1
    private const val IPV4 = 1
    private const val NAME = 3
    private const val IPV6 = 4
    private const val SUCCEEDED = 0
    private const val NOT_ALLOWED = 2
    private const val UNREACHABLE = 4
    private const val REFUSED = 5
    private const val NOT_SUPPORTED = 7

    private const val BACKLOG = 16
    private const val HANDSHAKE_MS = 10_000
    private const val CONNECT_MS = 10_000
    private const val APPLY_MS = 5_000L
    private const val FRESH_MS = 30_000L
}
