package io.github.sirionero.mycar.car

/** The assistant in Italian (see [AssistantLanguage]). */
internal object ItalianPhrases : Phrasebook() {

    override val leadFillers = listOf(
        "ehi mycar", "ok mycar", "ehi my car", "ok my car", "mycar", "my car", "ehi", "ok", "okay", "allora", "senti",
        "per favore", "per piacere", "mi puoi", "me lo puoi", "puoi", "potresti", "riesci a", "vorrei", "voglio", "dai", "ora", "adesso",
    )
    override val tailFillers = listOf("grazie mille", "per favore", "per piacere", "grazie", "subito", "adesso", "ora", "dai")
    override val appWords = listOf(" con ", " su ", " usando ", " tramite ")

    private val NEAR = Regex("^(.+?) (?:più vicin[oaie]|qui vicino|vicino a me|nei dintorni|in zona)((?: (?:con|su|usando|tramite) .+)?)$")

    /** "al benzinaio più vicino" → "the nearest benzinaio", "a Roma" → "roma": the place, for the map. */
    private fun place(said: String): String {
        var t = said.trim()
        t = t.replace(Regex("^(?:verso|fino a|fino al|fino alla|per)\\s+"), "")
        t = t.replace(Regex("^(?:a|ad|al|alla|allo|ai|alle|agli|in|nel|nella|nello|nei|negli|nelle|dal|dalla|dallo|da|il|lo|la|i|gli|le|un|una|uno)\\s+"), "")
        t = t.replace(Regex("^(?:all'|dall'|nell'|l'|un')"), "")
        NEAR.matchEntire(t)?.let { return "the nearest " + it.groupValues[1] + it.groupValues[2] }
        return t
    }

    /** "il benzinaio più vicino" out of "al benzinaio più vicino": what's near, with its article. */
    private fun withArticle(said: String): String {
        for ((from, to) in ARTICLES) if (said.startsWith(from)) return to + said.removePrefix(from)
        return said
    }

    private val ARTICLES = listOf(
        "al " to "il ", "alla " to "la ", "allo " to "lo ", "ai " to "i ", "alle " to "le ", "agli " to "gli ", "all'" to "l'",
        "a un " to "un ", "a una " to "una ", "ad un " to "un ", "ad una " to "una ",
    )

    /**
     * Where to, said back: "la stazione" (the recognizer missed the "al") becomes "alla stazione",
     * and "casa mia" is "casa tua".
     */
    private fun there(said: String): String {
        val t = said.replace(Regex("\\bcasa mia\\b"), "casa tua")
        for ((from, to) in BARE) if (t.startsWith(from)) return to + t.removePrefix(from)
        return t
    }

    private val BARE = listOf("il " to "al ", "lo " to "allo ", "la " to "alla ", "i " to "ai ", "gli " to "agli ", "le " to "alle ", "l'" to "all'")

    /** "navigate to" the place in [said], remembered as said. */
    private fun navigate(said: String) = "navigate to " + remember(place(said), said)

    /** Who: "ad Anna", "la mamma" → "anna", "mamma". */
    private fun who(raw: String): String =
        raw.trim().replace(Regex("^(?:(?:a|ad|al|alla|allo|il|lo|la)\\s+|l')"), "").trim()

    private val numberType = mapOf(
        "cellulare" to "mobile", "telefonino" to "mobile", "mobile" to "mobile",
        "fisso" to "home", "casa" to "home", "lavoro" to "work", "ufficio" to "work",
    )

    /** myCar's own apps by their Italian names. */
    private fun app(name: String) = when (name) {
        "telefono" -> "phone"
        "condivisione schermo", "condivisione dello schermo" -> "screen sharing"
        "dati dell'auto", "dati del veicolo", "dati della macchina" -> "vehicle data"
        "impostazioni" -> "settings"
        else -> name
    }

