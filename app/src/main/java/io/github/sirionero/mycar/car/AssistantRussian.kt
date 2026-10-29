package io.github.sirionero.mycar.car

/**
 * The assistant in Russian (see [AssistantLanguage]). Its answers say "вы", as myCar's Russian
 * screens do; the driver may say "ты", "вы" or the infinitive ("позвони", "позвоните",
 * "позвонить"), and the recognizer writes "ё" or "е" as it likes.
 *
 * A name or a place changes its ending after a verb or a preposition ("позвони Анне", "до
 * вокзала"). AssistantCommands finds a contact by the name the phone has, and the map searches for
 * a place's name: both get the nominative back ("анна", "вокзал") where the ending says it
 * clearly, and a stem that still finds the contact where it doesn't ("набери Юлию": "юли", for
 * Юлия or Юлий, confirmed on the screen). What follows a place's noun ("улицы Ленина", "станции
 * метро") stays as said.
 */
internal object RussianPhrases : Phrasebook() {

    override val leadFillers = listOf(
        "эй майкар", "окей майкар", "ок майкар", "привет майкар", "эй mycar", "окей mycar", "ok mycar", "эй my car", "окей my car",
        "ok my car", "майкар", "май кар", "mycar", "my car", "эй", "привет", "окей", "ок", "ok", "okay", "слушай", "слушайте", "так",
        "ну", "а", "пожалуйста", "будь добр", "будь добра", "будьте добры", "будь другом", "не мог бы ты", "не могла бы ты",
        "не могли бы вы", "ты можешь", "вы можете", "можешь", "можете", "сможешь", "сможете", "можно", "давай", "давайте", "помоги",
        "помогите", "скажи мне", "скажите мне", "скажи", "скажите", "подскажи мне", "подскажите мне", "подскажи", "подскажите",
        "я хочу", "хочу", "мне нужно", "мне надо", "нужно", "надо", "сейчас", "теперь", "быстро", "срочно",
    )
    override val tailFillers = listOf(
        "спасибо большое", "большое спасибо", "пожалуйста", "спасибо", "сейчас", "срочно", "быстро", "побыстрее", "давай", "давайте",
    )
    override val appWords = listOf(" в приложении ", " через ", " с помощью ", " используя ", " в ", " на ")

    /** A pattern as written here: "~" is the polite "-те" ("позвони~": позвони, позвоните), and "ё" takes "е" too. */
    private fun ru(pattern: String) = pattern.replace("~", "(?:те)?").replace("ё", "[её]")

    private fun rule(pattern: String, to: String) = Rule(ru(pattern), to)
    private fun rule(pattern: String, to: (MatchResult) -> String?) = Rule(ru(pattern), to)

    /** The case a name or a place is in: after "позвони" the dative, after "до" the genitive… */
    private enum class Case {
        NOM, GEN, DAT, ACC, INS, PREP,

        /** After "набери": "набери маме" or "набери маму". */
        DAT_OR_ACC,

        /** After "ближайшей": a feminine word in the genitive, dative or prepositional. */
        FEM,
    }

    /** After these a genitive "-и" or a dative "-е" stands for "-а": "Ольги", "Маше", "заправке". */
    private const val HARD = "гкхжшчщц"
    private const val VOWELS = "аеёиоуыэюя"

    private fun String.russian() = isNotEmpty() && (last() in 'а'..'я' || last() == 'ё')

    /** A word's [ending], with what takes its place: a fixed ending or one that depends on the stem. */
    private class End(val ending: String, val min: Int = 2, val to: (String) -> String)

    private fun end(ending: String, to: String, min: Int = 2) = End(ending, min) { it + to }

    /** [w] with the first of [ends] it has changed, keeping at least each one's [End.min] letters; null for none. */
    private fun change(w: String, ends: List<End>): String? {
        for (e in ends) if (w.endsWith(e.ending) && w.length - e.ending.length >= e.min) return e.to(w.dropLast(e.ending.length))
        return null
    }

    // Names. "позвони Анне", "набери Ивана", "свяжись с Сергеем": what a contact list has.

    /** Names and words for people whose nominative ends in "-а", for "набери Анну" (or Ивану?). */
    private val A_NAMES = setOf(
        "анна", "елена", "ольга", "ирина", "татьяна", "светлана", "екатерина", "марина", "галина", "людмила", "надежда", "вера",
        "нина", "валентина", "тамара", "лариса", "алла", "инна", "жанна", "яна", "оксана", "алина", "кристина", "полина", "вероника",
        "диана", "карина", "алёна", "алена", "арина", "ульяна", "варвара", "василиса", "милана", "злата", "лиза", "лена", "света",
        "маша", "даша", "саша", "наташа", "ира", "вика", "ника", "алиса", "ева", "эмма", "зина", "рита", "мила", "лера", "дина",
        "лида", "люба", "роза", "ксюша", "дима", "миша", "никита", "гриша", "лёша", "леша", "серёжа", "сережа", "вова", "юра",
        "гена", "слава", "лёва", "лева", "паша", "рома", "гоша", "мама", "папа", "мамочка", "бабушка", "дедушка", "сестра", "жена",
        "дочка", "подруга", "соседка", "тёща", "теща",
    )

    /** Names and words for people whose nominative ends in "-я" after a consonant: "Тане" is Таня, "Анне" Анна. */
    private val YA_NAMES = setOf(
        "аня", "таня", "ваня", "катя", "коля", "оля", "валя", "настя", "витя", "юля", "галя", "надя", "боря", "костя", "толя",
        "федя", "петя", "соня", "тоня", "поля", "женя", "сеня", "лёня", "митя", "люся", "ася", "вася", "варя", "лиля", "эля", "аля",
        "уля", "даня", "саня", "тася", "маня", "дуня", "стася", "ляля", "майя", "тётя", "тетя", "дядя", "няня", "мамуля", "бабуля",
        "дедуля", "папуля",
    )

    /** Forms no ending rule gets right: a vowel that comes and goes, or another stem. */
    private val NAME_FORMS = mapOf(
        "отцу" to "отец", "отца" to "отец", "отцом" to "отец", "матери" to "мать", "матерью" to "мать",
        "дочери" to "дочь", "дочерью" to "дочь", "павлу" to "павел", "павла" to "павел", "павлом" to "павел",
        "петру" to "петр", "петра" to "петр", "петром" to "петр", "льву" to "лев", "льва" to "лев", "львом" to "лев",
        "игоря" to "игорь", "игорем" to "игорь", "свёкру" to "свёкор", "свёкра" to "свёкор",
        // "Жене" is Женя or the wife: the piece finds both, and the screen asks which.
        "жене" to "жен",
    )

    /** "-е" in the dative: "Анне", "Маше", "Тане", "Зое" → анна, маша, таня, зоя. */
    private fun dativeE(stem: String) = when {
        stem.last() in HARD -> stem + "а"
        stem + "я" in YA_NAMES || stem.last() in VOWELS -> stem + "я"
        else -> stem + "а"
    }

    private val SURNAME_F = listOf(end("ской", "ская"), end("цкой", "цкая"), end("ой", "а", min = 3))
    /** A surname like an adjective: "Достоевскому", "Толстому"; "-ему" is left to "Артему", "Семену". */
    private val ADJ_DAT_M = listOf(end("скому", "ский"), end("цкому", "цкий"), end("ому", "ый"))
    private val ADJ_GEN_M = listOf(end("ского", "ский"), end("цкого", "цкий"), end("ого", "ый"), end("его", "ий"))
    private val MASC_ACC = listOf(end("ия", "ий"), end("ея", "ей"), end("ая", "ай"), end("а", ""))

