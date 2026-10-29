package io.github.sirionero.mycar.car

/** The assistant in German (see [AssistantLanguage]). */
internal object GermanPhrases : Phrasebook() {

    override val leadFillers = listOf(
        "hey mycar", "hallo mycar", "ok mycar", "hey my car", "ok my car", "mycar", "my car", "hey", "hallo", "ok", "okay", "also",
        "bitte", "kannst du bitte", "kannst du", "könntest du", "koenntest du", "würdest du", "wuerdest du", "ich möchte", "ich moechte",
        "ich will", "ich würde gern", "ich würde gerne", "jetzt",
    )
    override val tailFillers = listOf("danke schön", "dankeschön", "bitte", "danke", "jetzt", "sofort")
    override val appWords = listOf(" mit ", " über ", " via ", " in ")

    private val NEAR = listOf(
        Regex("^(?:nächste[nmrs]?|naechste[nmrs]?) (.+?)((?: (?:mit|über|ueber|via) .+)?)$"),
        Regex("^(.+?) (?:in der nähe|in der naehe|hier in der nähe|in meiner nähe|in der umgebung|in der gegend)((?: (?:mit|über|ueber|via) .+)?)$"),
    )

    /** "zur nächsten Tankstelle" → "the nearest tankstelle", "nach Berlin" → "berlin", "nach Hause" → "zuhause". */
    private fun place(said: String): String {
        var t = said.trim()
        t = t.replace(Regex("^(?:bis zum|bis zur|bis nach|bis)\\s+"), "")
        t = t.replace(Regex("^(?:zum|zur|zu der|zu dem|zu|nach|in die|in den|ins|in das|in|an den|an die|ans|an das|auf den|auf die|aufs)\\s+"), "")
        t = t.replace(Regex("^(?:der|die|das|den|dem|einen|eine|einem|einer|ein)\\s+"), "")
        if (t == "hause" || t == "haus") return "zuhause"
        for (near in NEAR) near.matchEntire(t)?.let { return "the nearest " + it.groupValues[1] + it.groupValues[2] }
        return t
    }

    private fun navigate(said: String) = "navigate to " + remember(place(said), said)

    private fun who(raw: String): String = raw.trim().replace(Regex("^(?:an|auf|die|der|den|dem|das)\\s+"), "").trim()

    private val numberType = mapOf(
        "handy" to "mobile", "mobiltelefon" to "mobile", "mobilnummer" to "mobile",
        "festnetz" to "home", "festnetznummer" to "home", "privatnummer" to "home", "zu hause" to "home", "zuhause" to "home",
        "arbeit" to "work", "büro" to "work", "buero" to "work", "geschäftsnummer" to "work", "geschaeftsnummer" to "work",
    )

    private fun app(name: String) = when (name) {
        "telefon" -> "phone"
        "bildschirmfreigabe", "bildschirm teilen" -> "screen sharing"
        "fahrzeugdaten", "autodaten" -> "vehicle data"
        "einstellungen" -> "settings"
        else -> name
    }

    private const val TYPES = "handy|mobiltelefon|mobilnummer|festnetz|festnetznummer|privatnummer|arbeit|büro|buero|geschäftsnummer|geschaeftsnummer"

