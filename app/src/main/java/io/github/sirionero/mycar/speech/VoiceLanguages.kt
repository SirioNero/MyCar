package io.github.sirionero.mycar.speech

import android.content.Context
import java.util.Locale

object VoiceLanguages {

    /** The phone's language, for the assistant, whether or not a model on the phone hears it yet. */
    fun phone(): VoiceLanguage = VoiceLanguage.of(Locale.getDefault())

    /**
     * The language the assistant listens and answers in: the phone's, once a model on the phone
     * hears it (or the phone's own recognizer listens instead, [systemRecognizer]). English until
     * then, as it always was: a phone in Italian without the model for it keeps English commands.
     */
    fun effective(context: Context, systemRecognizer: Boolean): VoiceLanguage {
        val phone = phone()
        if (phone == VoiceLanguage.EN || systemRecognizer) return phone
        return if (SpeechModels.finalFor(context, phone) != null) phone else VoiceLanguage.EN
    }

    /** The voice to answer in: the phone's own kind of [language] (Brazilian or European Portuguese). */
    fun speakingLocale(language: VoiceLanguage): Locale =
        Locale.getDefault().takeIf { it.language == language.code } ?: language.locale
}