    override val commands = listOf(
        Rule("(?:aiuto|cosa posso dire|che cosa posso dire|cosa sai fare|che cosa sai fare|comandi|comandi vocali)", "help"),
        // Screens of myCar's own, before "vai a…" is taken for a place.
        Rule("(?:vai alla|torna alla|apri la) (?:home|schermata principale)|home|schermata principale", "go home"),
        Rule("(?:(?:apri|vai alle|vai nelle|mostrami|fammi vedere) )?(?:le )?impostazioni", "open settings"),
        Rule("(?:mostrami |apri |fammi vedere )?(?:le mie app|tutte le app|le app|app|applicazioni)", "open apps"),
        Rule("esci|esci da mycar|chiudi mycar", "exit"),
        Rule("(?:torna )?indietro", "go back"),
        // The route.
        Rule("(?:ferma|interrompi|termina|chiudi|annulla|stop|basta)(?: la| il)? (?:navigazione|percorso|navigatore)|smetti di navigare", "stop navigation"),
        Rule(
            "(?:quando arrivo|quando arriviamo|a che ora arrivo|a che ora arriviamo|quanto manca|quanto ci manca|quanto tempo manca|" +
                "tra quanto arrivo|tra quanto arriviamo|orario di arrivo)(?: .*)?",
            "when do i arrive",
        ),
        // Messages.
        Rule("(?:leggi|leggimi|leggere|controlla)(?: .*)? (?:messaggi|messaggio|sms)(?: .*)?") { m ->
            val said = m.value
            when {
                "nuov" in said || "non lett" in said -> "read my new messages"
                "ultim" in said -> "read my last message"
                else -> "read my messages"
            }
        },
        Rule("(?:rispondi|rispondere|scrivi|scrivere|manda un messaggio|mandare un messaggio|invia un messaggio|scrivi un messaggio) (?:a|ad|al|alla|allo) (.+)") { m ->
            "reply to " + who(m.groupValues[1])
        },
        // Calls.
        Rule(
            "(?:richiama|richiamalo|richiamala|richiamali|richiamare|richiama la chiamata persa|richiama l'ultima chiamata(?: persa)?|" +
                "rispondi alla chiamata persa)",
            "call back",
        ),
        Rule(
            "(?:ripeti l'ultima chiamata|rifai l'ultima chiamata|chiama di nuovo|richiama l'ultimo numero|chiama l'ultimo numero|" +
                "ricomponi(?: il numero| l'ultimo numero)?)",
            "redial",
        ),
        Rule("(?:chiama|chiamare|telefona|telefonare|fai una chiamata|chiamami) (.+?) (?:sul|al|a) (cellulare|telefonino|mobile|fisso|casa|lavoro|ufficio)") { m ->
            "call ${who(m.groupValues[1])} on ${numberType.getValue(m.groupValues[2])}"
        },
        Rule("(?:chiama|chiamare|telefona|fai una chiamata|fai una telefonata)", "call"),
        Rule("(?:chiama|chiamare|telefona|telefonare|fai una chiamata|chiamami) (.+)") { m -> "call " + who(m.groupValues[1]) },
        // The music, before "metti…" is taken for a song.
        Rule("(?:pausa|metti in pausa|ferma la musica|ferma|stop|spegni la musica|basta musica|basta|silenzio)", "pause"),
        Rule(
            "(?:riprendi|continua|riparti|fai ripartire la musica|rimetti la musica|metti la musica|accendi la musica|" +
                "musica|metti un po' di musica|play)",
            "continue",
        ),
        Rule(
            "(?:avanti|vai avanti|prossima|prossimo|successiva|successivo|prossima canzone|prossimo brano|canzone successiva|" +
                "brano successivo|salta|salta questa|salta canzone|cambia canzone|un'altra|un'altra canzone|metti un'altra canzone|" +
                "metti qualcos'altro|qualcos'altro)",
            "next",
        ),
        Rule("(?:precedente|canzone precedente|brano precedente|la canzone di prima|quella di prima|torna alla canzone di prima)", "previous"),
        Rule("(?:ripeti|rimetti|ricomincia)(?: questa| la| questo| il)?(?: canzone| brano| pezzo)?(?: da capo| dall'inizio)?", "play it again"),
        Rule(
            "(?:che canzone è|che canzone e|chi canta|cosa sta suonando|cos'è questa canzone|come si chiama questa canzone|che brano è|" +
                "che cosa sto ascoltando|cosa sto ascoltando)(?: questa| questo| adesso)?",
            "what's playing",
        ),
        Rule("(?:metti|mettere|riproduci|riprodurre|suona|suonare|fammi sentire|fammi ascoltare|ascolta|ascoltare|ascoltiamo|sentire) (.+)", "play $1"),
        // Places: "trova…" only for what's near; "vai a…" a place or an app; the rest a place.
        Rule("(?:trova|cerca|cercami|trovami) (.+)") { m ->
            m.groupValues[1].takeIf { place(it).startsWith("the nearest ") }?.let(::navigate)
        },
        Rule("(?:vai su|apri|aprire|avvia|lancia|mostrami|fammi vedere) (.+)") { m -> "open " + app(place(m.groupValues[1])) },
        Rule("(?:vai|andiamo|andare|torna|tornare|torniamo) (.+)") { m ->
            val p = place(m.groupValues[1])
            if (app(p) != p) "go to " + app(p) else "go to " + remember(p, m.groupValues[1])
        },
        Rule(
            "(?:portami|portaci|accompagnami|guidami|navigami|naviga|navigare|indicazioni(?: stradali)?|percorso|" +
                "come (?:arrivo|si arriva|faccio ad arrivare|ci arrivo)) (.+)",
        ) { m -> navigate(m.groupValues[1]) },
        // The time and the weather.
        Rule("(?:che ore sono|che ora è|che ora e|dimmi l'ora|mi dici l'ora|l'ora|ora)", "what time is it"),
        Rule("(?:che tempo fa|che tempo farà|com'è il tempo|meteo|il meteo|previsioni(?: del tempo)?|piove|pioverà)(?: .*)?", "what's the weather"),
    )

