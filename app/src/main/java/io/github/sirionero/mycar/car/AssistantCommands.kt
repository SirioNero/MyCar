package io.github.sirionero.mycar.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.car.app.navigation.model.NavigationTemplate
import io.github.sirionero.mycar.media.CarMedia
import io.github.sirionero.mycar.notify.CarNotifications
import io.github.sirionero.mycar.speech.VoiceFocus
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns what [CarAssistant] heard into an action plus something to say back.
 *
 * Deliberately small and keyword-matched rather than run through any NLU or bundled model: a
 * fixed set of things a driver actually wants hands-free (media, calling, opening an app,
 * getting back Home), covering the same ground the rest of the car UI already does rather than
 * trying to be a general assistant.
 */
object AssistantCommands {

    suspend fun handle(context: Context, navigator: CarNavigator, frame: CarEnvironment.Frame, heardRaw: String): String {
        val heard = natural(heardRaw)
        VoiceHelp.hide()
        if (heard in HELP_WORDS || heard.startsWith("what can i say") || heard.startsWith("what can you do")) {
            VoiceHelp.show()
            return "Here's what you can say."
        }
        if (heard.matches(Regex("(fuck|f\\*+) (you|off)( too)?"))) return "Fuck you too."

        navigateCommand(context, navigator, frame, heard)?.let { return it }
        stopRouteCommand(heard)?.let { return it }
        // Before "play X": "play my messages" isn't a song.
        messageCommand(heard)?.let { return it }
        playSearchCommand(context, navigator, heard)?.let { return it }
        mediaCommand(heard)?.let { return it }
        navCommand(navigator, heard)?.let { return it }
        infoCommand(heard)?.let { return it }
        recallCommand(context, heard)?.let { return it }
        callCommand(context, heard)?.let { return it }
        openAppCommand(context, navigator, frame, heard)?.let { return it }

        return "I don't know how to do that yet."
    }

    /**
     * What was said, minus the padding people wrap a command in: "hey", "can you", "I want to",
     * "please", "thanks". Each command below then only has to know its own words.
     */
    private fun natural(raw: String): String {
        var t = raw.lowercase().replace(Regex("[.,!?]"), " ").replace(Regex("\\s+"), " ").trim()
        var changed = true
        while (changed) {
            changed = false
            for (lead in LEAD_FILLERS) if (t.startsWith("$lead ")) { t = t.removePrefix("$lead ").trim(); changed = true }
            for (tail in TAIL_FILLERS) if (t.endsWith(" $tail")) { t = t.removeSuffix(" $tail").trim(); changed = true }
        }
        return t
    }

    /** The part after the first of [triggers] that [text] starts with, or null. */
    private fun after(text: String, triggers: List<String>): String? =
        triggers.firstOrNull { text.startsWith(it) }?.let { text.removePrefix(it).trim() }

    private suspend fun allEntries(context: Context): List<Entry> {
        val vehicleData = CarSettings.experimentalFeaturesEnabled.value && CarSettings.carSensorsEnabled.value
        val builtIn = BuiltInApps.entries.filter { it.builtIn !== BuiltInApps.VehicleData || vehicleData }
        return builtIn + (LauncherCache.entries ?: withContext(Dispatchers.IO) { LauncherCache.load(context) })
    }