    override val commands = listOf(
        Rule("(?:hilfe|was kann ich sagen|was kannst du|was kannst du alles|befehle|sprachbefehle)", "help"),
        Rule("(?:(?:geh|gehe|zurück|zurueck) (?:zum|zur) )?(?:startbildschirm|startseite|home)", "go home"),
        Rule("(?:(?:öffne|oeffne|zeig|zeige|zeig mir|geh zu den|gehe zu den) )?(?:die )?einstellungen", "open settings"),
        Rule("(?:(?:zeig mir|zeige mir|zeig|öffne|oeffne) )?(?:meine |alle |die )?(?:apps|anwendungen)", "open apps"),
        Rule("beenden|beende|beende mycar|schließe mycar|schliesse mycar|verlassen", "exit"),
        Rule("zurück|zurueck|geh zurück|gehe zurück|zurückgehen", "go back"),
        Rule(
            "(?:beende|stoppe|stopp|stop|brich)(?: die| den)? (?:navigation|route|routenführung|zielführung)(?: ab)?|" +
                "navigation (?:beenden|stoppen|abbrechen)",
            "stop navigation",
        ),
        Rule(
            "(?:wann komme ich an|wann kommen wir an|wann bin ich da|wann sind wir da|wie lange noch|wie lange dauert es noch|" +
                "wie weit ist es noch|ankunftszeit|um wie viel uhr komme ich an)(?: .*)?",
            "when do i arrive",
        ),
        Rule("(?:(?:lies|lese)(?: mir)?(?: .*)? (?:nachrichten|nachricht|sms)(?: .*)?|(?:.* )?(?:nachrichten|nachricht|sms) vorlesen)") { m ->
            val said = m.value
            when {
                "neu" in said || "ungelesen" in said -> "read my new messages"
                "letzt" in said -> "read my last message"
                else -> "read my messages"
            }
        },
        Rule("(?:antworte|antworten|schreib|schreibe|schicke eine nachricht an|sende eine nachricht an|nachricht an) (.+)") { m ->
            "reply to " + who(m.groupValues[1])
        },
        Rule(
            "(?:ruf zurück|rufe zurück|ruf zurueck|zurückrufen|zurueckrufen|rückruf|rueckruf|ruf den letzten anruf zurück|" +
                "verpassten anruf zurückrufen)",
            "call back",
        ),
        Rule(
            "(?:wahlwiederholung|nochmal anrufen|noch mal anrufen|erneut anrufen|ruf nochmal an|ruf noch mal an|ruf erneut an|" +
                "die letzte nummer anrufen|letzte nummer wählen|letzte nummer waehlen)",
            "redial",
        ),
        Rule("(?:ruf|rufe) (.+?) (?:auf dem|auf der|am|auf|im|unter der|bei der) ($TYPES)(?: an)?") { m ->
            "call ${who(m.groupValues[1])} on ${numberType.getValue(m.groupValues[2])}"
        },
        Rule("(?:ruf|rufe) (.+?) (zu hause|zuhause) an") { m -> "call ${who(m.groupValues[1])} on home" },
        Rule("(?:anrufen|jemanden anrufen|einen anruf)", "call"),
        Rule("(?:ruf|rufe) (.+) (?:an|zurück|zurueck)") { m -> "call " + who(m.groupValues[1]) },
        Rule("(?:telefoniere mit|wähle|waehle) (.+)") { m -> "call " + who(m.groupValues[1]) },
        Rule("(.+) anrufen") { m -> "call " + who(m.groupValues[1]) },
        Rule("(?:pause|pausieren|pausiere|musik (?:aus|stoppen|anhalten)|stopp|stop|halt|anhalten|ruhe|leise)", "pause"),
        Rule("(?:weiter|fortsetzen|weiterspielen|spiel weiter|musik an|musik|play|abspielen|wiedergabe)", "continue"),
        Rule(
            "(?:nächster|naechster|nächstes|naechstes|nächste|naechste|nächster titel|nächstes lied|nächster song|überspringen|" +
                "ueberspringen|skip|anderes lied|ein anderes lied|was anderes|etwas anderes)",
            "next",
        ),
        Rule("(?:vorheriger|vorheriges|vorherige|vorheriger titel|vorheriges lied|zurück zum letzten lied|letztes lied|das lied davor)", "previous"),
        Rule("(?:nochmal|noch mal|wiederholen|wiederhole)(?: das lied| den titel| diesen song)?(?: von vorne)?", "play it again"),
        Rule(
            "(?:was läuft|was laeuft|was läuft gerade|was ist das für ein lied|was für ein lied ist das|welches lied ist das|" +
                "wer singt|wer singt das|wie heißt das lied|wie heisst das lied)(?: .*)?",
            "what's playing",
        ),
        Rule("(?:spiel mir|spiele mir|spiel|spiele|hör|höre) (.+)", "play $1"),
        Rule("(.+) (?:abspielen|spielen)", "play $1"),
        Rule("(?:finde|such|suche|zeig mir)(?: mir)? (.+)") { m ->
            m.groupValues[1].takeIf { place(it).startsWith("the nearest ") }?.let(::navigate)
        },
        Rule("(?:zeig mir|zeige mir|öffne|oeffne|starte|zeig|zeige) (.+)") { m -> "open " + app(place(m.groupValues[1])) },
        Rule("(.+) (?:öffnen|oeffnen|starten)") { m -> "open " + app(place(m.groupValues[1])) },
        Rule("(?:geh|gehe|wechsel|wechsle) (.+)") { m ->
            val p = place(m.groupValues[1])
            if (app(p) != p) "go to " + app(p) else "go to " + remember(p, m.groupValues[1])
        },
        Rule(
            "(?:navigiere|navigier|navigation|route|bring mich|bringe mich|bring uns|fahr mich|fahre mich|fahr uns|fahr|fahre|führe mich|fuehre mich|" +
                "lotse mich|wie komme ich|wie kommen wir)(?: bitte)? (.+)",
        ) { m -> navigate(m.groupValues[1]) },
        Rule("(?:mich|uns) (.+) (?:bringen|fahren|führen|fuehren|navigieren|lotsen)") { m -> navigate(m.groupValues[1]) },
        Rule("(?:wie spät ist es|wie spaet ist es|wie viel uhr ist es|wieviel uhr ist es|uhrzeit|die uhrzeit|zeit)", "what time is it"),
        Rule("(?:wie ist das wetter|wie wird das wetter|wetter|das wetter|regnet es|wird es regnen|wettervorhersage|vorhersage)(?: .*)?", "what's the weather"),
    )