    /** "le 14:30", "l'1:30": a time with its article. */
    private fun at(time: String) = if (oneOClock(time)) "l'${time.removePrefix("0")}" else "le $time"

    private fun left(english: String) = span(english, "ora" to "ore", "minuto" to "minuti", "e")

    override val answers: Map<Said, (List<String>) -> String> = mapOf(
        Said.HELP to t("Ecco cosa puoi dire."),
        Said.UNKNOWN to t("Questo non lo so ancora fare."),
        Said.WHERE_TO to t("Dove vuoi andare?"),
        Said.NO_NAV_APPS to t("Non hai app di navigazione installate."),
        Said.NO_NAV_APP_CALLED to t("Non trovo un'app di navigazione che si chiama $1."),
        Said.NOT_NAV_APP to t("Non è un'app di navigazione."),
        Said.FINDING_NEAREST to { g -> "Cerco ${asSaid(g[1])?.let(::withArticle) ?: "${g[1]} qui vicino"} con ${g[2]}." },
        Said.NAVIGATING to { g -> "Ti porto ${asSaid(g[1])?.let(::there) ?: "a ${g[1]}"} con ${g[2]}." },
        Said.OPENING_SETTINGS to t("Apro le impostazioni."),
        Said.OPENING_VEHICLE_DATA to t("Apro i dati dell'auto."),
        Said.OPENING to t("Apro $1."),
        Said.OPEN_MUSIC_FIRST to t("Prima apri un'app musicale, poi chiedimelo di nuovo."),
        Said.MUSIC_APP_LATE to t("L'app musicale non si è aperta in tempo. Riprova."),
        Said.IGNORES_SEARCH to t("$1 ignora le ricerche vocali. Scegli qualcosa sul suo schermo."),
        Said.LOOKING_FOR to t("Cerco $1 su $2."),
        Said.NEEDS_NOTIFICATIONS to t("Per leggere i messaggi myCar ha bisogno dell'accesso alle notifiche. Consentilo sul telefono."),
        Said.NO_MESSAGE_FROM to t("Non trovo un messaggio di $1 a cui rispondere."),
        Said.NO_MESSAGES_YET to t("Non ci sono ancora messaggi."),
        Said.NO_NEW_MESSAGES to t("Nessun messaggio nuovo."),
        Said.ENDING_ROUTE to t("Termino il percorso."),
        Said.PAUSED to t("In pausa."),
        Said.NOTHING_PLAYING to t("Non sta suonando niente."),
        Said.SKIPPING to t("Avanti."),
        Said.PREVIOUS_TRACK to t("Torno al brano precedente."),
        Said.PLAYING to t("Riprendo."),
        Said.PLAYING_APP to { g -> if (g[1] == "the music") "Metto la musica." else "Metto ${g[1]}." },
        Said.GOING_BACK to t("Torno indietro."),
        Said.BACK_TO_CAR to t("Torno allo schermo dell'auto."),
        Said.HEADING_HOME to t("Torno alla schermata principale."),
        Said.YOUR_APPS to t("Ecco le tue app."),
        Said.VEHICLE_DATA_OFF to t("I dati dell'auto sono spenti. Accendili nelle impostazioni, tra le funzioni sperimentali."),
        Said.TIME to { g -> "${if (oneOClock(g[1])) "È" else "Sono"} ${at(g[1])}." },
        Said.WEATHER to { g -> listOfNotNull(g[1], sky(g[2])).joinToString(", ") + ", a ${g[3]}." },
        Said.NO_WEATHER to t("Non ho ancora il meteo."),
        Said.NO_ROUTE to t("Non c'è un percorso sulla mappa."),
        Said.ARRIVE_AT_IN to { g -> "Verso ${at(g[1])}, tra ${left(g[2])}." },
        Said.ARRIVE_AT to { g -> "Verso ${at(g[1])}." },
        Said.ARRIVE_IN to { g -> "Tra circa ${left(g[1])}." },
        Said.MAP_HASNT_SAID to t("La mappa non l'ha ancora detto."),
        Said.NEEDS_PHONE to t("Prima myCar ha bisogno del permesso Telefono: consentilo dallo schermo del telefono."),
        Said.NEEDS_CALL_LOG to t("Prima myCar ha bisogno del registro chiamate: consentilo sul telefono."),
        Said.NO_REDIAL to t("Non c'è una chiamata da ripetere."),
        Said.NO_CALL_BACK to t("Non c'è una chiamata da richiamare."),
        Said.PRIVATE_NUMBER to t("Quella chiamata era da un numero privato: non si può richiamare."),
        Said.CALLING_AGAIN to t("Richiamo $1."),
        Said.CALLING_BACK to t("Richiamo $1."),
        Said.WHO_TO_CALL to t("Chi devo chiamare?"),
        Said.NEEDS_CONTACTS to t("Prima myCar ha bisogno dei permessi Telefono e Contatti: consentili dallo schermo del telefono."),
        Said.NOT_IN_CONTACTS to t("Non trovo $1 nei tuoi contatti."),
        Said.NO_SUCH_NUMBER to { g ->
            val kind = mapOf("mobile" to "di cellulare", "cell" to "di cellulare", "phone" to "di telefono", "home" to "di casa", "work" to "di lavoro")
            "${g[1]} non ha un numero ${kind[g[2]] ?: g[2]}. Chiamo ${if (g[3] == "the other one") "l'altro numero" else "il numero di ${g[3]}"}?"
        },
        Said.CALL_QUESTION to t("Chiamo $1?"),
        Said.CALLING_TYPE to t("Chiamo $1, $2."),
        Said.CALLING to t("Chiamo $1."),
        Said.NO_APP_CALLED to t("Non trovo un'app che si chiama $1."),
        Said.SOMETHING_WRONG to t("Qualcosa è andato storto. Riprova."),
        Said.DONE to t("Fatto."),
        Said.HOME_ASSISTANT_SILENT to t("Home Assistant non ha risposto."),
        Said.NEEDS_MIC to t("Serve il permesso del microfono."),
        Said.AFTER_CALL to t("I comandi vocali funzionano quando finisce la chiamata."),
        Said.DIDNT_CATCH to t("Non ho capito."),
        Said.RECOGNIZER_BUSY to t("Il riconoscimento vocale è occupato."),
        Said.RECOGNIZER_NETWORK to t("Il riconoscimento vocale ha bisogno di una rete che non c'è."),
        Said.RECOGNITION_FAILED to t("Il riconoscimento vocale non è riuscito."),
        Said.SPEECH_NOT_READY to t("La voce non è ancora pronta."),
        Said.MODEL_NOT_LOADED to t("Il modello vocale non si è caricato."),
        Said.MODEL_NOT_DOWNLOADED to t("Prima scarica un modello vocale nelle impostazioni di myCar sul telefono."),
        Said.MIC_SILENCED to t("Il microfono del telefono è silenziato. Sblocca il telefono e riprova."),
        Said.MIC_UNAVAILABLE to t("Il microfono non è disponibile adesso."),
        Said.MISSED_CALL_FROM to t("Chiamata persa da $1."),
        Said.WANT_TO_REPLY to t("Vuoi rispondere?"),
        Said.GO_AHEAD to t("Dimmi."),
        Said.NO_CAR_REPLIES to t("$1 non permette di rispondere dall'auto."),
        Said.REPLY_AFTER_CALL to t("La risposta a voce funziona quando finisce la chiamata."),
        Said.YOUR_REPLY_TO to t("Cosa rispondi a $1?"),
        Said.YOUR_REPLY to t("Cosa rispondi?"),
        Said.SENDING_TO to t("Invio a $1: $2"),
        Said.SENDING to t("Invio: $1"),
        Said.NOT_SENT to t("Non inviato."),
        Said.MESSAGE_SENT to t("Messaggio inviato."),
        Said.REPLY_WITHDRAWN to t("$1 ha ritirato la risposta. Apri l'app sul telefono per rispondere."),
        Said.NOW_PLAYING to t("$1, di $2."),
    )