    /**
     * Best label match: exact, then a label starting with [name], then one containing it. Spaces
     * and punctuation don't count, since the recognizer may hear "MapQuest" as "map quest".
     */
    private fun <T> bestMatch(items: List<T>, name: String, label: (T) -> String): T? {
        val n = squash(name.removePrefix("the ").removePrefix("my ").removeSuffix(" app"))
        if (n.isEmpty()) return null
        items.firstOrNull { squash(label(it)) == n }?.let { return it }
        // Loose matches need a real word: "pizza in a" mustn't pick "Organic Maps" for its "a".
        if (n.length < 3) return null
        return items.firstOrNull { squash(label(it)).startsWith(n) }
            ?: items.firstOrNull { squash(label(it)).contains(n) }
    }

    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * "navigate to X [with|using|on|via Y]" - Y picks the nav app by (partial) label; without it,
     * whichever nav app was used last, or the only one installed, or the first one found. Starts
     * it straight into navigating there via [CarAppConnection]'s `ACTION_NAVIGATE` deep link -
     * the same one Android Auto's own Assistant integration uses - rather than opening the app
     * and leaving the driver to type the address in on the car keyboard.
     */
    private suspend fun navigateCommand(
        context: Context,
        navigator: CarNavigator,
        frame: CarEnvironment.Frame,
        heard: String,
    ): String? {
        // "use Organic Maps to get to X", "open OsmAnd and navigate to X": the app comes first.
        var text = heard
        var leadingApp: String? = null
        Regex("^(?:use|using|with|open|in|on) (.+?) (?:to |and )(.+)$").find(text)?.let { m ->
            if (after(m.groupValues[2], NAVIGATE_TRIGGERS) != null) {
                leadingApp = m.groupValues[1]
                text = m.groupValues[2]
            }
        }
        var destination = after(text, NAVIGATE_TRIGGERS) ?: return null
        if (destination.isBlank()) return "Where to?"

        val entries = allEntries(context)
        // "go to Spotify" means the app, and "go to settings" the screen; "go to Target" is a place.
        val asApp = destination.removePrefix("the ").removeSuffix(" app")
        if (text.startsWith("go to ") &&
            (asApp in SCREEN_WORDS || asApp in APP_NICKNAMES || entries.any { it.label.lowercase() == asApp })
        ) {
            return null
        }
        val navApps = entries.filter { it.template?.isNavigation == true }
        if (navApps.isEmpty()) return "You don't have any navigation apps installed."

        // "X with Organic Maps", "X on OsmAnd": split only when the end names a nav app, so a
        // place like "Starbucks on Main Street" stays whole.
        var chosenApp = leadingApp?.let { name -> bestMatch(navApps, name) { it.label } ?: return "I couldn't find a navigation app called $name." }
        if (chosenApp == null) {
            for (sep in listOf(" with ", " using ", " on ", " via ", " in ", " through ")) {
                val at = destination.lastIndexOf(sep)
                if (at <= 0) continue
                val app = bestMatch(navApps, destination.substring(at + sep.length)) { it.label } ?: continue
                chosenApp = app
                destination = destination.substring(0, at).trim()
                break
            }
        }
        // "the nearest gas station" -> "gas station": the map app already searches around the car
        // and AutoRoute picks the nearest result, and Organic Maps took "nearest" as part of the name.
        val (place, nearby) = plainPlace(destination)
        destination = place
        if (destination.isBlank()) return "Where to?"

        val chosen = chosenApp ?: when {
            navApps.size == 1 -> navApps.first()
            else -> navApps.firstOrNull { it.componentId == CarSettings.lastNav.value } ?: navApps.first()
        }
        val template = chosen.template ?: return "That's not a navigation app."

        CarServices.connectNav(template, frame, destination)
        navigator.home()
        return if (nearby) "Finding the nearest $destination with ${chosen.label}." else "Navigating to $destination with ${chosen.label}."
    }

    /** [destination] without "a", "the nearest", "near me" and the like, and whether any of that said "close by". */
    private fun plainPlace(destination: String): Pair<String, Boolean> {
        var t = destination
        var nearby = false
        var changed = true
        while (changed) {
            changed = false
            for (lead in PLACE_LEADS) if (t.startsWith(lead)) {
                t = t.removePrefix(lead).trim(); changed = true
                if (lead.trim() !in ARTICLES) nearby = true
            }
            for (tail in PLACE_TAILS) if (t.endsWith(tail)) { t = t.removeSuffix(tail).trim(); changed = true; nearby = true }
        }
        return t to nearby
    }

