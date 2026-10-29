package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguage.DE
import io.github.sirionero.mycar.speech.VoiceLanguage.EN
import io.github.sirionero.mycar.speech.VoiceLanguage.ES
import io.github.sirionero.mycar.speech.VoiceLanguage.FR
import io.github.sirionero.mycar.speech.VoiceLanguage.IT
import io.github.sirionero.mycar.speech.VoiceLanguage.PT
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The phrases are what the multilingual model (Parakeet v3) wrote for them, punctuation and
 * capitals included, from speech synthesized in each language: what toEnglish really gets.
 */
class AssistantLanguageTest {

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun heard(l: VoiceLanguage, vararg pairs: Pair<String, String>) {
        for ((said, command) in pairs) assertEquals(said, command, AssistantLanguage.toEnglish(said, l))
    }

    @Test
    fun `English passes as it was`() {
        assertEquals("Navigate to the station.", AssistantLanguage.toEnglish("Navigate to the station.", EN))
        assertEquals("Calling Anna.", AssistantLanguage.answer("Calling Anna.", EN))
    }

    @Test
    fun `Italian commands`() = heard(
        IT,
        "Portami al benzinaio più vicino." to "navigate to the nearest benzinaio",
        "Portami a casa con Organic Maps." to "navigate to casa con organic maps",
        "Vai alla stazione." to "go to stazione",
        "Trova il supermercato più vicino." to "navigate to the nearest supermercato",
        "Chiama Anna sul cellulare." to "call anna on mobile",
        "Chiama la mamma." to "call mamma",
        "Richiama." to "call back",
        "Chiama di nuovo." to "redial",
        "Metti vasco rossi." to "play vasco rossi",
        "Metti in pausa." to "pause",
        "Prossima canzone." to "next",
        "Canzone precedente" to "previous",
        "Che canzone è?" to "what's playing",
        "Leggi i messaggi nuovi." to "read my new messages",
        "Rispondi a Giulia." to "reply to giulia",
        "Ferma la navigazione." to "stop navigation",
        "Quando arriviamo?" to "when do i arrive",
        "Apri il telefono." to "open phone",
        "Apri Spotify." to "open spotify",
        "Che ore sono?" to "what time is it",
        "Che tempo fa?" to "what's the weather",
        "Torna alla home" to "go home",
        "Apri le impostazioni." to "open settings",
        "Esci" to "exit",
        "Per favore, metti un po' di musica." to "continue",
        "Torna a casa." to "go to casa",
    )

    @Test
    fun `Spanish commands`() = heard(
        ES,
        "Llévame a la gasolinera más cercana." to "navigate to the nearest gasolinera",
        "Vamos a casa con Organic Maps." to "go to casa con organic maps",
        "Llama a Juan al móvil." to "call juan on mobile",
        "Llama a mamá." to "call mamá",
        "Devuelve la llamada." to "call back",
        "Pon Shakira" to "play shakira",
        "Siguiente canción" to "next",
        "¿Qué canción es?" to "what's playing",
        "Lee mis mensajes." to "read my messages",
        "Responde a Carlos." to "reply to carlos",
        "Para la navegación." to "stop navigation",
        "Cuando llegamos." to "when do i arrive",
        "¿Qué hora es?" to "what time is it",
        "¿Qué tiempo hace?" to "what's the weather",
        "Abre los ajustes." to "open settings",
        "Para." to "pause",
    )

    @Test
    fun `French commands`() = heard(
        FR,
        "Emmène-moi la gare." to "navigate to gare",
        "Trouve la station service la plus proche." to "navigate to the nearest station service",
        "Appelle Marie sur son portable." to "call marie on mobile",
        "Rappelle" to "call back",
        "Rappelle Paul." to "call paul",
        "Mets Stromae" to "play stromae",
        "Mets en pause" to "pause",
        "Chanson suivante" to "next",
        "C'est quoi cette chanson?" to "what's playing",
        "Lis mes messages" to "read my messages",
        "Répond à Paul." to "reply to paul",
        "Arrête la navigation." to "stop navigation",
        "Quand est-ce que j'arrive?" to "when do i arrive",
        "Quelle heure est-il?" to "what time is it",
        "Quel temps fait-il?" to "what's the weather",
        "Ouvre les paramètres." to "open settings",
        "Trouve-moi une pharmacie à proximité." to "navigate to the nearest pharmacie",
    )

