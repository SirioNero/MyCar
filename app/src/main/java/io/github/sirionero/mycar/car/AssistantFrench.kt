package io.github.sirionero.mycar.car

/** The assistant in French (see [AssistantLanguage]). Hyphens are spaces by then: "emmène moi". */
internal object FrenchPhrases : Phrasebook() {

    override val leadFillers = listOf(
        "hé mycar", "hey mycar", "ok mycar", "dis mycar", "hé my car", "ok my car", "mycar", "my car", "hé", "hey", "eh", "ok", "okay",
        "alors", "dis", "s'il te plaît", "s'il vous plaît", "est ce que tu peux", "est ce que vous pouvez", "est ce que tu pourrais",
        "tu peux", "tu pourrais", "vous pouvez", "pourriez vous", "peux tu", "pouvez vous", "je veux", "je voudrais", "j'aimerais",
        "maintenant",
    )
    override val tailFillers = listOf("s'il te plaît", "s'il vous plaît", "merci beaucoup", "merci", "maintenant", "stp", "svp")
    override val appWords = listOf(" avec ", " sur ", " via ")

    private val NEAR = Regex(
        "^(.+?) (?:le plus proche|la plus proche|les plus proches|à proximité|a proximité|près d'ici|pres d'ici|près de moi|" +
            "pres de moi|dans le coin|pas loin|autour de moi)((?: (?:avec|sur|via) .+)?)$",
    )

    /** "à la station service la plus proche" → "the nearest station service", "à Lyon" → "lyon". */
    private fun place(said: String): String {
        var t = said.trim()
        t = t.replace(Regex("^(?:vers|jusqu'à|jusqu'au|jusqu'aux|pour)\\s+"), "")
        t = t.replace(Regex("^(?:(?:à la|a la|au|aux|à|a|en|dans le|dans la|dans les|le|la|les|un|une)\\s+|(?:à l'|a l'|l'))"), "")
        NEAR.matchEntire(t)?.let { return "the nearest " + it.groupValues[1] + it.groupValues[2] }
        return t
    }

    /** "la station la plus proche" out of "à la station la plus proche": what's near, with its article. */
    private fun withArticle(said: String): String {
        val articles = listOf(
            "au " to "le ", "à la " to "la ", "a la " to "la ", "à l'" to "l'", "a l'" to "l'", "aux " to "les ",
            "à un " to "un ", "à une " to "une ",
        )
        for ((from, to) in articles) if (said.startsWith(from)) return to + said.removePrefix(from)
        return said
    }

    private fun navigate(said: String) = "navigate to " + remember(place(said), said)

    private fun who(raw: String): String = raw.trim().replace(Regex("^(?:à|a)\\s+"), "").trim()

    private val numberType = mapOf(
        "portable" to "mobile", "mobile" to "mobile", "cellulaire" to "mobile",
        "fixe" to "home", "domicile" to "home", "maison" to "home", "travail" to "work", "bureau" to "work",
    )

    private fun app(name: String) = when (name) {
        "téléphone", "telephone" -> "phone"
        "partage d'écran", "partage d'ecran", "partage de l'écran", "partage de l'ecran" -> "screen sharing"
        "données du véhicule", "donnees du vehicule", "données de la voiture", "donnees de la voiture" -> "vehicle data"
        "paramètres", "parametres", "réglages", "reglages" -> "settings"
        "navigateur" -> "browser"
        else -> name
    }

