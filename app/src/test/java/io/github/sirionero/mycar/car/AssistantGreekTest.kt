package io.github.sirionero.mycar.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Greek phrasebook. The phrases are written the way the recognizers write them. Parakeet v3 has
 * no final ς among its tokens and may drop an accent or the ν of "τον": from Piper's Greek voice it
 * wrote "Άνοιξε τι βυθύσει." for "Άνοιξε τις ρυθμίσεις", "Πάρε το Γιώργο στο κινητό." and
 * "Οδήγησε με στη Θεσσαλονίκη.", questions ending in a full stop. The phone's own recognizer writes
 * ς, accents and ";" (a Greek question mark), so both kinds are here.
 */
class AssistantGreekTest {

    private fun heard(vararg pairs: Pair<String, String>) {
        for ((said, command) in pairs) assertEquals(said, command, AssistantLanguage.toEnglish(said, GreekPhrases))
    }

    private fun answer(english: String) = AssistantLanguage.answer(english, GreekPhrases)

    @Test
    fun `places`() = heard(
        "Πήγαινέ με στο πλησιέστερο βενζινάδικο." to "navigate to the nearest βενζινάδικο",
        "Πήγαινε με στο πλησιέστερο βενζινάδικο." to "navigate to the nearest βενζινάδικο",
        "Πηγαίνετέ με στο σπίτι με το Organic Maps." to "navigate to σπίτι με το organic maps",
        "Οδήγησε με στη Θεσσαλονίκη." to "navigate to θεσσαλονίκη",
        "Πάρε με σπίτι." to "navigate to σπίτι",
        "Οδηγίες για το κέντρο." to "navigate to κέντρο",
        "Πώς θα πάω στο αεροδρόμιο;" to "navigate to αεροδρόμιο",
        "Μπορεί να με πας στη δουλειά." to "navigate to δουλειά",
        "Βρες το πλησιέστερο φαρμακείο." to "navigate to the nearest φαρμακείο",
        "Βρε ένα βενζινάδικο κοντά μου." to "navigate to the nearest βενζινάδικο",
        "Πού είναι το κοντινότερο ATM;" to "navigate to the nearest atm",
        "Ψάξε μια τράπεζα εδώ κοντά με το Waze." to "navigate to the nearest τράπεζα με το waze",
        "Πάμε στο αεροδρόμιο." to "go to αεροδρόμιο",
        "Πάμε για καφέ." to "go to καφέ",
        "Γύρνα σπίτι." to "go to σπίτι",
        "Πήγαινε στο Spotify." to "go to spotify",
    )

    @Test
    fun `calls`() = heard(
        "Πάρε τον Γιώργο." to "call γιώργος",
        // The ν of "τον" lost: "το" might be the office, so the name stays as said, a piece of "γιώργος".
        "Πάρε το Γιώργο." to "call γιώργο",
        "Πάρε τηλέφωνο τη μαμά." to "call μαμά",
        "Πάρε τηλέφωνο στη μαμά μου." to "call μαμά",
        "Τηλεφώνησε στον Κώστα." to "call κώστας",
        "Κάλεσε τον κύριο Παπαδόπουλο." to "call παπαδόπουλος",
        "Μπορεί να καλέσει τον Γιάννη;" to "call γιάννης",
        "Μπορείτε να καλέσετε την Ελένη;" to "call ελένη",
        "Κάλεσε την Άννα στο κινητό." to "call άννα on mobile",
        "Πάρε το Γιώργο στο κινητό." to "call γιώργο on mobile",
        "Πάρτε τον Νίκο Παπαδόπουλο στη δουλειά." to "call νίκος παπαδόπουλος on work",
        "Κάλεσε το κινητό του Γιώργου." to "call γιώργος on mobile",
        "Πάρε τηλέφωνο στο σπίτι." to "call σπίτι",
        "Θέλω να μιλήσω με τον Αντώνη." to "call αντώνης",
        "Πάρε πίσω." to "call back",
        "Πάρε πίσω την αναπάντητη." to "call back",
        "Ξαναπάρε." to "redial",
        "Ξαναπάρε τον τελευταίο αριθμό." to "redial",
        // So Parakeet heard "αριθμό" from Piper's voice.
        "Ξαναπάρε τον τελευταίο ρυθμό." to "redial",
        "Πάρε πίσω τον Γιώργο." to "call γιώργος",
        "Πάρε τηλέφωνο." to "call",
        "Κάνε μια κλήση στη Μαρία." to "call μαρία",
    )