    /** The sky as Weather says it, in Italian; null for none ("-"). */
    private fun sky(english: String): String? = when (english) {
        "Clear" -> "sereno"
        "Mostly clear" -> "quasi sereno"
        "Partly cloudy" -> "parzialmente nuvoloso"
        "Overcast" -> "coperto"
        "Fog" -> "nebbia"
        "Drizzle" -> "pioviggine"
        "Rain" -> "pioggia"
        "Snow" -> "neve"
        "Rain showers" -> "rovesci"
        "Snow showers" -> "rovesci di neve"
        "Thunderstorm" -> "temporale"
        "-" -> null
        else -> english
    }

    override val yes = setOf("sì", "si", "certo", "ok", "okay", "va bene", "rispondi", "volentieri", "dai")
    override val no = setOf("no", "annulla", "lascia", "lascia stare", "niente", "stop", "basta")
    override val cancel = setOf("annulla", "no", "stop", "basta", "lascia", "ferma")
    override val change = setOf("cambia", "modifica", "rifai", "ripeti", "sbagliato", "correggi", "di nuovo")
    override val send = setOf("invia", "manda", "sì", "si", "ok", "okay", "vai")

    override val samples = listOf("Metti Vasco Rossi", "Che canzone è?", "Prossima canzone", "Chiama la mamma", "Apri Organic Maps", "Pausa", "Aiuto")

