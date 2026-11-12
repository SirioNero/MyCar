package io.github.sirionero.mycar.wireless

/**
 * When to tell the driver that a VPN keeps myCar off the car's Wi-Fi (WirelessLink.connectTcp). No
 * Android here: VpnBlockTest.
 *
 * Inside a VPN that apps can't get past (Tailscale's is one), Android refuses myCar a socket bound to
 * the car's network (EPERM), and the tries go over the phone's ordinary route instead. That route goes
 * into the VPN, or past it to the phone's main network: it leads to the car only when the phone's own
 * Wi-Fi is the car's. The start-up said so only after a whole minute of tries, and a car that asks for
 * its Wi-Fi again starts the tries anew: in a tester's log (an Ottocast adapter) the refusal came back
 * over and over, and the driver was left without a reason. Now it's said once per start-up, counted
 * over all its tries: [SAY_AFTER_MS] after Android first refused the car's network, with
 * [SAY_AFTER_TRIES] tries over the ordinary route that the car didn't answer. When the car answers on
 * that route, nothing is said: it works.
 */
internal object VpnBlock {

    /** At least this long since Android first refused myCar the car's network in this start-up... */
    const val SAY_AFTER_MS = 10_000L

    /** ...and at least this many tries over the ordinary route that the car didn't answer. */
    const val SAY_AFTER_TRIES = 2

    /**
     * Whether the car answered a try over the ordinary route that failed: a refused port answers at
     * once. That route leads to the car only when the phone's own Wi-Fi is the car's, or may be
     * ([carWifiOnPhone]: CarNetwork's CAR or MAYBE, joined by hand); otherwise a refusal comes from
     * something else - the VPN's own stack, or a device at home with the same address. A timeout, an
     * unreachable network or a denied permission got nowhere.
     */
    fun answered(error: String?, carWifiOnPhone: Boolean): Boolean =
        carWifiOnPhone && error?.contains("ECONNREFUSED") == true

    /**
     * Whether to say it now. [sinceRefusedMs]: since Android first refused myCar the car's network in
     * this start-up, null if it never did; [unanswered]: the tries over the ordinary route since then
     * that the car didn't answer; [answered]: it answered one; [said]: said already in this start-up.
     */
    fun due(sinceRefusedMs: Long?, unanswered: Int, answered: Boolean, said: Boolean): Boolean =
        sinceRefusedMs != null && !answered && !said &&
            unanswered >= SAY_AFTER_TRIES && sinceRefusedMs >= SAY_AFTER_MS
}