    @Test
    fun `music`() = heard(
        "Βάλε Μίκη Θεοδωράκη." to "play μίκη θεοδωράκη",
        "Βάλτε Χατζιδάκι." to "play χατζιδάκι",
        "Παίξε λίγο Νταλάρα στο Spotify." to "play νταλάρα στο spotify",
        "Θέλω να ακούσω τραγούδια του Σαββόπουλου." to "play σαββόπουλου",
        "Βάλε μουσική." to "continue",
        "Σε παρακαλώ, βάλε λίγη μουσική." to "continue",
        "Συνέχισε." to "continue",
        "Παίξε." to "continue",
        "Παύση." to "pause",
        "Σταμάτα." to "pause",
        "Σταμάτα τη μουσική." to "pause",
        "Επόμενο τραγούδι." to "next",
        "Το επόμενο." to "next",
        "Βάλε κάτι άλλο." to "next",
        "Προηγούμενο." to "previous",
        "Βάλε το προηγούμενο τραγούδι." to "previous",
        "Βάλ' το ξανά από την αρχή." to "play it again",
        "Ξαναβάλε το." to "play it again",
        "Τι τραγούδι είναι αυτό;" to "what's playing",
        "Ποιο τραγουδάει." to "what's playing",
        "Ποιος τραγουδάει;" to "what's playing",
        "Τι παίζει τώρα;" to "what's playing",
    )

    @Test
    fun `messages, the route, the time and the screens`() = heard(
        "Διάβασε τα μηνύματά μου." to "read my messages",
        "Διάβασέ μου τα νέα μηνύματα." to "read my new messages",
        "Διάβασε το τελευταίο μήνυμα." to "read my last message",
        "Έχω νέα μηνύματα;" to "read my new messages",
        "Τα μηνύματα." to "read my messages",
        "Απάντησε στον Γιώργο." to "reply to γιώργος",
        "Απάντησε στο Γιώργο." to "reply to γιώργο",
        "Στείλε μήνυμα στη Μαρία." to "reply to μαρία",
        "Γράψε στην Άννα ότι αργώ." to "reply to άννα",
        "Σταμάτα την πλοήγηση." to "stop navigation",
        "Τερμάτισε τη διαδρομή." to "stop navigation",
        "Πότε φτάνουμε;" to "when do i arrive",
        "Πόση ώρα μένει ακόμα." to "when do i arrive",
        "Τι ώρα θα φτάσουμε;" to "when do i arrive",
        "Τι ώρα είναι;" to "what time is it",
        "Πες μου την ώρα." to "what time is it",
        "Τι καιρό κάνει;" to "what's the weather",
        "Πώ είναι ο καιρό." to "what's the weather",
        "Θα βρέξει σήμερα;" to "what's the weather",
        "Άνοιξε τι ρυθμίσει." to "open settings",
        "Άνοιξε τις ρυθμίσεις." to "open settings",
        "Δείξε μου τι εφαρμογέ." to "open apps",
        "Άνοιξε το τηλέφωνο." to "open phone",
        "Ανοίξτε το Spotify." to "open spotify",
        "Άνοιξε την κοινή χρήση οθόνης." to "open screen sharing",
        "Πήγαινε στην αρχική οθόνη." to "go home",
        "Πίσω." to "go back",
        "Έξοδος." to "exit",
        "Βοήθεια." to "help",
        "Τι μπορώ να πω;" to "help",
    )

    @Test
    fun `what isn't a command passes through, lower case`() {
        assertEquals("τι ωραία μέρα", AssistantLanguage.toEnglish("Τι ωραία μέρα!", GreekPhrases))
        // "Βρες" with nothing near isn't a place to drive to.
        assertEquals("βρες το despacito", AssistantLanguage.toEnglish("Βρες το Despacito.", GreekPhrases))
        // "Άνοιξε την πόρτα του γκαράζ" is for the house: it stays "open …", which AssistantBrain gives Home Assistant.
        assertEquals("open πόρτα του γκαράζ", AssistantLanguage.toEnglish("Άνοιξε την πόρτα του γκαράζ.", GreekPhrases))
        // What comes with "μια" is no one to call.
        assertEquals("πάρε μια βαθιά ανάσα", AssistantLanguage.toEnglish("Πάρε μια βαθιά ανάσα.", GreekPhrases))
    }

