package io.github.sirionero.mycar

/** What the car said of the certificate myCar presented. */
sealed interface CertVerdict {
    /** AuthComplete 0. */
    data object Accepted : CertVerdict

    /**
     * The car turned it down. [hard]: it said so (a TLS alert, AuthComplete -2 or -3); otherwise it
     * only hung up while checking it, which a loose cable does too. [why] goes to the log.
     */
    data class Refused(val hard: Boolean, val why: String) : CertVerdict

    /** AuthComplete -23/-24: the car's date is outside the certificate's, its clock most likely. */
    data object Clock : CertVerdict
}

/**
 * Which certificate myCar presents next, after the car's verdict on this one ([CertRotation] keeps
 * the state and acts on it). No Android here, so every sequence can be tried on the JVM.
 *
 * A catalog certificate the car turns down gives way to the next one on the phone, in catalog
 * order, until each has been turned down once; then myCar goes back to the first and stops
 * switching until the driver starts over. One a car has accepted isn't dropped for a hang-up (a
 * loose cable does that too), and the driver's own certificate is never swapped.
 */
object CertRotationPolicy {

    data class State(
        /** The catalog certificate a car accepted last. */
        val accepted: String? = null,
        /** Turned down since the last acceptance, or since the driver started over. */
        val refused: Set<String> = emptySet(),
        /** Every one was turned down: no more switching until the driver starts over. */
        val exhausted: Boolean = false,
    )

    enum class Notice { NONE, SWITCHED, NO_SPARE, EXHAUSTED, OWN_REFUSED, NO_CERT, CLOCK }

    /** [switchTo]: the catalog certificate to put in use now, when it's another one. */
    data class Outcome(val state: State, val notice: Notice, val switchTo: String? = null)

    /**
     * [presented]: the catalog id of the certificate the car was shown; null for one from elsewhere,
     * [own] (imported, or pushed over adb) or not (none at all: a self-signed one).
     * [present]: the catalog certificates on the phone, in the order to try them.
     */
    fun decide(state: State, verdict: CertVerdict, presented: String?, own: Boolean, present: List<String>): Outcome =
        when (verdict) {
            CertVerdict.Accepted -> Outcome(State(accepted = presented ?: state.accepted), Notice.NONE)
            CertVerdict.Clock -> Outcome(state, Notice.CLOCK)
            is CertVerdict.Refused -> refused(state, verdict, presented, own, present)
        }

    private fun refused(
        state: State,
        verdict: CertVerdict.Refused,
        presented: String?,
        own: Boolean,
        present: List<String>,
    ): Outcome {
        if (presented == null) return Outcome(state, if (own) Notice.OWN_REFUSED else Notice.NO_CERT)
        // Taken by a car before: a hang-up is likelier the cable than a change of mind.
        if (!verdict.hard && presented == state.accepted) return Outcome(state, Notice.NONE)
        if (state.exhausted) return Outcome(state, Notice.EXHAUSTED)
        val refused = state.refused + presented
        val next = present.firstOrNull { it !in refused }
        return when {
            next != null -> Outcome(state.copy(refused = refused), Notice.SWITCHED, next)
            present.size < 2 -> Outcome(state.copy(refused = refused), Notice.NO_SPARE)
            else -> Outcome(
                state.copy(refused = refused, exhausted = true),
                Notice.EXHAUSTED,
                present.first().takeIf { it != presented },
            )
        }
    }

    /** The driver starts over: every certificate may be tried again, from the first. */
    fun startOver(state: State): State = state.copy(refused = emptySet(), exhausted = false)
}
