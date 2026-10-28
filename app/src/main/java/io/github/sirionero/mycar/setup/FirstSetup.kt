package io.github.sirionero.mycar.setup

import android.content.Context
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.car.CarSettings

/**
 * The few choices the setup screen remembers. Everything else it reads from the phone each time
 * ([SetupSignals]), so going back to it never resets or deletes anything.
 */
object FirstSetup {
    private const val PREFS = "first_setup"
    private const val KEY_KEEP_ANDROID_AUTO = "keep_android_auto"
    private const val KEY_SKIPPED = "skipped"
    private const val KEY_ASKED = "asked"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The driver keeps Android Auto, and picks myCar when Android asks which app to open. */
    fun keepAndroidAuto(context: Context): Boolean = prefs(context).getBoolean(KEY_KEEP_ANDROID_AUTO, false)

    fun setKeepAndroidAuto(context: Context, keep: Boolean) {
        prefs(context).edit().putBoolean(KEY_KEEP_ANDROID_AUTO, keep).apply()
    }

    fun skipped(context: Context): Set<SetupSteps.Id> =
        prefs(context).getStringSet(KEY_SKIPPED, emptySet()).orEmpty()
            .mapNotNull { runCatching { SetupSteps.Id.valueOf(it) }.getOrNull() }.toSet()

    fun skip(context: Context, id: SetupSteps.Id) {
        prefs(context).edit().putStringSet(KEY_SKIPPED, skipped(context).map { it.name }.toSet() + id.name).apply()
    }

    /** Permissions myCar has asked for here: refused again, Android stops showing the dialog. */
    fun asked(context: Context): Set<String> = prefs(context).getStringSet(KEY_ASKED, emptySet()).orEmpty().toSet()

    fun markAsked(context: Context, permissions: Collection<String>) {
        prefs(context).edit().putStringSet(KEY_ASKED, asked(context) + permissions).apply()
    }

    /**
     * Leaving the setup, done or for later. The guide counts as seen, and the car doesn't open on
     * "What's new" at the very first drive: there's nothing new to someone who just installed it.
     */
    fun finish(context: Context) {
        AppSettings.setSeenPermissionsSetup(context)
        if (CarSettings.whatsNewSeen.value == 0) CarSettings.closeWhatsNew(BuildConfig.VERSION_CODE)
    }
}
