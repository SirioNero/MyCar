package io.github.sirionero.mycar.host

import android.content.Context
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words

/**
 * Copies of apps that turned myCar down as their host in a way that lasts (see [RefusalRule]).
 * Modified copies of the apps [KnownApps] lists as broken that refused all the same: a copy signed
 * by someone other than its publisher counts as patched to accept other hosts, but it may have been
 * patched for something else, or with Waze's Morphe patch, which by default lets in Gearslip alone.
 * And any app whose own host check said no: it lets in only the hosts its developer chose. One that
 * refused is broken (red X, never started by itself) until it accepts myCar or another copy is
 * installed, an update or a copy patched again; its tile still opens it.
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

    /** The car's words for a refusal of [kind] by the app named [label], shown under "… would not start." */
    fun words(context: Context, kind: RefusalRule.Kind, label: String): String = when (kind) {
        RefusalRule.Kind.MODIFIED_COPY -> Words.get(R.string.car_host_modified_refused, label, hostsEntry(context))
        RefusalRule.Kind.HOST_CHECK -> Words.get(R.string.car_host_refused, label)
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