    override fun says(who: String, text: String) = "$who dice: $text"
    override fun inGroup(sender: String, group: String) = "$sender in $group"

    override val help = listOf(
        "Portami a…" to "Indicazioni stradali. Aggiungi \"con Organic Maps\" per scegliere l'app",
        "Metti…" to "Una canzone, un artista o un album nell'app musicale aperta",
        "Pausa · Riprendi · Avanti · Precedente" to "Comandi della musica",
        "Che canzone è?" to "La canzone e chi la canta",
        "Chiama…" to "Un contatto per nome. Aggiungi \"sul cellulare\" per scegliere il numero",
        "Richiama · Chiama di nuovo" to "L'ultima chiamata arrivata, o l'ultima fatta",
        "Leggi i messaggi · Rispondi a…" to "I messaggi nuovi letti ad alta voce, o una risposta a chi ti ha scritto",
        "Ferma la navigazione" to "Termina il percorso",
        "Quando arrivo?" to "L'ora d'arrivo e il tempo che manca",
        "Apri…" to "Un'app, come Browser, Telefono o Condivisione schermo",
        "Esci" to "Torna allo schermo dell'auto",
        "Vai alla home · Impostazioni" to "Muoversi in myCar",
        "Che ore sono? · Che tempo fa?" to "L'ora, o il meteo",
        "Aiuto" to "Questo elenco",
    )

    override val labels = mapOf(
        "Listening…" to "Ti ascolto…",
        "One moment…" to "Un momento…",
        "You can say" to "Puoi dire",
        "Say “cancel” or “change it”" to "Di' «annulla» o «cambia»",
        "Do you want to reply? Say yes or no." to "Vuoi rispondere? Di' sì o no.",
        "Get ready to say your reply." to "Preparati a dire la risposta.",
        "Say your reply" to "Di' la risposta",
        "Anything else" to "Tutto il resto",
        "Home Assistant answers it: your home, and any question its voice assistant knows." to "Risponde Home Assistant: la casa, e le domande che il suo assistente vocale conosce.",
    )
}