    @Test
    fun `German commands`() = heard(
        DE,
        "Navigiere zur nächsten Tankstelle." to "navigate to the nearest tankstelle",
        "Bring mich nach Hause." to "navigate to zuhause",
        "Fahr mich zum Bahnhof mit Organic Maps." to "navigate to bahnhof mit organic maps",
        "Ruf Anna auf dem Handy an." to "call anna on mobile",
        "Ruf Mama an." to "call mama",
        "Ruf zurück." to "call back",
        "Spiel Herbert Grönemeyer" to "play herbert grönemeyer",
        "Spiel mir Queen." to "play queen",
        "Pause." to "pause",
        "Nächstes Lied." to "next",
        "Was läuft gerade?" to "what's playing",
        "Lies meine Nachrichten." to "read my messages",
        "Antworte Peter" to "reply to peter",
        "Navigation beenden" to "stop navigation",
        "Wann komme ich an?" to "when do i arrive",
        "Wie spät ist es?" to "what time is it",
        "Wie ist das Wetter?" to "what's the weather",
        "Öffne die Einstellungen." to "open settings",
        "Kannst du Anna anrufen?" to "call anna",
        "Zeig mir Spotify." to "open spotify",
    )

    @Test
    fun `Portuguese commands`() = heard(
        PT,
        "Leve me para o posto de gasolina mais próximo." to "navigate to the nearest posto de gasolina",
        "Vamos para casa." to "go to casa",
        "Ligue para a Maria no celular." to "call maria on mobile",
        "Ligue para o João." to "call joão",
        "Ligue de volta." to "call back",
        "Toca Anitta." to "play anitta",
        "Próxima música." to "next",
        "Que música é essa?" to "what's playing",
        "Leia as mensagens." to "read my messages",
        "Responda para a Ana." to "reply to ana",
        "Pare a navegação." to "stop navigation",
        "Quando eu chego." to "when do i arrive",
        "Que horas são?" to "what time is it",
        "Como está o tempo?" to "what's the weather",
        "Abra as configurações." to "open settings",
    )

    @Test
    fun `what isn't a command passes through, lower case`() {
        assertEquals("che bella giornata", AssistantLanguage.toEnglish("Che bella giornata!", IT))
        // "Trova" without "vicino" isn't a place to drive to.
        assertEquals("trova despacito", AssistantLanguage.toEnglish("Trova Despacito.", IT))
    }

    @Test
    fun `a destination is said back the way the driver said it`() {
        AssistantLanguage.toEnglish("Vai alla stazione.", IT)
        assertEquals("Ti porto alla stazione con Vela.", AssistantLanguage.answer("Navigating to stazione with Vela.", IT))
        AssistantLanguage.toEnglish("Portami al benzinaio più vicino.", IT)
        assertEquals("Cerco il benzinaio più vicino con Vela.", AssistantLanguage.answer("Finding the nearest benzinaio with Vela.", IT))
        // AssistantCommands cut the app off the end: the words said lose it too.
        AssistantLanguage.toEnglish("Portami a casa con Organic Maps.", IT)
        assertEquals("Ti porto a casa con Organic Maps.", AssistantLanguage.answer("Navigating to casa with Organic Maps.", IT))
        // The recognizer missed the "à": said back with it.
        AssistantLanguage.toEnglish("Emmène-moi la gare.", FR)
        assertEquals("Je vous emmène à la gare avec Vela.", AssistantLanguage.answer("Navigating to gare with Vela.", FR))
        AssistantLanguage.toEnglish("Llévame a la gasolinera más cercana.", ES)
        assertEquals("Busco la gasolinera más cercana con Vela.", AssistantLanguage.answer("Finding the nearest gasolinera with Vela.", ES))
        AssistantLanguage.toEnglish("Bring mich nach Hause.", DE)
        assertEquals("Navigation nach hause mit Vela gestartet.", AssistantLanguage.answer("Navigating to zuhause with Vela.", DE))
        AssistantLanguage.toEnglish("Leve me para o centro.", PT)
        assertEquals("Vamos para o centro com Vela.", AssistantLanguage.answer("Navigating to centro with Vela.", PT))
        // Another place than the one said: no words borrowed from it.
        assertEquals("Ti porto a roma con Vela.", AssistantLanguage.answer("Navigating to roma with Vela.", IT))
    }

