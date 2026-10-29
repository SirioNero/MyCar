package io.github.sirionero.mycar.car

import java.text.Normalizer

/**
 * The assistant in Greek (see [AssistantLanguage]). Answers, help and labels use the polite plural,
 * as myCar's Greek screens do; the driver may say a command either way ("πήγαινέ με", "πηγαίνετέ
 * με"), or after "μπορείς να…" ("καλέσεις"), and every one of those forms is taken.
 *
 * How the multilingual model (Parakeet v3) writes Greek: its tokens have no final ς at all, so it
 * leaves it out ("Άνοιξε τι ρυθμίσει." for "τις ρυθμίσεις") or now and then writes σ; it often loses
 * the ν of "τον" and "στον" ("Πάρε το Γιώργο."), may drop an accent ("Πήγαινε με"), and has no ";"
 * to end a question with. The rules are written in good Greek and read through [loose], which takes
 * all of that, and what the phone's own recognizer writes, ς and accents included.
 */
internal object GreekPhrases : Phrasebook() {

    /** Each vowel with or without its accent, and ς as σ or nothing: how a rule reads a letter. */
    private val LOOSE = mapOf(
        'α' to "αά", 'ά' to "αά", 'ε' to "εέ", 'έ' to "εέ", 'η' to "ηή", 'ή' to "ηή",
        'ι' to "ιίϊΐ", 'ί' to "ιίϊΐ", 'ϊ' to "ιίϊΐ", 'ΐ' to "ιίϊΐ", 'ο' to "οό", 'ό' to "οό",
        'υ' to "υύϋΰ", 'ύ' to "υύϋΰ", 'ϋ' to "υύϋΰ", 'ΰ' to "υύϋΰ", 'ω' to "ωώ", 'ώ' to "ωώ", 'ς' to "σς",
    )

    /**
     * [pattern], written in good Greek, as the recognizer may write it: any vowel with or without an
     * accent, and a final ς as ς, σ or nothing ("τις" also reads "τι", "βρες" also "βρε").
     */
    private fun loose(pattern: String): String {
        val out = StringBuilder()
        var inClass = false
        var escaped = false
        for (c in pattern) {
            val letters = LOOSE[c]
            when {
                escaped -> { out.append(c); escaped = false }
                c == '\\' -> { out.append(c); escaped = true }
                c == '[' -> { inClass = true; out.append(c) }
                c == ']' -> { inClass = false; out.append(c) }
                letters == null -> out.append(c)
                inClass -> out.append(letters)
                c == 'ς' -> out.append("[σς]?")
                else -> out.append('[').append(letters).append(']')
            }
        }
        return out.toString()
    }

    private fun re(pattern: String) = Regex(loose(pattern))
    private fun rule(pattern: String, to: String) = Rule(loose(pattern), to)
    private fun rule(pattern: String, to: (MatchResult) -> String?) = Rule(loose(pattern), to)