    private val NAME_ENDS: Map<Case, List<End>> = mapOf(
        Case.DAT to listOf(end("ии", "ия"), end("ье", "ья"), end("ови", "овь")) + SURNAME_F + ADJ_DAT_M + listOf(
            end("ию", "ий"), end("ею", "ей"), end("аю", "ай"), end("ю", "ь"), end("у", ""), End("е") { dativeE(it) },
        ),
        Case.ACC to listOf(end("ию", "ия"), end("ью", "ья"), end("скую", "ская"), end("цкую", "цкая"), end("ую", "ая")) +
            ADJ_GEN_M + listOf(end("ю", "я"), end("у", "а")) + MASC_ACC,
        Case.DAT_OR_ACC to listOf(end("ии", "ия"), end("ье", "ья"), end("ови", "овь")) + SURNAME_F + ADJ_DAT_M + ADJ_GEN_M + listOf(
            end("скую", "ская"), end("ую", "ая"),
            // "Юлию" or "Дмитрию": the piece of both.
            end("ию", "и"), end("ью", "ья"), end("ею", "ей"), end("аю", "ай"),
            End("ю") { if (it + "я" in YA_NAMES) it + "я" else it + "ь" },
            End("у") { if (it + "а" in A_NAMES) it + "а" else it },
            End("е") { dativeE(it) },
        ) + MASC_ACC,
        Case.GEN to listOf(end("ии", "ия"), end("ьи", "ья"), end("ови", "овь")) + SURNAME_F + ADJ_GEN_M + listOf(
            end("ы", "а"), End("и") { if (it.last() in HARD) it + "а" else it + "я" },
            end("ия", "ий"), end("ея", "ей"), end("ая", "ай"), end("я", "ь"), end("а", ""),
        ),
        Case.INS to listOf(
            end("ией", "ия"), end("ьей", "ья"), end("ьёй", "ья"), end("овью", "овь"), end("ской", "ская"), end("цкой", "цкая"), end("ой", "а"),
            End("ей") { if (it.last() in HARD) it + "а" else it + "я" },
            end("ским", "ский"), end("цким", "цкий"), end("ым", ""), end("ием", "ий"), end("еем", "ей"), end("аем", "ай"),
            End("ем") { if (it.last() in HARD) it else it + "ь" }, end("ом", ""),
        ),
    )

    /** One word of a name, back in the nominative where its ending says so: "анне" → "анна", "ивану" → "иван". */
    private fun name(w: String, case: Case): String {
        NAME_FORMS[w]?.let { return it }
        if (w.length < 3 || !w.russian()) return w
        return change(w, NAME_ENDS[case].orEmpty()) ?: w
    }

    private val MINE = setOf(
        "мой", "моя", "моё", "мое", "мою", "моей", "моему", "моего", "моим", "свою", "своей", "своему", "своего", "своим",
    )

    /**
     * Who, as said after a verb in [case] ("маме", "Ивана Петрова", "для Анны", "в офис"), as a
     * contact may be called: "мама", "иван петров", "анна", "офис". A message said with the
     * name ("напиши Анне, что я опаздываю") is left for the reply's own question.
     */
    private fun who(raw: String, case: Case): String {
        var t = raw.trim().replace(Regex(" (?:что|чтобы) .*$"), "")
        var c = case
        Regex("^(?:(для|от|у)|(к|ко)|(с|со)|(в|во|на)) (?=.)").find(t)?.let { m ->
            c = when {
                m.groups[1] != null -> Case.GEN
                m.groups[2] != null -> Case.DAT
                m.groups[3] != null -> Case.INS
                else -> Case.ACC
            }
            t = t.substring(m.range.last + 1)
        }
        if (t == "домой") return "дом"
        return t.split(" ").filter { it.isNotEmpty() && it !in MINE }.joinToString(" ") { name(it, c) }.ifEmpty { t }
    }

    // Places. "до вокзала", "в аптеку", "к ближайшей заправке": what the map can search for.

    /** Words that don't change ("до кафе", "в Сочи"), and abbreviations. */
    private val UNCHANGING = setOf(
        "кафе", "кофе", "метро", "такси", "кино", "казино", "бистро", "шоссе", "депо", "ателье", "сочи", "тбилиси", "хельсинки",
        "баку", "туапсе", "азс", "тц", "трц", "мфц", "гибдд", "жд",
    )

    /** Places whose nominative ends in "-я" after a consonant: "до деревни" is деревня, "до Казани" Казань. */
    private val YA_PLACES = setOf("деревня", "кофейня", "пекарня", "баня", "кухня", "часовня", "конюшня", "земля")

    /** A vowel that comes and goes ("до рынка": рынок), and neuter nouns whose genitive looks masculine ("до озера"). */
    private val PLACE_FORMS = mapOf(
        "рынка" to "рынок", "рынку" to "рынок", "рынке" to "рынок", "посёлка" to "посёлок", "посёлку" to "посёлок",
        "поселка" to "поселок", "поселку" to "поселок", "городка" to "городок", "городку" to "городок",
        "перекрёстка" to "перекрёсток", "перекрёстку" to "перекрёсток", "перекрестка" to "перекресток", "перекрестку" to "перекресток",
        "ларька" to "ларёк", "ларьку" to "ларёк", "дворца" to "дворец", "дворцу" to "дворец",
        "озера" to "озеро", "озеру" to "озеро", "села" to "село", "селу" to "село", "кладбища" to "кладбище", "кладбищу" to "кладбище",
        "моря" to "море", "морю" to "море", "поля" to "поле", "полю" to "поле",
    )

    private fun softPlace(stem: String) = if (stem + "я" in YA_PLACES) stem + "я" else stem + "ь"
    private fun dativePlace(stem: String) = when {
        stem.last() in HARD -> stem + "а"
        stem + "я" in YA_PLACES -> stem + "я"
        else -> stem + "а"
    }

    private val PLACE_ENDS: Map<Case, List<End>> = mapOf(
        Case.GEN to listOf(
            end("ии", "ия"), end("ьи", "ья"), end("ы", "а"), End("и") { if (it.last() in HARD) it + "а" else softPlace(it) },
            end("ения", "ение"), end("ания", "ание"), end("ия", "ий"), end("ея", "ей"), end("ая", "ай"), end("я", "ь"), end("а", ""),
        ),
        Case.DAT to listOf(
            end("ии", "ия"), end("ье", "ья"), End("е") { dativePlace(it) }, end("ению", "ение"), end("ию", "ий"), end("ею", "ей"),
            end("ю", "ь"), end("у", ""), end("и", "ь"),
        ),
        Case.ACC to listOf(end("ию", "ия"), end("ью", "ья"), end("ю", "я"), end("у", "а")),
        Case.FEM to listOf(
            end("ии", "ия"), end("ье", "ья"), end("ьи", "ья"), end("ы", "а"),
            End("и") { if (it.last() in HARD) it + "а" else softPlace(it) }, End("е") { dativePlace(it) },
        ),
        Case.PREP to listOf(end("ии", "ий"), end("е", "")),
    )