    override val commands = listOf(
        Rule(
            "(?:aide|à l'aide|qu'est ce que je peux dire|que puis je dire|qu'est ce que tu sais faire|que sais tu faire|commandes|" +
                "commandes vocales)",
            "help",
        ),
        Rule("(?:(?:va|aller|retourne|retour) (?:à|a) )?l'(?:accueil|écran d'accueil|ecran d'accueil)|accueil|écran d'accueil", "go home"),
        Rule("(?:(?:ouvre|ouvrir|va dans|affiche|montre moi) )?(?:les )?(?:paramètres|parametres|réglages|reglages)", "open settings"),
        Rule("(?:(?:montre moi|ouvre|affiche) )?(?:mes |les |toutes les )?(?:applis|applications|apps)", "open apps"),
        Rule("quitter|quitte|quitte mycar|ferme mycar|sortir|sors", "exit"),
        Rule("(?:retour|reviens|revenir)(?: en arrière| en arriere)?|en arrière|en arriere", "go back"),
        Rule(
            "(?:arrête|arrete|arrêter|arreter|termine|annule|stoppe|coupe)(?: la| le| l')? ?(?:navigation|guidage|itinéraire|itineraire|trajet)|" +
                "arrête de naviguer|arrete de naviguer",
            "stop navigation",
        ),
        Rule(
            "(?:quand est ce que j'arrive|quand est ce qu'on arrive|quand j'arrive|quand arrive t on|quand est ce qu'on sera là|" +
                "à quelle heure j'arrive|à quelle heure on arrive|a quelle heure j'arrive|a quelle heure on arrive|" +
                "à quelle heure est ce que j'arrive|combien de temps (?:il reste|reste t il)|il reste combien de temps|" +
                "heure d'arrivée|heure d'arrivee)(?: .*)?",
            "when do i arrive",
        ),
        Rule("(?:lis|lire|lis moi|vérifie|verifie|consulte)(?: .*)? (?:messages|message|sms|textos)(?: .*)?") { m ->
            val said = m.value
            when {
                "nouveau" in said || "non lu" in said -> "read my new messages"
                "dernier" in said -> "read my last message"
                else -> "read my messages"
            }
        },
        Rule(
            "(?:réponds|reponds|répond|repond|répondre|repondre|écris|ecris|écrire|ecrire|envoie un message|envoyer un message|envoie un sms|" +
                "envoie un texto) (?:à|a) (.+)",
        ) { m -> "reply to " + who(m.groupValues[1]) },
        Rule(
            "(?:rappelle|rappeler|rappelle le|rappelle la|rappelle les|rappelle le dernier appel|rappelle l'appel manqué|" +
                "rappelle l'appel manque|rappeler l'appel manqué|rappelle le dernier appel manqué)",
            "call back",
        ),
        Rule(
            "(?:recompose|recomposer|recompose le numéro|recompose le dernier numéro|appelle le dernier numéro|" +
                "appelle le dernier numero|refais le dernier appel|appelle encore|appelle à nouveau|appelle a nouveau)",
            "redial",
        ),
        Rule(
            "(?:appelle|appeler|rappelle|téléphone à|telephone a|téléphoner à|passe un appel à|fais un appel à) (.+?) " +
                "(?:sur son|sur le|au|à la|à son|sur|a son|a la) (portable|mobile|cellulaire|fixe|domicile|maison|travail|bureau)",
        ) { m -> "call ${who(m.groupValues[1])} on ${numberType.getValue(m.groupValues[2])}" },
        Rule("(?:appelle|appeler|passe un appel|fais un appel)", "call"),
        Rule("(?:appelle|appeler|rappelle|téléphone à|telephone a|téléphoner à|passe un appel à|fais un appel à) (.+)") { m ->
            "call " + who(m.groupValues[1])
        },
        Rule(
            "(?:pause|mets en pause|mettre en pause|arrête la musique|arrete la musique|stop|stoppe|coupe la musique|coupe le son|" +
                "silence|tais toi)",
            "pause",
        ),
        Rule(
            "(?:reprends|reprendre|continue|lecture|relance la musique|remets la musique|mets de la musique|mets la musique|" +
                "lance la musique|musique|play|joue)",
            "continue",
        ),
        Rule(
            "(?:suivant|suivante|chanson suivante|morceau suivant|titre suivant|passe|passe à la suivante|passe a la suivante|" +
                "zappe|saute|une autre|une autre chanson|mets autre chose|autre chose|change de chanson)",
            "next",
        ),
        Rule(
            "(?:précédent|precedent|précédente|precedente|chanson précédente|chanson precedente|morceau précédent|titre précédent|" +
                "la chanson d'avant|celle d'avant|reviens à la précédente)",
            "previous",
        ),
        Rule(
            "(?:remets|rejoue|recommence)(?: cette| la| ce| le)?(?: chanson| morceau| titre)?(?: depuis le début| depuis le debut| encore| une fois)?",
            "play it again",
        ),
        Rule(
            "(?:c'est quoi cette chanson|quelle est cette chanson|qu'est ce qui joue|qu'est ce qui passe|qui chante|" +
                "c'est quelle chanson|quel est ce morceau|qu'est ce que j'écoute|qu'est ce que j'ecoute|c'est qui qui chante|" +
                "c'est quoi ce morceau)(?: .*)?",
            "what's playing",
        ),
        Rule("(?:mets|mettre|joue|jouer|fais moi écouter|fais moi ecouter|écouter|ecouter|écoute|ecoute|je veux écouter) (.+)", "play $1"),
        Rule("(?:trouve moi|cherche moi|trouve|cherche) (.+)") { m ->
            m.groupValues[1].takeIf { place(it).startsWith("the nearest ") }?.let(::navigate)
        },
        Rule("(?:ouvre|ouvrir|lance|lancer|démarre|demarre|démarrer|affiche|montre moi) (.+)") { m -> "open " + app(place(m.groupValues[1])) },
        Rule("(?:va|aller|allons|on va|rentre|rentrons|rentrer) (.+)") { m ->
            val p = place(m.groupValues[1])
            if (app(p) != p) "go to " + app(p) else "go to " + remember(p, m.groupValues[1])
        },
        Rule(
            "(?:emmène moi|emmene moi|emmène nous|emmene nous|amène moi|amene moi|conduis moi|conduis nous|guide moi|navigue|" +
                "naviguer|itinéraire|itineraire|direction|comment aller|comment je vais|comment on va) (.+)",
        ) { m -> navigate(m.groupValues[1]) },
        Rule("(?:quelle heure est il|quelle heure il est|il est quelle heure|l'heure|heure|donne moi l'heure|tu as l'heure)", "what time is it"),
        Rule(
            "(?:quel temps fait il|quel temps il fait|il fait quel temps|la météo|météo|meteo|la meteo|va t il pleuvoir|" +
                "est ce qu'il va pleuvoir|il va pleuvoir|il pleut|prévisions|previsions)(?: .*)?",
            "what's the weather",
        ),
    )

