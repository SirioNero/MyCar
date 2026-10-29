package io.github.sirionero.mycar.home

import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.Tailnet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Why Home Assistant can't be reached or used, in words for the car and the phone alike; [cause] is the network's error. */
class HaException(message: String, val reason: Reason = Reason.OTHER, cause: Throwable? = null) : Exception(message, cause) {
    enum class Reason { BAD_ADDRESS, NO_RESPONSE, INVALID_TOKEN, ACCESS_DENIED, HTTP_ERROR, NETWORK, CLOSED, OTHER }

    companion object {
        /** How a connection to Home Assistant failed, as far as the network goes. */
        fun failureOf(e: Throwable?): Tailnet.Failure = when {
            e is HaException && e.reason == Reason.NO_RESPONSE -> Tailnet.Failure.TIMEOUT
            e is HaException && e.reason != Reason.NETWORK -> Tailnet.Failure.OTHER
            else -> Tailnet.failureOf(e)
        }
    }
}

/**
 * One WebSocket connection to Home Assistant (/api/websocket): signing in with the token,
 * commands by id and event subscriptions. From Plancia, the Home Assistant app for the car.
 */
class HaClient(private val http: OkHttpClient) {

    private class Box(val value: Any?)

    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<Box>>()
    private val handlers = ConcurrentHashMap<Int, (JSONObject) -> Unit>()
    private val auth = CompletableDeferred<String>()

    /** Completes, with the reason, once the connection has closed. */
    val closed = CompletableDeferred<String>()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var token: String = ""

    /** Connects and signs in. Returns Home Assistant's version. */
    suspend fun connect(baseUrl: String, token: String, timeoutMs: Long = 10_000): String {
        this.token = token
        val request = try {
            Request.Builder().url(websocketUrl(baseUrl)).build()
        } catch (e: IllegalArgumentException) {
            throw HaException("Not a valid address: $baseUrl", HaException.Reason.BAD_ADDRESS)
        }
        socket = http.newWebSocket(request, listener)
        val version = try {
            withTimeoutOrNull(timeoutMs) { auth.await() }
        } catch (e: HaException) {
            close()
            throw e
        }
        if (version == null) {
            // A server that took the connection but never answered: drop the call too, which would
            // otherwise wait without end (no read timeout, for the long-lived socket).
            socket?.cancel()
            close()
            throw HaException("No answer from $baseUrl", HaException.Reason.NO_RESPONSE)
        }
        return version.ifBlank { "?" }
    }

    suspend fun command(type: String, fill: JSONObject.() -> Unit = {}): Any? =
        send(ids.getAndIncrement(), type, fill)

    suspend fun subscribeEvents(eventType: String, onEvent: (JSONObject) -> Unit) {
        val id = ids.getAndIncrement()
        handlers[id] = onEvent
        try {
            send(id, "subscribe_events") { put("event_type", eventType) }
        } catch (e: Exception) {
            handlers.remove(id)
            throw e
        }
    }

    /**
     * A command that goes on answering with events under its own id after its result, as
     * assist_pipeline/run does: [onEvent] gets them until [forget]. Returns that id.
     */
    suspend fun stream(type: String, onEvent: (JSONObject) -> Unit, fill: JSONObject.() -> Unit = {}): Int {
        val id = ids.getAndIncrement()
        handlers[id] = onEvent
        try {
            send(id, type, fill)
        } catch (e: Exception) {
            handlers.remove(id)
            throw e
        }
        return id
    }

    fun forget(id: Int) {
        handlers.remove(id)
    }

    fun close() {
        socket?.close(1000, null)
        shutdown(HaException("Connection closed", HaException.Reason.CLOSED))
    }

    private suspend fun send(id: Int, type: String, fill: JSONObject.() -> Unit): Any? {
        val ws = socket
        if (ws == null || closed.isCompleted) throw HaException("Not connected to Home Assistant", HaException.Reason.CLOSED)
        val message = JSONObject().put("id", id).put("type", type).apply(fill)
        val deferred = CompletableDeferred<Box>()
        pending[id] = deferred
        if (!ws.send(message.toString())) {
            pending.remove(id)
            throw HaException("Couldn't send to Home Assistant", HaException.Reason.CLOSED)
        }
        val box = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { deferred.await() }
        if (box == null) {
            pending.remove(id)
            throw HaException("Home Assistant isn't answering", HaException.Reason.NO_RESPONSE)
        }
        return box.value
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val trimmed = text.trimStart()
                if (trimmed.startsWith("[")) {
                    val array = JSONArray(trimmed)
                    for (i in 0 until array.length()) array.optJSONObject(i)?.let { handle(webSocket, it) }
                } else {
                    handle(webSocket, JSONObject(trimmed))
                }
            } catch (e: Exception) {
                MyCarLog.w("plancia: a message from Home Assistant didn't parse: ${e.message}")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            shutdown(HaException("Connection closed", HaException.Reason.CLOSED))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val error = when {
                response?.code == 401 || response?.code == 403 ->
                    HaException("Access denied", HaException.Reason.ACCESS_DENIED)
                response != null ->
                    HaException("HTTP error ${response.code}", HaException.Reason.HTTP_ERROR)
                else ->
                    HaException(t.message ?: "Network error", HaException.Reason.NETWORK, t)
            }
            shutdown(error)
        }
    }

    private fun handle(ws: WebSocket, msg: JSONObject) {
        when (msg.optString("type")) {
            "auth_required" -> ws.send(JSONObject().put("type", "auth").put("access_token", token).toString())
            "auth_ok" -> auth.complete(msg.optString("ha_version"))
            "auth_invalid" -> auth.completeExceptionally(HaException("The token isn't valid", HaException.Reason.INVALID_TOKEN))
            "result" -> {
                val deferred = pending.remove(msg.optInt("id")) ?: return
                if (msg.optBoolean("success")) {
                    val result = msg.opt("result")
                    deferred.complete(Box(if (result == JSONObject.NULL) null else result))
                } else {
                    val error = msg.optJSONObject("error")
                    deferred.completeExceptionally(HaException(error?.str("message") ?: "Command refused"))
                }
            }
            "event" -> {
                val event = msg.optJSONObject("event") ?: return
                val handler = handlers[msg.optInt("id")] ?: return
                try {
                    handler(event)
                } catch (e: Exception) {
                    MyCarLog.w("plancia: an event from Home Assistant failed: ${e.message}")
                }
            }
        }
    }

    private fun shutdown(error: HaException) {
        auth.completeExceptionally(error)
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
        closed.complete(error.message.orEmpty())
    }

    companion object {
        private const val COMMAND_TIMEOUT_MS = 20_000L

        fun websocketUrl(base: String): String {
            val trimmed = base.trim().trimEnd('/')
            val ws = when {
                trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
                trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
                else -> "ws://$trimmed"
            }
            return "$ws/api/websocket"
        }
    }
}

/** Like optString, but null for a value that's missing, null or empty (org.json would say "null"). */
internal fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
