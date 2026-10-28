package io.github.sirionero.mycar.xonos.data

import android.util.Log
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * Tiene viva una connessione all'API di Music Assistant mentre l'app serve: dice quale indirizzo
 * risponde (casa o remoto), inoltra gli eventi del server e i comandi dell'app.
 */
class MaRepository(
    private val settings: Settings,
    /** In myCar: la strada per Music Assistant, attraverso l'app Xonos quando serve (vedi bridge/Bridges). */
    network: OkHttpClient.Builder.() -> Unit = {},
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Uno solo per tutta l'app: API, audio e copertine. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .apply(network)
        .build()

    private val _connection = MutableStateFlow<Connection>(Connection.Connecting(""))
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val listeners = CopyOnWriteArrayList<(String, String?, Any?) -> Unit>()

    @Volatile private var client: MaClient? = null
    private var loop: Job? = null
    private var users = 0

    fun newClient() = MaClient(http, LOCALE)

    /** Riceve tutti gli eventi del server (nome, object_id, dati), sul thread di OkHttp. */
    fun addEventListener(listener: (String, String?, Any?) -> Unit) {
        listeners += listener
    }

    fun removeEventListener(listener: (String, String?, Any?) -> Unit) {
        listeners -= listener
    }

    /** Chiamato quando parte il servizio di riproduzione. */
    @Synchronized
    fun acquire() {
        users++
        if (loop?.isActive != true) loop = scope.launch { connectLoop() }
    }

    /** Chiamato quando il servizio si ferma: chiude la connessione. */
    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            loop?.cancel()
            loop = null
            client?.close()
            client = null
        }
    }

    /** Riparte da capo, per esempio dopo aver cambiato indirizzo o token, o rete. */
    @Synchronized
    fun reconnect() {
        if (users == 0) return
        loop?.cancel()
        client?.close()
        loop = scope.launch { connectLoop() }
    }

    /** L'indirizzo che sta rispondendo: lo usano anche l'audio e le copertine. */
    val baseUrl: String? get() = (connection.value as? Connection.Connected)?.url

    /** Un comando all'API; fallisce subito se Music Assistant non è collegato. */
    suspend fun command(name: String, timeoutMs: Long = 30_000, fill: JSONObject.() -> Unit = {}): Any? {
        val c = client ?: throw MaException("Music Assistant non è collegato", problem = Problem.NOT_CONNECTED)
        return c.command(name, timeoutMs, fill)
    }

    /** Un comando che risponde con un elenco, trasformato elemento per elemento mentre arriva (vedi [MaClient.items]). */
    suspend fun <T : Any> items(
        name: String,
        timeoutMs: Long = 30_000,
        transform: (JSONObject) -> T?,
        fill: JSONObject.() -> Unit = {},
        onPart: ((List<T>) -> Unit)? = null,
    ): List<T> {
        val c = client ?: throw MaException("Music Assistant non è collegato", problem = Problem.NOT_CONNECTED)
        return c.items(name, timeoutMs, transform, fill, onPart)
    }

    private suspend fun connectLoop() {
        var failures = 0
        while (true) {
            val config = settings.load()
            if (!config.isComplete) {
                _connection.value = Connection.NotConfigured
                delay(4_000)
                continue
            }

            var session: MaClient? = null
            var server: ServerInfo? = null
            var usedUrl = ""
            var lastError = MaException("Music Assistant non raggiungibile", problem = Problem.NETWORK)
            for (url in config.urls) {
                _connection.value = Connection.Connecting(url)
                val candidate = MaClient(http, LOCALE)
                try {
                    server = candidate.connect(url, config.token)
                    session = candidate
                    usedUrl = url
                    break
                } catch (e: CancellationException) {
                    candidate.close()
                    throw e
                } catch (e: Exception) {
                    candidate.close()
                    lastError = e as? MaException ?: MaException(e.message ?: lastError.message.orEmpty(), problem = Problem.NETWORK, detail = e.message)
                }
            }

            if (session == null || server == null) {
                _connection.value = Connection.Failed(lastError.message.orEmpty(), lastError.problem, lastError.detail)
                failures++
                delay((2_000L * failures).coerceAtMost(20_000L))
                continue
            }

            failures = 0
            val active: MaClient = session
            active.onEvent = { event, objectId, data -> listeners.forEach { it(event, objectId, data) } }
            client = active
            try {
                Log.i(TAG, "Collegato a ${server.name} (${server.version}) su $usedUrl")
                _connection.value = Connection.Connected(usedUrl, server)
                val reason = active.closed.await()
                _connection.value = Connection.Failed("Connessione persa ($reason), riprovo", Problem.LOST, reason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connection.value = Connection.Failed(
                    e.message ?: "Errore di connessione",
                    (e as? MaException)?.problem ?: Problem.NETWORK,
                    (e as? MaException)?.detail ?: e.message,
                )
            } finally {
                if (client === active) client = null
                active.onEvent = null
                active.close()
            }
            delay(1_000)
        }
    }

    companion object {
        private const val TAG = "Xonos"

        /** La lingua dei testi che Music Assistant traduce da sé: l'inglese di myCar. */
        private const val LOCALE = "en"

        /**
         * L'indirizzo di un'immagine: dal proxy di Music Assistant, che la rimpicciolisce, oppure
         * quello pubblico originale. Le versioni recenti accettano solo alcune misure.
         */
        fun imageUrl(baseUrl: String, image: MaImage, size: Int): String {
            val base = baseUrl.trimEnd('/')
            if (image.proxyId != null) return "$base/imageproxy/${image.proxyId}?size=$size&fmt=jpg"
            if (image.remote && (image.path.startsWith("https://") || image.path.startsWith("http://"))) return image.path
            return "$base/imageproxy?path=${encode(image.path)}&provider=${encode(image.provider)}&size=$size"
        }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}
