package io.github.sirionero.mycar.car

/** The assistant in Spanish (see [AssistantLanguage]). */
internal object SpanishPhrases : Phrasebook() {

    override val leadFillers = listOf(
        "oye mycar", "ok mycar", "oye my car", "ok my car", "mycar", "my car", "oye", "ok", "vale", "bueno",
        "por favor", "me puedes", "puedes", "podrías", "podrias", "quiero", "quisiera", "me gustaría", "me gustaria", "ahora",
    )
    override val tailFillers = listOf("muchas gracias", "por favor", "gracias", "ahora")
    override val appWords = listOf(" con ", " en ", " usando ")

    private val NEAR = Regex(
        "^(.+?) (?:más cercan[oa]s?|mas cercan[oa]s?|cerca de mí|cerca de mi|cerca de aquí|cerca de aqui|por aquí|por aqui|cerca)" +
            "((?: (?:con|en|usando) .+)?)$",
    )

    /** "a la gasolinera más cercana" → "the nearest gasolinera", "a Madrid" → "madrid": the place, for the map. */
    private fun place(said: String): String {
        var t = said.trim()
        t = t.replace(Regex("^(?:hacia|hasta|para)\\s+"), "")
        t = t.replace(Regex("^(?:a la|a las|a los|al|a|en la|en el|en|el|la|los|las|un|una)\\s+"), "")
        NEAR.matchEntire(t)?.let { return "the nearest " + it.groupValues[1] + it.groupValues[2] }
        return t
    }

    /** "la gasolinera más cercana" out of "a la gasolinera más cercana": what's near, with its article. */
    private fun withArticle(said: String): String {
        val articles = listOf("al " to "el ", "a la " to "la ", "a los " to "los ", "a las " to "las ", "a un " to "un ", "a una " to "una ")
        for ((from, to) in articles) if (said.startsWith(from)) return to + said.removePrefix(from)
        return said
    }

    /** Where to, said back: "la playa" (the recognizer missed the "a") becomes "a la playa", "mi casa" is "tu casa". */
    private fun there(said: String): String {
        val t = said.replace(Regex("\\bmi casa\\b"), "tu casa")
        for ((from, to) in listOf("el " to "al ", "la " to "a la ", "los " to "a los ", "las " to "a las ")) {
            if (t.startsWith(from)) return to + t.removePrefix(from)
        }
        return t
    }

    private fun navigate(said: String) = "navigate to " + remember(place(said), said)

    private fun who(raw: String): String = raw.trim().replace(Regex("^(?:a la|a|al|el|la)\\s+"), "").trim()

    private val numberType = mapOf(
        "móvil" to "mobile", "movil" to "mobile", "celular" to "mobile",
        "casa" to "home", "fijo" to "home", "trabajo" to "work", "oficina" to "work",
    )

    private fun app(name: String) = when (name) {
        "teléfono", "telefono" -> "phone"
        "compartir pantalla", "pantalla compartida" -> "screen sharing"
        "datos del coche", "datos del vehículo", "datos del vehiculo", "datos del auto" -> "vehicle data"
        "ajustes", "configuración", "configuracion" -> "settings"
        "navegador" -> "browser"
        else -> name
    }