    private fun placeNoun(w: String, case: Case): String {
        if (w in UNCHANGING || w.length < 3 || !w.russian()) return w
        PLACE_FORMS[w]?.let { return it }
        return change(w, PLACE_ENDS[case].orEmpty()) ?: w
    }

    /** The masculine adjective's ending for [stem]: "торгов" → "торговый", "детск" → "детский". */
    private fun masc(stem: String) = stem + if (stem.last() in HARD) "ий" else "ый"
    private fun fem(stem: String) = stem + if (stem.last() in "жшчщ") "ая" else "яя"

    private val FEM_ADJ = listOf(end("ой", "ая", 1), End("ей", 1) { fem(it) })

    /** Whether [w] is an adjective in the nominative: "торговый", "зарядная"; not "здание" or "санаторий". */
    private fun isAdjective(w: String) = w.russian() && Regex("(?:ый|ий|ой|ая|яя|ое|ее|ые|ие)$").containsMatchIn(w) &&
        !Regex("(?:ние|тие|рий)$").containsMatchIn(w)

    /** An adjective in [case], in the nominative ("красной" → "красная"); null when [w] isn't one, "" when unsure. */
    private fun adjective(w: String, case: Case): String? {
        if (!w.russian() || w.length < 4) return null
        val ends = when (case) {
            Case.GEN -> listOf(End("ого", 1) { masc(it) }, end("его", "ий", 1)) + FEM_ADJ + listOf(end("ых", "", 1), end("их", "", 1))
            Case.DAT -> listOf(End("ому", 1) { masc(it) }, end("ему", "ий", 1)) + FEM_ADJ + listOf(end("ым", "", 1), end("им", "", 1))
            Case.FEM -> FEM_ADJ
            Case.PREP -> listOf(End("ом", 1) { masc(it) }, end("ем", "ий", 1))
            // The accusative of a thing is its nominative, but for the feminine: "на Красную площадь".
            Case.ACC -> return when {
                w.endsWith("ую") -> w.dropLast(2) + "ая"
                w.endsWith("юю") -> w.dropLast(2) + "яя"
                isAdjective(w) -> w
                else -> null
            }
            else -> return null
        }
        val plural = w.endsWith("ых") || w.endsWith("их") || w.endsWith("ым") || w.endsWith("им")
        return change(w, ends)?.let { if (plural) "" else it }
    }

    /** "ленинградский" before "шоссе": the neuter, "ленинградское". */
    private fun neuter(adjective: String) = when {
        adjective.endsWith("ый") || adjective.endsWith("ой") -> adjective.dropLast(2) + "ое"
        adjective.endsWith("ий") -> adjective.dropLast(2) + if (adjective.dropLast(2).last() in "гкх") "ое" else "ее"
        else -> adjective
    }

    /**
     * [phrase] in [case] in the nominative, as far as its noun: "торгового центра Мега" →
     * "торговый центр мега", "улицы Ленина" → "улица ленина". A street's name before a house
     * number is left as it is ("до Ленина 5"), and so is all of it when an adjective is unsure.
     */
    private fun nominative(phrase: String, case: Case): String {
        if (case == Case.NOM || case == Case.INS) return phrase
        val words = phrase.split(" ").toMutableList()
        var i = 0
        while (i < words.size) {
            val a = adjective(words[i], case) ?: break
            if (a.isEmpty()) return phrase
            words[i] = a
            i++
        }
        if (i < words.size && words.getOrNull(i + 1)?.firstOrNull()?.isDigit() != true) words[i] = placeNoun(words[i], case)
        val noun = words.getOrNull(i)
        if (noun != null && noun.russian() && noun.last() in "оеё") for (j in 0 until i) words[j] = neuter(words[j])
        return words.joinToString(" ")
    }

    private val PREPOSITION = Regex("^(?:(до|от|из|около|возле|у)|(к|ко)|(в|во|на)|(по адресу)) ")

    /** "Ближайшая", "ближайшую", "ближайшей": the place after it is near, in the case this says. */
    private val NEAREST = Regex("^ближайш(ий|ая|ее|ие|его|ей|ему|ую|им|ем|их|ими) (.+)$")

    private val NEAR = Regex(
        ru(
            "^(.+?) (?:где нибудь )?(?:рядом(?: со мной| с нами)?|поблизости|неподалёку|недалеко(?: отсюда)?|поближе|около меня|" +
                "возле меня|вблизи|по пути|по дороге|в округе|вокруг)((?: (?:через|с помощью|в приложении|используя|в|на) .+)?)$",
        ),
    )

    private fun nearestCase(ending: String) = when (ending) {
        "его" -> Case.GEN
        "ему" -> Case.DAT
        "ую" -> Case.ACC
        "ей" -> Case.FEM
        "ем" -> Case.PREP
        else -> Case.NOM
    }

    /**
     * The place in [said] ("до вокзала", "в аптеку", "к ближайшей заправке", "домой") for the map:
     * "вокзал", "аптека", "the nearest заправка", "дом". Without a preposition it's in [default].
     */
    private fun place(said: String, default: Case = Case.NOM): String {
        var t = said.trim().replace(Regex("^(?:меня|нас) "), "")
        if (t == "домой" || t.startsWith("домой ")) return "дом" + t.removePrefix("домой")
        var case = default
        PREPOSITION.find(t)?.let { m ->
            case = when {
                m.groups[1] != null -> Case.GEN
                m.groups[2] != null -> Case.DAT
                m.groups[3] != null -> Case.ACC
                else -> Case.NOM
            }
            t = t.substring(m.range.last + 1)
        }
        NEAREST.matchEntire(t)?.let { m -> return "the nearest " + nominative(m.groupValues[2], nearestCase(m.groupValues[1])) }
        NEAR.matchEntire(t)?.let { m -> return "the nearest " + nominative(m.groupValues[1], case) + m.groupValues[2] }
        return nominative(t, case)
    }

    /** Kinds of places a driver looks for nearby: "найди аптеку" is the nearest one. */
    private val NEARBY_KINDS = listOf(
        "заправк", "азс", "бензоколонк", "аптек", "больниц", "поликлиник", "травмпункт", "кафе", "кофейн", "ресторан", "столов",
        "макдональдс", "магазин", "супермаркет", "гипермаркет", "продуктов", "банкомат", "банк", "парковк", "стоянк", "автостоянк",
        "автосервис", "шиномонтаж", "автомойк", "мойк", "гостиниц", "отель", "хостел", "туалет", "зарядк", "зарядн",
        "электрозаправк", "почт", "полици", "отделение полиции", "остановк", "пекарн", "торговый центр", "тц", "рынок",
        "пункт выдачи", "ветклиник", "стоматологи",
    )

    /** What's near in [said] ("ближайшую аптеку", "аптеку рядом", or just "аптеку"), for the map; null for anything else. */
    private fun nearby(said: String, default: Case): String? {
        val p = place(said, default)
        if (p.startsWith("the nearest ")) return p
        return if (NEARBY_KINDS.any { p.startsWith(it) }) "the nearest $p" else null
    }

    /** "navigate to" the place in [said], remembered as said for the answer. */
    private fun navigate(said: String, default: Case = Case.NOM): String {
        val text = withApp(said)
        return "navigate to " + remember(place(text, default), text)
    }