    /** "14:30 Uhr"; a time written with AM or PM stays as it is. */
    private fun uhr(time: String) = if (Regex("^\\d{1,2}:\\d{2}$").matches(time)) "$time Uhr" else time

    private fun left(english: String) = span(english, "Stunde" to "Stunden", "Minute" to "Minuten", "und")

    override val answers: Map<Said, (List<String>) -> String> = mapOf(
        Said.HELP to t("Das kannst du sagen."),
        Said.UNKNOWN to t("Das kann ich noch nicht."),
        Said.WHERE_TO to t("Wohin soll es gehen?"),
        Said.NO_NAV_APPS to t("Du hast keine Navigations-App installiert."),
        Said.NO_NAV_APP_CALLED to t("Ich finde keine Navigations-App namens $1."),
        Said.NOT_NAV_APP to t("Das ist keine Navigations-App."),
        Said.FINDING_NEAREST to t("Suche in der Nähe: $1, mit $2."),
        Said.NAVIGATING to { g -> "Navigation ${asSaid(g[1]) ?: "nach ${g[1]}"} mit ${g[2]} gestartet." },
        Said.OPENING_SETTINGS to t("Ich öffne die Einstellungen."),
        Said.OPENING_VEHICLE_DATA to t("Ich öffne die Fahrzeugdaten."),
        Said.OPENING to t("Ich öffne $1."),
        Said.OPEN_MUSIC_FIRST to t("Öffne zuerst eine Musik-App und frag dann noch einmal."),
        Said.MUSIC_APP_LATE to t("Die Musik-App hat sich nicht rechtzeitig geöffnet. Versuch es noch einmal."),
        Said.IGNORES_SEARCH to t("$1 ignoriert Sprachsuchen. Wähle stattdessen etwas auf dem Bildschirm."),
        Said.LOOKING_FOR to t("Ich suche $1 in $2."),
        Said.NEEDS_NOTIFICATIONS to t("Zum Vorlesen von Nachrichten braucht myCar Zugriff auf Benachrichtigungen. Erlaube ihn auf dem Telefon."),
        Said.NO_MESSAGE_FROM to t("Ich finde keine Nachricht von $1, auf die ich antworten kann."),
        Said.NO_MESSAGES_YET to t("Es gibt noch keine Nachrichten."),
        Said.NO_NEW_MESSAGES to t("Keine neuen Nachrichten."),
        Said.ENDING_ROUTE to t("Ich beende die Route."),
        Said.PAUSED to t("Pausiert."),
        Said.NOTHING_PLAYING to t("Gerade läuft nichts."),
        Said.SKIPPING to t("Nächster Titel."),
        Said.PREVIOUS_TRACK to t("Vorheriger Titel."),
        Said.PLAYING to t("Weiter geht's."),
        Said.PLAYING_APP to { g -> if (g[1] == "the music") "Die Musik läuft." else "Ich starte ${g[1]}." },
        Said.GOING_BACK to t("Zurück."),
        Said.BACK_TO_CAR to t("Zurück zum Bildschirm des Autos."),
        Said.HEADING_HOME to t("Zurück zum Startbildschirm."),
        Said.YOUR_APPS to t("Hier sind deine Apps."),
        Said.VEHICLE_DATA_OFF to t("Die Fahrzeugdaten sind aus. Schalte sie in den Einstellungen unter den experimentellen Funktionen ein."),
        Said.TIME to { g -> "Es ist ${uhr(g[1])}." },
        Said.WEATHER to { g -> listOfNotNull(g[1], sky(g[2])).joinToString(", ") + ", in ${g[3]}." },
        Said.NO_WEATHER to t("Ich habe das Wetter noch nicht."),
        Said.NO_ROUTE to t("Auf der Karte ist keine Route."),
        Said.ARRIVE_AT_IN to { g -> "Gegen ${uhr(g[1])}, in ${left(g[2])}." },
        Said.ARRIVE_AT to { g -> "Gegen ${uhr(g[1])}." },
        Said.ARRIVE_IN to { g -> "In etwa ${left(g[1])}." },
        Said.MAP_HASNT_SAID to t("Die Karte hat es noch nicht gesagt."),
        Said.NEEDS_PHONE to t("myCar braucht zuerst die Berechtigung Telefon: erlaube sie auf dem Bildschirm des Telefons."),
        Said.NEEDS_CALL_LOG to t("myCar braucht zuerst die Anrufliste: erlaube sie auf dem Telefon."),
        Said.NO_REDIAL to t("Es gibt keinen Anruf zum Wiederholen."),
        Said.NO_CALL_BACK to t("Es gibt keinen Anruf zum Zurückrufen."),
        Said.PRIVATE_NUMBER to t("Der Anruf kam von einer unterdrückten Nummer, Zurückrufen geht nicht."),
        Said.CALLING_AGAIN to t("Ich rufe $1 noch einmal an."),
        Said.CALLING_BACK to t("Ich rufe $1 zurück."),
        Said.WHO_TO_CALL to t("Wen soll ich anrufen?"),
        Said.NEEDS_CONTACTS to t("myCar braucht zuerst die Berechtigungen Telefon und Kontakte: erlaube sie auf dem Bildschirm des Telefons."),
        Said.NOT_IN_CONTACTS to t("Ich finde $1 nicht in deinen Kontakten."),
        Said.NO_SUCH_NUMBER to { g ->
            val kind = mapOf(
                "mobile" to "Handynummer", "cell" to "Handynummer", "phone" to "Telefonnummer", "home" to "Festnetznummer",
                "work" to "geschäftliche Nummer",
            )
            "${g[1]} hat keine ${kind[g[2]] ?: "Nummer ${g[2]}"}. " +
                if (g[3] == "the other one") "Stattdessen die andere Nummer anrufen?" else "Stattdessen die Nummer ${g[3]} anrufen?"
        },
        Said.CALL_QUESTION to t("$1 anrufen?"),
        Said.CALLING_TYPE to t("Ich rufe $1 an, $2."),
        Said.CALLING to t("Ich rufe $1 an."),
        Said.NO_APP_CALLED to t("Ich finde keine App namens $1."),
        Said.SOMETHING_WRONG to t("Etwas ist schiefgelaufen. Versuch es noch einmal."),
        Said.DONE to t("Erledigt."),
        Said.HOME_ASSISTANT_SILENT to t("Home Assistant hat nicht geantwortet."),
        Said.NEEDS_MIC to t("Dafür braucht myCar die Mikrofon-Berechtigung."),
        Said.AFTER_CALL to t("Sprachbefehle gehen wieder, wenn der Anruf vorbei ist."),
        Said.DIDNT_CATCH to t("Das habe ich nicht verstanden."),
        Said.RECOGNIZER_BUSY to t("Die Spracherkennung ist beschäftigt."),
        Said.RECOGNIZER_NETWORK to t("Die Spracherkennung braucht ein Netz, das sie nicht hat."),
        Said.RECOGNITION_FAILED to t("Die Spracherkennung hat nicht geklappt."),
        Said.SPEECH_NOT_READY to t("Die Sprache ist noch nicht bereit."),
        Said.MODEL_NOT_LOADED to t("Das Sprachmodell wurde nicht geladen."),
        Said.MODEL_NOT_DOWNLOADED to t("Lade zuerst ein Sprachmodell in den Einstellungen von myCar auf dem Telefon herunter."),
        Said.MIC_SILENCED to t("Das Mikrofon des Telefons ist stumm. Entsperre das Telefon und versuch es noch einmal."),
        Said.MIC_UNAVAILABLE to t("Das Mikrofon ist gerade nicht verfügbar."),
        Said.MISSED_CALL_FROM to t("Verpasster Anruf von $1."),
        Said.WANT_TO_REPLY to t("Möchtest du antworten?"),
        Said.GO_AHEAD to t("Ich höre."),
        Said.NO_CAR_REPLIES to t("$1 erlaubt keine Antworten aus dem Auto."),
        Said.REPLY_AFTER_CALL to t("Sprachantworten gehen wieder, wenn der Anruf vorbei ist."),
        Said.YOUR_REPLY_TO to t("Was möchtest du $1 antworten?"),
        Said.YOUR_REPLY to t("Was möchtest du antworten?"),
        Said.SENDING_TO to t("Ich sende an $1: $2"),
        Said.SENDING to t("Ich sende: $1"),
        Said.NOT_SENT to t("Nicht gesendet."),
        Said.MESSAGE_SENT to t("Nachricht gesendet."),
        Said.REPLY_WITHDRAWN to t("$1 hat die Antwort zurückgezogen. Öffne die App auf dem Telefon, um zu antworten."),
        Said.NOW_PLAYING to t("$1, von $2."),
    )