    /**
     * "play X" with something after it: a song, artist, album or playlist, asked of the music app
     * that's open through its own search ([CarMedia.search]). A bare "play", or "play music",
     * falls through to resume.
     */
    private suspend fun playSearchCommand(context: Context, navigator: CarNavigator, heard: String): String? {
        var query = after(heard, PLAY_TRIGGERS) ?: return null
        query = query.removePrefix("some ").trim()
        // "Play the next song", "play it again": the music's own buttons, not a search.
        if (query.isEmpty() || query in RESUME_WORDS || TRANSPORT.matches(query)) return null
        val media = CarServices.media

        // "play Daft Punk on Metrolist": switch to that app first, if it's one we know.
        for (sep in listOf(" on ", " in ", " from ", " with ", " using ")) {
            val at = query.lastIndexOf(sep)
            if (at <= 0) continue
            val apps = withContext(Dispatchers.IO) { io.github.sirionero.mycar.media.MediaCatalog.installed(context) }
            val wanted = bestMatch(apps, query.substring(at + sep.length)) { it.label } ?: continue
            query = query.substring(0, at).trim()
            if (query.isEmpty() || query in RESUME_WORDS) {
                CarServices.openMedia(wanted, autoplay = true)
                navigator.media()
                return "Opening ${wanted.label}."
            }
            if (CarServices.mediaApp.value?.component != wanted.component) {
                CarServices.openMedia(wanted)
                navigator.media()
                // Give it a moment to connect before asking it to search.
                withTimeoutOrNull(APP_CONNECT_MS) {
                    while (media.appLabel == null || (!media.canSearchLibrary && !media.now.value.canSearch)) delay(200)
                }
            }
            break
        }

        // No music app open, or its connection is gone (the app was closed): use the one played
        // last, the way "play X" works on Android Auto.
        val phase = media.phase.value
        if (media.appLabel == null || phase == CarMedia.Phase.IDLE || phase == CarMedia.Phase.REJECTED) {
            if (!CarServices.reopenLastMedia()) return "Open a music app first, then ask again."
            navigator.media()
        }
        withTimeoutOrNull(APP_CONNECT_MS) {
            while (media.phase.value != CarMedia.Phase.READY || media.appLabel == null ||
                (!media.canSearchLibrary && !media.now.value.canSearch)
            ) delay(200)
        }
        val app = media.appLabel ?: return "Your music app didn't open in time. Try again."
        if (!media.canSearchLibrary && !media.now.value.canSearch) {
            return "$app ignores voice searches. Pick something on its screen instead."
        }
        media.playBestMatch(query)
        return "Looking for $query on $app."
    }

    /**
     * "Read my messages": the newest chat with something new, read aloud ("my last message": the
     * newest, read or not), then the offer to reply. "Reply to Anna", "text Anna": straight to the
     * reply, in Anna's latest chat. Only chats the phone showed during the drive: a new message
     * to someone who hasn't written isn't something myCar can send.
     */
    private fun messageCommand(heard: String): String? {
        val read = READ_MESSAGES.matchEntire(heard)
        val to = REPLY_TO.matchEntire(heard)?.groupValues?.get(1)?.trim()
        if (read == null && to.isNullOrEmpty()) return null
        if (!CarNotifications.listening.value) return "myCar needs notification access to read messages. Allow it on the phone."
        val chats = CarNotifications.history.value.filter { it.lines.isNotEmpty() || it.reply != null }
        val n = if (to != null) {
            chats.firstOrNull { it.reply != null && it.title.lowercase().contains(to) }
                ?: return "I couldn't find a message from $to to reply to."
        } else {
            val newest = read?.groupValues?.get(1).orEmpty().let { "last" in it || "latest" in it }
            (if (newest) chats.firstOrNull() else chats.firstOrNull { CarNotifications.isUnread(it.key) })
                ?: return if (newest) "There are no messages yet." else "No new messages."
        }
        // VoiceReply speaks from here: the assistant's own answer is nothing, which closes it.
        Handler(Looper.getMainLooper()).post { if (to != null) VoiceReply.reply(n, readFirst = false) else VoiceReply.read(n) }
        return ""
    }

