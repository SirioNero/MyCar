package io.github.sirionero.mycar.host

import android.content.Context
import io.github.sirionero.mycar.BuildConfig

/**
 * Modified copies of the apps [KnownApps] lists as broken that turned myCar down all the same. A
 * copy signed by someone other than its publisher counts as patched to accept other hosts, but it
 * may have been patched for something else, or with Waze's Morphe patch, which by default lets in
 * Gearslip alone. One that refused is broken again (red X, never started by itself) until it
 * accepts myCar or another copy is installed; its tile still opens it.
 *
 * Kept with the copy's install time (PackageInfo.lastUpdateTime), not with its signer: Morphe signs
 * every copy it patches with the same key, so a copy patched again to accept myCar has the signer
 * of the one that refused.
 */
object HostRefusals {

    /** Named when it held patched copies only (PatchRefusals): kept, so what it holds stays. */
    private const val PREFS = "mycar_patch_refusals"

    /** myCar's release signing certificate, SHA-256, as hosts lists write it. */
    private const val RELEASE_SIGNER = "e477a4794b984927360e9ae4a31e87cb08a26c54e0e27e4334116d1c7cdccc31"

    /** Whether the copy installed at [installed] is the one that refused myCar, installed at [refused]. */
    internal fun sameCopy(refused: Long?, installed: Long?): Boolean = refused != null && refused == installed

    /** The installed copy of [packageName] refused myCar. */
    fun remember(context: Context, packageName: String) {
        val installed = installedAt(context, packageName) ?: return
        prefs(context).edit().putLong(packageName, installed).apply()
    }

    /** [packageName] accepted myCar. */
    fun forget(context: Context, packageName: String) {
        val prefs = prefs(context)
        if (prefs.contains(packageName)) prefs.edit().remove(packageName).apply()
    }

    /** Whether the installed copy of [packageName] is one that refused myCar; another copy isn't. */
    fun refused(context: Context, packageName: String): Boolean {
        val prefs = prefs(context)
        if (!prefs.contains(packageName)) return false
        return sameCopy(prefs.getLong(packageName, 0L), installedAt(context, packageName))
    }

    /**
     * myCar as an entry of the Waze patch's "Accepted hosts": the SHA-256 of its signing certificate,
     * then its package. The debug build is signed with a key of its own, so it gets "*", any certificate.
     */
    fun hostsEntry(context: Context): String = (if (BuildConfig.DEBUG) "*" else RELEASE_SIGNER) + "," + context.packageName

    private fun installedAt(context: Context, packageName: String): Long? =
        runCatching { context.packageManager.getPackageInfo(packageName, 0).lastUpdateTime }.getOrNull()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
