package io.github.sirionero.mycar.setup

import android.content.Context
import io.github.sirionero.mycar.AppSettings

/**
 * The few choices the setup screen remembers. Everything else it reads from the phone each time
 * ([SetupSignals]), so going back to it never resets or deletes anything.
 */
object FirstSetup {
    private const val PREFS = "first_setup"
    private const val KEY_KEEP_ANDROID_AUTO = "keep_android_auto"
    private const val KEY_SKIPPED = "skipped"
    private const val KEY_ASKED = "asked"
    private const val KEY_BLOCKED = "blocked"

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

    /** Permissions myCar has asked for here. */
    fun asked(context: Context): Set<String> = prefs(context).getStringSet(KEY_ASKED, emptySet()).orEmpty().toSet()

    fun markAsked(context: Context, permissions: Collection<String>) {
        prefs(context).edit().putStringSet(KEY_ASKED, asked(context) + permissions).apply()
    }

    /**
     * Permissions Android no longer asks for, as myCar saw it happen: refused again after Android
     * had explained them (it stops there), or answered with no dialog at all. Not Back on a dialog,
     * nor the dialogs of the same request that never came after it: Android asks for those again,
     * and setup used to say it wouldn't, with only "Open settings" to offer.
     */
    fun blocked(context: Context): Set<String> = prefs(context).getStringSet(KEY_BLOCKED, emptySet()).orEmpty().toSet()

    fun answered(context: Context, blocked: Collection<String>, granted: Collection<String>) {
        prefs(context).edit().putStringSet(KEY_BLOCKED, blocked(context) - granted.toSet() + blocked).apply()
    }

    /**
     * Leaving the setup, done or for later: the guide counts as seen. (A new install doesn't open
     * the car on "What's new" either: CarSettings sees to that.)
     */
    fun finish(context: Context) {
        AppSettings.setSeenPermissionsSetup(context)
    }
}