    /** The nearest place in [said] ("ближайшую аптеку", "аптеку рядом", "аптеку"), remembered as said; null for anything else. */
    private fun navigateNear(said: String, default: Case): String? {
        val text = withApp(said)
        return nearby(text, default)?.let { "navigate to " + remember(it, text) }
    }

    /** What "рядом" can follow without being a place: "что рядом?". */
    private val NOT_PLACES = setOf(
        "что", "кто", "где", "как", "там", "тут", "здесь", "всё", "все", "ничего", "никого", "я", "мы", "ты", "вы", "он", "она", "они", "это",
    )

    // Apps.

    /** myCar's own apps by their Russian names, which the car's screen shows in English. */
    private val OWN_APPS = listOf(
        Regex("телефон|звонилк[аиу]|набор номера|номеронабиратель") to "phone",
        Regex("браузер|интернет|веб браузер") to "browser",
        Regex(ru("(?:демонстраци|трансляци)[яюи] экрана|(?:дублировани|зеркалировани)[ея] экрана|показ экрана")) to "screen sharing",
        Regex("данные (?:автомобиля|машины|авто)") to "vehicle data",
        Regex("настройки") to "settings",
        Regex("кино|кинотеатр|синема") to "cinema",
        Regex("планч[аиуя]") to "plancia",
    )

    /** Apps the recognizer may write in Cyrillic, by how their name starts: "Спотифай", "в Спотифае". */
    private val APP_SPELLINGS = listOf(
        "спотиф" to "spotify", "ютуб музык" to "youtube music", "ютуб" to "youtube", "ютюб" to "youtube", "телеграм" to "telegram",
        "ватсап" to "whatsapp", "вотсап" to "whatsapp", "вацап" to "whatsapp", "вайбер" to "viber", "вейз" to "waze", "вэйз" to "waze",
        "дизер" to "deezer", "тайдал" to "tidal", "шазам" to "shazam", "органик мапс" to "organic maps", "органик мэпс" to "organic maps",
        "османд" to "osmand", "осмэнд" to "osmand", "ксонос" to "xonos", "вела" to "vela",
    )

    private fun latin(name: String): String? = APP_SPELLINGS.firstOrNull { name.startsWith(it.first) }?.second

    /** The start of an app's name ending as an object or after "с помощью": "Яндекс Музыку" → "яндекс музык", which finds "Яндекс Музыка". */
    private fun appStem(name: String): String {
        val last = name.substringAfterLast(' ')
        return if (last.length > 3 && last.russian() && last.last() in "аяуюеиыо") name.dropLast(1) else name
    }

    /**
     * [text] with the app at its end as AssistantCommands can find it: after "через", "с помощью"
     * the start of its name ("через Яндекс Музыку" → "через яндекс музык"), and an app written in
     * Cyrillic in its own letters ("в Спотифае" → "в spotify").
     */
    private fun withApp(text: String): String {
        Regex("^(.* (?:через|с помощью|используя) )(.+)$").matchEntire(" $text")?.let { m ->
            return (m.groupValues[1] + (latin(m.groupValues[2]) ?: appStem(m.groupValues[2]))).trim()
        }
        Regex("^(.* (?:в приложении|в|на) )(.+)$").matchEntire(" $text")?.let { m ->
            latin(m.groupValues[2])?.let { return (m.groupValues[1] + it).trim() }
        }
        return text
    }

    /** The last app asked for, as said, next to the name AssistantCommands got: an answer says it back. */
    @Volatile private var saidApp: Pair<String, String>? = null

    /**
     * An app's name as said after "открой", for AssistantCommands' match on the start of a name:
     * "Яндекс Музыку" ends as an object, and "яндекс музык" finds "Яндекс Музыка" (or "карт",
     * "Карты", for "открой карту").
     */
    private fun app(said: String): String {
        val t = said.removePrefix("приложение ").trim()
        OWN_APPS.firstOrNull { it.first.matches(t) }?.let { return it.second }
        latin(t)?.let { return it }
        val stem = if (t.length > 3 && t.russian() && (t.endsWith("у") || t.endsWith("ю"))) t.dropLast(1) else t
        saidApp = stem to t
        return stem
    }

    // Music.

    /** What a song, an album or an artist is called before its name: "включи песню …", "поставь альбом …". */
    private val MUSIC_WORDS = Regex(
        "^(?:(?:песню|песни|трек|треки|композицию|композиции|альбом|плейлист|подборку|музыку)(?: (?:группы|исполнителя|артиста|певца|певицы))?" +
            "|группу|исполнителя|артиста|певца|певицу|что нибудь|что то)(?: (?:из|от))? ",
    )

    /** What "включи", "поставь" turn on that isn't music: left to Home Assistant, or to nothing. */
    private val NOT_MUSIC = setOf(
        "свет", "лампу", "лампы", "люстру", "светильник", "подсветку", "гирлянду", "кондиционер", "климат", "обогрев", "подогрев",
        "отопление", "печку", "вентилятор", "вентиляцию", "телевизор", "чайник", "бойлер", "сигнализацию", "охрану", "розетку",
        "увлажнитель", "пылесос", "фары", "дворники", "аварийку", "будильник", "таймер", "напоминание", "фонарик", "блютуз",
        "bluetooth", "вайфай", "wifi", "интернет", "навигацию", "навигатор", "карту", "карты", "маршрут", "режим", "экран",
        "камеру", "камеры", "ворота", "гараж", "кофеварку", "кофемашину", "обогреватель",
    )

    /** "play" what's said, without "песню", "альбом"; null for a light or the heating. */
    private fun music(said: String): String? {
        val what = withApp(said.replace(MUSIC_WORDS, "").trim())
        if (what.isEmpty() || what.substringBefore(' ') in NOT_MUSIC) return null
        // "включи музыку на Spotify", "что-нибудь через Spotify": the app's own music, as English "play music on Spotify".
        if (Regex("^(?:на|в|через|с помощью|в приложении|используя) ").containsMatchIn(what)) return "play music $what"
        return "play $what"
    }

    // Calls.

    /** Verbs for a call; the name after "позвони" is in the dative, after "вызови" the accusative, after "набери" either. */
    private const val CALL = "позвони~|позвонить|позвоним|перезвони~|перезвонить|звони~|звонить|звякни~|брякни~|" +
        "сделай~ звонок|сделать звонок|набери~|набрать|наберём|вызови~|вызвать"

    private fun callCase(verb: String) = when {
        verb.startsWith("набер") || verb.startsWith("набра") -> Case.DAT_OR_ACC
        verb.startsWith("вызов") || verb.startsWith("вызв") -> Case.ACC
        else -> Case.DAT
    }

    private const val TYPES = "мобильный|мобильник|мобилу|мобилку|сотовый|домашний|рабочий|работу|служебный"

    private fun numberType(word: String) = when (word) {
        "домашний", "домой" -> "home"
        "рабочий", "работу", "служебный", "офис" -> "work"
        else -> "mobile"
    }