    /** "Stop navigation", "cancel the route": ends the guidance - it used to pause the music. */
    private fun stopRouteCommand(heard: String): String? {
        if (!Regex("^(?:stop|end|cancel|quit|exit) (?:the )?(?:navigation|navigating|route|directions|guidance)$").matches(heard)) return null
        CarServices.nav.stopNavigating()
        return "Ending the route."
    }

    private suspend fun mediaCommand(heard: String): String? {
        // "Call Skip", "phone Nick": a person, not the music.
        if (CALL_TRIGGERS.any { heard.startsWith(it) }) return null
        val media = CarServices.media
        val none = CarServices.noMusicApp()
        return when {
            "pause" in heard || heard.startsWith("stop") || heard == "quiet" || heard == "shut up" -> {
                media.pause()
                VoiceFocus.afterRelease { media.pause() }
                "Paused."
            }
            // Before "play" below: "play the next song", "play something else", "play it again".
            "next" in heard || "skip" in heard || heard.startsWith("change the song") || ANOTHER.matches(heard) -> {
                if (none) return "Nothing's playing."
                media.next()
                "Skipping."
            }
            "previous" in heard || "last song" in heard || "last track" in heard || "last one" in heard || "back song" in heard ||
                "go back a song" in heard || AGAIN.matches(heard) || "replay" in heard -> {
                if (none) return "Nothing's playing."
                media.previous()
                "Playing the previous track."
            }
            "resume" in heard || "unpause" in heard || heard in RESUME_PHRASES ||
                (heard.startsWith("play") && "playing" !in heard) -> {
                // No music app open (the first drive, one closed from its menu): the one played
                // last, playing - as the car's ▶ key does. It used to say "Playing." to silence.
                if (none) {
                    return if (CarServices.reopenLastMedia(autoplay = true)) {
                        "Playing ${CarServices.mediaApp.value?.label ?: "the music"}."
                    } else {
                        "Open a music app first, then ask again."
                    }
                }
                if (!media.now.value.playing) media.togglePlay()
                "Playing."
            }
            ("what" in heard && "playing" in heard) || "what song" in heard || "who sings" in heard ||
                "who's singing" in heard || "who is singing" in heard || "what's this song" in heard ||
                "name of this song" in heard -> {
                val now = media.now.value
                if (now.isActive) listOf(now.title, now.artist).filter { it.isNotBlank() }.joinToString(" by ")
                else "Nothing's playing."
            }
            else -> null
        }
    }

    private fun navCommand(navigator: CarNavigator, heard: String): String? = when {
        heard == "go back" || heard == "back" -> {
            navigator.back()
            "Going back."
        }
        heard in EXIT_WORDS -> {
            BuiltInApps.Exit.open(navigator)
            "Back to the car's screen."
        }
        "go home" in heard || heard == "home" || heard == "home screen" || "go to the home screen" in heard -> {
            navigator.home()
            "Heading home."
        }
        "open apps" in heard || heard == "apps" || "app list" in heard || "all apps" in heard || "my apps" in heard || heard == "go to apps" -> {
            navigator.apps()
            "Here are your apps."
        }
        "settings" in heard -> {
            navigator.settings()
            "Opening settings."
        }
        "vehicle data" in heard || "car data" in heard -> {
            if (CarSettings.experimentalFeaturesEnabled.value && CarSettings.carSensorsEnabled.value) {
                navigator.vehicleData()
                "Opening vehicle data."
            } else {
                "Vehicle data is off. Turn it on under experimental features in settings."
            }
        }
        else -> null
    }

