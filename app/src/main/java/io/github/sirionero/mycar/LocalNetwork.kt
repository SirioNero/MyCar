package io.github.sirionero.mycar

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.net.URI

/**
 * The home network - Home Assistant at http://192.168.1.10:8123, Music Assistant at
 * homeassistant.local - as Android 17 guards it. An app made for Android 17, as myCar is, reaches
 * it only with the local network permission, one of the "Nearby devices" ones: without it, its
 * connections there just time out and .local names don't resolve, its web pages included. Apps
 * made for older versions keep it without asking, which is why the Plancia and Xonos apps reached
 * addresses that Plancia and Xonos inside myCar didn't.
 */
object LocalNetwork {

    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Whether this Android asks for it at all: from Android 17. */
    val asked: Boolean get() = Build.VERSION.SDK_INT >= 37

    /** Whether myCar may reach the home network now. */
    fun allowed(context: Context): Boolean =
        !asked || context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Whether some of [urls] lead into the home network, which myCar may not reach now. */
    fun blocks(context: Context, urls: List<String>): Boolean = !allowed(context) && urls.any(::isLocal)

    /**
     * Whether Home Assistant or Music Assistant at [urls] may be out of myCar's reach now: on
     * Android 17, without the permission, any of their addresses can be at home. Android goes by
     * the address a name leads to, and homeassistant.lan, ha.fritz.box or one of the driver's own
     * domains that the router answers with 192.168... look no different from the internet here.
     */
    fun mayBlock(context: Context, urls: List<String>): Boolean = !allowed(context) && urls.any { it.isNotBlank() }

    /** Asking when the phone's screen opens, once a run: the settings show the rest. */
    @Volatile var askedAtStart = false

    /** What to say when the home network is what's missing, in the car and on the phone alike. */
    const val HINT = "Android keeps myCar off your home network: on the phone, allow myCar's Nearby devices permission."

    /**
     * Whether [url] points into a local network: a .local name, a name without dots, or a private,
     * link-local or loopback address. A public name that the home router answers with a private
     * address can't be told apart from here.
     */
    fun isLocal(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host }.getOrNull()
            ?.removeSurrounding("[", "]")?.lowercase()?.trimEnd('.')
        if (host.isNullOrEmpty()) return false
        // Tailscale's addresses go through its VPN, which Android 17 doesn't guard (fd7a:... is a
        // unique local address only in name).
        if (Tailnet.isTailnetAddress(host)) return false
        if (host.endsWith(".local") || ('.' !in host && ':' !in host)) return true
        return if (':' in host) localIpv6(host) else privateIpv4(host)
    }

    private fun privateIpv4(host: String): Boolean {
        val parts = host.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts
        return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
    }

    /** Loopback, link-local (fe80::/10) or unique local (fc00::/7). */
    private fun localIpv6(host: String): Boolean {
        val ip = host.substringBefore('%')
        return ip == "::1" || ip.take(3) in setOf("fe8", "fe9", "fea", "feb") || ip.take(2) in setOf("fc", "fd")
    }
}