    override val commands = listOf(
        // "Позвони, пожалуйста, маме": the same request without the "пожалуйста" in it.
        rule("(.+?) пожалуйста (.+)") { m -> AssistantLanguage.toEnglish("${m.groupValues[1]} ${m.groupValues[2]}", this) },
        rule(
            "(?:помощь|справка|помоги~|подсказка|что (?:ты умеешь|вы умеете|ты можешь|вы можете|умеешь|умеете)(?: делать)?|" +
                "что (?:я могу|можно|мне|нужно) (?:сказать|говорить)|(?:какие )?(?:есть )?(?:голосовые )?команды|список команд)",
            "help",
        ),
        // Screens of myCar's own, before "вернись…" and "в…" are taken for a place.
        rule(
            "(?:(?:вернись|вернитесь|вернуться|перейди~|перейти|иди~|открой~|открыть|покажи~|показать|выйди~|выйти) )?(?:на |в )?" +
                "(?:главн(?:ый|ое) (?:экран|меню)|главн(?:ая|ую)(?: страниц[ау]| экран)?|домашн(?:ий|ее) (?:экран|меню)|домашн(?:яя|юю) страниц[ау]|рабочий стол)",
            "go home",
        ),
        rule("(?:(?:открой~|открыть|покажи~|показать|зайди~|зайти|перейди~|перейти|иди~) )?(?:в )?настройки(?: mycar| майкар| приложения)?", "open settings"),
        rule("(?:(?:открой~|открыть|покажи~|показать) )?(?:мои |все |список )?(?:приложения|приложений|программы)", "open apps"),
        rule("(?:выход|выйди~|выйти|выходи~|закрой~|закрыть)(?: из)? (?:mycar|my car|майкар|май кар)|выход|выйди~|выйти", "exit"),
        rule("(?:(?:вернись|вернитесь|вернуться|иди~|шаг) )?назад|вернись|вернитесь|обратно|(?:вернись|вернитесь) обратно", "go back"),
        // The route.
        rule(
            "(?:останови~|остановить|отмени~|отменить|заверши~|завершить|закончи~|закончить|прекрати~|прекратить|выключи~|выключить|" +
                "отключи~|отключить|сбрось~|сбросить|убери~|убрать|стоп|отмена|сброс)(?: эту| этот| текущую| текущий)? " +
                "(?:навигаци[юяи]|маршрут|маршрута|ведение(?: по маршруту)?|навигатор)|(?:хватит|перестань~) (?:вести|прокладывать маршрут)",
            "stop navigation",
        ),
        rule(
            "(?:(?:когда|во сколько|через сколько|через какое время) (?:я |мы )?(?:приеду|приедем|доеду|доедем|буду|будем|прибуду|прибудем|" +
                "окажусь|окажемся)(?: на месте)?|сколько (?:ещё |нам |мне |осталось |времени )*(?:ехать|осталось|в пути|до места|до конца)|" +
                "(?:ещё )?(?:долго|далеко)(?: ещё)?(?: ехать)?|время (?:прибытия|в пути|до прибытия)|скоро (?:приедем|доедем|будем))(?: .*)?",
            "when do i arrive",
        ),
        // Messages: "напиши Анне" before "сообщения…" is taken for reading them; "ответь ей" is the last one.
        rule("(?:ответь~|ответить)(?: на)? (?:последнее(?: сообщение| смс)?|ему|ей|им)", "read my last message"),
        rule("(?:ответь~|ответить|ответим) на (?:(?:последнее |его |её )?(?:сообщение|смс|письмо)(?: от)?|сообщение от) (.+)") { m ->
            "reply to " + who(m.groupValues[1], Case.GEN)
        },
        rule(
            "(?:ответь~|ответить|ответим|напиши~|написать|напишем|(?:отправь~|отправить|пошли~|послать|скинь~|напиши~|написать) " +
                "(?:сообщение|смс|смску|эсэмэску|ответ)|сообщение|смс) (?!на |от |ему$|ей$|им$)(.+)",
        ) { m -> "reply to " + who(m.groupValues[1], Case.DAT) },
        rule(
            "(?:(?:прочитай~|прочти~|прочитать|прочесть|зачитай~|зачитать|почитай~|читай~|проверь~|проверить|озвучь~|озвучить)(?: мне| нам)?(?: .*)? " +
                "(?:сообщени[еяй]|смс|sms|смски|смску|эсэмэски|эсэмэску|переписк[уи]|уведомления)(?: .*)?|" +
                "(?:есть(?: у меня)?|какие(?: есть)?|мои|новые|непрочитанные|последнее|последние)?(?: новые| непрочитанные)? ?" +
                "(?:сообщения|сообщение|смс)|что (?:мне )?(?:пришло|написали))",
        ) { m ->
            val said = m.value
            when {
                "нов" in said || "непрочитан" in said || "пришло" in said || "написали" in said -> "read my new messages"
                "последн" in said -> "read my last message"
                else -> "read my messages"
            }
        },
        // Calls.
        rule(
            "(?:перезвони~|перезвонить|перезвоним)(?: (?:ему|ей|им|обратно|назад))?|(?:позвони~|набери~) (?:ему|ей|им)|" +
                "(?:перезвони~|перезвонить)(?: на)? (?:последн(?:ий|ему) )?(?:пропущенн(?:ый|ому)(?: звонок| вызов)?|звонок|вызов)|" +
                "(?:ответь~|ответить) на (?:последний )?пропущенный(?: звонок| вызов)?|перезвони~ (?:тому|той|тем) кто (?:звонил|звонила|звонили)",
            "call back",
        ),
        rule(
            "(?:повтори~|повторить) (?:последний )?(?:звонок|вызов|набор)|повторный (?:звонок|набор|вызов)|" +
                "(?:позвони~|позвонить|набери~|набрать|звони~) (?:ещё раз|снова|опять|повторно)|" +
                "(?:позвони~|позвонить|набери~|набрать|перезвони~)(?: на| по)? последн(?:ий|ему) (?:набранн(?:ый|ому) )?номер(?:у)?",
            "redial",
        ),
        rule("($CALL) (.+?) (?:на ($TYPES)(?: номер| телефон)?|(домой)|в (офис))") { m ->
            val type = m.groupValues.drop(3).first { it.isNotEmpty() }
            "call ${who(m.groupValues[2], callCase(m.groupValues[1]))} on ${numberType(type)}"
        },
        rule("($CALL) на ($TYPES)(?: номер| телефон)? (.+)") { m ->
            "call ${who(m.groupValues[3], callCase(m.groupValues[1]))} on ${numberType(m.groupValues[2])}"
        },
        rule("(?:позвони~|позвонить|набери~|набрать|сделай~ звонок|сделать звонок|звонок|вызов)", "call"),
        rule("(?:набери~|набрать|позвони~ на|позвонить на) номер (.+)") { m -> "call " + who(m.groupValues[1], Case.GEN) },
        rule("(?:свяжи(?:сь|тесь)|связаться|соедини~(?: меня)?|соединить(?: меня)?|созвони(?:сь|тесь)|созвониться) (?:с|со) (.+)") { m ->
            "call " + who(m.groupValues[1], Case.INS)
        },
        rule("($CALL) (?!мне$)(.+)") { m -> "call " + who(m.groupValues[2], callCase(m.groupValues[1])) },
        // The music, before "включи…" is taken for a song.
        rule(
            "(?:пауза|на паузу|(?:поставь~|поставить|сделай~|сделать) (?:на паузу|паузу)|приостанови~|останови~(?: музыку| воспроизведение| песню| трек)?|" +
                "выключи~(?: музыку| радио| звук| песню)?|выключить музыку|стоп(?: музыка)?|тихо|тишина|хватит(?: музыки)?|" +
                "замолчи~|помолчи~|убери~ музыку)",
            "pause",
        ),
        rule(
            "(?:(?:продолжи~|продолжай~|продолжить|возобнови~|возобновить)(?: воспроизведение| музыку| играть)?|играй~ дальше|" +
                "(?:сними~|убери~) (?:с паузы|паузу)|(?:включи~|включить|запусти~|запустить|поставь~|поставить|верни~|вернуть|сыграй~) " +
                "(?:музыку|музычку|песню|что нибудь|что то|какую нибудь музыку|звук)|включи~ обратно|музыку|музыка|музычку|плей|play)",
            "continue",
        ),
        rule(
            "(?:дальше|вперёд|следующ(?:ий|ая|ую|ее)(?: трек| песня| песню| композиция| композицию)?|" +
                "(?:включи~|поставь~|давай~) (?:следующ(?:ий|ую)|другую)(?: трек| песню)?|переключи~(?: трек| песню)?|" +
                "пропусти~(?: трек| песню| эту)?|пропустить|другую(?: песню)?|(?:включи~ |поставь~ )?что нибудь другое|смени~ (?:песню|трек)|некст|next)",
            "next",
        ),
        rule(
            "(?:предыдущ(?:ий|ая|ую|ее)(?: трек| песня| песню| композиция| композицию)?|прошл(?:ый|ая|ую) (?:трек|песня|песню)|" +
                "(?:верни~|включи~|поставь~) (?:предыдущ(?:ий|ую)|прошл(?:ый|ую))(?: трек| песню)?|(?:трек|песню) назад)",
            "previous",
        ),
        rule(
            "(?:(?:повтори~|повторить|включи~|поставь~|сыграй~|запусти~|начни~)(?: (?:эту|этот|её|его))?(?: (?:песню|трек|композицию))? " +
                "(?:ещё раз|заново|сначала|с начала)|(?:повтори~|повторить)(?: (?:эту |этот )?(?:песню|трек|композицию))?|ещё раз|заново|сначала)",
            "play it again",
        ),
        rule(
            "(?:что (?:сейчас |это |щас )?(?:играет|звучит|за песня|за трек|за музыка|за композиция|слушаем|я слушаю|поют)|" +
                "какая (?:это |сейчас )?(?:песня|композиция|музыка)(?: играет| звучит)?|какой (?:это |сейчас )?трек(?: играет| звучит)?|" +
                "кто (?:это )?(?:поёт|исполняет)|кто исполнитель|как называется (?:эта |этот )?(?:песня|трек|композиция)|чья (?:это )?песня)(?: .*)?",
            "what's playing",
        ),
        rule("(?:включи~|включить|поставь~|поставить|сыграй~|сыграть|играй~|проиграй~|воспроизведи~|воспроизвести|послушаем|послушать)(?: мне| нам)? (.+)") { m ->
            music(m.groupValues[1])
        },
        rule("(?:запусти~|запустить|найди~|найти|поищи~)(?: мне)? ((?:песн|трек|альбом|плейлист|подкаст|композици).+)") { m -> music(m.groupValues[1]) },
        // The time and the weather, before "покажи…" is taken for an app.
        rule(
            "(?:который час|сколько (?:сейчас )?времени|сколько время|какое (?:сейчас )?время|(?:скажи~|подскажи~) время|время|точное время|текущее время)",
            "what time is it",
        ),
        rule(
            "(?:(?:какая|что за|что с|что по)(?: сейчас| сегодня| будет)? погод(?:а|ой|е)|(?:покажи~ |скажи~ )?погод[ауеы]|(?:какой )?прогноз(?: погоды)?|" +
                "(?:будет|пойдёт|идёт)(?: ли)? (?:дождь|снег)|(?:дождь|снег) (?:будет|пойдёт)|сколько (?:сейчас )?градусов|" +
                "какая (?:сейчас )?температура(?: на улице| за бортом)?)(?: .*)?",
            "what's the weather",
        ),
        // Places: "найди…" only for what's near; "открой…" an app; the verbs of driving a place.
        rule("(?:заправиться|заправить (?:машину|автомобиль|авто))(?: .*)?") { "navigate to " + remember("the nearest заправка", "на заправку") },
        rule("(?:зарядиться|подзарядиться|зарядить (?:машину|автомобиль|авто))(?: .*)?") {
            "navigate to " + remember("the nearest зарядная станция", "на зарядку")
        },
        rule("(?:найди~|найти|поищи~|поискать|ищи~|покажи~|показать)(?: мне| нам)? (.+)") { m -> navigateNear(m.groupValues[1], Case.ACC) },
        rule("где(?: тут| здесь)?(?: поблизости| рядом)?(?: находится| есть)? (.+)") { m -> navigateNear(m.groupValues[1], Case.NOM) },
        rule(
            "(?:(?:проложи~|проложить|построй~|построить|составь~|составить|покажи~|показать|найди~|найти|задай~|задать|включи~|включить|" +
                "запусти~|запустить) )?(?:маршрут|путь|дорогу|навигацию|навигация)(?: мне| нам)? (.+)",
        ) { m -> navigate(m.groupValues[1]) },
        rule("(?:отвези~|отвезти|довези~|довезти|вези~|везти|подвези~|подвезти|веди~|вести)(?: меня| нас)? (.+)") { m -> navigate(m.groupValues[1]) },
        rule("(?:поехали|поедем|едем|поехать|ехать|езжай~|поезжай~|еду|поеду|едь)(?: со мной)? (.+)") { m -> navigate(m.groupValues[1]) },
        rule("как (?:мне |нам )?(?:доехать|проехать|добраться|попасть|заехать) (.+)") { m -> navigate(m.groupValues[1]) },
        rule(
            "(?:открой~|открыть|запусти~|запустить|покажи~|показать|переключи(?:сь|тесь) на|переключиться на|перейди~ (?:в|на)|" +
                "перейти (?:в|на)|зайди~ в|зайти в)(?: приложение)? (.+)",
        ) { m -> "open " + app(m.groupValues[1]) },
        // A place on its own: "домой", "в аптеку", "ближайшая заправка".
        rule("домой|ближайш\\S* .+") { m -> navigate(m.value) },
        rule("(?:в|во|на|до|к|ко) (?!свидания$|мне$|нам$|тебе$|вам$|себе$)(.+)") { m -> navigate(m.value) },
        rule(".+") { m ->
            val text = withApp(m.value)
            place(text).takeIf { it.startsWith("the nearest ") && it.removePrefix("the nearest ").substringBefore(' ') !in NOT_PLACES }
                ?.let { "navigate to " + remember(it, text) }
        },
    )