    private fun infoCommand(heard: String): String? = when {
        // Before "what time": "what time do I arrive" is the route's, not the clock's.
        ETA.containsMatchIn(heard) -> arrival()
        "what time" in heard || "what's the time" in heard || heard == "time" || "the time" in heard ->
            "It's ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())}."
        "weather" in heard || "going to rain" in heard || "gonna rain" in heard || "how hot" in heard ||
            "how cold" in heard || "temperature outside" in heard -> {
            val data = (Weather.state.value as? WeatherState.Ready)?.data
            if (data != null) "${data.temp}${data.tempUnit} and ${Weather.describe(data.code)} in ${data.place}."
            else "I don't have the weather yet."
        }
        else -> null
    }

    /** "When do I arrive?": the route's own estimate, the arrival time as the phone writes it. */
    private fun arrival(): String {
        val estimate = (CarServices.nav.template.value as? NavigationTemplate)?.destinationTravelEstimate
            ?: return "There's no route on the map."
        val at = QuickReplyTexts.arrival(estimate.arrivalTimeAtDestination)
        val left = spokenTimeLeft(estimate.remainingTimeSeconds)
        return when {
            at.isNotEmpty() && left.isNotEmpty() -> "Around $at, in $left."
            at.isNotEmpty() -> "Around $at."
            left.isNotEmpty() -> "In about $left."
            else -> "The map hasn't said yet."
        }
    }

    /** "1 hour 5 minutes", as it's said - not "1 hr 5 min". */
    private fun spokenTimeLeft(seconds: Long): String {
        if (seconds <= 0) return ""
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        fun unit(n: Long, one: String) = if (n == 1L) "1 $one" else "$n ${one}s"
        return when {
            hours > 0 && minutes > 0 -> "${unit(hours, "hour")} ${unit(minutes, "minute")}"
            hours > 0 -> unit(hours, "hour")
            else -> unit(minutes.coerceAtLeast(1), "minute")
        }
    }