    override val commands = listOf(
        Rule("(?:ayuda|qué puedo decir|que puedo decir|qué sabes hacer|que sabes hacer|comandos|comandos de voz)", "help"),
        Rule("(?:ve a|vuelve a|abre) (?:la )?(?:pantalla de inicio|pantalla principal|inicio)|inicio|pantalla de inicio|pantalla principal", "go home"),
        Rule("(?:(?:abre|ve a|muéstrame|muestrame) )?(?:los |la )?(?:ajustes|configuración|configuracion)", "open settings"),
        Rule("(?:(?:muéstrame|muestrame|abre) )?(?:mis |las |todas las )?(?:apps|aplicaciones)", "open apps"),
        Rule("salir|sal|sal de mycar|cierra mycar", "exit"),
        Rule("(?:vuelve |volver |ir )?(?:atrás|atras)|regresa", "go back"),
        Rule(
            "(?:para|detén|deten|termina|cancela|finaliza|quita)(?: la| el)? (?:navegación|navegacion|ruta|guía|guia)|deja de navegar",
            "stop navigation",
        ),
        Rule(
            "(?:cuándo llego|cuando llego|cuándo llegamos|cuando llegamos|a qué hora llego|a que hora llego|a qué hora llegamos|" +
                "a que hora llegamos|cuánto falta|cuanto falta|cuánto queda|cuanto queda|hora de llegada)(?: .*)?",
            "when do i arrive",
        ),
        Rule("(?:lee|léeme|leeme|leer|revisa)(?: .*)? (?:mensajes|mensaje|sms|whatsapps?)(?: .*)?") { m ->
            val said = m.value
            when {
                "nuev" in said || "sin leer" in said -> "read my new messages"
                "últim" in said || "ultim" in said -> "read my last message"
                else -> "read my messages"
            }
        },
        Rule(
            "(?:responde|contesta|responder|contestar|escribe|escribir|envía un mensaje|envia un mensaje|manda un mensaje|" +
                "mándale un mensaje|mandale un mensaje|mandar un mensaje) (?:a|al|a la) (.+)",
        ) { m -> "reply to " + who(m.groupValues[1]) },
        Rule(
            "(?:devuelve la llamada|devolver la llamada|llama de vuelta|devuélvele la llamada|devuelvele la llamada|" +
                "devuelve la llamada perdida|regresa la llamada)",
            "call back",
        ),
        Rule(
            "(?:vuelve a llamar|llama otra vez|llama de nuevo|repite la llamada|rellama|rellamar|marca de nuevo|" +
                "llama al último número|llama al ultimo numero)",
            "redial",
        ),
        Rule("(?:llama|llamar|llámame|llamame|marca|telefonea) (.+?) (?:al|en el|en|a la|a su|a|en su) (móvil|movil|celular|casa|fijo|trabajo|oficina)") { m ->
            "call ${who(m.groupValues[1])} on ${numberType.getValue(m.groupValues[2])}"
        },
        Rule("(?:llama|llamar|marca|telefonea|haz una llamada)", "call"),
        Rule("(?:llama|llamar|llámame|llamame|marca|telefonea|haz una llamada a) (.+)") { m -> "call " + who(m.groupValues[1]) },
        Rule(
            "(?:pausa|pon pausa|pausar|para la música|para la musica|para|detén la música|deten la musica|stop|silencio|" +
                "apaga la música|apaga la musica)",
            "pause",
        ),
        Rule("(?:reanuda|continúa|continua|sigue|reanudar|pon música|pon musica|pon la música|pon la musica|música|musica|play)", "continue"),
        Rule(
            "(?:siguiente|la siguiente|próxima|proxima|siguiente canción|siguiente cancion|próxima canción|proxima cancion|salta|" +
                "sáltala|saltala|pasa|cambia de canción|cambia de cancion|otra canción|otra cancion|otra|pon otra|pon otra canción|" +
                "pon otra cancion|pon otra cosa|otra cosa)",
            "next",
        ),
        Rule("(?:anterior|la anterior|canción anterior|cancion anterior|la de antes|vuelve a la anterior)", "previous"),
        Rule(
            "(?:repite|repítela|repitela|pon otra vez|vuelve a poner)(?: esta| la)?(?: canción| cancion)?(?: otra vez| de nuevo| desde el principio)?",
            "play it again",
        ),
        Rule(
            "(?:qué canción es|que cancion es|qué suena|que suena|quién canta|quien canta|qué está sonando|que esta sonando|" +
                "cómo se llama esta canción|como se llama esta cancion)(?: esta| ahora)?",
            "what's playing",
        ),
        Rule("(?:pon|poner|ponme|reproduce|reproducir|toca|tocar|escuchar|escucha|quiero escuchar) (.+)", "play $1"),
        Rule("(?:busca|encuentra|búscame|buscame|encuéntrame|encuentrame) (.+)") { m ->
            m.groupValues[1].takeIf { place(it).startsWith("the nearest ") }?.let(::navigate)
        },
        Rule("(?:abre|abrir|inicia|lanza|muéstrame|muestrame) (.+)") { m -> "open " + app(place(m.groupValues[1])) },
        Rule("(?:ve|vete|ir|vamos|volver|vuelve|volvamos) (.+)") { m ->
            val p = place(m.groupValues[1])
            if (app(p) != p) "go to " + app(p) else "go to " + remember(p, m.groupValues[1])
        },
        Rule(
            "(?:llévame|llevame|llévanos|llevanos|guíame|guiame|navega|navegar|cómo llego|como llego|indicaciones|ruta) (.+)",
        ) { m -> navigate(m.groupValues[1]) },
        Rule("(?:qué hora es|que hora es|dime la hora|la hora|hora)", "what time is it"),
        Rule(
            "(?:qué tiempo hace|que tiempo hace|el tiempo|tiempo|el clima|clima|va a llover|llueve|pronóstico|pronostico)(?: .*)?",
            "what's the weather",
        ),
    )

