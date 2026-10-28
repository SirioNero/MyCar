package io.github.sirionero.mycar.xonos

import android.content.Context
import android.net.Uri
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.xonos.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The Xonos app's Music Assistant account, handed to Xonos inside myCar: its addresses, its token
 * and who signed in, so nothing has to be typed again. The Xonos app answers only myCar, checking
 * the signing certificate of the app asking, and only from the version that learnt to.
 */
object XonosAccount {

    class Imported(val config: Settings.Config, val username: String?)

    private const val AUTHORITY = "it.xonos.ma.account"
    private val ACCOUNT: Uri = Uri.parse("content://$AUTHORITY")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The Xonos app's account, if it has one and will hand it over; null otherwise. Not on the main thread. */
    fun fromXonosApp(context: Context): Imported? = runCatching {
        context.contentResolver.call(ACCOUNT, "account", null, null)
    }.onFailure { MyCarLog.i("xonos: no account from the Xonos app (${it.javaClass.simpleName})") }
        .getOrNull()?.let { b ->
            val config = Settings.Config(
                Settings.normalizeUrl(b.getString("home").orEmpty()),
                Settings.normalizeUrl(b.getString("remote").orEmpty()),
                b.getString("token").orEmpty().trim(),
            )
            if (!config.isComplete) return@let null
            MyCarLog.i("xonos: took the Xonos app's Music Assistant account")
            Imported(config, b.getString("username")?.ifBlank { null })
        }

    /** Whether the Xonos app on the phone can hand its account over: installed, and recent enough. */
    fun xonosAppCanShare(context: Context): Boolean =
        context.packageManager.resolveContentProvider(AUTHORITY, 0) != null

    /**
     * With nothing set up in myCar yet, takes the Xonos app's account on its own, in the background:
     * a driver who already uses Xonos finds it in the car with nothing to do.
     */
    fun adoptIfUnset(context: Context) {
        val app = context.applicationContext
        Xonos.refresh(app)
        if (Xonos.setUp.value || Xonos.importOff(app) || !xonosAppCanShare(app)) return
        scope.launch {
            val imported = fromXonosApp(app) ?: return@launch
            if (!Xonos.setUp.value) Xonos.save(app, imported.config, imported.username)
        }
    }
}
