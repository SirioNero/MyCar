package io.github.sirionero.mycar.speech

import kotlin.math.sqrt

/**
 * Whether the driver is still talking, from how loud the microphone is. In a language other than
 * English the end of a phrase can't be left to Kroko alone: it knows English only, and in Italian it
 * can go a second without writing a word while the driver is still speaking. The phrase was cut
 * there, and the final model heard half of it. Kroko's end now counts once the microphone is quiet
 * too: back near the quietest it has been since the press, which is the car's own noise.
 */
internal class SpeechEnd {
    /** The quietest the microphone has been: the car's noise. Rises slowly, so one dip doesn't fix it. */
    private var floor = Float.MAX_VALUE
    private var heardMs = 0
    private var loudMs = 0
    private var quietFor = 0

    /** The driver has said something: loud for a while at a stretch, not a bump or two. */
    var spoke = false
        private set

    /** How long the microphone has been at the car's noise. */
    val quietMs: Int get() = quietFor

    /** The next [n] [samples], [ms] long. */
    fun add(samples: FloatArray, n: Int, ms: Int) {
        if (n <= 0) return
        var sum = 0.0
        for (i in 0 until n) sum += samples[i].toDouble() * samples[i]
        val level = sqrt(sum / n).toFloat()
        // The start is no measure of anything: the cue's tail as the car plays it, a microphone that
        // fades in (which held the floor so low that the car's noise never counted as quiet).
        heardMs += ms
        if (heardMs <= SETTLE_MS) return
        // Exact silence (a car's microphone starting up sends zeros) is no measure of its noise.
        if (level >= SILENCE) floor = if (level < floor) level else floor * FLOOR_RISE
        loudMs = if (level > floor * SPEECH_RATIO) loudMs + ms else 0
        if (loudMs >= SPOKE_MS) spoke = true
        quietFor = if (level < floor * QUIET_RATIO || level < SILENCE) quietFor + ms else 0
    }

    private companion object {
        /** Below this the microphone sends nothing at all. */
        const val SILENCE = 1e-4f
        /** Louder than the car's noise by this much (about 10 dB) is a voice. */
        const val SPEECH_RATIO = 3f
        /** Within this of the car's noise (about 6 dB) is quiet. */
        const val QUIET_RATIO = 2f
        /** A voice for this long at a stretch is someone speaking: a bump on the road is shorter. */
        const val SPOKE_MS = 200
        /** How fast the noise floor may rise, per piece: a car that gets louder is followed. */
        const val FLOOR_RISE = 1.03f
        /** The first moments after the microphone opens, left out. */
        const val SETTLE_MS = 300
    }
}