    private fun left(english: String) = span(english, "heure" to "heures", "minute" to "minutes", "et")

    /** Where to, said back: "la gare" (the recognizer missed the "à") becomes "à la gare", "chez moi" is "chez vous". */
    private fun there(said: String): String {
        val t = said.replace(Regex("\\bchez (?:moi|nous)\\b"), "chez vous")
        for ((from, to) in listOf("le " to "au ", "la " to "à la ", "les " to "aux ", "l'" to "à l'")) {
            if (t.startsWith(from)) return to + t.removePrefix(from)
        }
        return t
    }

    override val answers: Map<Said, (List<String>) -> String> = mapOf(
        Said.HELP to t("Voici ce que vous pouvez dire."),
        Said.UNKNOWN to t("Je ne sais pas encore faire ça."),
        Said.WHERE_TO to t("Où voulez-vous aller ?"),
        Said.NO_NAV_APPS to t("Aucune application de navigation n'est installée."),
        Said.NO_NAV_APP_CALLED to t("Je ne trouve pas d'application de navigation appelée $1."),
        Said.NOT_NAV_APP to t("Ce n'est pas une application de navigation."),
        Said.FINDING_NEAREST to { g -> "Je cherche ${asSaid(g[1])?.let(::withArticle) ?: "${g[1]} à proximité"} avec ${g[2]}." },
        Said.NAVIGATING to { g -> "Je vous emmène ${asSaid(g[1])?.let(::there) ?: "à ${g[1]}"} avec ${g[2]}." },
        Said.OPENING_SETTINGS to t("J'ouvre les paramètres."),
        Said.OPENING_VEHICLE_DATA to t("J'ouvre les données du véhicule."),
        Said.OPENING to t("J'ouvre $1."),
        Said.OPEN_MUSIC_FIRST to t("Ouvrez d'abord une application de musique, puis redemandez-moi."),
        Said.MUSIC_APP_LATE to t("L'application de musique ne s'est pas ouverte à temps. Réessayez."),
        Said.IGNORES_SEARCH to t("$1 ignore les recherches vocales. Choisissez plutôt sur son écran."),
        Said.LOOKING_FOR to t("Je cherche $1 sur $2."),
        Said.NEEDS_NOTIFICATIONS to t("Pour lire les messages, myCar a besoin de l'accès aux notifications. Autorisez-le sur le téléphone."),
        Said.NO_MESSAGE_FROM to t("Je ne trouve pas de message de $1 auquel répondre."),
        Said.NO_MESSAGES_YET to t("Il n'y a pas encore de messages."),
        Said.NO_NEW_MESSAGES to t("Aucun nouveau message."),
        Said.ENDING_ROUTE to t("Je termine l'itinéraire."),
        Said.PAUSED to t("En pause."),
        Said.NOTHING_PLAYING to t("Rien n'est en lecture."),
        Said.SKIPPING to t("Suivant."),
        Said.PREVIOUS_TRACK to t("Je reviens au titre précédent."),
        Said.PLAYING to t("Je reprends."),
        Said.PLAYING_APP to { g -> if (g[1] == "the music") "Je lance la musique." else "Je lance ${g[1]}." },
        Said.GOING_BACK to t("Je reviens en arrière."),
        Said.BACK_TO_CAR to t("Retour à l'écran de la voiture."),
        Said.HEADING_HOME to t("Retour à l'accueil."),
        Said.YOUR_APPS to t("Voici vos applications."),
        Said.VEHICLE_DATA_OFF to t("Les données du véhicule sont désactivées. Activez-les dans les paramètres, parmi les fonctions expérimentales."),
        Said.TIME to t("Il est $1."),
        Said.WEATHER to { g -> listOfNotNull(g[1], sky(g[2])).joinToString(", ") + ", à ${g[3]}." },
        Said.NO_WEATHER to t("Je n'ai pas encore la météo."),
        Said.NO_ROUTE to t("Il n'y a pas d'itinéraire sur la carte."),
        Said.ARRIVE_AT_IN to { g -> "Vers ${g[1]}, dans ${left(g[2])}." },
        Said.ARRIVE_AT to t("Vers $1."),
        Said.ARRIVE_IN to { g -> "Dans environ ${left(g[1])}." },
        Said.MAP_HASNT_SAID to t("La carte ne l'a pas encore indiqué."),
        Said.NEEDS_PHONE to t("myCar a d'abord besoin de l'autorisation Téléphone : autorisez-la depuis l'écran du téléphone."),
        Said.NEEDS_CALL_LOG to t("myCar a d'abord besoin du journal d'appels : autorisez-le sur le téléphone."),
        Said.NO_REDIAL to t("Il n'y a aucun appel à recomposer."),
        Said.NO_CALL_BACK to t("Il n'y a aucun appel à rappeler."),
        Said.PRIVATE_NUMBER to t("Cet appel venait d'un numéro masqué : impossible de rappeler."),
        Said.CALLING_AGAIN to t("Je rappelle $1."),
        Said.CALLING_BACK to t("Je rappelle $1."),
        Said.WHO_TO_CALL to t("Qui dois-je appeler ?"),
        Said.NEEDS_CONTACTS to t("myCar a d'abord besoin des autorisations Téléphone et Contacts : autorisez-les depuis l'écran du téléphone."),
        Said.NOT_IN_CONTACTS to t("Je ne trouve pas $1 dans vos contacts."),
        Said.NO_SUCH_NUMBER to { g ->
            val kind = mapOf(
                "mobile" to "de portable", "cell" to "de portable", "phone" to "de téléphone", "home" to "de domicile", "work" to "professionnel",
            )
            "${g[1]} n'a pas de numéro ${kind[g[2]] ?: g[2]}. " +
                if (g[3] == "the other one") "J'appelle l'autre numéro ?" else "J'appelle le numéro ${g[3]} à la place ?"
        },
        Said.CALL_QUESTION to t("J'appelle $1 ?"),
        Said.CALLING_TYPE to t("J'appelle $1, $2."),
        Said.CALLING to t("J'appelle $1."),
        Said.NO_APP_CALLED to t("Je ne trouve pas d'application appelée $1."),
        Said.SOMETHING_WRONG to t("Un problème est survenu. Réessayez."),
        Said.DONE to t("C'est fait."),
        Said.HOME_ASSISTANT_SILENT to t("Home Assistant n'a pas répondu."),
        Said.NEEDS_MIC to t("Il faut l'autorisation du micro."),
        Said.AFTER_CALL to t("Les commandes vocales fonctionnent une fois l'appel terminé."),
        Said.DIDNT_CATCH to t("Je n'ai pas compris."),
        Said.RECOGNIZER_BUSY to t("La reconnaissance vocale est occupée."),
        Said.RECOGNIZER_NETWORK to t("La reconnaissance vocale a besoin d'un réseau qu'elle n'a pas."),
        Said.RECOGNITION_FAILED to t("La reconnaissance vocale a échoué."),
        Said.SPEECH_NOT_READY to t("La voix n'est pas encore prête."),
        Said.MODEL_NOT_LOADED to t("Le modèle vocal ne s'est pas chargé."),
        Said.MODEL_NOT_DOWNLOADED to t("Téléchargez d'abord un modèle vocal dans les paramètres de myCar sur le téléphone."),
        Said.MIC_SILENCED to t("Le micro du téléphone est coupé. Déverrouillez le téléphone et réessayez."),
        Said.MIC_UNAVAILABLE to t("Le micro n'est pas disponible pour le moment."),
        Said.MISSED_CALL_FROM to t("Appel manqué de $1."),
        Said.WANT_TO_REPLY to t("Voulez-vous répondre ?"),
        Said.GO_AHEAD to t("Je vous écoute."),
        Said.NO_CAR_REPLIES to t("$1 ne permet pas de répondre depuis la voiture."),
        Said.REPLY_AFTER_CALL to t("La réponse vocale fonctionne une fois l'appel terminé."),
        Said.YOUR_REPLY_TO to t("Que voulez-vous répondre à $1 ?"),
        Said.YOUR_REPLY to t("Que voulez-vous répondre ?"),
        Said.SENDING_TO to t("J'envoie à $1 : $2"),
        Said.SENDING to t("J'envoie : $1"),
        Said.NOT_SENT to t("Pas envoyé."),
        Said.MESSAGE_SENT to t("Message envoyé."),
        Said.REPLY_WITHDRAWN to t("$1 a retiré la réponse. Ouvrez l'application sur le téléphone pour répondre."),
        Said.NOW_PLAYING to t("$1, de $2."),
    )