    @Test
    fun `the samples are commands`() {
        for (sample in GreekPhrases.samples) {
            val english = AssistantLanguage.toEnglish(sample, GreekPhrases)
            assertTrue("$sample → $english", english != AssistantLanguage.plain(sample))
        }
    }

    @Test
    fun `a destination is said back the way the driver said it`() {
        AssistantLanguage.toEnglish("Πήγαινέ με στο σταθμό.", GreekPhrases)
        assertEquals("Πάμε στο σταθμό με το Vela.", answer("Navigating to σταθμό with Vela."))
        AssistantLanguage.toEnglish("Πήγαινε με στο πλησιέστερο βενζινάδικο.", GreekPhrases)
        assertEquals("Ψάχνω το πλησιέστερο βενζινάδικο με το Vela.", answer("Finding the nearest βενζινάδικο with Vela."))
        // AssistantCommands cut the app off the end: the words said lose it too, and "μου" is "σας".
        AssistantLanguage.toEnglish("Πηγαίνετέ με στο σπίτι μου με το Organic Maps.", GreekPhrases)
        assertEquals("Πάμε στο σπίτι σας με το Organic Maps.", answer("Navigating to σπίτι μου with Organic Maps."))
        AssistantLanguage.toEnglish("Βρες ένα φαρμακείο εδώ κοντά.", GreekPhrases)
        assertEquals("Ψάχνω ένα φαρμακείο εδώ κοντά με το Vela.", answer("Finding the nearest φαρμακείο with Vela."))
        // The recognizer missed the σ of "στο": said back with it.
        AssistantLanguage.toEnglish("Οδήγησε με το σταθμό.", GreekPhrases)
        assertEquals("Πάμε στο σταθμό με το Vela.", answer("Navigating to σταθμό with Vela."))
        AssistantLanguage.toEnglish("Πάμε σπίτι.", GreekPhrases)
        assertEquals("Πάμε σπίτι με το Vela.", answer("Navigating to σπίτι with Vela."))
        // Another place than the one said: no words borrowed from it.
        assertEquals("Πάμε προς αθήνα με το Vela.", answer("Navigating to αθήνα with Vela."))
    }

    @Test
    fun `answers in Greek`() {
        assertEquals("Η ώρα είναι 14:05.", answer("It's 14:05."))
        assertEquals("Η ώρα είναι 1:05.", answer("It's 01:05."))
        assertEquals("Η ώρα είναι 2:30 μ.μ.", answer("It's 2:30 μ.μ.."))
        assertEquals("Γύρω στις 14:30, σε 1 ώρα και 5 λεπτά.", answer("Around 14:30, in 1 hour 5 minutes."))
        assertEquals("Γύρω στη 1:30.", answer("Around 01:30."))
        assertEquals("Σε περίπου 25 λεπτά.", answer("In about 25 minutes."))
        assertEquals("Αθήνα: 12°C, αραιή συννεφιά.", answer("12°C and Partly cloudy in Αθήνα."))
        assertEquals("Bolzano: -3°C.", answer("-3°C and - in Bolzano."))
        assertEquals("Καλώ τον Γιώργο Παπαδόπουλο.", answer("Calling Γιώργος Παπαδόπουλος."))
        assertEquals("Καλώ τη Μαρία.", answer("Calling Μαρία."))
        assertEquals("Καλώ την Άννα στο κινητό.", answer("Calling Άννα, κινητό."))
        assertEquals("Κλήση προς Vodafone.", answer("Calling Vodafone."))
        assertEquals("Κλήση προς 6944 123456.", answer("Calling 6944 123456."))
        assertEquals("Να καλέσω τον Νίκο;", answer("Call Νίκος?"))
        assertEquals("Να καλέσω την επαφή Γραφείο;", answer("Call Γραφείο?"))
        assertEquals(
            "Δεν υπάρχει αριθμός εργασίας για την Άννα. Να καλέσω στο κινητό;",
            answer("Άννα has no work number. Call κινητό instead?"),
        )
        assertEquals("Παίρνω πίσω τον Κώστα.", answer("Calling Κώστας back."))
        assertEquals("Ξαναπαίρνω την Ελένη.", answer("Calling Ελένη again."))
        assertEquals("Δεν βρίσκω τον Γιώργο στις επαφές σας.", answer("I couldn't find γιώργος in your contacts."))
        assertEquals("Δεν βρίσκω τη Μαμά στις επαφές σας.", answer("I couldn't find μαμά in your contacts."))
        assertEquals("Δεν βρίσκω «Γιώργο» στις επαφές σας.", answer("I couldn't find γιώργο in your contacts."))
        assertEquals("Αναπάντητη κλήση από τον Κώστα.", answer("Missed call from Κώστας."))
        assertEquals("Τι θέλετε να απαντήσετε στον Γιώργο Παπαδόπουλο;", answer("What's your reply to Γιώργος Παπαδόπουλος?"))
        assertEquals("Τι θέλετε να απαντήσετε στην Ορειβασία;", answer("What's your reply to Ορειβασία?"))
        assertEquals("Τι θέλετε να απαντήσετε στη συνομιλία Παιδιά;", answer("What's your reply to Παιδιά?"))
        assertEquals("Αποστολή στην Άννα: έρχομαι σε δέκα λεπτά", answer("Sending to Άννα: έρχομαι σε δέκα λεπτά"))
        assertEquals("Βάζω μουσική.", answer("Playing the music."))
        assertEquals("Βάζω μουσική από το Spotify.", answer("Playing Spotify."))
        assertEquals("Ψάχνω θεοδωράκη στο Spotify.", answer("Looking for θεοδωράκη on Spotify."))
        assertEquals("Συννεφιασμένη Κυριακή, από τον Γιώργο Νταλάρα.", answer("Συννεφιασμένη Κυριακή by Γιώργος Νταλάρας"))
        assertEquals("Bella ciao, ερμηνεία: Modena City Ramblers.", answer("Bella ciao by Modena City Ramblers"))
        assertEquals("Δεν το κατάλαβα.", answer("Didn't catch that."))
        // Something it has no words for stays as written.
        assertEquals("Fuck you too.", answer("Fuck you too."))
    }