    private fun sky(english: String): String? = when (english) {
        "Clear" -> "klar"
        "Mostly clear" -> "überwiegend klar"
        "Partly cloudy" -> "teilweise bewölkt"
        "Overcast" -> "bedeckt"
        "Fog" -> "Nebel"
        "Drizzle" -> "Nieselregen"
        "Rain" -> "Regen"
        "Snow" -> "Schnee"
        "Rain showers" -> "Regenschauer"
        "Snow showers" -> "Schneeschauer"
        "Thunderstorm" -> "Gewitter"
        "-" -> null
        else -> english
    }

    override val yes = setOf("ja", "jawohl", "klar", "okay", "ok", "gerne", "gern", "antworten", "sicher", "genau")
    override val no = setOf("nein", "abbrechen", "lass", "lass es", "nichts", "stopp", "stop", "nicht jetzt")
    override val cancel = setOf("abbrechen", "nein", "stopp", "stop", "halt")
    override val change = setOf("ändern", "aendern", "ändere", "korrigieren", "nochmal", "noch mal", "falsch")
    override val send = setOf("senden", "sende", "schicken", "schick", "abschicken", "ja", "okay", "ok")

    override val samples = listOf("Spiel Queen", "Was läuft gerade?", "Nächstes Lied", "Ruf Mama an", "Öffne Organic Maps", "Pause", "Hilfe")

