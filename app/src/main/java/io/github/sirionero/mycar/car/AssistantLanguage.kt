package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.speech.VoiceLanguage

/**
 * The car's assistant in the driver's language ([VoiceLanguage]: the phone's, see VoiceLanguages).
 * [AssistantCommands] knows English commands and answers in English. Here what the driver says in
 * their language becomes the English command it means, and every answer is said in their language.
 * Names, places, songs and messages pass through as they were said or written. Nothing here needs
 * Android, so every phrase can be tried on the JVM (AssistantLanguageTest).
 */
internal object AssistantLanguage {

    /** The language the assistant works in now; English until CarAssistant finds another. */
    @Volatile var language: VoiceLanguage = VoiceLanguage.EN

    private fun book(l: VoiceLanguage): Phrasebook? = when (l) {
        VoiceLanguage.EN -> null
        VoiceLanguage.IT -> ItalianPhrases
        VoiceLanguage.ES -> SpanishPhrases
        VoiceLanguage.FR -> FrenchPhrases
        VoiceLanguage.DE -> GermanPhrases
        VoiceLanguage.PT -> PortuguesePhrases
    }

    /** What the driver said, as the English command it means. English, or anything unknown, passes as it was. */
    fun toEnglish(heard: String, l: VoiceLanguage = language): String {
        val b = book(l) ?: return heard
        val t = b.trim(plain(heard))
        for (rule in b.commands) rule.apply(t)?.let { return it }
        return t
    }

    /** One of the assistant's answers in the driver's language; one with no translation stays as written. */
    fun answer(english: String, l: VoiceLanguage = language): String {
        val b = book(l) ?: return english
        for (said in Said.entries) {
            val m = said.regex.matchEntire(english) ?: continue
            val to = b.answers[said] ?: return english
            return to(m.groupValues)
        }
        return english
    }

    /** Words before an app's name at the end of a command ("… con Organic Maps"), besides the English ones. */
    fun appWords(l: VoiceLanguage = language): List<String> = book(l)?.appWords.orEmpty()

    fun yes(l: VoiceLanguage = language): Set<String> = book(l)?.yes.orEmpty()
    fun no(l: VoiceLanguage = language): Set<String> = book(l)?.no.orEmpty()
    fun cancel(l: VoiceLanguage = language): Set<String> = book(l)?.cancel.orEmpty()
    fun change(l: VoiceLanguage = language): Set<String> = book(l)?.change.orEmpty()
    fun send(l: VoiceLanguage = language): Set<String> = book(l)?.send.orEmpty()

    /** "Anna says: …", for a message read aloud. */
    fun says(who: String, text: String, l: VoiceLanguage = language): String =
        book(l)?.says(who, text) ?: "$who says: $text"

    /** "Sam in Climbing": who wrote, in a group. */
    fun inGroup(sender: String, group: String, l: VoiceLanguage = language): String =
        book(l)?.inGroup(sender, group) ?: "$sender in $group"

    /** What to say, as the help shows it; null for English, which has its own list. */
    fun help(l: VoiceLanguage = language): List<Pair<String, String>>? = book(l)?.help

    /** A few whole commands, for the phone's introduction; null for English. */
    fun samples(l: VoiceLanguage = language): List<String>? = book(l)?.samples

    /** A word of the assistant's own on the car's screen ("Listening…", "You can say"). */
    fun label(english: String, l: VoiceLanguage = language): String = book(l)?.labels?.get(english) ?: english

    /** Lower case, without punctuation, the way commands are matched. */
    internal fun plain(text: String): String =
        text.lowercase().replace('’', '\'').replace(Regex("[.,!?¿¡;:\"«»„“”-]"), " ").replace(Regex("\\s+"), " ").trim()
}

/**
 * Everything the assistant says, as [AssistantCommands], [CarAssistant], the speech engine and
 * [VoiceReply] write it in English. A [Phrasebook] gives each one in its language, with $1… for
 * the parts that pass through (a name, a place, an app). Tried in this order: the longer forms
 * first where two could fit ("Calling Anna back." before "Calling Anna.").
 */