    // Answers.

    /** "ближайшую заправку", "ближайший банкомат", "ближайшее кафе": what's near, as "Ищу…" says it; null when unsure. */
    private fun nearestAsObject(place: String): String? {
        val words = place.split(" ").toMutableList()
        val noun = words.indexOfFirst { !isAdjective(it) }
        val at = if (noun < 0) words.size - 1 else noun
        val head = words[at]
        val kind = when {
            head == "азс" -> 'f'
            head in UNCHANGING -> 'n'
            !head.russian() -> return null
            // An adjective on its own: "столовая", "парикмахерская".
            noun < 0 -> when {
                head.endsWith("ая") || head.endsWith("яя") -> 'f'
                head.endsWith("ое") || head.endsWith("ее") -> 'n'
                head.endsWith("ые") || head.endsWith("ие") -> 'p'
                else -> 'm'
            }
            head.last() in "ая" -> 'f'
            head.last() in "оеё" -> 'n'
            head.last() in "ыи" -> 'p'
            head.last() == 'ь' -> SOFT_GENDER[head] ?: return null
            else -> 'm'
        }
        if (kind == 'f') {
            for (i in 0..at) {
                val w = words[i]
                words[i] = when {
                    w.endsWith("ая") && (i < at || noun < 0) -> w.dropLast(2) + "ую"
                    w.endsWith("яя") && (i < at || noun < 0) -> w.dropLast(2) + "юю"
                    i == at && noun >= 0 && w.endsWith("а") -> w.dropLast(1) + "у"
                    i == at && noun >= 0 && w.endsWith("я") -> w.dropLast(1) + "ю"
                    else -> w
                }
            }
        }
        val near = when (kind) {
            'f' -> "ближайшую"
            'n' -> "ближайшее"
            'p' -> "ближайшие"
            else -> "ближайший"
        }
        return "$near ${words.joinToString(" ")}"
    }