    /** Without accents or diaeresis. */
    private fun unaccented(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** How two spellings of a word are compared: lower case, no accents, no final ς or σ. */
    private fun bare(s: String) = unaccented(s.lowercase().trim()).replace(Regex("[σς](?= |$)"), "")

    /** A word's last σ as the ς it is: "γιώργοσ" → "γιώργος". */
    private fun finalSigma(s: String) = s.replace(Regex("σ(?= |$)"), "ς")

    /** [phrases] and the ways the recognizer writes them: without the final ς, with σ, without accents. */
    private fun variants(vararg phrases: String): List<String> = phrases.flatMap { p ->
        val forms = listOf(p, p.replace(Regex("ς(?= |$)"), ""), p.replace(Regex("ς(?= |$)"), "σ"))
        forms + forms.map(::unaccented)
    }.distinct()

    override val leadFillers = variants(
        "έι mycar", "γεια σου mycar", "εε mycar", "έι my car", "γεια σου my car", "έι μάι καρ", "mycar", "my car", "μάι καρ",
        "έι", "εεε", "εε", "ε", "γεια σου", "γεια σας", "λοιπόν", "άκου", "άκουσε", "ακούστε", "εντάξει", "οκέι", "οκ", "ok", "okay",
        "σε παρακαλώ", "σας παρακαλώ", "παρακαλώ",
        "μπορείς να μου πεις", "μπορείτε να μου πείτε", "μπορείς να", "μπορείτε να", "θα μπορούσες να", "θα μπορούσατε να",
        "θέλω να μάθω", "θέλω να", "θέλουμε να", "θα ήθελα να", "θα ήθελα", "θέλω",
        "για πες μου", "πες μου", "πείτε μου", "ξέρεις", "ξέρετε", "για", "ας", "τώρα", "γρήγορα",
    )
    override val tailFillers = variants(
        "σε παρακαλώ", "σας παρακαλώ", "παρακαλώ", "ευχαριστώ πολύ", "ευχαριστώ", "τώρα", "αμέσως", "γρήγορα",
        "αν μπορείς", "αν μπορείτε", "αν θέλεις", "αν θέλετε", "αν γίνεται",
    )
    override val appWords = listOf(
        " με το ", " με την ", " με τον ", " με ", " μέσω του ", " μέσω της ", " μέσω ", " στο ", " στην ", " στη ", " από το ", " από την ", " από ",
    )

    /** A verb said to the car: "πάρε", "πάρτε", (να) "πάρεις", "πάρετε", "πάρω", "πάρουμε". */
    private const val E = "(?:ε|τε|εις|ετε|ω|ουμε)"
    private const val CALL_VERB = "(?:πάρ$E|κάλεσ$E|τηλεφώνησ$E|ξαναπάρ$E|ξανακάλεσ$E)"
    private const val CALL = "(?:$CALL_VERB|κλήση|κάν$E (?:μια |ένα )?(?:κλήση|τηλέφωνο)|μιλήσ(?:ω|ουμε) με)(?: μου| μας)?(?: τηλέφωνο)?(?: ξανά| πάλι| πίσω)?"
    private const val TYPES = "(?:κινητό|σταθερό|σπίτι|δουλειά|γραφείο|εργασία)"
    private const val PLAY = "(?:βάλ$E|παίξ$E|ξαναβάλ$E|ξαναπαίξ$E|ακούσ(?:ω|ουμε)|αναπαραγωγή)(?: μου| μας)?"
    private const val GO = "(?:πήγαιν(?:ε|ετε)|πηγαίν(?:εις|ω|ουμε)|πας|πάτε|πάω|πάμε|γύρνα|γυρίστε|γύρισ$E|γυρνάμε|γυρίζουμε)"
    private const val START = "(?:ξεκίν(?:α|ησε|ήστε|ήσεις|ήσετε|ήσω|ήσουμε))"
    private const val STOP = "(?:σταμάτ(?:α|ησε|ήστε|ήσεις|ήσετε|ήσω|ήσουμε))"
    private const val AGAIN_VERB = "(?:βάλ$E|παίξ$E|$START|βάλ'|παίξ'|βάλτο|παίξτο)"

    private val PLACE_LEAD = re("^(?:προς|για|μέχρι|ως|έως|ίσα) +")
    private val PLACE_IN = re("^(?:(?:στον|στην|στη|στο|στα|στους|στις|στου|σε) +|σ' *)")
    private val PLACE_ART = re("^(?:τον|την|τη|το|τα|τους|τις|ο|η|οι|ένα|έναν|ένας|μια|μία) +")
    private val NEAR_FIRST = re("^(?:πιο κοντιν(?:ό|ή|ά|ούς|ές)|πλησιέστερ(?:ο|η|α|ος|ους|ες)|κοντινότερ(?:ο|η|α|ος|ους|ες)|κοντιν(?:ό|ή|ά)) (.+)$")
    private val NEAR_AFTER = re(
        "^(.+?) (?:κοντά μου|κοντά μας|κοντά σε μένα|εδώ κοντά|εδώ γύρω|εκεί κοντά|γύρω μου|γύρω μας|τριγύρω|στην περιοχή|στη γειτονιά|" +
            "πιο κοντά|κοντά)((?: (?:με|μέσω) .+)?)$",
    )
    private val A_PLACE = re("^(?:(?:σε +|σ' *))?(?:ένα|έναν|μια|μία|κάποιο|κάποια|κάποιον) ")
    private val MUSIC = re("(?:^| )(?:τραγούδι|τραγούδια|κομμάτι|κομμάτια|άλμπουμ|μουσική|δίσκο|καλλιτέχνη|λίστα)(?: |$)")

    /** "στο πλησιέστερο βενζινάδικο" → "the nearest βενζινάδικο", "στη Θεσσαλονίκη" → "θεσσαλονίκη": the place, for the map. */
    private fun place(said: String): String {
        val t = finalSigma(latin(said.trim())).replace(PLACE_LEAD, "").replace(PLACE_IN, "").replace(PLACE_ART, "").trim()
        NEAR_FIRST.matchEntire(t)?.let { return "the nearest " + it.groupValues[1] }
        NEAR_AFTER.matchEntire(t)?.let { return "the nearest " + it.groupValues[1] + it.groupValues[2] }
        return t
    }

    /** "navigate to" the place in [said], remembered as said. */
    private fun navigate(said: String): String {
        val s = latin(said)
        return "navigate to " + remember(place(s), s)
    }

    /** "Βρες ένα φαρμακείο", "πού είναι το πλησιέστερο ATM": only what's near is a place to drive to. */
    private fun nearby(said: String): String? {
        val s = latin(said)
        val p = place(s)
        return when {
            p.startsWith("the nearest ") -> "navigate to " + remember(p, s)
            A_PLACE.containsMatchIn(s) && !MUSIC.containsMatchIn(s) -> "navigate to " + remember("the nearest $p", s)
            else -> null
        }
    }

    private val NEAR_IN = re("^(?:(?:προς|για|μέχρι) +)?(?:σε +|σ' *)")
    private val STO_ARTICLE = re("^σ(?=τ(?:ον|ην|η|ο|α|ους|ις) )")
    private val BARE_ARTICLE = re("^(?=(?:τον|την|τη|το|τα|τους|τις) )")
    private val MINE = re("(?<= )(?:μου|μας)(?= |$)")

    /** "κοντά μου" said back as "κοντά σας", "στο σπίτι μας" as "στο σπίτι σας". */
    private fun yours(said: String) = said.replace(MINE, "σας")

    /** "το πλησιέστερο βενζινάδικο" out of "στο πλησιέστερο βενζινάδικο": what's near, as the object of "ψάχνω". */
    private fun nearSaid(said: String) = yours(said.trim().replace(NEAR_IN, "").replace(STO_ARTICLE, ""))

    /** Where to, said back: "το σταθμό" (the recognizer missed the σ of "στο") becomes "στο σταθμό". */
    private fun there(said: String) = yours(said.trim()).replace(BARE_ARTICLE, "σ")

    private val WHO_ARTICLE = re("^(?:(?:προς|για|με|από) +)?(?:(στον|τον|των)|(?:στην|στη|την|τη|στο|το|στα|τα|στους|τους|στις|τις|σε|σ'))(?: +|$)")
    private val HONORIFIC = re("^(?:(κύριο|κύριε|κυρ)|κυρία|κα) +(?=\\S)")
    private val WHO_CUT = re(" +(?:ότι|πως|να|και|γιατί) .*$")
    private val WHO_TAIL = re("(?: +(?:μου|μας|τηλέφωνο|στο τηλέφωνο|ξανά|πάλι|πίσω|τώρα))+$")

    /** "μια βαθιά ανάσα": what comes with "ένα", "μια" isn't someone to call. */
    private val SOMETHING = re("^(?:ένα|έναν|μια|μία|λίγο|λίγη|λίγα|καμιά|κανένα) ")
    private val NOT_A_NAME = setOf("μήνυμα", "μηνύματα", "κλήση", "κλήσεις", "τηλέφωνο", "αριθμό", "κάποιον", "κάποια").map(::bare).toSet()

    /**
     * Who, as the contacts have them: "στη μαμά μου" → "μαμά". After "τον" (or "στον") a man's name is
     * in the accusative, which has lost the ς of the contact's "Γιώργος", "Κώστας", "Γιάννης": it
     * gets it back, so the whole name is found. After "το" it stays as said, since the recognizer
     * loses the ν of "τον" but "το γραφείο" is the office: "γιώργο" is then a piece of "γιώργος",
     * which AssistantCommands also finds, asking first.
     */
    private fun who(raw: String): String {
        var t = finalSigma(raw.trim())
        var man = false
        WHO_ARTICLE.find(t)?.let { m -> if (m.groups[1] != null) man = true; t = t.substring(m.range.last + 1) }
        HONORIFIC.find(t)?.let { m -> if (m.groups[1] != null) man = true; t = t.substring(m.range.last + 1) }
        t = t.replace(WHO_CUT, "").replace(WHO_TAIL, "").trim()
        if (bare(t) in NOT_A_NAME) return ""
        if (!man) return t
        return t.split(' ').joinToString(" ") { w -> if (Regex("(?:[οόαάηή]|ού)$").containsMatchIn(w)) w + "ς" else w }
    }

    /** "του Γιώργου" → "γιώργος", "της Μαρίας" → "μαρία": whose number, as the contacts have them. */
    private fun owner(article: String, raw: String): String {
        val name = who(raw)
        return when (bare(article)) {
            "του" -> name.split(' ').joinToString(" ") { w ->
                when {
                    Regex("[^αεηιουωάέήίόύώϊϋΐΰ]ου$").containsMatchIn(w) -> w.dropLast(2) + "ος"
                    Regex("[αάηή]$").containsMatchIn(w) -> w + "ς"
                    else -> w
                }
            }
            "τη" -> name.split(' ').joinToString(" ") { it.removeSuffix("ς") }
            else -> name
        }
    }

    private val NUMBER_TYPES = mapOf(
        "κινητό" to "mobile", "σταθερό" to "home", "σπίτι" to "home", "δουλειά" to "work", "γραφείο" to "work", "εργασία" to "work",
    ).mapKeys { bare(it.key) }
    private val TYPE_WORDS = listOf("κινητό", "σταθερό", "σπίτι", "δουλειά", "γραφείο", "εργασία").associateBy(::bare)

    /** "call X on mobile"; "πάρε τηλέφωνο στο σπίτι", with no one named, calls the contact called Σπίτι. */
    private fun typed(rawWho: String, rawType: String): String {
        val name = who(rawWho)
        val type = NUMBER_TYPES[bare(rawType)]
        return when {
            name.isEmpty() -> "call " + (TYPE_WORDS[bare(rawType)] ?: rawType)
            type == null -> "call $name"
            else -> "call $name on $type"
        }
    }

    private val SONG_LEAD = re(
        "^(?:(?:το|τη|την|τον|τα|τους|τις|ο|η|οι) +|(?:λίγο|λίγη|λίγα|κάτι|κανένα|καμιά|μερικά)(?: από)? +|" +
            "(?:μουσική|τραγούδια|κομμάτια|τραγούδι|κομμάτι|άλμπουμ|δίσκο|λίστα|playlist)(?: (?:από|του|της|των|με))? +)",
    )

    /** "λίγο Νταλάρα", "τραγούδια του Χατζιδάκι", "το άλμπουμ X": what to look for in the music app. */
    private fun query(raw: String): String? {
        var t = latin(raw.trim())
        while (true) {
            val next = t.replace(SONG_LEAD, "").trim()
            if (next == t || next.isEmpty()) break
            t = next
        }
        return t.ifEmpty { null }
    }

    private val APP_LEAD = re("^(?:(?:το|τη|την|τον|τα|τις|τους|στο|στην|στη) +)?(?:(?:εφαρμογή|app)(?: του| της)? +)?")

    /** The app's name after "άνοιξε": "το Spotify", "την εφαρμογή Vela". */
    private fun appName(raw: String) = latin(raw.trim()).replace(APP_LEAD, "").trim().ifEmpty { null }

    /** myCar's own apps by their Greek names. */
    private val OWN_APPS = mapOf(
        "τηλέφωνο" to "phone", "κλήσεις" to "phone",
        "κοινή χρήση οθόνης" to "screen sharing", "κοινοποίηση οθόνης" to "screen sharing", "διαμοιρασμός οθόνης" to "screen sharing",
        "κατοπτρισμός οθόνης" to "screen sharing", "κατοπτρισμός" to "screen sharing", "οθόνη του τηλεφώνου" to "screen sharing",
        "δεδομένα οχήματος" to "vehicle data", "δεδομένα του οχήματος" to "vehicle data", "δεδομένα αυτοκινήτου" to "vehicle data",
        "δεδομένα του αυτοκινήτου" to "vehicle data", "στοιχεία οχήματος" to "vehicle data", "στοιχεία του οχήματος" to "vehicle data",
        "ρυθμίσεις" to "settings",
        "πρόγραμμα περιήγησης" to "browser", "φυλλομετρητής" to "browser", "μπράουζερ" to "browser", "ίντερνετ" to "browser",
        "διαδίκτυο" to "browser", "σινεμά" to "cinema", "κινηματογράφος" to "cinema", "χάρτης" to "χάρτες", "χάρτες" to "χάρτες",
    ).mapKeys { bare(it.key) }

    private fun app(name: String) = OWN_APPS[bare(name)] ?: name

    /** Apps the recognizer may write in Greek letters, as their labels have them. */
    private val BRANDS = listOf(
        "γιουτιούμπ μιούζικ" to "youtube music", "γιουτιούμπ" to "youtube", "γιουτούμπ" to "youtube", "σποτιφάι" to "spotify",
        "γουέιζ" to "waze", "γκουγκλ μαπς" to "google maps", "όργκανικ μαπς" to "organic maps", "όσμαντ" to "osmand", "βέλα" to "vela",
        "ντίζερ" to "deezer", "τάιντλ" to "tidal", "ξόνος" to "xonos", "πλάντσια" to "plancia", "βάιμπερ" to "viber",
        "γουότσαπ" to "whatsapp", "γουάτσαπ" to "whatsapp", "σίγκναλ" to "signal", "τέλεγκραμ" to "telegram",
    ).map { (greek, latin) -> re("(?<=^| )$greek(?= |$)") to latin }

    private fun latin(text: String) = BRANDS.fold(text) { t, (greek, latin) -> greek.replace(t, latin) }

    private val NEW = re("(?:^| )(?:νέα|νέο|καινούργια|καινούργιο|καινούρια|καινούριο|αδιάβαστα|αδιάβαστο)(?: |$)")
    private val LAST = re("(?:^| )(?:τελευταία|τελευταίο)(?: |$)")

    private fun messages(said: String) = when {
        NEW.containsMatchIn(said) -> "read my new messages"
        LAST.containsMatchIn(said) -> "read my last message"
        else -> "read my messages"
    }

    override val commands = listOf(
        rule(
            "(?:βοήθεια|βοήθησέ με|βοηθήστε με|τι (?:μπορώ|μπορούμε) να (?:σου |σας )?(?:πω|ζητήσω)|τι (?:μπορείς|ξέρεις) να κάνεις|" +
                "τι (?:μπορείτε|ξέρετε) να κάνετε|(?:ποιες )?(?:είναι )?(?:οι )?(?:φωνητικές )?εντολές(?: υπάρχουν)?|λίστα εντολών|" +
                "δείξ$E (?:μου |μας )?(?:τις )?(?:φωνητικές )?εντολές)",
            "help",
        ),
        // Screens of myCar's own, before "πήγαινε…" is taken for a place.
        rule("(?:(?:$GO|επιστροφή|άνοιξ$E|δείξ$E (?:μου|μας)) (?:στην |στη |την |τη )?)?(?:αρχική|κεντρική)(?: οθόνη| σελίδα)?", "go home"),
        rule("(?:(?:άνοιξ$E|$GO|δείξ$E)(?: μου| μας)? )?(?:(?:στις|τις) )?ρυθμίσεις(?: του mycar| του αυτοκινήτου)?", "open settings"),
        rule(
            "(?:(?:δείξ$E|άνοιξ$E|$GO)(?: μου| μας)? )?(?:(?:στις|τις|όλες τις|οι|όλες οι) )?(?:εφαρμογές|apps)(?: μου| μας)?|λίστα εφαρμογών",
            "open apps",
        ),
        rule("(?:έξοδος|βγες|βγείτε|(?:έξοδος|βγες|βγείτε) από το mycar|κλείσ$E το mycar)", "exit"),
        rule("(?:$GO )?πίσω|επιστροφή|προηγούμενη οθόνη", "go back"),
        // The route.
        rule(
            "(?:$STOP|τερμάτισ$E|ακύρωσ$E|κλείσ$E|διέκοψ$E|διακόψ$E|σβήσ$E) (?:την |τη |το |τις )?(?:πλοήγηση|διαδρομή|καθοδήγηση|οδηγίες|gps)|" +
                "(?:τέλος|διακοπή|τερματισμός|ακύρωση) (?:της )?(?:πλοήγησης|διαδρομής|καθοδήγησης)|$STOP να με (?:πλοηγείς|καθοδηγείς)",
            "stop navigation",
        ),
        rule(
            "(?:πότε (?:θα )?(?:φτάσουμε|φτάσω|φτάνουμε|φτάνω|φθάσουμε|φθάνουμε|είμαστε εκεί)|" +
                "(?:σε πόση ώρα|σε πόσο|τι ώρα) (?:θα )?(?:φτάσουμε|φτάσω|φτάνουμε|φτάνω|φθάσουμε|φθάνουμε)|" +
                "πόση ώρα (?:μένει|έμεινε|ακόμα|ακόμη|θέλουμε|θέλει|έχουμε)|πόσο (?:μένει|έμεινε|ακόμα|ακόμη|θέλουμε|θέλει|απέχουμε|απέχει)|" +
                "πόσα χιλιόμετρα (?:μένουν|έμειναν|ακόμα|ακόμη|έχουμε)|(?:η )?(?:εκτιμώμενη )?ώρα άφιξης)(?: .*)?",
            "when do i arrive",
        ),
        // Messages.
        rule(
            "(?:διάβασ$E|ξαναδιάβασ$E|ακούσ(?:ω|ουμε)|έλεγξ$E|δες|δείτε|τσέκαρ(?:ε|ετε)|δείξ$E)(?: μου| μας)?(?: .*)? " +
                "(?:μηνύματα|μήνυμα|sms|ειδοποιήσεις)(?: .*)?",
        ) { m -> messages(m.value) },
        rule(
            "(?:(?:τα|το) )?(?:(?:νέα|νέο|καινούργια|καινούργιο|καινούρια|καινούριο|τελευταία|τελευταίο|αδιάβαστα|αδιάβαστο) )?" +
                "(?:μηνύματα|μήνυμα|sms|ειδοποιήσεις)(?: μου| μας)?",
        ) { m -> messages(m.value) },
        rule(
            "(?:(?:ποιος|ποια|ποιοι|τι) )?(?:έχω|έχουμε|υπάρχουν|υπάρχει|ήρθε|ήρθαν|έχει έρθει|έχουν έρθει|μου ήρθε|μου ήρθαν|μου έστειλε|" +
                "μου έστειλαν|μου έγραψε)(?: .*)? (?:μηνύματα|μήνυμα|sms)(?: .*)?",
            "read my new messages",
        ),
        rule(
            "(?:απάντησ$E|απάντηση|απάντα|στείλ$E|γράψ$E|μήνυμα)(?: (?:ένα )?(?:μήνυμα|sms|γραπτό))? " +
                "((?:στον|στην|στη|στο|στους|στις|σε|σ'|προς|για) ?.+)",
        ) { m -> who(m.groupValues[1]).takeIf { it.isNotEmpty() }?.let { "reply to $it" } },
        // Where to, as "take me": before the calls, since "πάρε με" calls no one.
        rule(
            "(?:(?:πήγαιν(?:ε|ετε)|πάρ$E|οδήγησ$E|καθοδήγησ$E) (?:με|μας)|(?:με|μας) (?:πας|πάτε|πηγαίνεις|πηγαίνετε|πάρεις|πάρετε|οδηγήσεις|οδηγήσετε)|" +
                "(?:δείξ$E|δώσ$E) (?:μου|μας) (?:το δρόμο|τον δρόμο|τη διαδρομή|την διαδρομή|οδηγίες|πώς να πάω|πώς να πάμε)|" +
                "οδηγίες|πλοήγηση|διαδρομή|δρομολόγιο|$START (?:την |τη )?πλοήγηση|πλοηγήσου|πλοηγηθείτε|πλοήγησ$E|" +
                "πώς (?:θα )?(?:πάω|πάμε|πηγαίνω|πηγαίνουμε|φτάνω|φτάνουμε|φτάσω|φτάσουμε|να πάω|να πάμε|να φτάσω|να φτάσουμε)) (.+)",
        ) { m -> navigate(m.groupValues[1]) },
        // Calls.
        rule(
            "$CALL_VERB(?: (?:τον|την|τη|το|τους|τις))? πίσω|$CALL_VERB (?:πίσω )?(?:την |τη )?(?:τελευταία )?αναπάντητη(?: κλήση)?|" +
                "επίστρεψ$E (?:την |τη )?(?:τελευταία )?κλήση|επιστροφή (?:της )?(?:τελευταίας )?κλήσης|" +
                "$CALL_VERB(?: πίσω)? (?:αυτόν|αυτήν|αυτή|τον τελευταίο|την τελευταία|όποιον|όποια) (?:που )?(?:με|μας) (?:πήρε|κάλεσε|πήρανε|πήραν)(?: .*)?",
            "call back",
        ),
        rule(
            "(?:ξαναπάρ$E|ξανακάλεσ$E|επανάκληση|$CALL_VERB (?:ξανά|πάλι)|" +
                "$CALL_VERB(?: ξανά| πάλι)? (?:τον |το )?τελευταίο (?:αριθμό|ρυθμό|νούμερο)(?: ξανά| πάλι)?|" +
                "(?:κάν$E|ξανακάν$E)(?: ξανά)? (?:την |τη )?τελευταία κλήση|ξανά την τελευταία κλήση|επανάληψη (?:της )?(?:τελευταίας )?κλήσης)",
            "redial",
        ),
        rule("$CALL (.+?) (?:στο|στη|στην|στον|σε) ($TYPES)(?: του| της| τους)?") { m -> typed(m.groupValues[1], m.groupValues[2]) },
        rule("$CALL (?:στο |το )?($TYPES) (του|της|των) (.+)") { m -> typed(owner(m.groupValues[2], m.groupValues[3]), m.groupValues[1]) },
        rule("(?:$CALL_VERB τηλέφωνο|κάλεσ$E|τηλεφώνησ$E|κλήση|κάν$E (?:μια |ένα )?(?:κλήση|τηλέφωνο)|τηλεφώνημα)", "call"),
        rule("$CALL (.+)") { m ->
            if (SOMETHING.containsMatchIn(m.groupValues[1])) return@rule null
            who(m.groupValues[1]).takeIf { it.isNotEmpty() }?.let { "call $it" } ?: "call"
        },
        // The music, before "βάλε…" is taken for a song.
        rule(
            "(?:παύση|pause|στοπ|stop|(?:βάλ$E|κάν$E) παύση|$STOP(?: (?:τη |την )?(?:μουσική|αναπαραγωγή)| το (?:τραγούδι|κομμάτι))?|" +
                "(?:κλείσ$E|σβήσ$E|κόψ$E) (?:τη |την )?μουσική|ησυχία|σιωπή|σκασμός|σώπα|φτάνει|αρκετά|πάγωσ$E|παύσ$E)",
            "pause",
        ),
        rule(
            "(?:συνέχισ$E|συνέχεια|συνέχισ$E (?:τη |την )?(?:μουσική|αναπαραγωγή)|συνέχισ$E να παίζεις|αναπαραγωγή|παίξ$E|play|" +
                "$START (?:τη |την )?μουσική|(?:βάλ$E|παίξ$E|ξαναβάλ$E|ακούσ(?:ω|ουμε)) (?:λίγη |λίγο |τη |την |καμιά |κάποια )?μουσική|" +
                "(?:βάλ$E|παίξ$E|ακούσ(?:ω|ουμε)) κάτι|άνοιξ$E (?:τη |την )?μουσική|(?:η )?μουσική)",
            "continue",
        ),
        rule(
            "(?:(?:το |την )?επόμεν(?:ο|η)(?: τραγούδι| κομμάτι)?|(?:$GO|πέρνα|περάστε|βάλ$E|παίξ$E) (?:στο |το |στην |την )?επόμεν(?:ο|η)(?: τραγούδι| κομμάτι)?|" +
                "παράλειψη|προσπέρασ$E(?: το| αυτό)?(?: το τραγούδι)?|skip|next|άλλαξ$E (?:το )?(?:τραγούδι|κομμάτι)|(?:ένα )?άλλο (?:τραγούδι|κομμάτι)|" +
                "(?:βάλ$E|παίξ$E) (?:κάτι άλλο|κάτι διαφορετικό|(?:ένα )?άλλο (?:τραγούδι|κομμάτι))|κάτι άλλο|παρακάτω)",
            "next",
        ),
        rule(
            "(?:(?:το |την )?προηγούμεν(?:ο|η)(?: τραγούδι| κομμάτι)?|" +
                "(?:$GO|βάλ$E|παίξ$E) (?:πίσω )?(?:στο |το |στην |την )?προηγούμεν(?:ο|η)(?: τραγούδι| κομμάτι)?|" +
                "(?:το τραγούδι|το κομμάτι|αυτό) (?:από )?πριν|πίσω ένα (?:τραγούδι|κομμάτι))",
            "previous",
        ),
        rule(
            "(?:(?:ξαναβάλ$E|ξαναπαίξ$E)(?: το| αυτό)?(?: το)?(?: τραγούδι| κομμάτι)?(?: από την αρχή| απ' την αρχή)?|" +
                "$AGAIN_VERB(?: το| αυτό)?(?: το)?(?: τραγούδι| κομμάτι)?(?: ξανά| πάλι)(?: από την αρχή| απ' την αρχή)?|" +
                "$AGAIN_VERB(?: το| αυτό)?(?: το)?(?: τραγούδι| κομμάτι)? (?:από την αρχή|απ' την αρχή)|" +
                "επανάλαβ(?:ε|ετε)(?: το| αυτό)?(?: το)?(?: τραγούδι| κομμάτι)?|(?:ξανά|πάλι) (?:το τραγούδι|το κομμάτι|αυτό)|" +
                "(?:το τραγούδι |το κομμάτι )?(?:ξανά |πάλι )?από την αρχή)",
            "play it again",
        ),
        rule(
            "(?:τι (?:παίζει|παίζεις|ακούμε|ακούω|τραγούδι παίζει|τραγούδι είναι(?: αυτό)?|κομμάτι παίζει|κομμάτι είναι(?: αυτό)?|" +
                "είναι αυτό που (?:παίζει|ακούμε|ακούω))|ποιο (?:τραγούδι|κομμάτι) (?:είναι αυτό|παίζει|ακούμε|ακούω)|" +
                "ποιο είναι αυτό το (?:τραγούδι|κομμάτι)|ποιος (?:τραγουδάει|τραγουδά|το τραγουδάει|το τραγουδά|το λέει|είναι ο τραγουδιστής|" +
                "είναι ο καλλιτέχνης|ερμηνεύει)|πώς (?:λέγεται|το λένε)(?: αυτό)?(?: το)?(?: τραγούδι| κομμάτι)?|τίτλος(?: του τραγουδιού)?)" +
                "(?: αυτή τη στιγμή)?",
            "what's playing",
        ),
        rule("(?:$PLAY|ψάξ$E (?:το |τη |την )?(?:τραγούδι|άλμπουμ|καλλιτέχνη|λίστα)) (.+)") { m -> query(m.groupValues[1])?.let { "play $it" } },
        // Places: "βρες…" only for what's near; "πήγαινε…" a place or an app.
        rule("(?:βρ(?:ες|είτε|εις|ω|ούμε)|ψάξ$E|εντόπισ$E|πού (?:είναι|υπάρχει|έχει|βρίσκεται)|υπάρχει)(?: μου| μας)? (.+)") { m ->
            nearby(m.groupValues[1])
        },
        rule("(?:άνοιξ$E|$START|εκκίνηση|άνοιγμα|δείξ$E)(?: μου| μας)? (.+)") { m -> appName(m.groupValues[1])?.let { "open " + app(it) } },
        rule("(?:$GO|επιστροφή) (.+)") { m ->
            val said = latin(m.groupValues[1])
            val p = place(said)
            if (app(p) != p) "go to " + app(p) else "go to " + remember(p, said)
        },
        // The time and the weather.
        rule("(?:τι ώρα(?: είναι| έχουμε| πάει| έχει)?|(?:την |τη |η )?ώρα)", "what time is it"),
        rule(
            "(?:τι καιρό(?: κάνει| θα κάνει| έχει| θα έχει| έχουμε| θα έχουμε)?|πώς (?:είναι|θα είναι) ο καιρός|(?:ο |τον )?καιρός|" +
                "(?:η )?πρόγνωση(?: του)?(?: καιρού)?|(?:θα )?βρέχει|θα βρέξει|πόσους βαθμούς (?:έχει|κάνει|έχουμε)|πόσοι βαθμοί (?:είναι|έχει)|" +
                "τι θερμοκρασία (?:έχει|κάνει|έχουμε)|(?:η )?θερμοκρασία|(?:θα )?κάνει (?:κρύο|ζέστη)|(?:θα )?χιονίζει|θα χιονίσει)(?: .*)?",
            "what's the weather",
        ),
    )

    private val GROUPS = setOf(
        "παιδιά", "αδέρφια", "αδέλφια", "ξαδέρφια", "ξαδέλφια", "κολλητάρια", "κορίτσια", "αγόρια", "φιλαράκια",
        "γενέθλια", "ανίψια", "εγγόνια", "πεθερικά", "γραφεία", "συνεργεία",
    ).map(::bare).toSet()

    private enum class Sex { MAN, WOMAN }

    /**
     * A man's or a woman's name, by how a Greek name ends ("Γιώργος", "Άννα"); null for anything
     * else (a name in Latin letters, a number, "Γραφείο", a group like "Παιδιά"), which is then said
     * in a way that needs no article.
     */
    private fun sex(name: String): Sex? {
        val words = name.trim().split(Regex(" +"))
        if (words.size > 3 || words.any { w -> w.isEmpty() || w.any { c -> c !in 'Ά'..'ώ' && c != '.' && c != '-' } }) return null
        val first = words.first().lowercase().trimEnd('.')
        return when {
            bare(first) in GROUPS -> null
            Regex("(?:ος|ός|ας|άς|ης|ής|ούς)$").containsMatchIn(first) -> Sex.MAN
            Regex("(?:α|ά|η|ή|ω|ώ|ού)$").containsMatchIn(first) -> Sex.WOMAN
            else -> null
        }
    }

    /** "την" before a vowel and κ, π, τ, ξ, ψ, μπ, ντ, γκ; "τη" before the rest, as Greek writes it. */
    private fun her(name: String): String {
        val n = unaccented(name.trim().lowercase())
        val keepsN = n.take(1) in setOf("α", "ε", "η", "ι", "ο", "υ", "ω", "κ", "π", "τ", "ξ", "ψ") ||
            n.startsWith("μπ") || n.startsWith("ντ") || n.startsWith("γκ")
        return if (keepsN) "την" else "τη"
    }

    /** "τον Γιώργο Παπαδόπουλο", "την Άννα", "τη Μαρία": a person as the object; null when it doesn't look like one. */
    private fun acc(name: String): String? = when (sex(name)) {
        Sex.MAN -> "τον " + name.trim().split(Regex(" +")).joinToString(" ") { w ->
            if (Regex("(?:ος|ός|ας|άς|ης|ής|ούς)$").containsMatchIn(w.lowercase())) w.dropLast(1) else w
        }
        Sex.WOMAN -> her(name) + " " + name.trim()
        null -> null
    }

    /** "Ο Γιώργος", "Η Άννα": a person as the subject; null when it doesn't look like one. */
    private fun nom(name: String): String? = when (sex(name)) {
        Sex.MAN -> "Ο ${name.trim()}"
        Sex.WOMAN -> "Η ${name.trim()}"
        null -> null
    }

    /** A name as the driver said it, which comes lower case: "γιώργος" → "Γιώργος". */
    private fun cap(s: String) = s.trim().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

    /** Where a number is, from its label as the phone writes it ("Κινητό", "Οικία"): "στο κινητό". */
    private fun where(label: String): String? = when (bare(label)) {
        "κινητο", "mobile", "cell" -> "στο κινητό"
        "οικια", "σπιτι", "home" -> "στο σπίτι"
        "εργασια", "δουλεια", "γραφειο", "work" -> "στη δουλειά"
        "σταθερο" -> "στο σταθερό"
        "κυριο", "main" -> "στον κύριο αριθμό"
        "αλλο", "other" -> "στον άλλο αριθμό"
        else -> null
    }

    /** "14:05", "9:30": the time as the phone writes it, without a leading zero. */
    private fun hour(time: String) = time.replace(Regex("^0(?=[1-9])"), "")

    /** [s] with one full stop at the end, also after a time that has its own ("2:30 μ.μ."). */
    private fun stop(s: String) = if (s.endsWith(".")) s else "$s."

    /** "στις 14:30", "στη 1:30". */
    private fun at(time: String) = (if (oneOClock(time)) "στη " else "στις ") + hour(time)

    private fun left(english: String) = span(english, "ώρα" to "ώρες", "λεπτό" to "λεπτά", "και")

    override val answers: Map<Said, (List<String>) -> String> = mapOf(
        Said.HELP to t("Να τι μπορείτε να πείτε."),
        Said.UNKNOWN to t("Αυτό δεν μπορώ να το κάνω ακόμα."),
        Said.WHERE_TO to t("Πού θέλετε να πάτε;"),
        Said.NO_NAV_APPS to t("Δεν έχετε εγκατεστημένη καμία εφαρμογή πλοήγησης."),
        Said.NO_NAV_APP_CALLED to t("Δεν βρίσκω εφαρμογή πλοήγησης με το όνομα $1."),
        Said.NOT_NAV_APP to t("Αυτή δεν είναι εφαρμογή πλοήγησης."),
        Said.FINDING_NEAREST to { g -> "Ψάχνω ${asSaid(g[1])?.let(::nearSaid) ?: "${g[1]} εδώ κοντά"} με το ${g[2]}." },
        Said.NAVIGATING to { g ->
            "Πάμε ${asSaid(g[1])?.let(::there) ?: if (g[1] == "σπίτι") "σπίτι" else "προς ${g[1]}"} με το ${g[2]}."
        },
        Said.OPENING_SETTINGS to t("Ανοίγω τις ρυθμίσεις."),
        Said.OPENING_VEHICLE_DATA to t("Ανοίγω τα δεδομένα του οχήματος."),
        Said.OPENING to t("Ανοίγω το $1."),
        Said.OPEN_MUSIC_FIRST to t("Ανοίξτε πρώτα μια εφαρμογή μουσικής και ζητήστε το ξανά."),
        Said.MUSIC_APP_LATE to t("Η εφαρμογή μουσικής δεν άνοιξε εγκαίρως. Δοκιμάστε ξανά."),
        Said.IGNORES_SEARCH to t("Το $1 δεν δέχεται φωνητικές αναζητήσεις. Διαλέξτε κάτι στην οθόνη του."),
        Said.LOOKING_FOR to t("Ψάχνω $1 στο $2."),
        Said.NEEDS_NOTIFICATIONS to t("Για να διαβάζει μηνύματα, το myCar χρειάζεται πρόσβαση στις ειδοποιήσεις. Επιτρέψτε την από το τηλέφωνο."),
        Said.NO_MESSAGE_FROM to { g -> "Δεν βρίσκω μήνυμα από ${cap(g[1]).let { acc(it) ?: "«$it»" }} για να απαντήσω." },
        Said.NO_MESSAGES_YET to t("Δεν υπάρχουν ακόμα μηνύματα."),
        Said.NO_NEW_MESSAGES to t("Δεν υπάρχουν νέα μηνύματα."),
        Said.ENDING_ROUTE to t("Τερματίζω την πλοήγηση."),
        Said.PAUSED to t("Παύση."),
        Said.NOTHING_PLAYING to t("Δεν παίζει τίποτα."),
        Said.SKIPPING to t("Επόμενο."),
        Said.PREVIOUS_TRACK to t("Πάω στο προηγούμενο."),
        Said.PLAYING to t("Συνεχίζω."),
        Said.PLAYING_APP to { g -> if (g[1] == "the music") "Βάζω μουσική." else "Βάζω μουσική από το ${g[1]}." },
        Said.GOING_BACK to t("Πάω πίσω."),
        Said.BACK_TO_CAR to t("Επιστροφή στην οθόνη του αυτοκινήτου."),
        Said.HEADING_HOME to t("Επιστροφή στην αρχική οθόνη."),
        Said.YOUR_APPS to t("Ορίστε οι εφαρμογές σας."),
        Said.VEHICLE_DATA_OFF to t(
            "Τα δεδομένα του οχήματος είναι απενεργοποιημένα. Ενεργοποιήστε τα στις ρυθμίσεις, στις πειραματικές λειτουργίες.",
        ),
        Said.TIME to { g -> stop("Η ώρα είναι ${hour(g[1])}") },
        Said.WEATHER to { g -> "${g[3]}: " + listOfNotNull(g[1], sky(g[2])).joinToString(", ") + "." },
        Said.NO_WEATHER to t("Δεν έχω ακόμα τον καιρό."),
        Said.NO_ROUTE to t("Δεν υπάρχει διαδρομή στον χάρτη."),
        Said.ARRIVE_AT_IN to { g -> "Γύρω ${at(g[1])}, σε ${left(g[2])}." },
        Said.ARRIVE_AT to { g -> stop("Γύρω ${at(g[1])}") },
        Said.ARRIVE_IN to { g -> "Σε περίπου ${left(g[1])}." },
        Said.MAP_HASNT_SAID to t("Ο χάρτης δεν το έχει πει ακόμα."),
        Said.NEEDS_PHONE to t("Το myCar χρειάζεται πρώτα την άδεια Τηλέφωνο: επιτρέψτε την από την οθόνη του τηλεφώνου."),
        Said.NEEDS_CALL_LOG to t("Το myCar χρειάζεται πρώτα το αρχείο κλήσεων του τηλεφώνου: επιτρέψτε το στο τηλέφωνο."),
        Said.NO_REDIAL to t("Δεν υπάρχει κλήση για επανάκληση."),
        Said.NO_CALL_BACK to t("Δεν υπάρχει κλήση για να πάρω πίσω."),
        Said.PRIVATE_NUMBER to t("Εκείνη η κλήση ήταν από απόκρυψη, οπότε δεν μπορώ να πάρω πίσω."),
        Said.CALLING_AGAIN to { g -> acc(g[1])?.let { "Ξαναπαίρνω $it." } ?: "Νέα κλήση προς ${g[1]}." },
        Said.CALLING_BACK to { g -> acc(g[1])?.let { "Παίρνω πίσω $it." } ?: "Επιστροφή κλήσης προς ${g[1]}." },
        Said.WHO_TO_CALL to t("Ποιον να καλέσω;"),
        Said.NEEDS_CONTACTS to t("Το myCar χρειάζεται πρώτα τις άδειες Τηλέφωνο και Επαφές: επιτρέψτε τες από την οθόνη του τηλεφώνου."),
        Said.NOT_IN_CONTACTS to { g -> "Δεν βρίσκω ${cap(g[1]).let { acc(it) ?: "«$it»" }} στις επαφές σας." },
        Said.NO_SUCH_NUMBER to { g ->
            val kind = mapOf("mobile" to "κινητού", "cell" to "κινητού", "phone" to "τηλεφώνου", "home" to "σπιτιού", "work" to "εργασίας")
            val other = if (g[3] == "the other one") "στον άλλο αριθμό" else where(g[3]) ?: "τον αριθμό «${g[3]}»"
            "Δεν υπάρχει αριθμός ${kind[g[2]] ?: "«${g[2]}»"} για ${acc(g[1]) ?: "την επαφή ${g[1]}"}. Να καλέσω $other;"
        },
        Said.CALL_QUESTION to { g -> "Να καλέσω ${acc(g[1]) ?: "την επαφή ${g[1]}"};" },
        Said.CALLING_TYPE to { g ->
            acc(g[1])?.let { who -> where(g[2])?.let { "Καλώ $who $it." } ?: "Καλώ $who, ${g[2]}." }
                ?: "Κλήση προς ${g[1]}, ${where(g[2]) ?: g[2]}."
        },
        Said.CALLING to { g -> acc(g[1])?.let { "Καλώ $it." } ?: "Κλήση προς ${g[1]}." },
        Said.NO_APP_CALLED to t("Δεν βρίσκω εφαρμογή με το όνομα $1."),
        Said.SOMETHING_WRONG to t("Κάτι πήγε στραβά. Δοκιμάστε ξανά."),
        Said.DONE to t("Έγινε."),
        Said.HOME_ASSISTANT_SILENT to t("Το Home Assistant δεν απάντησε."),
        Said.NEEDS_MIC to t("Χρειάζεται η άδεια για το μικρόφωνο."),
        Said.AFTER_CALL to t("Οι φωνητικές εντολές λειτουργούν όταν τελειώσει η κλήση."),
        Said.DIDNT_CATCH to t("Δεν το κατάλαβα."),
        Said.RECOGNIZER_BUSY to t("Η αναγνώριση ομιλίας είναι απασχολημένη."),
        Said.RECOGNIZER_NETWORK to t("Η αναγνώριση ομιλίας χρειάζεται δίκτυο που δεν έχει."),
        Said.RECOGNITION_FAILED to t("Η αναγνώριση ομιλίας απέτυχε."),
        Said.SPEECH_NOT_READY to t("Η φωνή δεν είναι ακόμα έτοιμη."),
        Said.MODEL_NOT_LOADED to t("Το φωνητικό μοντέλο δεν φορτώθηκε."),
        Said.MODEL_NOT_DOWNLOADED to t("Κατεβάστε πρώτα ένα φωνητικό μοντέλο από τις ρυθμίσεις του myCar στο τηλέφωνο."),
        Said.MIC_SILENCED to t("Το μικρόφωνο του τηλεφώνου είναι σε σίγαση. Ξεκλειδώστε το τηλέφωνο και δοκιμάστε ξανά."),
        Said.MIC_UNAVAILABLE to t("Το μικρόφωνο δεν είναι διαθέσιμο αυτή τη στιγμή."),
        Said.MISSED_CALL_FROM to { g -> "Αναπάντητη κλήση από ${acc(g[1]) ?: g[1]}." },
        Said.WANT_TO_REPLY to t("Θέλετε να απαντήσετε;"),
        Said.GO_AHEAD to t("Σας ακούω."),
        Said.NO_CAR_REPLIES to t("Το $1 δεν επιτρέπει απαντήσεις από το αυτοκίνητο."),
        Said.REPLY_AFTER_CALL to t("Η φωνητική απάντηση λειτουργεί όταν τελειώσει η κλήση."),
        Said.YOUR_REPLY_TO to { g -> "Τι θέλετε να απαντήσετε ${acc(g[1])?.let { "σ$it" } ?: "στη συνομιλία ${g[1]}"};" },
        Said.YOUR_REPLY to t("Τι θέλετε να απαντήσετε;"),
        Said.SENDING_TO to { g -> "Αποστολή ${acc(g[1])?.let { "σ$it" } ?: "στη συνομιλία ${g[1]}"}: ${g[2]}" },
        Said.SENDING to t("Αποστολή: $1"),
        Said.NOT_SENT to t("Δεν στάλθηκε."),
        Said.MESSAGE_SENT to t("Το μήνυμα στάλθηκε."),
        Said.REPLY_WITHDRAWN to t("Το $1 απέσυρε την απάντηση. Ανοίξτε την εφαρμογή στο τηλέφωνο για να απαντήσετε."),
        // A band's name ("Ξύλινα Σπαθιά") can't be told from a woman's: only a man's takes an article.
        Said.NOW_PLAYING to { g ->
            "${g[1]}, " + (acc(g[2])?.takeIf { sex(g[2]) == Sex.MAN }?.let { "από $it" } ?: "ερμηνεία: ${g[2]}") + "."
        },
    )

    /** The sky as Weather says it, in Greek; null for none ("-"). */
    private fun sky(english: String): String? = when (english) {
        "Clear" -> "καθαρός ουρανός"
        "Mostly clear" -> "λίγα σύννεφα"
        "Partly cloudy" -> "αραιή συννεφιά"
        "Overcast" -> "συννεφιά"
        "Fog" -> "ομίχλη"
        "Drizzle" -> "ψιχάλα"
        "Rain" -> "βροχή"
        "Snow" -> "χιόνι"
        "Rain showers" -> "μπόρες"
        "Snow showers" -> "μπόρες χιονιού"
        "Thunderstorm" -> "καταιγίδα"
        "-" -> null
        else -> english
    }

    override val yes = variants(
        "ναι", "εντάξει", "οκ", "οκέι", "ok", "okay", "σίγουρα", "βέβαια", "βεβαίως", "φυσικά", "μάλιστα", "απάντησε", "απαντήστε", "θέλω",
    ).toSet()
    override val no = variants(
        "όχι", "άκυρο", "ακύρωση", "ακύρωσε", "ακυρώστε", "άσ' το", "άσ'το", "άστο", "άσε", "τίποτα", "όχι τώρα", "δεν θέλω", "δε θέλω",
        "δεν χρειάζεται", "σταμάτα", "στοπ", "stop", "αργότερα",
    ).toSet()
    override val cancel = variants(
        "ακύρωση", "ακύρωσε", "ακυρώστε", "άκυρο", "όχι", "σταμάτα", "σταματήστε", "στοπ", "άσ' το", "άσ'το", "άστο", "άσε", "μην", "μη",
    ).toSet()
    override val change = variants(
        "αλλαγή", "άλλαξε", "άλλαξέ το", "αλλάξτε", "διόρθωσε", "διορθώστε", "διόρθωση", "ξανά", "πάλι", "λάθος", "από την αρχή",
    ).toSet()
    override val send = variants("στείλε", "στείλ' το", "στείλ'το", "στείλτε", "αποστολή", "ναι", "οκ", "ok", "okay", "εντάξει", "πάμε").toSet()

    override val samples = listOf(
        "Βάλε Μίκη Θεοδωράκη", "Τι τραγούδι είναι αυτό;", "Επόμενο τραγούδι", "Πάρε τη μαμά", "Άνοιξε το Organic Maps", "Παύση", "Βοήθεια",
    )

    /** "Ο Γιώργος λέει: …"; one that doesn't look like a person, a group or a shop, is just its name. */
    override fun says(who: String, text: String): String = when {
        who.startsWith("Ο ") || who.startsWith("Η ") -> "$who λέει: $text"
        else -> nom(who)?.let { "$it λέει: $text" } ?: "$who: $text"
    }

    override fun inGroup(sender: String, group: String) = "${nom(sender) ?: sender} στην ομάδα $group"

    override val help = listOf(
        "Πήγαινέ με στο…" to "Οδηγίες για έναν προορισμό. Προσθέστε «με το Organic Maps» για να διαλέξετε την εφαρμογή",
        "Βάλε…" to "Ένα τραγούδι, έναν καλλιτέχνη ή ένα άλμπουμ στην ανοιχτή εφαρμογή μουσικής",
        "Παύση · Συνέχισε · Επόμενο · Προηγούμενο" to "Χειρισμός της μουσικής",
        "Τι τραγούδι είναι αυτό;" to "Το τραγούδι και ποιος το ερμηνεύει",
        "Πάρε…" to "Μια επαφή με το όνομά της. Προσθέστε «στο κινητό» για να διαλέξετε τον αριθμό",
        "Πάρε πίσω · Ξαναπάρε" to "Η τελευταία κλήση που δεχτήκατε, ή η τελευταία που κάνατε",
        "Διάβασε τα μηνύματα · Απάντησε στον…" to "Ακούτε τα νέα μηνύματα, ή απαντάτε σε όποιον σας έγραψε",
        "Σταμάτα την πλοήγηση" to "Τερματίζει τη διαδρομή",
        "Πότε φτάνουμε;" to "Η ώρα άφιξης και ο χρόνος που απομένει",
        "Άνοιξε…" to "Μια εφαρμογή, όπως Browser, Τηλέφωνο ή Κοινή χρήση οθόνης",
        "Έξοδος" to "Επιστροφή στην οθόνη του αυτοκινήτου",
        "Αρχική οθόνη · Ρυθμίσεις" to "Για να κινηθείτε στο myCar",
        "Τι ώρα είναι; · Τι καιρό κάνει;" to "Η ώρα ή ο καιρός",
        "Βοήθεια" to "Αυτή η λίστα",
    )

    override val labels = mapOf(
        "Listening…" to "Σας ακούω…",
        "One moment…" to "Μια στιγμή…",
        "You can say" to "Μπορείτε να πείτε",
        "Say “cancel” or “change it”" to "Πείτε «ακύρωση» ή «αλλαγή»",
        "Do you want to reply? Say yes or no." to "Θέλετε να απαντήσετε; Πείτε ναι ή όχι.",
        "Get ready to say your reply." to "Ετοιμαστείτε να πείτε την απάντησή σας.",
        "Say your reply" to "Πείτε την απάντησή σας",
        "Anything else" to "Οτιδήποτε άλλο",
        "Home Assistant answers it: your home, and any question its voice assistant knows." to
            "Απαντά το Home Assistant: το σπίτι σας και κάθε ερώτηση που ξέρει ο φωνητικός του βοηθός.",
    )
}
