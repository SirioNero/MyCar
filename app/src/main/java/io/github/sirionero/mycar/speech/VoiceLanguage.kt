package io.github.sirionero.mycar.speech

import java.util.Locale

/**
 * The languages the car's assistant listens and answers in. The rest of the car's screen stays in
 * English; the assistant follows the phone (see [VoiceLanguages.effective]).
 */
enum class VoiceLanguage(val code: String, /** Its own name, as the phone's settings write it. */ val nativeName: String) {
    EN("en", "English"),
    IT("it", "Italiano"),
    ES("es", "Español"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    PT("pt", "Português"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        /** [locale]'s language, when the assistant knows it; English otherwise. */
        fun of(locale: Locale): VoiceLanguage = entries.firstOrNull { it.code == locale.language } ?: EN
    }
}