    /**
     * "Call back": the last call that came in, missed or not; "redial": the last one made. Off the
     * phone's call log, one call at a time (not the Recents' lines, where a call back joins the
     * call before it). Before [callCommand], which would look for a contact called "back".
     */
    private suspend fun recallCommand(context: Context, heard: String): String? {
        val redial = heard in REDIAL_PHRASES
        if (!redial && heard !in CALL_BACK_PHRASES) return null
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return "myCar needs the phone permission first, from the phone screen."
        }
        if (!hasCallLog(context)) return "myCar needs the phone's call log first: allow it on the phone."
        val calls = withContext(Dispatchers.IO) { loadRecents(context, grouped = false) }
        val call = calls.firstOrNull { it.number.isNotBlank() && (it.kind == RecentKind.OUTGOING) == redial }
            ?: return if (redial) "There's no call to redial." else "There's no call to return."
        val who = call.name ?: shownNumber(call.number)
        placeCall(context, call.number)
        return if (redial) "Calling $who again." else "Calling $who back."
    }

    private suspend fun callCommand(context: Context, heard: String): String? {
        val asked = after(heard, CALL_TRIGGERS)
            ?: Regex("^give (.+) a (?:call|ring)$").find(heard)?.groupValues?.get(1)
            ?: return null
        // "on mobile", "at work": which of the person's numbers - it used to be thrown away.
        val typeWord = NUMBER_TYPE.find(asked)?.groupValues?.get(1)
        val name = asked.replace(NUMBER_TYPE, "").trim()
        if (name.isBlank()) return "Who should I call?"
        if (!hasCallPermissions(context)) return "myCar needs the phone and contacts permissions first, from the phone screen."

        // The whole name, then a whole word of it ("Luca" in "Luca Rossi"), and only then a piece
        // of one: "Luca" used to call "Gianluca", first in alphabetical order.
        val contacts = withContext(Dispatchers.IO) { loadContacts(context) }
        fun tier(c: Contact): Int {
            val full = c.name.lowercase().trim()
            return when {
                full == name -> 0
                " $full ".contains(" $name ") -> 1
                full.contains(name) -> 2
                else -> 3
            }
        }
        val best = contacts.minOfOrNull(::tier)?.takeIf { it < 3 } ?: return "I couldn't find $name in your contacts."
        val matches = contacts.filter { tier(it) == best }
        val match = matches.first()
        // Only a piece of a name, or more than one person: the driver confirms on the screen.
        if (best == 2 || matches.distinctBy { it.name }.size > 1) {
            CallPrompt.ask(match.name, match.number)
            return "Call ${match.name}?"
        }
        // One person, maybe with several numbers: the one asked for, the phone's default for them,
        // the mobile, the first. It used to be whichever the contacts gave first - the office, say.
        val wanted = when (typeWord) {
            "mobile", "cell" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
            "work" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
            else -> null
        }
        val ofType = wanted?.let { w -> matches.firstOrNull { it.type == w } }
        if (wanted != null && ofType == null) {
            // Not that one on record: another, but asked first.
            val other = matches.firstOrNull { it.primary } ?: match
            CallPrompt.ask(listOfNotNull(other.name, other.typeLabel).joinToString(" · "), other.number)
            return "${match.name} has no $typeWord number. Call ${other.typeLabel?.lowercase() ?: "the other one"} instead?"
        }
        val pick = ofType ?: matches.firstOrNull { it.primary }
            ?: matches.firstOrNull { it.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE } ?: match
        placeCall(context, pick.number)
        // Which one, out loud, when there was a choice.
        return if (matches.size > 1 && pick.typeLabel != null) "Calling ${pick.name}, ${pick.typeLabel.lowercase()}." else "Calling ${pick.name}."
    }

    private suspend fun openAppCommand(
        context: Context,
        navigator: CarNavigator,
        frame: CarEnvironment.Frame,
        heard: String,
    ): String? {
        val name = (after(heard, OPEN_TRIGGERS) ?: return null)
            .removePrefix("the ").removePrefix("my ").removeSuffix(" app").trim()
        if (name.isBlank()) return null
        val wanted = APP_NICKNAMES[name] ?: name

        // myCar's own apps come first in the list, so "phone" opens its dialer, not the phone's.
        val match = bestMatch(allEntries(context), wanted) { it.label }
            ?: return "I couldn't find an app called $name."

        launchEntry(match, navigator, frame)
        return "Opening ${match.label}."
    }

    private val EXIT_WORDS = setOf("exit", "exit my car", "close my car", "quit", "quit my car", "open exit")

    /** Other names people use for myCar's own apps. */
    private val APP_NICKNAMES = mapOf(
        "web" to "browser", "internet" to "browser", "the web" to "browser", "web browser" to "browser",
        "dialer" to "phone", "the dialer" to "phone", "keypad" to "phone",
        "screen share" to "screen sharing", "screen mirroring" to "screen sharing", "mirroring" to "screen sharing",
    )

    private val NAVIGATE_TRIGGERS = listOf(
        "navigate to ", "navigate me to ", "directions to ", "get directions to ", "give me directions to ",
        "take me to ", "drive to ", "drive me to ", "get me to ", "go to ", "head to ", "route to ",
        "how do i get to ", "show me the way to ", "find a route to ", "navigation to ", "get to ",
        // Last, so "navigate to" wins: the recognizer sometimes drops the "to".
        "navigate me ", "navigate ", "find me the nearest ", "find the nearest ", "find the closest ",
    )

    private val ARTICLES = setOf("a", "an", "the", "some")
    // Longest first, so "the nearest " goes before "the ".
    private val PLACE_LEADS = listOf(
        "the nearest ", "the closest ", "a nearby ", "the nearby ", "nearest ", "closest ", "nearby ",
        "a ", "an ", "some ",
    )
    private val PLACE_TAILS = listOf(" near me", " nearby", " near here", " around here", " close by", " closest to me")

    /** After "play": the next or the last song, this one again, something else - not a title. */
    private val TRANSPORT = Regex(
        "^(?:the )?(?:next|previous|last)(?: (?:song|track|one))?$|^(?:that|it|this)(?: song| track| one)? (?:again|over)$" +
            "|^(?:something else|another (?:song|track|one)|a different (?:song|track))$",
    )

    /** "Play it again", "that song again": the song from the start, as Previous does. */
    private val AGAIN = Regex("^(?:(?:play|put on) )?(?:that|it|this)(?: song| track| one)? (?:again|over)$")

    /** "Play something else", "another song": the next one. */
    private val ANOTHER = Regex("^(?:(?:play|put on) )?(?:something else|another (?:song|track|one)|a different (?:song|track))$")

    private val PLAY_TRIGGERS = listOf("play me ", "play ", "put on ", "listen to ", "i want to hear ", "shuffle ")

    /** "When do I arrive", "what time will we get there", "how much longer", "ETA". */
    private val ETA = Regex(
        "\\b(?:eta|arrival time|time of arrival|how much longer|how long (?:until|till|left|is left|to go)" +
            "|(?:when|what time) (?:do|will|are|am) (?:i|we)(?: going to)? (?:arrive|get there|be there))\\b",
    )

    /** "Read my messages", "play my new texts", "read me the last message". */
    private val READ_MESSAGES = Regex("^(?:read|play|check)(?: me)?(?: my| the)?((?: new| last| latest| unread)?) (?:messages?|texts?)$")

    /** "Reply to Anna", "text Anna", "send a message to Anna". */
    private val REPLY_TO = Regex("^(?:reply to|respond to|text|message|send a message to|write to) (.+)$")

    /** " on mobile", " at work", " on her cell": the number asked for, at the end of a call. */
    private val NUMBER_TYPE = Regex(" (?:on|at) (?:their |his |her )?(mobile|cell|phone|home|work)$")

    private val CALL_BACK_PHRASES = setOf(
        "call back", "call them back", "call him back", "call her back", "call back the last call",
        "call back the missed call", "call back the last missed call", "call the last missed call",
        "return the call", "return the last call", "return my last call", "return the missed call", "return my missed call",
    )

    private val REDIAL_PHRASES = setOf(
        "redial", "redial the last number", "call again", "dial again", "call the last number",
        "call the last number again", "call last number", "dial the last number",
    )

    private val CALL_TRIGGERS = listOf("call ", "phone ", "dial ", "ring ", "make a call to ", "place a call to ", "start a call with ")

    private val OPEN_TRIGGERS = listOf("open ", "launch ", "start ", "go to ", "switch to ", "show me ", "bring up ", "pull up ")

    /** Screens "go to X" means rather than a place. */
    private val SCREEN_WORDS = setOf("settings", "home", "the home screen", "home screen", "apps", "my apps", "all apps")

    /** Padding stripped from the front of a command, longest first where they overlap. */
    private val LEAD_FILLERS = listOf(
        "hey my car", "ok my car", "okay my car", "my car", "hey", "ok", "okay", "um", "uh", "so",
        "please", "can you please", "can you", "could you", "would you", "will you", "can i", "could i",
        "i want you to", "i want to", "i'd like to", "i would like to", "i wanna", "i need to", "i need you to",
        "let's", "lets", "go ahead and", "just", "now", "quickly",
    )

    /** Longest first, so "right now" goes whole rather than leaving "right". */
    private val TAIL_FILLERS = listOf("thank you very much", "thank you", "right now", "for me", "please", "thanks", "now")

    private const val APP_CONNECT_MS = 5_000L

    private val HELP_WORDS = setOf("help", "commands", "voice commands", "list commands", "show commands")

    private val RESUME_WORDS = setOf("music", "the music", "some music", "something", "it", "again", "that", "my music")

    private val RESUME_PHRASES = setOf("keep playing", "continue", "keep going", "start the music", "music on", "turn the music back on")
}
