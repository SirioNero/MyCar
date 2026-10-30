package io.github.sirionero.mycar.car

import android.content.Context
import android.os.SystemClock
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.home.Assist
import io.github.sirionero.mycar.home.HaConnection
import io.github.sirionero.mycar.home.HomeAssistant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Who answers what isn't one of myCar's commands: the driver's Home Assistant, through the voice
 * assistant set up there (home/Assist) - Claude, Gemini, ChatGPT, a model at home, or Home
 * Assistant's own, which also runs the house. With the phone locked it stands in for the phone's
 * assistant, which can't listen over the lock screen (AssistantChoice): myCar listens and speaks,
 * and nothing has to open on the phone.
 */
object AssistantBrain {
    private const val KEY = "car_ask_home_assistant"

    private var app: Context? = null
    private val on = MutableStateFlow(true)

    /** On unless the driver turned it off (Settings > Plancia on the phone). */
    val enabled: StateFlow<Boolean> = on

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        on.value = AppSettings.getString(app, KEY, "1") == "1"
    }

    fun setEnabled(value: Boolean) {
        on.value = value
        app?.let { AppSettings.putString(it, KEY, if (value) "1" else "0") }
    }

    /** Home Assistant set up in myCar and connected for this drive, and not turned off. */
    val ready: Boolean get() = on.value && HomeAssistant.connection.value is HaConnection.Connected

    /**
     * What to say, already in the driver's language; [goOn]: Home Assistant asked something back.
     * The microphone doesn't open again for it: the driver answers with the microphone, and the
     * conversation carries on ([FOLLOW_ON_MS]) as long as the words aren't one of myCar's commands.
     */
    class Reply(val text: String, val goOn: Boolean)

    private var conversation: String? = null
    private var lastAt = 0L

    /**
     * Home Assistant's answer to [heard], as the driver said it; null when it can't be asked (not
     * set up, not connected, turned off). What was said isn't logged: it may be anything.
     */
    suspend fun ask(heard: String): Reply? {
        if (!ready) return null
        val goingOn = conversation?.takeIf { SystemClock.elapsedRealtime() - lastAt < FOLLOW_ON_MS }
        return try {
            // Not the 40 s a large model may take: the driver waited on "…" with the microphone
            // deaf, and took the assistant for stuck.
            val answer = withTimeoutOrNull(ANSWER_MS) { Assist.ask(heard, AssistantLanguage.language.code, goingOn) }
            if (answer == null) {
                MyCarLog.w("assistant: Home Assistant didn't answer in ${ANSWER_MS / 1000} s")
                return Reply(AssistantLanguage.answer("Home Assistant didn't answer."), false)
            }
            conversation = answer.conversationId
            lastAt = SystemClock.elapsedRealtime()
            MyCarLog.i("assistant: Home Assistant answered" + if (answer.goOn) ", and asks back" else "")
            Reply(answer.speech.ifBlank { AssistantLanguage.answer("Done.") }, answer.goOn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MyCarLog.w("assistant: Home Assistant didn't answer: ${e.message}")
            Reply(AssistantLanguage.answer("Home Assistant didn't answer."), false)
        }
    }

    /** A question within a few minutes of the last carries on with it ("and tomorrow?"). */
    private const val FOLLOW_ON_MS = 5 * 60_000L

    /** The longest the car waits for Home Assistant's answer. */
    private const val ANSWER_MS = 15_000L
}
