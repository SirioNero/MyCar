package io.github.sirionero.mycar.bridge

import android.content.Context
import android.net.ConnectivityManager
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.Tailnet
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Which of myCar's connections go through an app's bridge ([AppBridge]): Plancia's through the
 * Plancia app, Xonos's through the Xonos app. Only with myCar left out of the phone's VPN - as
 * Tailscale has to leave it for the car's Wi-Fi - and only to an address on the tailnet, or to a
 * home address while the phone is away from home (a Tailscale subnet route): everything else
 * goes the ordinary way. And Xonos's always when its Music Assistant is the one on the phone itself,
 * inside the Xonos app: the binding keeps the app, and the server with it, awake for the trip.
 */
object Bridges {

    val plancia = AppBridge("it.plancia.ha", "Plancia", "Home Assistant")
    val xonos = AppBridge("it.xonos.ma", "Xonos", "Music Assistant")

    @Volatile var context: Context? = null
        private set

    /** Idempotent; any context will do. */
    fun init(context: Context) {
        if (this.context == null) this.context = context.applicationContext
    }

    /** The car preview's (debug only): every connection to the server through the app, VPN or not. */
    @Volatile var forced = false

    /** Whether myCar's connection to [host] goes through [bridge] now. */
    fun through(bridge: AppBridge, host: String): Boolean {
        val context = context ?: return false
        if (!bridge.available(context)) return false
        if (forced) return true
        if (bridge === xonos && onThePhone(host)) return true
        if (Tailnet.reach(context) != Tailnet.Reach.VPN_LEAVES_MYCAR_OUT) return false
        return Tailnet.hostOnTailnet(context, host) || awayFromHome(context, host)
    }

    /** The phone itself: where the Xonos app with Music Assistant inside answers (127.0.0.1:8095). */
    private fun onThePhone(host: String): Boolean {
        val name = host.removeSurrounding("[", "]").lowercase()
        return name == "localhost" || name == "::1" || name.startsWith("127.")
    }

    /**
     * A home address (private IPv4) on none of the phone's networks now: away from home, it's
     * reached only along a route of the VPN's - and myCar is left out of the VPN.
     */
    private fun awayFromHome(context: Context, host: String): Boolean {
        // An address as written, never a name: looking one up here would be the network's work.
        if (!host.all { it.isDigit() || it == '.' } || !LocalNetwork.isLocal("http://$host")) return false
        val ip = runCatching { InetAddress.getByName(host) }.getOrNull() as? Inet4Address ?: return false
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        @Suppress("DEPRECATION") // allNetworks: the car's Wi-Fi and the phone's own, whichever is default
        val networks = runCatching { manager.allNetworks.toList() }.getOrDefault(emptyList())
        return networks.none { network ->
            manager.getLinkProperties(network)?.linkAddresses.orEmpty().any { link ->
                val own = link.address as? Inet4Address ?: return@any false
                sameSubnet(own.address, ip.address, link.prefixLength)
            }
        }
    }

    private fun sameSubnet(a: ByteArray, b: ByteArray, prefix: Int): Boolean {
        var bits = prefix
        for (i in a.indices) {
            if (bits <= 0) return true
            val mask = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
            if ((a[i].toInt() and mask) != (b[i].toInt() and mask)) return false
            bits -= 8
        }
        return true
    }
}