    @Test
    fun `every answer has Greek words`() {
        for (said in Said.entries) {
            val english = saidSample(said)
            assertTrue("no Greek for $said", answer(english) != english)
        }
    }

    /** As VoiceReply hears a yes, a no or a command: whole words, any case. */
    private fun says(text: String, words: Set<String>): Boolean {
        val said = " " + text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }.joinToString(" ") + " "
        return words.any { said.contains(" $it ") }
    }

    @Test
    fun `yes, no and the reply's commands`() {
        assertTrue(says("Ναι, απάντησε.", GreekPhrases.yes) && !says("Ναι, απάντησε.", GreekPhrases.no))
        assertTrue(says("Όχι, ευχαριστώ.", GreekPhrases.no) && says("Οχι.", GreekPhrases.no) && says("Άσ' το.", GreekPhrases.no))
        assertTrue(says("Ακύρωση.", GreekPhrases.cancel) && says("Μην το στείλεις.", GreekPhrases.cancel))
        assertTrue(says("Άλλαξέ το.", GreekPhrases.change) && says("Αλλαγή.", GreekPhrases.change))
        assertTrue(says("Στείλ' το.", GreekPhrases.send) && says("Στείλε.", GreekPhrases.send) && says("Αποστολή.", GreekPhrases.send))
    }

    @Test
    fun `labels, help and replies`() {
        for (label in ASSISTANT_LABELS) assertTrue("no label for $label", GreekPhrases.labels[label].let { it != null && it != label })
        assertEquals(14, GreekPhrases.help.size)
        assertTrue(GreekPhrases.yes.isNotEmpty() && GreekPhrases.no.isNotEmpty())
        assertTrue(GreekPhrases.cancel.isNotEmpty() && GreekPhrases.change.isNotEmpty() && GreekPhrases.send.isNotEmpty())
        // The words the labels tell the driver to say are the ones listened for.
        assertTrue("ακύρωση" in GreekPhrases.cancel && "αλλαγή" in GreekPhrases.change)
        assertTrue("ναι" in GreekPhrases.yes && "όχι" in GreekPhrases.no && "οχι" in GreekPhrases.no)
        assertEquals("Ο Γιώργος λέει: έρχομαι", GreekPhrases.says("Γιώργος", "έρχομαι"))
        assertEquals("Η Άννα λέει: πού είσαι;", GreekPhrases.says("Άννα", "πού είσαι;"))
        assertEquals(
            "Ο Νίκος στην ομάδα Ορειβασία λέει: πάμε αύριο;",
            GreekPhrases.says(GreekPhrases.inGroup("Νίκος", "Ορειβασία"), "πάμε αύριο;"),
        )
        assertEquals("Vodafone: Ο λογαριασμός σας είναι έτοιμος.", GreekPhrases.says("Vodafone", "Ο λογαριασμός σας είναι έτοιμος."))
    }
}