    private fun sky(english: String): String? = when (english) {
        "Clear" -> "ciel dégagé"
        "Mostly clear" -> "ciel peu nuageux"
        "Partly cloudy" -> "partiellement nuageux"
        "Overcast" -> "couvert"
        "Fog" -> "brouillard"
        "Drizzle" -> "bruine"
        "Rain" -> "pluie"
        "Snow" -> "neige"
        "Rain showers" -> "averses"
        "Snow showers" -> "averses de neige"
        "Thunderstorm" -> "orage"
        "-" -> null
        else -> english
    }

    override val yes = setOf("oui", "ouais", "d'accord", "ok", "okay", "bien sûr", "réponds", "reponds", "vas y", "volontiers")
    override val no = setOf("non", "annule", "annuler", "laisse tomber", "rien", "stop", "pas maintenant")
    override val cancel = setOf("annule", "annuler", "non", "stop", "arrête", "arrete")
    override val change = setOf("change", "modifie", "corrige", "recommence", "encore", "faux")
    override val send = setOf("envoie", "envoyer", "oui", "ok", "okay", "vas y")

    override val samples = listOf("Mets Stromae", "C'est quoi cette chanson ?", "Chanson suivante", "Appelle maman", "Ouvre Organic Maps", "Pause", "Aide")