    @Test
    fun `answers in Italian`() {
        assertEquals("Sono le 14:05.", AssistantLanguage.answer("It's 14:05.", IT))
        assertEquals("È l'1:05.", AssistantLanguage.answer("It's 01:05.", IT))
        assertEquals("Verso le 14:30, tra 1 ora e 5 minuti.", AssistantLanguage.answer("Around 14:30, in 1 hour 5 minutes.", IT))
        assertEquals("Tra circa 25 minuti.", AssistantLanguage.answer("In about 25 minutes.", IT))
        assertEquals("12°C, parzialmente nuvoloso, a Roma.", AssistantLanguage.answer("12°C and Partly cloudy in Roma.", IT))
        assertEquals("-3°C, a Bolzano.", AssistantLanguage.answer("-3°C and - in Bolzano.", IT))
        assertEquals("Metto la musica.", AssistantLanguage.answer("Playing the music.", IT))
        assertEquals("Metto Spotify.", AssistantLanguage.answer("Playing Spotify.", IT))
        assertEquals("Torno al brano precedente.", AssistantLanguage.answer("Playing the previous track.", IT))
        assertEquals("Riprendo.", AssistantLanguage.answer("Playing.", IT))
        assertEquals("Richiamo Anna.", AssistantLanguage.answer("Calling Anna back.", IT))
        assertEquals("Chiamo Anna, cellulare.", AssistantLanguage.answer("Calling Anna, cellulare.", IT))
        assertEquals(
            "Anna non ha un numero di lavoro. Chiamo il numero di cellulare?",
            AssistantLanguage.answer("Anna has no work number. Call cellulare instead?", IT),
        )
        assertEquals("Bella ciao, di Modena City Ramblers.", AssistantLanguage.answer("Bella ciao by Modena City Ramblers", IT))
        assertEquals("Invio a Anna: arrivo tra poco", AssistantLanguage.answer("Sending to Anna: arrivo tra poco", IT))
        assertEquals("Non ho capito.", AssistantLanguage.answer("Didn't catch that.", IT))
        assertEquals("Cerco despacito su Spotify.", AssistantLanguage.answer("Looking for despacito on Spotify.", IT))
        // Something it has no words for stays as written.
        assertEquals("Fuck you too.", AssistantLanguage.answer("Fuck you too.", IT))
    }

    @Test
    fun `the hour in each language`() {
        assertEquals("Es la 1:05.", AssistantLanguage.answer("It's 1:05.", ES))
        assertEquals("Son las 14:05.", AssistantLanguage.answer("It's 14:05.", ES))
        assertEquals("Il est 14:05.", AssistantLanguage.answer("It's 14:05.", FR))
        assertEquals("Es ist 14:05 Uhr.", AssistantLanguage.answer("It's 14:05.", DE))
        assertEquals("Gegen 14:30 Uhr, in 1 Stunde und 5 Minuten.", AssistantLanguage.answer("Around 14:30, in 1 hour 5 minutes.", DE))
        assertEquals("São 14:05.", AssistantLanguage.answer("It's 14:05.", PT))
        assertEquals("Por volta da 1:30.", AssistantLanguage.answer("Around 01:30.", PT))
    }