internal enum class Said(pattern: String) {
    HELP("""Here's what you can say\."""),
    UNKNOWN("""I don't know how to do that yet\."""),
    WHERE_TO("""Where to\?"""),
    NO_NAV_APPS("""You don't have any navigation apps installed\."""),
    NO_NAV_APP_CALLED("""I couldn't find a navigation app called (.+)\."""),
    NOT_NAV_APP("""That's not a navigation app\."""),
    FINDING_NEAREST("""Finding the nearest (.+) with (.+)\."""),
    NAVIGATING("""Navigating to (.+) with (.+)\."""),
    OPENING_SETTINGS("""Opening settings\."""),
    OPENING_VEHICLE_DATA("""Opening vehicle data\."""),
    OPENING("""Opening (.+)\."""),
    OPEN_MUSIC_FIRST("""Open a music app first, then ask again\."""),
    MUSIC_APP_LATE("""Your music app didn't open in time\. Try again\."""),
    IGNORES_SEARCH("""(.+) ignores voice searches\. Pick something on its screen instead\."""),
    LOOKING_FOR("""Looking for (.+) on (.+)\."""),
    NEEDS_NOTIFICATIONS("""myCar needs notification access to read messages\. Allow it on the phone\."""),
    NO_MESSAGE_FROM("""I couldn't find a message from (.+) to reply to\."""),
    NO_MESSAGES_YET("""There are no messages yet\."""),
    NO_NEW_MESSAGES("""No new messages\."""),
    ENDING_ROUTE("""Ending the route\."""),
    PAUSED("""Paused\."""),
    NOTHING_PLAYING("""Nothing's playing\."""),
    SKIPPING("""Skipping\."""),
    PREVIOUS_TRACK("""Playing the previous track\."""),
    PLAYING("""Playing\."""),
    PLAYING_APP("""Playing (.+)\."""),
    GOING_BACK("""Going back\."""),
    BACK_TO_CAR("""Back to the car's screen\."""),
    HEADING_HOME("""Heading home\."""),
    YOUR_APPS("""Here are your apps\."""),
    VEHICLE_DATA_OFF("""Vehicle data is off\. Turn it on under experimental features in settings\."""),
    TIME("""It's (.+)\."""),
    WEATHER("""(-?[\d.,]+\S*) and (.+) in (.+)\."""),
    NO_WEATHER("""I don't have the weather yet\."""),
    NO_ROUTE("""There's no route on the map\."""),
    ARRIVE_AT_IN("""Around (.+), in (.+)\."""),
    ARRIVE_AT("""Around (.+)\."""),
    ARRIVE_IN("""In about (.+)\."""),
    MAP_HASNT_SAID("""The map hasn't said yet\."""),
    NEEDS_PHONE("""myCar needs the phone permission first, from the phone screen\."""),
    NEEDS_CALL_LOG("""myCar needs the phone's call log first: allow it on the phone\."""),
    NO_REDIAL("""There's no call to redial\."""),
    NO_CALL_BACK("""There's no call to return\."""),
    PRIVATE_NUMBER("""That call was from a private number, so it can't be returned\."""),
    CALLING_AGAIN("""Calling (.+) again\."""),
    CALLING_BACK("""Calling (.+) back\."""),
    WHO_TO_CALL("""Who should I call\?"""),
    NEEDS_CONTACTS("""myCar needs the phone and contacts permissions first, from the phone screen\."""),
    NOT_IN_CONTACTS("""I couldn't find (.+) in your contacts\."""),
    NO_SUCH_NUMBER("""(.+) has no (.+) number\. Call (.+) instead\?"""),
    CALL_QUESTION("""Call (.+)\?"""),
    CALLING_TYPE("""Calling (.+), (.+)\."""),
    CALLING("""Calling (.+)\."""),
    NO_APP_CALLED("""I couldn't find an app called (.+)\."""),
    SOMETHING_WRONG("""Something went wrong\. Try again\."""),
    // Home Assistant's Assist, for what isn't one of myCar's commands (AssistantBrain).
    DONE("""Done\."""),
    HOME_ASSISTANT_SILENT("""Home Assistant didn't answer\."""),
    // The microphone and the speech engine.
    NEEDS_MIC("""Needs the microphone permission\."""),
    AFTER_CALL("""Voice commands work once the call ends\."""),
    DIDNT_CATCH("""Didn't catch that\."""),
    RECOGNIZER_BUSY("""The speech recognizer is busy\."""),
    RECOGNIZER_NETWORK("""The speech recognizer needs a network it doesn't have\."""),
    RECOGNITION_FAILED("""Speech recognition failed\."""),
    SPEECH_NOT_READY("""Speech isn't ready yet\."""),
    MODEL_NOT_LOADED("""The speech model didn't load\."""),
    MODEL_NOT_DOWNLOADED("""Download a voice model in myCar's settings on the phone first\."""),
    MIC_SILENCED("""The phone's microphone is silenced\. Unlock the phone and try again\."""),
    MIC_UNAVAILABLE("""The microphone isn't available right now\."""),
    // Messages read aloud and answered.
    MISSED_CALL_FROM("""Missed call from (.+)\."""),
    WANT_TO_REPLY("""Do you want to reply\?"""),
    GO_AHEAD("""Go ahead\."""),
    NO_CAR_REPLIES("""(.+) doesn't allow replies from the car\."""),
    REPLY_AFTER_CALL("""Voice reply works once the call ends\."""),
    YOUR_REPLY_TO("""What's your reply to (.+)\?"""),
    YOUR_REPLY("""What's your reply\?"""),
    SENDING_TO("""(?s)Sending to ([^:]+): (.*)"""),
    SENDING("""(?s)Sending: (.*)"""),
    NOT_SENT("""Not sent\."""),
    MESSAGE_SENT("""Message sent\."""),
    REPLY_WITHDRAWN("""(.+) withdrew the reply\. Open the app on the phone to answer\."""),
    // Last: what's playing is the song's own words, "Song by Artist".
    NOW_PLAYING("""(.+) by (.+)"""),
    ;

