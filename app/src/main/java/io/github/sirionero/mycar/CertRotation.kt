package io.github.sirionero.mycar

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Acts on what the car says of the certificate: keeps [CertRotationPolicy]'s state, puts the next
 * catalog certificate in use after a refusal, and leaves a [notice] for the phone.
 */
object CertRotation {
    private val log = MyCarLog.tagged("CERT")

    private const val PREFS = "cert_catalog"
    private const val KEY_ACCEPTED = "accepted"
    private const val KEY_REFUSED = "refused"
    private const val KEY_EXHAUSTED = "exhausted"
    private const val KEY_NOTICE = "notice"
    private const val KEY_NOTICE_CERT = "notice_cert"

    /** What to tell the driver, and about which certificate: the one turned down, or the next. */
    data class Notice(val kind: CertRotationPolicy.Notice, val cert: String? = null)

    private val _notice = MutableStateFlow(Notice(CertRotationPolicy.Notice.NONE))
    val notice: StateFlow<Notice> = _notice.asStateFlow()

    /** Reads the notice left by the last drive. */
    fun init(context: Context) {
        val prefs = prefs(context)
        val kind = runCatching { CertRotationPolicy.Notice.valueOf(prefs.getString(KEY_NOTICE, null) ?: "NONE") }
            .getOrDefault(CertRotationPolicy.Notice.NONE)
        _notice.value = Notice(kind, prefs.getString(KEY_NOTICE_CERT, null))
    }

    /**
     * Off the session's thread: reading a certificate file can take a second on a phone, and the
     * car is waiting for the session to go on meanwhile.
     */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "mycar-cert-verdict") }

    /** The car's verdict on [identity], once per session. Returns at once; the work goes on aside. */
    fun report(context: Context, identity: CertProvider.Identity, verdict: CertVerdict) {
        val app = context.applicationContext
        worker.execute { runCatching { decide(app, identity, verdict) }.onFailure { log.e("the certificate's verdict went unhandled", it) } }
    }

    @Synchronized
    private fun decide(context: Context, identity: CertProvider.Identity, verdict: CertVerdict) {
        // An aasdk downloaded before the catalog counts as one of its own.
        runCatching { CertCatalog.adoptLegacy(context) }
        val presented = if (identity.kind == CertProvider.Kind.DOWNLOADED) CertCatalog.idOf(identity.certificate) else null
        val own = identity.kind == CertProvider.Kind.IMPORTED || identity.kind == CertProvider.Kind.ADB_STAGED
        // Past its end by the phone's clock too (CarService lasts a few months): not the car's date.
        // It counts as turned down, so the next one goes in use, and a new one is fetched.
        val expired = presented != null && identity.certificate.notAfter.time < System.currentTimeMillis()
        val seen = if (verdict == CertVerdict.Clock && expired) CertVerdict.Refused(hard = true, why = "expired") else verdict
        val outcome = CertRotationPolicy.decide(read(context), seen, presented, own, CertCatalog.present(context))
        write(context, outcome.state)
        outcome.switchTo?.let { id ->
            runCatching { CertCatalog.activate(context, id) }.onFailure { log.e("couldn't put $id in use", it) }
        }
        setNotice(context, Notice(outcome.notice, outcome.switchTo ?: presented))
        log.i(
            "the car's verdict on ${presented ?: identity.kind.name.lowercase()}: $verdict -> ${outcome.notice}" +
                (outcome.switchTo?.let { ", $it next" } ?: "") + (if (expired) " (it had run out)" else ""),
        )
        if (expired) runCatching { CertCatalog.refreshIfDue(context, force = true) }
    }

    /** "Try again from the first": every certificate may be tried again. */
    @Synchronized
    fun startOver(context: Context) {
        write(context, CertRotationPolicy.startOver(read(context)))
        CertCatalog.present(context).firstOrNull()?.let { CertCatalog.activate(context, it) }
        setNotice(context, Notice(CertRotationPolicy.Notice.NONE))
    }

    /** The driver picked [id] in Settings: in use from now, and every certificate may be tried again. */
    @Synchronized
    fun choose(context: Context, id: String) {
        CertCatalog.activate(context, id)
        write(context, CertRotationPolicy.startOver(read(context)))
        setNotice(context, Notice(CertRotationPolicy.Notice.NONE))
    }

    /**
     * After the spare is downloaded: the first certificate on the phone the car hasn't turned down
     * goes in use. Returns its id, or null when there's none.
     */
    @Synchronized
    fun useSpare(context: Context): String? {
        val state = read(context)
        val next = CertCatalog.present(context).firstOrNull { it !in state.refused } ?: return null
        CertCatalog.activate(context, next)
        setNotice(context, Notice(CertRotationPolicy.Notice.NONE))
        return next
    }

    /** The driver dismissed the notice. */
    fun dismiss(context: Context) = setNotice(context, Notice(CertRotationPolicy.Notice.NONE))

    private fun setNotice(context: Context, notice: Notice) {
        prefs(context).edit()
            .putString(KEY_NOTICE, notice.kind.name)
            .putString(KEY_NOTICE_CERT, notice.cert)
            .apply()
        _notice.value = notice
    }

    private fun read(context: Context): CertRotationPolicy.State {
        val prefs = prefs(context)
        return CertRotationPolicy.State(
            accepted = prefs.getString(KEY_ACCEPTED, null),
            refused = prefs.getStringSet(KEY_REFUSED, emptySet()).orEmpty().toSet(),
            exhausted = prefs.getBoolean(KEY_EXHAUSTED, false),
        )
    }

    private fun write(context: Context, state: CertRotationPolicy.State) {
        prefs(context).edit()
            .putString(KEY_ACCEPTED, state.accepted)
            .putStringSet(KEY_REFUSED, state.refused)
            .putBoolean(KEY_EXHAUSTED, state.exhausted)
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