    @Test
    fun `Portuguese for Portugal and for Brazil`() {
        Locale.setDefault(Locale.forLanguageTag("pt-PT"))
        assertEquals("Algo correu mal. Tente de novo.", AssistantLanguage.answer("Something went wrong. Try again.", PT))
        assertEquals("A ouvir…", AssistantLanguage.label("Listening…", PT))
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        assertEquals("Algo deu errado. Tente de novo.", AssistantLanguage.answer("Something went wrong. Try again.", PT))
        assertEquals("Ouvindo…", AssistantLanguage.label("Listening…", PT))
    }

    @Test
    fun `every answer has words in every language`() {
        for (l in VoiceLanguage.entries.filter { it != EN }) {
            for (said in Said.entries) {
                val english = sample(said)
                assertTrue("$said doesn't match its own sample", said.regex.matches(english))
                assertTrue("$l has no words for $said", AssistantLanguage.answer(english, l) != english)
            }
        }
    }

    @Test
    fun `each sample is taken by its own answer, not an earlier one`() {
        for (said in Said.entries) {
            val english = sample(said)
            assertEquals(english, said, Said.entries.first { it.regex.matches(english) })
        }
    }

    @Test
    fun `labels, help and replies in every language`() {
        for (l in VoiceLanguage.entries.filter { it != EN }) {
            for (label in listOf("Listening…", "One moment…", "You can say", "Say “cancel” or “change it”", "Say your reply")) {
                assertTrue("$l has no label for $label", AssistantLanguage.label(label, l) != label)
            }
            assertEquals(14, AssistantLanguage.help(l)!!.size)
            assertTrue(AssistantLanguage.yes(l).isNotEmpty() && AssistantLanguage.no(l).isNotEmpty())
            assertTrue(AssistantLanguage.cancel(l).isNotEmpty() && AssistantLanguage.change(l).isNotEmpty() && AssistantLanguage.send(l).isNotEmpty())
        }
        assertEquals("Anna dice: ci sono", AssistantLanguage.says("Anna", "ci sono", IT))
        assertEquals("Anna says: on my way", AssistantLanguage.says("Anna", "on my way", EN))
    }

