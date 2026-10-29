package io.github.sirionero.mycar.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Home Assistant's Assist, asked by myCar's own assistant (car/AssistantBrain): what the driver
 * said that isn't one of myCar's commands goes to the voice assistant set up in Home Assistant -
 * its conversation agent: Claude, Gemini, ChatGPT, a model at home, or Home Assistant's own, which
 * runs the house - over the connection Plancia keeps for the drive (through the Plancia app when
 * Tailscale leaves myCar out). Text in, text out: myCar listens and speaks itself, so it works with
 * the phone locked in a pocket.
 */
object Assist {

    /** What Home Assistant said back, in the driver's language. [goOn]: it asked something in turn. */
    class Answer(val speech: String, val conversationId: String?, val goOn: Boolean)

    /**
     * Asks Home Assistant, on the drive's connection, [text] as the driver said it. [conversationId]
     * carries on an earlier exchange ("and tomorrow?"). Throws [HaException] when it can't.
     */
    suspend fun ask(text: String, language: String, conversationId: String?): Answer {
        val client = HomeAssistant.live ?: throw HaException("Not connected to Home Assistant", HaException.Reason.CLOSED)
        val known = pipelines(client)
        return when (val plan = if (known != null) AssistPlan.of(known.first, known.second, language) else AssistPlan.Process(null, language)) {
            is AssistPlan.Run -> run(client, plan.pipeline, text, conversationId)
            is AssistPlan.Process -> process(client, plan, text, conversationId)
        }
    }

    /** The pipelines, read once per connection: they change when the driver edits them at home. */
    private suspend fun pipelines(client: HaClient): Pair<List<AssistPipeline>, String?>? {
        cache?.takeIf { it.first === client }?.let { return it.second }
        val result = try {
            client.command("assist_pipeline/pipeline/list") as? JSONObject
        } catch (e: HaException) {
            null
        } ?: return null
        val array = result.optJSONArray("pipelines")
        val list = (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val p = array?.optJSONObject(i) ?: return@mapNotNull null
            val id = p.str("id") ?: return@mapNotNull null
            AssistPipeline(id, p.str("conversation_language") ?: p.str("language").orEmpty(), p.str("conversation_engine"))
        }
        val read = list to result.str("preferred_pipeline")
        cache = client to read
        return read
    }

    @Volatile private var cache: Pair<HaClient, Pair<List<AssistPipeline>, String?>>? = null

    private suspend fun run(client: HaClient, pipeline: String, text: String, conversationId: String?): Answer {
        val answer = CompletableDeferred<Answer>()
        // A connection lost meanwhile ends the wait, rather than the timeout.
        client.closed.invokeOnCompletion { answer.completeExceptionally(HaException("Connection closed", HaException.Reason.CLOSED)) }
        val id = client.stream(
            "assist_pipeline/run",
            onEvent = { event ->
                val data = event.optJSONObject("data")
                when (event.optString("type")) {
                    "intent-end" -> answer.complete(parse(data?.optJSONObject("intent_output")))
                    "error" -> answer.completeExceptionally(HaException(data?.str("message") ?: "Assist stopped"))
                    "run-end" -> answer.completeExceptionally(HaException("Assist ended without an answer"))
                }
            },
        ) {
            put("start_stage", "intent")
            put("end_stage", "intent")
            put("input", JSONObject().put("text", text))
            put("pipeline", pipeline)
            conversationId?.let { put("conversation_id", it) }
            put("timeout", TIMEOUT_S)
        }
        try {
            return withTimeoutOrNull(TIMEOUT_S * 1_000L) { answer.await() }
                ?: throw HaException("Home Assistant didn't answer in time", HaException.Reason.NO_RESPONSE)
        } finally {
            client.forget(id)
        }
    }

    private suspend fun process(client: HaClient, plan: AssistPlan.Process, text: String, conversationId: String?): Answer {
        val result = client.command("conversation/process") {
            put("text", text)
            put("language", plan.language)
            plan.agent?.let { put("agent_id", it) }
            conversationId?.let { put("conversation_id", it) }
        } as? JSONObject
        return parse(result)
    }

    /** Home Assistant's ConversationResult: response.speech.plain.speech, and how to go on. */
    private fun parse(output: JSONObject?): Answer = Answer(
        output?.optJSONObject("response")?.optJSONObject("speech")?.optJSONObject("plain")?.str("speech")?.trim().orEmpty(),
        output?.str("conversation_id"),
        output?.optBoolean("continue_conversation") == true,
    )

    /** Long enough for a large model at home to think; the car shows "One moment…" meanwhile. */
    private const val TIMEOUT_S = 40
}
