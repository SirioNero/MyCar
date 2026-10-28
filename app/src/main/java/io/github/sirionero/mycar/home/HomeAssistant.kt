package io.github.sirionero.mycar.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.Tailnet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Where things stand with Home Assistant, for the car's screen and the phone's settings. */
sealed interface HaConnection {
    data object NotConfigured : HaConnection
    data class Connecting(val url: String) : HaConnection
    data class Connected(val url: String, val version: String) : HaConnection
    data class Failed(val message: String) : HaConnection
}

/**
 * Plancia, inside myCar: the driver's Home Assistant - its address at home, the one away, and a
 * long-lived token - and, while the car is connected, a live connection to it that says which
 * address answers and which dashboards there are. The dashboard itself is Home Assistant's own
 * page (see [io.github.sirionero.mycar.car.PlanciaWeb]).
 *
 * Set up on the phone, never in the car. With the Plancia app on the phone, its account is taken
 * from it ([fromPlanciaApp]) so nothing has to be typed again.
 */
object HomeAssistant {

    data class Config(val homeUrl: String = "", val remoteUrl: String = "", val token: String = "") {
        /** Home first, then away. */
        val urls: List<String> get() = listOf(homeUrl, remoteUrl).filter { it.isNotBlank() }.distinct()
        val isComplete: Boolean get() = urls.isNotEmpty() && token.isNotBlank()
    }

    private var app: Context? = null
    private val configFlow = MutableStateFlow(Config())
    private val zoomFlow = MutableStateFlow(100)
    private val dashboardFlow = MutableStateFlow<String?>(null)

    val config: StateFlow<Config> = configFlow.asStateFlow()

    /** How big the dashboard is drawn in the car, in percent. */
    val zoom: StateFlow<Int> = zoomFlow.asStateFlow()