    /** "las 14:30", "la 1:30". */
    private fun at(time: String) = if (oneOClock(time)) "la ${time.removePrefix("0")}" else "las $time"

    private fun left(english: String) = span(english, "hora" to "horas", "minuto" to "minutos", "y")

    override val answers: Map<Said, (List<String>) -> String> = mapOf(
        Said.HELP to t("Esto es lo que puedes decir."),
        Said.UNKNOWN to t("Todavía no sé hacer eso."),
        Said.WHERE_TO to t("¿A dónde vamos?"),
        Said.NO_NAV_APPS to t("No tienes ninguna app de navegación instalada."),
        Said.NO_NAV_APP_CALLED to t("No encuentro una app de navegación llamada $1."),
        Said.NOT_NAV_APP to t("No es una app de navegación."),
        Said.FINDING_NEAREST to { g -> "Busco ${asSaid(g[1])?.let(::withArticle) ?: "${g[1]} cerca"} con ${g[2]}." },
        Said.NAVIGATING to { g -> "Vamos ${asSaid(g[1])?.let(::there) ?: "a ${g[1]}"} con ${g[2]}." },
        Said.OPENING_SETTINGS to t("Abro los ajustes."),
        Said.OPENING_VEHICLE_DATA to t("Abro los datos del coche."),
        Said.OPENING to t("Abro $1."),
        Said.OPEN_MUSIC_FIRST to t("Primero abre una app de música y vuelve a pedírmelo."),
        Said.MUSIC_APP_LATE to t("La app de música no se abrió a tiempo. Inténtalo otra vez."),
        Said.IGNORES_SEARCH to t("$1 no hace caso a las búsquedas por voz. Elige algo en su pantalla."),
        Said.LOOKING_FOR to t("Busco $1 en $2."),
        Said.NEEDS_NOTIFICATIONS to t("Para leer los mensajes myCar necesita acceso a las notificaciones. Permítelo en el teléfono."),
        Said.NO_MESSAGE_FROM to t("No encuentro un mensaje de $1 al que responder."),
        Said.NO_MESSAGES_YET to t("Todavía no hay mensajes."),
        Said.NO_NEW_MESSAGES to t("No hay mensajes nuevos."),
        Said.ENDING_ROUTE to t("Termino la ruta."),
        Said.PAUSED to t("En pausa."),
        Said.NOTHING_PLAYING to t("No suena nada."),
        Said.SKIPPING to t("Siguiente."),
        Said.PREVIOUS_TRACK to t("Vuelvo a la anterior."),
        Said.PLAYING to t("Sigo."),
        Said.PLAYING_APP to { g -> if (g[1] == "the music") "Pongo la música." else "Pongo ${g[1]}." },
        Said.GOING_BACK to t("Vuelvo atrás."),
        Said.BACK_TO_CAR to t("Vuelvo a la pantalla del coche."),
        Said.HEADING_HOME to t("Vuelvo al inicio."),
        Said.YOUR_APPS to t("Aquí están tus apps."),
        Said.VEHICLE_DATA_OFF to t("Los datos del coche están desactivados. Actívalos en los ajustes, entre las funciones experimentales."),
        Said.TIME to { g -> "${if (oneOClock(g[1])) "Es" else "Son"} ${at(g[1])}." },
        Said.WEATHER to { g -> listOfNotNull(g[1], sky(g[2])).joinToString(", ") + ", en ${g[3]}." },
        Said.NO_WEATHER to t("Todavía no tengo el tiempo."),
        Said.NO_ROUTE to t("No hay ninguna ruta en el mapa."),
        Said.ARRIVE_AT_IN to { g -> "Hacia ${at(g[1])}, dentro de ${left(g[2])}." },
        Said.ARRIVE_AT to { g -> "Hacia ${at(g[1])}." },
        Said.ARRIVE_IN to { g -> "Dentro de unos ${left(g[1])}." },
        Said.MAP_HASNT_SAID to t("El mapa todavía no lo ha dicho."),
        Said.NEEDS_PHONE to t("Primero myCar necesita el permiso de Teléfono: permítelo desde la pantalla del teléfono."),
        Said.NEEDS_CALL_LOG to t("Primero myCar necesita el registro de llamadas: permítelo en el teléfono."),
        Said.NO_REDIAL to t("No hay ninguna llamada que repetir."),
        Said.NO_CALL_BACK to t("No hay ninguna llamada que devolver."),
        Said.PRIVATE_NUMBER to t("Esa llamada era de un número privado: no se puede devolver."),
        Said.CALLING_AGAIN to t("Vuelvo a llamar a $1."),
        Said.CALLING_BACK to t("Devuelvo la llamada a $1."),
        Said.WHO_TO_CALL to t("¿A quién llamo?"),
        Said.NEEDS_CONTACTS to t("Primero myCar necesita los permisos de Teléfono y Contactos: permítelos desde la pantalla del teléfono."),
        Said.NOT_IN_CONTACTS to t("No encuentro a $1 en tus contactos."),
        Said.NO_SUCH_NUMBER to { g ->
            val kind = mapOf("mobile" to "de móvil", "cell" to "de móvil", "phone" to "de teléfono", "home" to "de casa", "work" to "del trabajo")
            "${g[1]} no tiene número ${kind[g[2]] ?: g[2]}. " + if (g[3] == "the other one") "¿Llamo al otro?" else "¿Llamo al número de ${g[3]}?"
        },
        Said.CALL_QUESTION to t("¿Llamo a $1?"),
        Said.CALLING_TYPE to t("Llamando a $1, $2."),
        Said.CALLING to t("Llamando a $1."),
        Said.NO_APP_CALLED to t("No encuentro una app llamada $1."),
        Said.SOMETHING_WRONG to t("Algo salió mal. Inténtalo otra vez."),
        Said.DONE to t("Hecho."),
        Said.HOME_ASSISTANT_SILENT to t("Home Assistant no ha respondido."),
        Said.NEEDS_MIC to t("Hace falta el permiso del micrófono."),
        Said.AFTER_CALL to t("Los comandos de voz funcionan cuando termina la llamada."),
        Said.DIDNT_CATCH to t("No te he entendido."),
        Said.RECOGNIZER_BUSY to t("El reconocimiento de voz está ocupado."),
        Said.RECOGNIZER_NETWORK to t("El reconocimiento de voz necesita una red que no tiene."),
        Said.RECOGNITION_FAILED to t("El reconocimiento de voz ha fallado."),
        Said.SPEECH_NOT_READY to t("La voz todavía no está lista."),
        Said.MODEL_NOT_LOADED to t("El modelo de voz no se ha cargado."),
        Said.MODEL_NOT_DOWNLOADED to t("Primero descarga un modelo de voz en los ajustes de myCar en el teléfono."),
        Said.MIC_SILENCED to t("El micrófono del teléfono está silenciado. Desbloquea el teléfono y vuelve a intentarlo."),
        Said.MIC_UNAVAILABLE to t("El micrófono no está disponible ahora."),
        Said.MISSED_CALL_FROM to t("Llamada perdida de $1."),
        Said.WANT_TO_REPLY to t("¿Quieres responder?"),
        Said.GO_AHEAD to t("Adelante."),
        Said.NO_CAR_REPLIES to t("$1 no permite responder desde el coche."),
        Said.REPLY_AFTER_CALL to t("La respuesta por voz funciona cuando termina la llamada."),
        Said.YOUR_REPLY_TO to t("¿Qué le respondes a $1?"),
        Said.YOUR_REPLY to t("¿Qué respondes?"),
        Said.SENDING_TO to t("Enviando a $1: $2"),
        Said.SENDING to t("Enviando: $1"),
        Said.NOT_SENT to t("No enviado."),
        Said.MESSAGE_SENT to t("Mensaje enviado."),
        Said.REPLY_WITHDRAWN to t("$1 retiró la respuesta. Abre la app en el teléfono para contestar."),
        Said.NOW_PLAYING to t("$1, de $2."),
    )

