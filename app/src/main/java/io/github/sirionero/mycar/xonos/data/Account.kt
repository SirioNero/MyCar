package io.github.sirionero.mycar.xonos.data

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Entrare in Music Assistant con nome utente e password, come fa la sua interfaccia web, invece
 * di copiare un token. Xonos se ne fa dare uno suo a lunga durata (un anno, si vede tra i token
 * dell'utente in Music Assistant) e la password non la tiene. Gli ospiti non possono avere token
 * a lunga durata: per loro resta quello della sessione, che dura un giorno.
 *
 * Su un Music Assistant appena installato, senza account, crea il primo (l'amministratore).
 */
class Account(private val repo: MaRepository) {

    /** L'accesso riuscito: il token per Xonos, chi è entrato e con quale ruolo ("admin", "user", "guest"). */
    data class Login(val token: String, val user: String, val role: String)

    /** Accede con nome utente e password ([device] è il nome del token in Music Assistant). */
    suspend fun login(url: String, username: String, password: String, device: String): Login {
        val client = repo.newClient()
        try {
            val server = client.open(url)
            val answer = try {
                client.command("auth/login") {
                    put("username", username)
                    put("password", password)
                    put("device_name", device)
                } as? JSONObject
            } catch (e: MaException) {
                // Senza account il server chiude subito dopo essersi presentato, spesso senza dire perché.
                if (e.problem in CLOSED && needsSetup(url)) {
                    throw MaException("Music Assistant non ha ancora un account", problem = Problem.SETUP_REQUIRED)
                }
                throw e
            }
            if (answer?.optBoolean("success") != true) {
                throw MaException(answer?.str("error") ?: "Accesso non riuscito", problem = Problem.BAD_LOGIN)
            }
            val session = answer.str("access_token") ?: throw MaException("Nessun token dall'accesso", problem = Problem.BAD_LOGIN)
            return keep(client, server, session, username, device)
        } finally {
            client.close()
        }
    }

    /** Crea il primo account di un Music Assistant nuovo (sarà l'amministratore) ed entra. */
    suspend fun createFirst(url: String, username: String, password: String, device: String): Login {
        val body = JSONObject()
            .put("username", username)
            .put("password", password)
            .put("device_name", device)
            .toString()
            .toRequestBody(JSON)
        val request = try {
            Request.Builder().url(Settings.normalizeUrl(url).trimEnd('/') + "/setup").post(body).build()
        } catch (e: IllegalArgumentException) {
            throw MaException("Indirizzo non valido: $url", problem = Problem.BAD_ADDRESS, detail = url)
        }
        val answer = repo.http.newCall(request).await().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (!response.isSuccessful || json?.optBoolean("success") != true) {
                val error = json?.str("error") ?: "HTTP ${response.code}"
                throw MaException(error, response.code, Problem.REFUSED, error)
            }
            json
        }
        val session = answer.str("token") ?: throw MaException("Nessun token dal server", problem = Problem.REFUSED)
        val client = repo.newClient()
        try {
            val server = client.open(url)
            return keep(client, server, session, username, device)
        } finally {
            client.close()
        }
    }

    /**
     * Vero se il server è appena installato e non ha nessun account: allora la sua pagina /setup
     * risponde (altrimenti la rifiuta con 403).
     */
    suspend fun needsSetup(url: String): Boolean {
        val request = try {
            Request.Builder().url(Settings.normalizeUrl(url).trimEnd('/') + "/setup").build()
        } catch (e: IllegalArgumentException) {
            return false
        }
        return try {
            repo.http.newCall(request).await().use { it.code == 200 }
        } catch (e: MaException) {
            false
        }
    }

    /** Dal token della sessione a uno a lunga durata, se il ruolo lo permette; poi la sessione si chiude. */
    private suspend fun keep(client: MaClient, server: ServerInfo, session: String, username: String, device: String): Login {
        val info = client.authenticate(server, session)
        val role = info.role ?: "user"
        val user = info.user ?: username
        if (role == "guest") return Login(session, user, role)
        val token = client.command("auth/token/create") { put("name", device) } as? String ?: return Login(session, user, role)
        // La sessione dell'accesso non serve più: resta solo il token di Xonos.
        runCatching { client.command("auth/logout", 5_000) }
        return Login(token, user, role)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        val CLOSED = setOf(Problem.LOST, Problem.NOT_CONNECTED, Problem.REFUSED, Problem.SETUP_REQUIRED)
    }
}

/** Una chiamata HTTP di OkHttp come funzione sospesa, che si può annullare. */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) = continuation.resume(response)

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(
                    MaException(e.message ?: "Errore di rete", problem = Problem.NETWORK, detail = e.message),
                )
            }
        },
    )
}
