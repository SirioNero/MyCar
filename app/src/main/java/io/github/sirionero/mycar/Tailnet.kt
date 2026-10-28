package io.github.sirionero.mycar

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

/**
 * Tailscale - or another VPN - as myCar sees it. Many drivers reach Home Assistant and Music
 * Assistant through Tailscale, with a MagicDNS name like http://umbrel:8123: that name and the
 * 100.x addresses behind it exist only inside the VPN, and a VPN carries only the apps it's set to
 * carry. Drivers often leave myCar out of it, because a VPN that apps can't get past keeps myCar
 * off the car's Wi-Fi (see [io.github.sirionero.mycar.wireless.WirelessLink]) - and then Plancia
 * and Xonos inside myCar lose the tailnet while the Plancia and Xonos apps, inside it, keep it.
 * This tells which case it is, so the car and the phone say the real reason, not "can't be reached".
 */
object Tailnet {

    /** Where myCar's connections go now. */
    enum class Reach {
        /** Through a VPN. */
        IN_VPN,

        /** Not through the VPN the phone has on: it leaves myCar out. */
        VPN_LEAVES_MYCAR_OUT,

        /** No VPN on the phone. */
        NO_VPN,

        /** No network for myCar at all: none on the phone, or Android holding myCar's back. */
        NO_NETWORK,
    }

    /** How a connection failed, as far as the network goes. */
    enum class Failure { NO_SUCH_HOST, TIMEOUT, UNREACHABLE, OTHER }

    fun reach(context: Context): Reach {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Reach.NO_VPN
        val mine = cm.activeNetwork ?: return Reach.NO_NETWORK
        if (cm.getNetworkCapabilities(mine)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return Reach.IN_VPN
        return if (vpn(cm) != null) Reach.VPN_LEAVES_MYCAR_OUT else Reach.NO_VPN
    }

    /** The phone's VPN, whether it carries myCar or not. Android lists it either way. */
    @Suppress("DEPRECATION") // allNetworks: the only way to see a VPN that doesn't carry this app
    private fun vpn(cm: ConnectivityManager): Network? = runCatching {
        cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }.getOrNull()

    private fun vpnLink(context: Context): LinkProperties? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val vpn = vpn(cm) ?: return null
        return runCatching { cm.getLinkProperties(vpn) }.getOrNull()
    }

    /** Whether the VPN is Tailscale: its DNS is MagicDNS's 100.100.100.100, or an address of a tailnet. */
    private fun isTailscale(link: LinkProperties?): Boolean =
        link != null && (link.dnsServers.any { isTailnetAddress(it.hostAddress.orEmpty()) } ||
            link.linkAddresses.any { isTailnetAddress(it.address.hostAddress.orEmpty()) })

    /**
     * Whether [url] looks like an address inside a tailnet: a name without dots (MagicDNS), a
     * *.ts.net name, a name in one of the VPN's search domains, or a tailnet address (100.64.0.0/10,
     * fd7a:115c:a1e0::/48).
     */
    fun looksTailnet(url: String, searchDomains: List<String> = emptyList()): Boolean {
        val host = host(url) ?: return false
        if (isTailnetAddress(host)) return true
        if (':' in host) return false
        if ('.' !in host || host.endsWith(".ts.net")) return isName(host)
        return searchDomains.any { it.isNotEmpty() && host.endsWith(".$it") }
    }

    /** The host of [url], lower case, without brackets; null if there is none. */
    fun host(url: String): String? = runCatching { URI(url.trim()).host }.getOrNull()
        ?.removeSurrounding("[", "]")?.lowercase()?.trimEnd('.')?.ifEmpty { null }

    private fun isName(host: String): Boolean = host.any { it.isLetter() }

    /** 100.64.0.0/10 - which holds MagicDNS's 100.100.100.100 - or fd7a:115c:a1e0::/48. */
    fun isTailnetAddress(ip: String): Boolean {
        val address = ip.substringBefore('%').lowercase()
        if (address.startsWith("fd7a:115c:a1e0:")) return true
        val parts = address.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        return a == 100 && b in 64..127 && parts.drop(2).all { it.toIntOrNull() in 0..255 }
    }

    /** What went wrong, from an exception and the ones that caused it. */
    fun failureOf(error: Throwable?): Failure {
        var e = error
        var depth = 0
        while (e != null && depth++ < 8) {
            when (e) {
                is UnknownHostException -> return Failure.NO_SUCH_HOST
                is SocketTimeoutException -> return Failure.TIMEOUT
                is ConnectException, is NoRouteToHostException -> return Failure.UNREACHABLE
                is InterruptedIOException -> if (e.message?.contains("timeout", true) == true) return Failure.TIMEOUT
            }
            failureOf(e.message).let { if (it != Failure.OTHER) return it }
            e = e.cause
        }
        return Failure.OTHER
    }

    /** What went wrong, from an error's words: Android's and OkHttp's, which are in English. */
    fun failureOf(message: String?): Failure {
        val m = message?.lowercase() ?: return Failure.OTHER
        return when {
            "unable to resolve host" in m || "no address associated" in m || "name resolution" in m -> Failure.NO_SUCH_HOST
            "timeout" in m || "timed out" in m -> Failure.TIMEOUT
            "failed to connect" in m || "econnrefused" in m || "unreachable" in m || "enetunreach" in m -> Failure.UNREACHABLE
            else -> Failure.OTHER
        }
    }