    /** An English answer of each kind, as AssistantCommands, CarAssistant and VoiceReply write it. */
    private fun sample(said: Said): String = when (said) {
        Said.HELP -> "Here's what you can say."
        Said.UNKNOWN -> "I don't know how to do that yet."
        Said.WHERE_TO -> "Where to?"
        Said.NO_NAV_APPS -> "You don't have any navigation apps installed."
        Said.NO_NAV_APP_CALLED -> "I couldn't find a navigation app called waze."
        Said.NOT_NAV_APP -> "That's not a navigation app."
        Said.FINDING_NEAREST -> "Finding the nearest pharmacy with Vela."
        Said.NAVIGATING -> "Navigating to milano with Vela."
        Said.OPENING_SETTINGS -> "Opening settings."
        Said.OPENING_VEHICLE_DATA -> "Opening vehicle data."
        Said.OPENING -> "Opening Spotify."
        Said.OPEN_MUSIC_FIRST -> "Open a music app first, then ask again."
        Said.MUSIC_APP_LATE -> "Your music app didn't open in time. Try again."
        Said.IGNORES_SEARCH -> "Radio ignores voice searches. Pick something on its screen instead."
        Said.LOOKING_FOR -> "Looking for queen on Spotify."
        Said.NEEDS_NOTIFICATIONS -> "myCar needs notification access to read messages. Allow it on the phone."
        Said.NO_MESSAGE_FROM -> "I couldn't find a message from anna to reply to."
        Said.NO_MESSAGES_YET -> "There are no messages yet."
        Said.NO_NEW_MESSAGES -> "No new messages."
        Said.ENDING_ROUTE -> "Ending the route."
        Said.PAUSED -> "Paused."
        Said.NOTHING_PLAYING -> "Nothing's playing."
        Said.SKIPPING -> "Skipping."
        Said.PREVIOUS_TRACK -> "Playing the previous track."
        Said.PLAYING -> "Playing."
        Said.PLAYING_APP -> "Playing Spotify."
        Said.GOING_BACK -> "Going back."
        Said.BACK_TO_CAR -> "Back to the car's screen."
        Said.HEADING_HOME -> "Heading home."
        Said.YOUR_APPS -> "Here are your apps."
        Said.VEHICLE_DATA_OFF -> "Vehicle data is off. Turn it on under experimental features in settings."
        Said.TIME -> "It's 14:05."
        Said.WEATHER -> "12°C and Rain in Roma."
        Said.NO_WEATHER -> "I don't have the weather yet."
        Said.NO_ROUTE -> "There's no route on the map."
        Said.ARRIVE_AT_IN -> "Around 14:30, in 25 minutes."
        Said.ARRIVE_AT -> "Around 14:30."
        Said.ARRIVE_IN -> "In about 25 minutes."
        Said.MAP_HASNT_SAID -> "The map hasn't said yet."
        Said.NEEDS_PHONE -> "myCar needs the phone permission first, from the phone screen."
        Said.NEEDS_CALL_LOG -> "myCar needs the phone's call log first: allow it on the phone."
        Said.NO_REDIAL -> "There's no call to redial."
        Said.NO_CALL_BACK -> "There's no call to return."
        Said.PRIVATE_NUMBER -> "That call was from a private number, so it can't be returned."
        Said.CALLING_AGAIN -> "Calling Anna again."
        Said.CALLING_BACK -> "Calling Anna back."
        Said.WHO_TO_CALL -> "Who should I call?"
        Said.NEEDS_CONTACTS -> "myCar needs the phone and contacts permissions first, from the phone screen."
        Said.NOT_IN_CONTACTS -> "I couldn't find anna in your contacts."
        Said.NO_SUCH_NUMBER -> "Anna has no work number. Call the other one instead?"
        Said.CALL_QUESTION -> "Call Anna?"
        Said.CALLING_TYPE -> "Calling Anna, mobile."
        Said.CALLING -> "Calling Anna."
        Said.NO_APP_CALLED -> "I couldn't find an app called foo."
        Said.SOMETHING_WRONG -> "Something went wrong. Try again."
        Said.NEEDS_MIC -> "Needs the microphone permission."
        Said.AFTER_CALL -> "Voice commands work once the call ends."
        Said.DIDNT_CATCH -> "Didn't catch that."
        Said.RECOGNIZER_BUSY -> "The speech recognizer is busy."
        Said.RECOGNIZER_NETWORK -> "The speech recognizer needs a network it doesn't have."
        Said.RECOGNITION_FAILED -> "Speech recognition failed."
        Said.SPEECH_NOT_READY -> "Speech isn't ready yet."
        Said.MODEL_NOT_LOADED -> "The speech model didn't load."
        Said.MODEL_NOT_DOWNLOADED -> "Download a voice model in myCar's settings on the phone first."
        Said.MIC_SILENCED -> "The phone's microphone is silenced. Unlock the phone and try again."
        Said.MIC_UNAVAILABLE -> "The microphone isn't available right now."
        Said.MISSED_CALL_FROM -> "Missed call from Anna."
        Said.WANT_TO_REPLY -> "Do you want to reply?"
        Said.GO_AHEAD -> "Go ahead."
        Said.NO_CAR_REPLIES -> "Signal doesn't allow replies from the car."
        Said.REPLY_AFTER_CALL -> "Voice reply works once the call ends."
        Said.YOUR_REPLY_TO -> "What's your reply to Anna?"
        Said.YOUR_REPLY -> "What's your reply?"
        Said.SENDING_TO -> "Sending to Anna: ci sono"
        Said.SENDING -> "Sending: ci sono"
        Said.NOT_SENT -> "Not sent."
        Said.MESSAGE_SENT -> "Message sent."
        Said.REPLY_WITHDRAWN -> "Signal withdrew the reply. Open the app on the phone to answer."
        Said.NOW_PLAYING -> "Bella ciao by Modena City Ramblers"
    }
}