    override fun says(who: String, text: String) = "$who dit : $text"
    override fun inGroup(sender: String, group: String) = "$sender dans $group"

    override val help = listOf(
        "Emmène-moi à…" to "L'itinéraire. Ajoutez « avec Organic Maps » pour choisir l'application",
        "Mets…" to "Une chanson, un artiste ou un album dans l'application de musique ouverte",
        "Pause · Reprends · Suivant · Précédent" to "Contrôler la musique",
        "C'est quoi cette chanson ?" to "La chanson et qui la chante",
        "Appelle…" to "Un contact par son nom. Ajoutez « sur son portable » pour choisir le numéro",
        "Rappelle · Recompose" to "Le dernier appel reçu, ou le dernier passé",
        "Lis mes messages · Réponds à…" to "Les nouveaux messages lus à voix haute, ou une réponse à qui vous a écrit",
        "Arrête la navigation" to "Termine l'itinéraire",
        "Quand est-ce que j'arrive ?" to "L'heure d'arrivée et le temps restant",
        "Ouvre…" to "Une application, comme Browser, Téléphone ou Partage d'écran",
        "Quitter" to "Retour à l'écran de la voiture",
        "Accueil · Paramètres" to "Se déplacer dans myCar",
        "Quelle heure est-il ? · Quel temps fait-il ?" to "L'heure, ou la météo",
        "Aide" to "Cette liste",
    )

    override val labels = mapOf(
        "Listening…" to "Je vous écoute…",
        "One moment…" to "Un instant…",
        "You can say" to "Vous pouvez dire",
        "Say “cancel” or “change it”" to "Dites « annule » ou « change »",
        "Do you want to reply? Say yes or no." to "Voulez-vous répondre ? Dites oui ou non.",
        "Get ready to say your reply." to "Préparez-vous à dire votre réponse.",
        "Say your reply" to "Dites votre réponse",
    )
}