    /**
     * Whether every one of [urls] is on a tailnet while the phone's VPN leaves myCar out: none of
     * them can answer myCar, however many times it tries. Known without trying any.
     */
    fun outOfReach(context: Context, urls: List<String>): Boolean {
        if (urls.isEmpty() || reach(context) != Reach.VPN_LEAVES_MYCAR_OUT) return false
        val link = vpnLink(context)
        val tailscale = isTailscale(link)
        val domains = domainsOf(link)
        return urls.all { onTailnetBehind(it, tailscale, domains) }
    }

    /**
     * Whether [url] is on the tailnet behind the VPN up now: a tailnet address whatever the VPN; a
     * name (MagicDNS's without dots, *.ts.net, a search domain's) only when the VPN is Tailscale.
     * Another VPN that leaves myCar out (NetGuard, a firewall, Proton) leaves a name such as
     * http://homeassistant:8123 to the router, which myCar reaches by itself: taken for Tailscale's,
     * the app took Plancia's place at once, or every connection went through it.
     */
    internal fun onTailnetBehind(url: String, tailscale: Boolean, searchDomains: List<String>): Boolean =
        if (tailscale) looksTailnet(url, searchDomains) else host(url)?.let(::isTailnetAddress) == true

    /** Whether some of [urls] look like addresses on a tailnet (see [looksTailnet]). */
    fun onTailnet(context: Context, urls: List<String>): Boolean {
        val domains = searchDomains(context)
        return urls.any { looksTailnet(it, domains) }
    }

    /** Whether [host] - a name or an address, as a connection asks for it - is on a tailnet (see [looksTailnet]). */
    fun hostOnTailnet(context: Context, host: String): Boolean {
        val bare = host.removeSurrounding("[", "]")
        val link = vpnLink(context)
        return onTailnetBehind(if (':' in bare) "http://[$bare]" else "http://$bare", isTailscale(link), domainsOf(link))
    }

    private fun searchDomains(context: Context): List<String> = domainsOf(vpnLink(context))

    private fun domainsOf(link: LinkProperties?): List<String> =
        link?.domains.orEmpty().split(' ', ',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /**
     * Why [urls] can't be reached, when Tailscale or another VPN explains it; null when it doesn't.
     * Not for errors that aren't the network's (a wrong token, a refused sign-in).
     */
    fun explain(context: Context, urls: List<String>, failure: Failure): String? {
        if (failure == Failure.OTHER) return null
        val link = vpnLink(context)
        val domains = link?.domains.orEmpty().split(' ', ',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val inTailnet = urls.filter { looksTailnet(it, domains) }
        val name = (inTailnet.firstOrNull() ?: urls.firstOrNull())?.let(::host) ?: return null
        val tailscale = isTailscale(link)
        return when (reach(context)) {
            Reach.VPN_LEAVES_MYCAR_OUT -> when {
                tailscale && inTailnet.isNotEmpty() ->
                    "Tailscale leaves myCar out, so “$name” on your tailnet is out of its reach. " +
                        "In Tailscale on the phone: Settings, App split tunneling, let myCar in - and connect the car " +
                        "with the cable: inside a VPN, Android keeps myCar off the car's Wi-Fi."
                !tailscale && (inTailnet.isNotEmpty() || urls.any(LocalNetwork::isLocal)) ->
                    "The phone's VPN leaves myCar out, so “$name” is out of its reach. Let myCar into the VPN in its settings."
                else -> null
            }
            Reach.NO_VPN -> if (inTailnet.isNotEmpty() && failure != Failure.UNREACHABLE) {
                "“$name” isn't found from here. If it's a Tailscale name, turn Tailscale on: myCar reaches it only through Tailscale."
            } else {
                null
            }
            Reach.IN_VPN -> if (tailscale && failure == Failure.NO_SUCH_HOST && '.' !in name) {
                "Tailscale doesn't know “$name”: check the name, and that Use Tailscale DNS is on in Tailscale."
            } else {
                null
            }
            Reach.NO_NETWORK -> "No network for myCar right now."
        }
    }

    /**
     * The words for a connection to [urls] that failed with [failure]: the VPN's reason, or Android
     * 17 keeping myCar off the home network ([homeNetwork]), or else [fallback] - always the latter
     * for errors that aren't the network's.
     */
    fun why(context: Context, urls: List<String>, failure: Failure, fallback: String, homeNetwork: String = LocalNetwork.HINT): String =
        // Through an app's bridge, that app said what went wrong (bridge/AppBridge): said as it is.
        if (failure == Failure.OTHER || io.github.sirionero.mycar.bridge.AppBridge.THROUGH in fallback) {
            fallback
        } else {
            explain(context, urls, failure)
                ?: if (LocalNetwork.mayBlock(context, urls)) homeNetwork else fallback
        }

    /** For the log: where myCar's connections go, and the VPN's DNS and search domains if there is one. */
    fun describe(context: Context): String {
        val link = vpnLink(context) ?: return reach(context).name
        return "${reach(context).name} (VPN dns ${link.dnsServers.joinToString { it.hostAddress.orEmpty() }}, domains ${link.domains})"
    }
}
