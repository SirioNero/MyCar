package io.github.sirionero.mycar

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The languages myCar speaks, the same as Xonos's: on the phone, on the car's screen, and the
 * assistant's voice. The phone's own by default (English when it isn't one of these), or one
 * chosen in Settings. From Android 13 Android keeps the choice (it shows in Android's own settings
 * for myCar too) and every part of myCar gets it, with nothing restarted; on Android 12 myCar
 * applies it itself.
 */
enum class AppLanguage(
    val tag: String,
    val label: String,
    /** As Music Assistant names it, for Xonos inside myCar: it translates its own words into the connection's language. */
    val maLocale: String,
) {
    ENGLISH("en", "English", "en"),
    ITALIAN("it", "Italiano", "it"),
    SPANISH("es", "Español", "es"),
    GERMAN("de", "Deutsch", "de"),
    FRENCH("fr", "Français", "fr"),
    // Music Assistant's pt_PT is much more complete than its pt_BR, as the Xonos app found.
    PORTUGUESE("pt", "Português", "pt_PT"),
    GREEK("el", "Ελληνικά", "el_GR"),
    RUSSIAN("ru", "Русский", "ru_RU"),
    CHINESE("zh-CN", "简体中文", "zh_CN"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        private const val KEY = "app_language"

        /** The one chosen in Settings; null for the phone's. */
        fun chosen(context: Context): AppLanguage? {
            if (Build.VERSION.SDK_INT >= 33) {
                val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
                return if (locales == null || locales.isEmpty) null else of(locales[0])
            }
            val tag = AppSettings.getString(context, KEY, "")
            return entries.firstOrNull { it.tag == tag }
        }

        /** The language myCar is in now: the chosen one, else the phone's when it's one of these. */
        fun current(context: Context): AppLanguage = chosen(context) ?: of(phoneLocale()) ?: ENGLISH

        /** The phone's own language, whatever myCar is in. */
        fun phoneLocale(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.ENGLISH

        /** One of these for [locale], by language: any Chinese is the simplified one. */
        fun of(locale: Locale): AppLanguage? = entries.firstOrNull { it.locale.language == locale.language }

        /**
         * Chosen in Settings, or null for the phone's. Android 13 and later apply it at once; on
         * Android 12 myCar does, and its screen starts again unless a drive is under way (it would
         * end the drive), in which case the next start is in the new language.
         */
        fun choose(activity: Activity, language: AppLanguage?) {
            AppSettings.putString(activity, KEY, language?.tag.orEmpty())
            if (Build.VERSION.SDK_INT >= 33) {
                activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                    if (language == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
                return
            }
            applyToProcess(activity)
            if (!SessionService.running) activity.recreate()
        }

        /** Android 12: the chosen language for the whole process, services included. */
        fun applyToProcess(context: Context) {
            if (Build.VERSION.SDK_INT >= 33) return
            val locale = chosen(context)?.locale ?: phoneLocale()
            Locale.setDefault(locale)
            val resources = context.applicationContext.resources
            val config = Configuration(resources.configuration).apply { setLocale(locale) }
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }

        /** Android 12: [base] in the chosen language, for an activity's attachBaseContext. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= 33) return base
            val language = chosen(base) ?: return base
            return inLanguage(base, language)
        }

        private var appWords: Pair<String, Resources>? = null

        /**
         * The application's resources in myCar's language, for [Words]. Android 13 gives them so;
         * on Android 12 they come from a context of their own, which a change of the phone's
         * settings (a turn of the phone, dark mode) doesn't take back to the phone's language.
         */
        fun resources(app: Context): Resources {
            if (Build.VERSION.SDK_INT >= 33) return app.resources
            val language = chosen(app) ?: return app.resources
            appWords?.let { (tag, words) -> if (tag == language.tag) return words }
            return inLanguage(app, language).resources.also { appWords = language.tag to it }
        }

        /**
         * Android 12: [context] with its words in myCar's language, for a screen Android doesn't
         * give them to (the car's, on a display of its own). Display, density and theme stay
         * [context]'s.
         */
        fun localized(context: Context): Context {
            if (Build.VERSION.SDK_INT >= 33) return context
            val language = chosen(context) ?: return context
            val words = inLanguage(context, language).resources
            return object : ContextWrapper(context) {
                override fun getResources(): Resources = words
            }
        }

        private fun inLanguage(context: Context, language: AppLanguage): Context =
            context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(language.locale) })
    }
}