    override fun says(who: String, text: String) = "$who schreibt: $text"
    override fun inGroup(sender: String, group: String) = "$sender in $group"

    override val help = listOf(
        "Navigiere zu…" to "Eine Route. Sag „mit Organic Maps“ dazu, um die App zu wählen",
        "Spiel…" to "Ein Lied, einen Künstler oder ein Album in der offenen Musik-App",
        "Pause · Weiter · Nächster · Vorheriger" to "Die Musik steuern",
        "Was läuft gerade?" to "Das Lied und wer es singt",
        "Ruf … an" to "Einen Kontakt beim Namen. Sag „auf dem Handy“ dazu, um die Nummer zu wählen",
        "Ruf zurück · Wahlwiederholung" to "Der letzte eingehende Anruf, oder der letzte ausgehende",
        "Lies meine Nachrichten · Antworte …" to "Neue Nachrichten vorgelesen, oder eine Antwort an den, der geschrieben hat",
        "Navigation beenden" to "Beendet die Route",
        "Wann komme ich an?" to "Die Ankunftszeit und die restliche Fahrzeit",
        "Öffne…" to "Eine App, wie Browser, Telefon oder Bildschirmfreigabe",
        "Beenden" to "Zurück zum Bildschirm des Autos",
        "Startbildschirm · Einstellungen" to "In myCar bewegen",
        "Wie spät ist es? · Wie ist das Wetter?" to "Die Uhrzeit, oder das Wetter",
        "Hilfe" to "Diese Liste",
    )

    override val labels = mapOf(
        "Listening…" to "Ich höre…",
        "One moment…" to "Einen Moment…",
        "You can say" to "Du kannst sagen",
        "Say “cancel” or “change it”" to "Sag „abbrechen“ oder „ändern“",
        "Do you want to reply? Say yes or no." to "Möchtest du antworten? Sag ja oder nein.",
        "Get ready to say your reply." to "Mach dich bereit für deine Antwort.",
        "Say your reply" to "Sag deine Antwort",
    )
}