    private fun sky(english: String): String? = when (english) {
        "Clear" -> "despejado"
        "Mostly clear" -> "casi despejado"
        "Partly cloudy" -> "parcialmente nublado"
        "Overcast" -> "cubierto"
        "Fog" -> "niebla"
        "Drizzle" -> "llovizna"
        "Rain" -> "lluvia"
        "Snow" -> "nieve"
        "Rain showers" -> "chubascos"
        "Snow showers" -> "chubascos de nieve"
        "Thunderstorm" -> "tormenta"
        "-" -> null
        else -> english
    }

    override val yes = setOf("sí", "si", "claro", "vale", "ok", "okay", "responde", "responder", "venga", "dale")
    override val no = setOf("no", "cancela", "cancelar", "déjalo", "dejalo", "nada", "para")
    override val cancel = setOf("cancela", "cancelar", "no", "para", "déjalo", "dejalo")
    override val change = setOf("cambia", "cambiar", "corrige", "otra vez", "de nuevo", "mal", "repite")
    override val send = setOf("envía", "envia", "enviar", "manda", "mándalo", "mandalo", "sí", "si", "vale", "ok")

    override val samples = listOf("Pon Shakira", "¿Qué canción es?", "Siguiente canción", "Llama a mamá", "Abre Organic Maps", "Pausa", "Ayuda")

