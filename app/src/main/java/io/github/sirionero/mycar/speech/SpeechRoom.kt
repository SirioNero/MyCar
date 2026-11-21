package io.github.sirionero.mycar.speech

/**
 * Whether the phone has the memory for the model that rereads the phrase, whose weights are read into
 * myCar's own memory (Parakeet: ~950 MB of native heap for 640 MB on disk). Sizes in bytes, as
 * Android's ActivityManager.MemoryInfo gives them: [avail] free, Android's low-memory line
 * [threshold] (where it starts closing apps) and whether it's [low] already, and the model's [need].
 * Without Android, so the rule can be tried on the JVM (SpeechRoomTest).
 *
 * The 0.6.8/0.6.9 crashes came a second after Parakeet began to load, and some evenings the phone
 * had only 1.2-1.6 GB free; but a command in Italian was heard by Parakeet loaded at the press with
 * 2.5 GB free, under the margin for loading ahead - and Kroko doesn't hear Italian at all. So at a
 * press another language gets a smaller margin than ahead of a command, but still one: never the
 * 1.2-1.6 GB of those evenings.
 *
 * Those margins were sized on Parakeet (~800 MB in memory). A small model (Canary, ~250 MB; Parakeet
 * small, ~165 MB: [SMALL_MODEL]) gets a smaller one, ahead and at a press: twice Android's line and
 * at least [SMALL_SPARE] left after it. Under the big margins Canary stayed out on a Pixel 8 with
 * 1.5 GB free (GrapheneOS, 17 November), where Gearslip, which never looked, loaded it fine.
 */
internal object SpeechRoom {

    /** What a press of the microphone does about the model that rereads the phrase. */
    enum class Press {
        /** Loaded while the driver talks, as always. */
        LOAD,

        /** English, without the room: Kroko's live words are the answer, and the big model stays out. */
        LIVE_WORDS,

        /** Another language, which only the big model hears, and Android itself would be short of memory with it. */
        NONE,
    }

    /**
     * Ahead of any command: only with room to spare after it, over 4 times Android's line and 2 GB;
     * for a small model, over twice the line and [SMALL_SPARE].
     */
    fun ahead(avail: Long, threshold: Long, low: Boolean, need: Long): Boolean =
        !low && avail - need > spareAhead(threshold, need)

    private fun spareAhead(threshold: Long, need: Long): Long =
        if (need <= SMALL_MODEL) maxOf(2 * threshold, SMALL_SPARE) else maxOf(4 * threshold, AHEAD_SPARE)

    /**
     * At a press, the same room as [ahead]. Without it, English has Kroko's words; another language
     * has only the big model, which still loads with half the spare left after it: over twice
     * Android's line and 1 GB. Just over Android's line, as at first, it loaded on the 1.2-1.6 GB of
     * the evenings like those of the crashes.
     */
    fun atPress(avail: Long, threshold: Long, low: Boolean, need: Long, english: Boolean): Press = when {
        ahead(avail, threshold, low, need) -> Press.LOAD
        english -> Press.LIVE_WORDS
        !low && avail - need > maxOf(2 * threshold, PRESS_SPARE) -> Press.LOAD
        else -> Press.NONE
    }

    private const val AHEAD_SPARE = 2L shl 30

    /** A model this size or under, in memory, is small (Canary, Parakeet small; not Whisper or Parakeet large). */
    const val SMALL_MODEL = 300L shl 20

    /** Left after a small model, at the least (see [ahead]). */
    private const val SMALL_SPARE = 512L shl 20

    /** Left after the only model that hears the language, loaded at a press (see [atPress]). */
    private const val PRESS_SPARE = 1L shl 30
}
