package io.github.sirionero.mycar.host

/**
 * Which of an app's "no"s to myCar last, so that the app counts as broken until it changes (see
 * [HostRefusals]), and how the car says so. Pure, to be tried on the JVM.
 *
 * Most failures pass: a slow start, a crash, a state error. Two last as long as that copy of the app:
 * - a modified copy of an app that refuses myCar on its own (Waze re-signed by Morphe, see
 *   [KnownApps.modified]) that turned myCar down before taking it as its host, whatever said no: its
 *   patch leaves myCar out, and the car says what to add to it;
 * - any app whose own host check said no at the handshake: the Car App Library's HostValidator, which
 *   lets in only the hosts the app's developer chose (ABRP, A Better Routeplanner, for one). Only
 *   they can let myCar in, and the car says that.
 */
object RefusalRule {

    enum class Kind { MODIFIED_COPY, HOST_CHECK }

    /** The step whose failure carries the host check's answer: the handshake. */
    const val HANDSHAKE = "onHandshakeCompleted"

    /**
     * How the host check says no, by version of the Car App Library: "Unknown host '<host>',
     * uid:<uid>" up to 1.7 (an IllegalArgumentException), "Unrecognized host with uid <uid>" and "The
     * host is not allowed to call this service" after (SecurityExceptions).
     */
    private val SAID_NO = listOf("Unknown host", "Unrecognized host", "The host is not allowed")

    /**
     * Whether [step] failed because the app's host check said no, from [firstLine], the first line of
     * the stack trace the app answered with, as "java.lang.IllegalArgumentException: Unknown host
     * 'io.github.sirionero.mycar', uid:10339". Only at the handshake: the same words at another step,
     * or any other answer there (the app's API level, its own state), are a failure like the others.
     */
    fun hostCheckSaidNo(step: String, firstLine: String?): Boolean {
        if (step != HANDSHAKE || firstLine == null) return false
        val message = firstLine.substringAfterLast("Exception: ", firstLine).trim()
        return SAID_NO.any { message.startsWith(it) }
    }

    /**
     * Whether a failure lasts, and how the car says so; null for one said as it is. [rejected]: it
     * ended the app's start as a refusal; [accepted]: the app had already taken myCar as its host, so
     * what failed after that isn't a refusal of the host; [modified]: a modified copy of an app that
     * refuses myCar on its own; [hostCheck]: see [hostCheckSaidNo].
     */
    fun kind(rejected: Boolean, accepted: Boolean, modified: Boolean, hostCheck: Boolean): Kind? = when {
        !rejected || accepted -> null
        // The copy's own words first, whatever said no: what to add to its patch.
        modified -> Kind.MODIFIED_COPY
        hostCheck -> Kind.HOST_CHECK
        else -> null
    }

    /**
     * How a copy remembered as refusing myCar refused, for the car to say it again where the app
     * isn't started by itself: [modified], as a modified copy; any other was remembered only for its
     * host check.
     */
    fun remembered(modified: Boolean): Kind = if (modified) Kind.MODIFIED_COPY else Kind.HOST_CHECK
}