    /** A name as the driver said it, with its capital back: "анна" → "Анна". */
    private fun capital(name: String) = name.replaceFirstChar { it.uppercase() }

    /** "-ь" doesn't say a noun's gender: the places a driver looks for. */
    private val SOFT_GENDER = mapOf(
        "отель" to 'm', "мотель" to 'm', "госпиталь" to 'm', "кремль" to 'm', "площадь" to 'f', "церковь" to 'f', "пристань" to 'f',
    )

    /** A destination as said, when it says itself: "до вокзала", "в аптеку", "домой". */
    private val SAID_PLACE = Regex("^(?:(?:до|в|во|на|к|ко|от|из|по адресу) |домой)")

    /** The word for [n] of something: 1 час, 2 часа, 5 часов, 21 минуту, 12 минут. */
    private fun plural(n: Int, one: String, few: String, many: String) = when {
        n % 10 == 1 && n % 100 != 11 -> one
        n % 10 in 2..4 && n % 100 !in 12..14 -> few
        else -> many
    }

    /** "1 час 5 минут", as AssistantCommands says the time left, after "через": "через 21 минуту". */
    private fun left(english: String): String {
        val h = Regex("(\\d+) hours?").find(english)?.groupValues?.get(1)?.toInt()
        val m = Regex("(\\d+) minutes?").find(english)?.groupValues?.get(1)?.toInt()
        return listOfNotNull(
            h?.let { "$it ${plural(it, "час", "часа", "часов")}" },
            m?.let { "$it ${plural(it, "минуту", "минуты", "минут")}" },
        ).joinToString(" ").ifEmpty { english }
    }

    override val answers: Map<Said, (List<String>) -> String> = mapOf(
        Said.HELP to t("Вот что можно сказать."),
        Said.UNKNOWN to t("Этого я пока не умею."),
        Said.WHERE_TO to t("Куда едем?"),
        Said.NO_NAV_APPS to t("Не установлено ни одного навигационного приложения."),
        Said.NO_NAV_APP_CALLED to t("Не нахожу навигационное приложение «$1»."),
        Said.NOT_NAV_APP to t("Это не навигационное приложение."),
        Said.FINDING_NEAREST to { g -> "Ищу ${nearestAsObject(g[1]) ?: "поблизости «${g[1]}»"} через ${g[2]}." },
        Said.NAVIGATING to { g ->
            val said = asSaid(g[1])?.takeIf { SAID_PLACE.containsMatchIn(it) }
            if (said != null) "Прокладываю маршрут $said через ${g[2]}." else "Прокладываю маршрут через ${g[2]}: ${g[1]}."
        },
        Said.OPENING_SETTINGS to t("Открываю настройки."),
        Said.OPENING_VEHICLE_DATA to t("Открываю данные автомобиля."),
        Said.OPENING to t("Открываю приложение $1."),
        Said.OPEN_MUSIC_FIRST to t("Сначала откройте музыкальное приложение, потом попросите ещё раз."),
        Said.MUSIC_APP_LATE to t("Музыкальное приложение не открылось вовремя. Попробуйте ещё раз."),
        Said.IGNORES_SEARCH to t("$1 не поддерживает голосовой поиск. Выберите что-нибудь на экране приложения."),
        Said.LOOKING_FOR to t("Ищу «$1» в приложении $2."),
        Said.NEEDS_NOTIFICATIONS to t("Чтобы читать сообщения, myCar нужен доступ к уведомлениям. Разрешите его на телефоне."),
        Said.NO_MESSAGE_FROM to { g -> "${capital(g[1])}: нет сообщения, на которое можно ответить." },
        Said.NO_MESSAGES_YET to t("Сообщений пока нет."),
        Said.NO_NEW_MESSAGES to t("Новых сообщений нет."),
        Said.ENDING_ROUTE to t("Завершаю маршрут."),
        Said.PAUSED to t("Пауза."),
        Said.NOTHING_PLAYING to t("Сейчас ничего не играет."),
        Said.SKIPPING to t("Следующий трек."),
        Said.PREVIOUS_TRACK to t("Предыдущий трек."),
        Said.PLAYING to t("Включаю."),
        Said.PLAYING_APP to { g -> if (g[1] == "the music") "Включаю музыку." else "Включаю музыку в приложении ${g[1]}." },
        Said.GOING_BACK to t("Назад."),
        Said.BACK_TO_CAR to t("Возвращаюсь к экрану автомобиля."),
        Said.HEADING_HOME to t("Перехожу на главный экран."),
        Said.YOUR_APPS to t("Вот ваши приложения."),
        Said.VEHICLE_DATA_OFF to t("Данные автомобиля выключены. Включите их в настройках, в разделе экспериментальных функций."),
        Said.TIME to t("Сейчас $1."),
        Said.WEATHER to { g -> "${g[3]}: " + listOfNotNull(g[1], sky(g[2])).joinToString(", ") + "." },
        Said.NO_WEATHER to t("Данных о погоде пока нет."),
        Said.NO_ROUTE to t("На карте нет маршрута."),
        Said.ARRIVE_AT_IN to { g -> "Примерно в ${g[1]}, через ${left(g[2])}." },
        Said.ARRIVE_AT to { g -> "Примерно в ${g[1]}." },
        Said.ARRIVE_IN to { g -> "Примерно через ${left(g[1])}." },
        Said.MAP_HASNT_SAID to t("Карта пока этого не сообщила."),
        Said.NEEDS_PHONE to t("Сначала myCar нужно разрешение «Телефон»: дайте его на экране телефона."),
        Said.NEEDS_CALL_LOG to t("Сначала myCar нужен доступ к журналу звонков: разрешите его на телефоне."),
        Said.NO_REDIAL to t("Нет звонка, который можно повторить."),
        Said.NO_CALL_BACK to t("Нет звонка, на который можно перезвонить."),
        Said.PRIVATE_NUMBER to t("Этот звонок был со скрытого номера, перезвонить на него нельзя."),
        Said.CALLING_AGAIN to t("Звоню ещё раз: $1."),
        Said.CALLING_BACK to t("Перезваниваю: $1."),
        Said.WHO_TO_CALL to t("Кому позвонить?"),
        Said.NEEDS_CONTACTS to t("Сначала myCar нужны разрешения «Телефон» и «Контакты»: дайте их на экране телефона."),
        Said.NOT_IN_CONTACTS to { g -> "Контакт «${capital(g[1])}» не найден." },
        Said.NO_SUCH_NUMBER to { g ->
            val kind = mapOf("mobile" to "мобильного", "cell" to "мобильного", "phone" to "телефонного", "home" to "домашнего", "work" to "рабочего")
            "${g[1]}: нет ${kind[g[2]]?.let { "$it номера" } ?: "номера «${g[2]}»"}. " +
                "Позвонить на ${if (g[3] == "the other one") "другой" else g[3]}?"
        },
        Said.CALL_QUESTION to t("Позвонить: $1?"),
        Said.CALLING_TYPE to t("Звоню: $1, $2."),
        Said.CALLING to t("Звоню: $1."),
        Said.NO_APP_CALLED to { g -> "Не нахожу приложение «${saidApp?.takeIf { it.first == g[1] }?.second ?: g[1]}»." },
        Said.SOMETHING_WRONG to t("Что-то пошло не так. Попробуйте ещё раз."),
        Said.DONE to t("Готово."),
        Said.HOME_ASSISTANT_SILENT to t("Home Assistant не ответил."),
        Said.NEEDS_MIC to t("Нужно разрешение на доступ к микрофону."),
        Said.AFTER_CALL to t("Голосовые команды заработают, когда закончится звонок."),
        Said.DIDNT_CATCH to t("Не удалось расслышать."),
        Said.RECOGNIZER_BUSY to t("Распознавание речи занято."),
        Said.RECOGNIZER_NETWORK to t("Распознаванию речи нужна сеть, а её нет."),
        Said.RECOGNITION_FAILED to t("Не удалось распознать речь."),
        Said.SPEECH_NOT_READY to t("Распознавание речи ещё не готово."),
        Said.MODEL_NOT_LOADED to t("Голосовая модель не загрузилась."),
        Said.MODEL_NOT_DOWNLOADED to t("Сначала скачайте голосовую модель в настройках myCar на телефоне."),
        Said.MIC_SILENCED to t("Микрофон телефона отключён. Разблокируйте телефон и попробуйте ещё раз."),
        Said.MIC_UNAVAILABLE to t("Микрофон сейчас недоступен."),
        Said.MISSED_CALL_FROM to t("Пропущенный звонок: $1."),
        Said.WANT_TO_REPLY to t("Хотите ответить?"),
        Said.GO_AHEAD to t("Говорите."),
        Said.NO_CAR_REPLIES to t("$1 не позволяет отвечать из машины."),
        Said.REPLY_AFTER_CALL to t("Ответить голосом можно будет, когда закончится звонок."),
        Said.YOUR_REPLY_TO to t("$1: что ответить?"),
        Said.YOUR_REPLY to t("Что ответить?"),
        Said.SENDING_TO to t("Отправляю в чат «$1»: $2"),
        Said.SENDING to t("Отправляю: $1"),
        Said.NOT_SENT to t("Не отправлено."),
        Said.MESSAGE_SENT to t("Сообщение отправлено."),
        Said.REPLY_WITHDRAWN to t("$1: ответить из машины больше нельзя. Откройте приложение на телефоне, чтобы ответить."),
        Said.NOW_PLAYING to t("$1, исполняет $2."),
    )