    override fun says(who: String, text: String) = "$who dice: $text"
    override fun inGroup(sender: String, group: String) = "$sender en $group"

    override val help = listOf(
        "Llévame a…" to "Cómo llegar. Añade \"con Organic Maps\" para elegir la app",
        "Pon…" to "Una canción, un artista o un álbum en la app de música abierta",
        "Pausa · Sigue · Siguiente · Anterior" to "Controla la música",
        "¿Qué suena?" to "La canción y quién la canta",
        "Llama a…" to "Un contacto por su nombre. Añade \"al móvil\" para elegir el número",
        "Devuelve la llamada · Vuelve a llamar" to "La última llamada recibida, o la última que hiciste",
        "Lee mis mensajes · Responde a…" to "Los mensajes nuevos en voz alta, o una respuesta a quien te escribió",
        "Para la navegación" to "Termina la ruta",
        "¿Cuándo llego?" to "La hora de llegada y el tiempo que falta",
        "Abre…" to "Una app, como Browser, Teléfono o Compartir pantalla",
        "Salir" to "Vuelve a la pantalla del coche",
        "Inicio · Ajustes" to "Moverte por myCar",
        "¿Qué hora es? · ¿Qué tiempo hace?" to "La hora, o el tiempo",
        "Ayuda" to "Esta lista",
    )

    override val labels = mapOf(
        "Listening…" to "Te escucho…",
        "One moment…" to "Un momento…",
        "You can say" to "Puedes decir",
        "Say “cancel” or “change it”" to "Di «cancela» o «cambia»",
        "Do you want to reply? Say yes or no." to "¿Quieres responder? Di sí o no.",
        "Get ready to say your reply." to "Prepárate para decir tu respuesta.",
        "Say your reply" to "Di tu respuesta",
    )
}