    val regex = Regex(pattern)
}

/**
 * One language's words for the assistant. [commands] are tried in order against the whole phrase,
 * once [trim] has taken off what doesn't change it, and the first that fits wins.
 */
internal abstract class Phrasebook {
    /** Words in front of a command that don't change it ("per favore"), longest first where two overlap. */
    open val leadFillers: List<String> = emptyList()
    open val tailFillers: List<String> = emptyList()
    abstract val commands: List<Rule>
    abstract val answers: Map<Said, (List<String>) -> String>
    /** Words before an app's name at the end ("con", "su"): AssistantCommands splits there only for an app it knows. */
    open val appWords: List<String> = emptyList()
    abstract val yes: Set<String>
    abstract val no: Set<String>
    abstract val cancel: Set<String>
    abstract val change: Set<String>
    abstract val send: Set<String>
    abstract fun says(who: String, text: String): String
    abstract fun inGroup(sender: String, group: String): String
    abstract val help: List<Pair<String, String>>
    abstract val samples: List<String>
    abstract val labels: Map<String, String>

    fun trim(text: String): String {
        var t = text
        var changed = true
        while (changed) {
            changed = false
            for (lead in leadFillers) if (t.startsWith("$lead ")) { t = t.removePrefix("$lead ").trim(); changed = true }
            for (tail in tailFillers) if (t.endsWith(" $tail")) { t = t.removeSuffix(" $tail").trim(); changed = true }
        }
        return t
    }

    /** An answer with $1… for its parts. */
    protected fun t(template: String): (List<String>) -> String = { groups ->
        Regex("\\$(\\d)").replace(template) { m -> groups.getOrElse(m.groupValues[1].toInt()) { "" } }
    }

    /** "1 hour 5 minutes", as AssistantCommands says the time left, in this language's [hour] and [minute] (one, more). */
    protected fun span(english: String, hour: Pair<String, String>, minute: Pair<String, String>, and: String): String {
        fun unit(n: String, word: Pair<String, String>) = "$n ${if (n == "1") word.first else word.second}"
        val h = Regex("(\\d+) hours?").find(english)?.groupValues?.get(1)?.let { unit(it, hour) }
        val m = Regex("(\\d+) minutes?").find(english)?.groupValues?.get(1)?.let { unit(it, minute) }
        return listOfNotNull(h, m).joinToString(" $and ").ifEmpty { english }
    }

    /** Whether [time], as the phone writes it ("1:05", "01:05"), is in the one o'clock hour: "È l'1:05", "Es la 1:05". */
    protected fun oneOClock(time: String) = Regex("^0?1\\D").containsMatchIn(time)

    /**
     * The last destination as the driver said it ("al supermercato"), next to the plain one the
     * map is asked for ("supermercato"): the answer then says it back the same way.
     */
    @Volatile private var saidPlace: Pair<String, String>? = null

    /** [plain] for the map, remembered as [said]. "The nearest" in front doesn't count. */
    protected fun remember(plain: String, said: String): String {
        saidPlace = plain.removePrefix("the nearest ") to said.trim()
        return plain
    }

    /**
     * The destination [plain] as the driver said it, when that had more to it than the place
     * itself. AssistantCommands may have cut an app off its end ("casa con vela" → "casa"): the
     * said words lose the same end.
     */
    protected fun asSaid(plain: String): String? {
        val (full, said) = saidPlace ?: return null
        val tail = full.removePrefix(plain)
        val cut = when {
            full == plain -> said
            full.startsWith("$plain ") && said.endsWith(tail) -> said.removeSuffix(tail)
            else -> return null
        }.trim()
        return cut.takeIf { it != plain }
    }
}

/** A way of saying something, as the whole phrase, and the English command it becomes. */
internal class Rule(pattern: String, private val to: (MatchResult) -> String?) {
    /** [to] with $1… for the parts of the phrase. */
    constructor(pattern: String, to: String) : this(pattern, { m ->
        Regex("\\$(\\d)").replace(to) { g -> m.groupValues.getOrElse(g.groupValues[1].toInt()) { "" }.trim() }
    })

    private val regex = Regex(pattern)

    /** The English command, or null when [text] isn't said this way (or [to] turns it down). */
    fun apply(text: String): String? = regex.matchEntire(text)?.let(to)
}