    /** The sky as Weather says it, in Russian; null for none ("-"). */
    private fun sky(english: String): String? = when (english) {
        "Clear" -> "ясно"
        "Mostly clear" -> "преимущественно ясно"
        "Partly cloudy" -> "переменная облачность"
        "Overcast" -> "пасмурно"
        "Fog" -> "туман"
        "Drizzle" -> "морось"
        "Rain" -> "дождь"
        "Snow" -> "снег"
        "Rain showers" -> "ливень"
        "Snow showers" -> "снегопад"
        "Thunderstorm" -> "гроза"
        "-" -> null
        else -> english
    }

    override val yes = setOf(
        "да", "ага", "угу", "конечно", "давай", "давайте", "хорошо", "ладно", "окей", "ок", "ok", "okay", "ответь", "ответьте",
        "ответить", "хочу", "можно", "разумеется", "естественно",
    )
    override val no = setOf(
        "нет", "неа", "не надо", "не нужно", "не хочу", "не сейчас", "не буду", "не отвечай", "не отвечайте", "не отвечать",
        "отмена", "отмени", "отмените", "стоп", "хватит", "позже", "потом", "ничего",
    )
    override val cancel = setOf(
        "отмена", "отмени", "отмените", "отменить", "нет", "стоп", "стой", "хватит", "не надо", "не отправляй", "не отправляйте",
    )
    override val change = setOf(
        "измени", "измените", "изменить", "поменяй", "поменяйте", "поменять", "исправь", "исправьте", "исправить", "перепиши",
        "перепишите", "заново", "ещё раз", "еще раз", "снова", "неправильно", "не так",
    )
    override val send = setOf(
        "отправь", "отправьте", "отправить", "отправляй", "отправляйте", "шли", "посылай", "да", "давай", "давайте", "ок", "окей",
        "ok", "хорошо", "верно", "готово",
    )

    override val samples = listOf(
        "Включить Queen", "Что сейчас играет?", "Следующий трек", "Позвонить маме", "Открыть Organic Maps", "Пауза", "Помощь",
    )

    override fun says(who: String, text: String) = "$who пишет: $text"
    override fun inGroup(sender: String, group: String) = "$sender в чате «$group»"

    override val help = listOf(
        "Проложить маршрут до…" to "Маршрут на карте. Добавьте «через Organic Maps», чтобы выбрать приложение",
        "Включить…" to "Песня, исполнитель или альбом в открытом музыкальном приложении",
        "Пауза · Продолжить · Следующий трек · Предыдущий трек" to "Управление музыкой",
        "Что сейчас играет?" to "Песня и её исполнитель",
        "Позвонить…" to "Контакт по имени. Добавьте «на мобильный», чтобы выбрать номер",
        "Перезвонить · Повторить звонок" to "Последний входящий звонок или последний исходящий",
        "Прочитать сообщения · Ответить…" to "Новые сообщения вслух или ответ тому, кто написал",
        "Остановить навигацию" to "Завершает маршрут",
        "Когда приедем?" to "Время прибытия и сколько осталось ехать",
        "Открыть…" to "Приложение, например браузер, телефон или демонстрация экрана",
        "Выход" to "Возврат к экрану автомобиля",
        "Главный экран · Настройки" to "Перемещение по myCar",
        "Который час? · Какая погода?" to "Время или погода",
        "Помощь" to "Этот список",
    )

    override val labels = mapOf(
        "Listening…" to "Слушаю…",
        "One moment…" to "Секунду…",
        "You can say" to "Можно сказать",
        "Say “cancel” or “change it”" to "Скажите «отмена» или «изменить»",
        "Do you want to reply? Say yes or no." to "Хотите ответить? Скажите «да» или «нет».",
        "Get ready to say your reply." to "Приготовьтесь сказать ответ.",
        "Say your reply" to "Скажите ваш ответ",
        "Anything else" to "Всё остальное",
        "Home Assistant answers it: your home, and any question its voice assistant knows." to
            "Отвечает Home Assistant: ваш дом и любые вопросы, на которые может ответить его голосовой помощник.",
    )
}