    /** The dashboard Plancia opens (its url_path); null for the one Home Assistant picks. */
    val defaultDashboard: StateFlow<String?> = dashboardFlow.asStateFlow()

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        configFlow.value = Config(
            AppSettings.getString(app, KEY_HOME, ""),
            AppSettings.getString(app, KEY_REMOTE, ""),
            AppSettings.getString(app, KEY_TOKEN, ""),
        )
        zoomFlow.value = AppSettings.getString(app, KEY_ZOOM, "100").toIntOrNull()?.coerceIn(ZOOM_LEVELS.first(), ZOOM_LEVELS.last()) ?: 100
        dashboardFlow.value = AppSettings.getString(app, KEY_DASHBOARD, "").ifBlank { null }
        // Plancia on the phone, set up, and nothing here yet: its account, without asking. Asked
        // off the main thread, as asking may have to start Plancia first.
        if (!configFlow.value.isComplete && !importOff()) {
            scope.launch { fromPlanciaApp(app)?.let { if (!configFlow.value.isComplete) use(it) } }
        }
    }

    fun save(config: Config) = save(config, dashboardFlow.value, zoomFlow.value)

    /**
     * "Remove" on the phone: Plancia off in myCar, and the Plancia app's account isn't taken over
     * again by itself - otherwise the app, back in the car, was gone again at the next drive.
     */
    fun remove() {
        app?.let { AppSettings.putString(it, KEY_IMPORT_OFF, "1") }
        save(Config())
    }

    private fun importOff(): Boolean = app?.let { AppSettings.getString(it, KEY_IMPORT_OFF, "") == "1" } == true

    /** The Plancia app's account, with the dashboard it opens and its size. */
    fun use(imported: Imported) = save(imported.config, imported.dashboard, imported.zoom)

    private fun save(config: Config, dashboard: String?, zoom: Int) {
        val app = app ?: return
        configFlow.value = config
        if (config.isComplete) AppSettings.putString(app, KEY_IMPORT_OFF, "")
        AppSettings.putString(app, KEY_HOME, config.homeUrl)
        AppSettings.putString(app, KEY_REMOTE, config.remoteUrl)
        AppSettings.putString(app, KEY_TOKEN, config.token)
        setDefaultDashboard(dashboard)
        setZoom(zoom)
        reconnect()
    }

    fun setZoom(percent: Int) {
        zoomFlow.value = percent.coerceIn(ZOOM_LEVELS.first(), ZOOM_LEVELS.last())
        app?.let { AppSettings.putString(it, KEY_ZOOM, zoomFlow.value.toString()) }
    }

    fun setDefaultDashboard(urlPath: String?) {
        dashboardFlow.value = urlPath
        app?.let { AppSettings.putString(it, KEY_DASHBOARD, urlPath.orEmpty()) }
    }

    // --- Plancia, the app ------------------------------------------------------------------

    class Imported(val config: Config, val dashboard: String?, val zoom: Int)

    /**
     * The account of the Plancia app on the phone, if it has one and will hand it over: Plancia
     * answers only myCar, checking the signing certificate of the app asking, and only from the
     * version that learnt to. Null otherwise.
     */
    fun fromPlanciaApp(context: Context): Imported? = runCatching {
        context.contentResolver.call(PLANCIA_ACCOUNT, "account", null, null)
    }.onFailure { MyCarLog.i("plancia: no account from the Plancia app (${it.javaClass.simpleName})") }
        .getOrNull()?.let { b ->
            val config = Config(
                normalizeUrl(b.getString("home").orEmpty()),
                normalizeUrl(b.getString("remote").orEmpty()),
                b.getString("token").orEmpty().trim(),
            )
            if (!config.isComplete) return@let null
            MyCarLog.i("plancia: took the Plancia app's Home Assistant account")
            Imported(config, b.getString("dashboard")?.ifBlank { null }, b.getInt("zoom", 100))
        }

    /** Whether the Plancia app on the phone can hand its account over: installed, and recent enough. */
    fun planciaAppCanShare(context: Context): Boolean =
        context.packageManager.resolveContentProvider(PLANCIA_ACCOUNT_AUTHORITY, 0) != null

    // --- The live connection ---------------------------------------------------------------

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _dashboards = MutableStateFlow<List<Dashboard>>(emptyList())
    val dashboards: StateFlow<List<Dashboard>> = _dashboards.asStateFlow()

    /** The dashboard Home Assistant opens by itself (url_path). */
    private val _haDefault = MutableStateFlow<String?>(null)
    val haDefault: StateFlow<String?> = _haDefault.asStateFlow()

    private val _connection = MutableStateFlow<HaConnection>(HaConnection.NotConfigured)
    val connection: StateFlow<HaConnection> = _connection.asStateFlow()

    @Volatile private var client: HaClient? = null
    private var loop: Job? = null
    private var users = 0

    /** Cuts a wait between tries short: a new network for myCar (Tailscale up, Wi-Fi to mobile). */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var networkWatch: ConnectivityManager.NetworkCallback? = null

    fun newClient() = HaClient(http)

    /**
     * The car connected: stay connected, so the dashboard is ready before it's asked for. Not set
     * up yet, the Plancia app is asked again first: it may have been set up, or updated, since.
     */
    @Synchronized
    fun acquire() {
        users++
        val app = app
        if (!configFlow.value.isComplete && app != null && !importOff()) {
            scope.launch { fromPlanciaApp(app)?.let { if (!configFlow.value.isComplete) use(it) } }
        }
        if (loop?.isActive != true) loop = scope.launch { connectLoop() }
        if (networkWatch == null && app != null) {
            val watch = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    wake.trySend(Unit)
                }
            }
            runCatching { app.getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(watch) }
                .onSuccess { networkWatch = watch }
        }
    }

    /** The car went: the connection closes once nothing else needs it. */
    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            loop?.cancel()
            loop = null
            client?.close()
            client = null
            networkWatch?.let { watch ->
                runCatching { app?.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(watch) }
            }
            networkWatch = null
        }
    }

    /** From the start, as after a new address or token. */
    @Synchronized
    fun reconnect() {
        if (users == 0) return
        loop?.cancel()
        client?.close()
        loop = scope.launch { connectLoop() }
    }

    /** The dashboards again, as when the menu opens. */
    fun refreshDashboards() {
        val c = client ?: return
        scope.launch { loadDashboards(c) }
    }

    private suspend fun connectLoop() {
        var failures = 0
        while (true) {
            val config = configFlow.value
            if (!config.isComplete) {
                _connection.value = HaConnection.NotConfigured
                delay(4_000)
                continue
            }

            var session: HaClient? = null
            var version = ""
            var usedUrl = ""
            var lastError = "Home Assistant can't be reached"
            var lastException: Exception? = null
            var tokenRefused = false
            for (url in config.urls) {
                _connection.value = HaConnection.Connecting(url)
                val candidate = HaClient(http)
                try {
                    version = candidate.connect(url, config.token)
                    session = candidate
                    usedUrl = url
                    break
                } catch (e: CancellationException) {
                    candidate.close()
                    throw e
                } catch (e: Exception) {
                    candidate.close()
                    lastError = e.message ?: lastError
                    lastException = e
                    val reason = (e as? HaException)?.reason
                    // Only Home Assistant's own "auth_invalid" means the token. A 401/403 before the
                    // socket opens comes from a proxy in front of it, or a banned address: tried
                    // again like any failure, not given up on for the whole drive.
                    if (reason == HaException.Reason.INVALID_TOKEN) tokenRefused = true
                    if (reason == HaException.Reason.ACCESS_DENIED) {
                        lastError = "The server turned myCar away: a proxy in front of Home Assistant, or an address it has banned."
                    }
                }
            }

            if (session == null && tokenRefused) {
                // Trying a refused token again and again counts as failed logins in Home Assistant,
                // which can ban the phone's address: wait for a new one instead.
                MyCarLog.w("plancia: Home Assistant refused the token - waiting for a new one")
                _connection.value = HaConnection.Failed("Home Assistant refused the token: make a new one there, and set it in myCar on the phone.")
                configFlow.first { it != config }
                failures = 0
                continue
            }

            if (session == null) {
                // Tailscale leaving myCar out, or Android 17 keeping it off the home network, look like
                // an address that doesn't answer: say which.
                app?.let { context ->
                    val failure = HaException.failureOf(lastException)
                    if (failures == 0) {
                        MyCarLog.w("plancia: can't reach Home Assistant at ${config.urls}: ${lastException?.javaClass?.simpleName}: " +
                            "${lastException?.cause?.message ?: lastException?.message} - $failure, ${Tailnet.describe(context)}")
                    }
                    lastError = Tailnet.why(context, config.urls, failure, lastError)
                }
                _connection.value = HaConnection.Failed(lastError)
                failures++
                // Sooner when myCar gets a new network: Tailscale coming up, the Wi-Fi gone.
                withTimeoutOrNull((2_000L * failures).coerceAtMost(15_000L)) { wake.receive() }
                continue
            }

            failures = 0
            val active: HaClient = session
            client = active
            try {
                loadDashboards(active)
                watchPanels(active)
                _connection.value = HaConnection.Connected(usedUrl, version)
                MyCarLog.i("plancia: connected to Home Assistant $version")
                val reason = active.closed.await()
                _connection.value = HaConnection.Failed("Connection lost ($reason), trying again")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connection.value = HaConnection.Failed(e.message ?: "Connection error")
            } finally {
                if (client === active) client = null
                active.close()
            }
            delay(1_500)
        }
    }

    /** A dashboard added or removed in Home Assistant shows up in the menu by itself. */
    private suspend fun watchPanels(c: HaClient) {
        try {
            c.subscribeEvents("panels_updated") { scope.launch { loadDashboards(c) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MyCarLog.w("plancia: no dashboard updates: ${e.message}")
        }
    }

    /** The dashboards as Home Assistant's sidebar shows them, with the user's order and hidden ones. */
    private suspend fun loadDashboards(c: HaClient) {
        try {
            val panels = c.command("get_panels") as? JSONObject ?: return
            val sidebar = storedData(c, "frontend/get_user_data", "sidebar")
            val core = storedData(c, "frontend/get_user_data", "core")
            val system = storedData(c, "frontend/get_system_data", "core")
            val order = strings(sidebar?.optJSONArray("panelOrder"))
            val hidden = strings(sidebar?.optJSONArray("hiddenPanels")).toSet()
            val default = Panels.defaultPanel(panels, core?.str("default_panel"), system?.str("default_panel"))
            _haDefault.value = default
            _dashboards.value = Panels.dashboards(panels, order, hidden, default)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MyCarLog.w("plancia: couldn't read the dashboards: ${e.message}")
        }
    }

    /** What the frontend keeps (the sidebar's order, the default dashboard); missing on old versions. */
    private suspend fun storedData(c: HaClient, type: String, key: String): JSONObject? = try {
        (c.command(type) { put("key", key) } as? JSONObject)?.optJSONObject("value")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i -> array.optString(i).takeIf { it.isNotEmpty() } }
    }

    fun normalizeUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (url.isEmpty()) return ""
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        return url
    }

    val ZOOM_LEVELS = listOf(60, 70, 80, 90, 100, 115, 130, 150)

    private const val KEY_HOME = "ha_url_home"
    private const val KEY_REMOTE = "ha_url_remote"
    private const val KEY_TOKEN = "ha_token"
    private const val KEY_DASHBOARD = "ha_dashboard"
    private const val KEY_ZOOM = "ha_zoom"
    private const val KEY_IMPORT_OFF = "ha_import_off"

    /** The Plancia app's account, handed only to myCar (Plancia's AccountProvider). */
    private const val PLANCIA_ACCOUNT_AUTHORITY = "it.plancia.ha.account"
    private val PLANCIA_ACCOUNT: Uri = Uri.parse("content://$PLANCIA_ACCOUNT_AUTHORITY")
}
