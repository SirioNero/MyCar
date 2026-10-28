package io.github.sirionero.mycar.car

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.call.CallUiState
import io.github.sirionero.mycar.call.CarCalls
import io.github.sirionero.mycar.speech.SpeechEngine
import io.github.sirionero.mycar.speech.VoiceFocus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A voice assistant: myCar's own [SpeechEngine] (whisper.cpp) to listen, the system's
 * [TextToSpeech] to answer. Listening used to go through the system's [SpeechRecognizer] alone,
 * but many phones have no recognizer at all (GrapheneOS ships only text-to-speech), so the
 * system one is now just the fallback for when the bundled engine can't run.
 *
 * Listening and speaking live here; deciding what a command *means* is [AssistantCommands], kept
 * separate because it needs a [CarNavigator] and a frame to act on, neither of which this object
 * has - see [pendingCommand].
 */
object CarAssistant {

    enum class Phase { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

    data class State(
        val phase: Phase = Phase.IDLE,
        val heard: String = "",
        val reply: String = "",
        val missingPermission: Boolean = false,
        /** False from the press until the microphone is actually open, so the overlay doesn't claim to listen early. */
        val ready: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Set once a recognition result comes in; [reply] clears it once handled. */
    private val _pendingCommand = MutableStateFlow<String?>(null)
    val pendingCommand: StateFlow<String?> = _pendingCommand

    private var appContext: Context? = null
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun init(context: Context) {
        appContext = context.applicationContext
        if (tts != null) return
        tts = TextToSpeech(appContext) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Starts listening, or does nothing if the assistant is already busy with the last request.
     * [SpeechRecognizer] must be driven from a thread with a Looper, but this is also called from
     * the raw wire-protocol worker thread ([MyCarRunner]'s key-event handling for the head
     * unit's own PTT button) which has none - so this always hops to the main thread itself
     * rather than asking every caller to remember to.
     */
    fun start() {
        if (Looper.myLooper() == Looper.getMainLooper()) startOnMainThread() else mainHandler.post(::startOnMainThread)
    }

    private fun startOnMainThread() {
        val context = appContext ?: return
        if (_state.value.phase == Phase.LISTENING || _state.value.phase == Phase.THINKING) return
        // A message being read or answered gives way: the driver asked for the assistant instead,
        // and the reply left waiting would keep the car's voice key for itself. A reply counting
        // down goes now, as the wheel's voice key sends it: dropping it lost it without a word.
        if (VoiceReply.active) {
            if (VoiceReply.state.value.phase == VoiceReply.Phase.CONFIRMING) VoiceReply.sendNow() else VoiceReply.cancel()
        }
        // The driver may have picked another assistant, which then takes the button instead.
        if (AssistantChoice.startOther(context)) return

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = State(Phase.ERROR, reply = "Needs the microphone permission.", missingPermission = true)
            return
        }
        // Android hands every app but the dialer a silent microphone during a call.
        if (CarCalls.state.value is CallUiState.Active) {
            _state.value = State(Phase.ERROR, reply = "Voice commands work once the call ends.")
            return
        }
        tts?.stop()
        _state.value = State(Phase.LISTENING)
        listenOnce(
            onListening = { _state.value = _state.value.copy(ready = true) },
            onPartial = { text -> _state.value = _state.value.copy(heard = text) },
            onHeard = { _state.value = _state.value.copy(phase = Phase.THINKING) },
            onResult = { text ->
                _state.value = _state.value.copy(phase = Phase.THINKING, heard = text)
                _pendingCommand.value = text
            },
            onError = { message -> _state.value = State(Phase.ERROR, reply = message) },
        )
    }

    /** Closes the overlay from wherever it's showing: mid-listen, mid-think, or after speaking. */
    fun cancel() {
        releaseRecognizer()
        tts?.stop()
        _pendingCommand.value = null
        _state.value = State(Phase.IDLE)
    }

    /**
     * Called once [pendingCommand] has been turned into an answer - from [CarUi], the one place
     * that actually has a [CarNavigator] to have acted on it with.
     */
    fun reply(text: String) {
        _pendingCommand.value = null
        _state.value = _state.value.copy(phase = Phase.SPEAKING, reply = text)

        val engine = tts
        if (engine == null || !ttsReady || text.isBlank()) {
            _state.value = _state.value.copy(phase = Phase.IDLE)
            return
        }
        appContext?.let(VoiceFocus::acquire)
        val mine = ++replies
        speak(engine, text) {
            VoiceFocus.release()
            // Still this answer on screen: done speaking, or cut short by a message read aloud. Not
            // when the microphone or Cancel stopped it, which have moved the overlay on already.
            if (mine == replies && _state.value.phase == Phase.SPEAKING) _state.value = _state.value.copy(phase = Phase.IDLE)
        }
    }

    private var replies = 0

    /**
     * Speaks [text] for someone other than the assistant (a message read aloud), then runs
     * [onDone] on the main thread - straight away if speech isn't available.
     */
    fun say(text: String, onDone: () -> Unit) {
        val engine = tts
        val context = appContext
        if (engine == null || context == null || !ttsReady || text.isBlank()) {
            mainHandler.post(onDone)
            return
        }
        VoiceFocus.acquire(context)
        speak(engine, text) { stopped ->
            VoiceFocus.release()
            // Stopped part way: whoever stopped it has already moved on.
            if (!stopped) mainHandler.post(onDone)
        }
    }

    private var utterances = 0

    /** What to do when each utterance ends, by its id; true when it was stopped part way. */
    private val endings = java.util.concurrent.ConcurrentHashMap<String, (stopped: Boolean) -> Unit>()

    /**
     * One listener for every utterance, which finds its own ending by id. With a listener per
     * utterance, the end of the one cut short by the next went to the next's: the voice focus was
     * given back while it still spoke, and its caller was told it had finished.
     */
    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = end(utteranceId, stopped = false)
        @Deprecated("Deprecated in Java, no replacement on this API level")
        override fun onError(utteranceId: String?) = end(utteranceId, stopped = false)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = end(utteranceId, stopped = true)
    }

    private fun end(utteranceId: String?, stopped: Boolean) {
        endings.remove(utteranceId ?: return)?.invoke(stopped)
    }

    /**
     * Speaks [text], then calls [ending] once. A [TextToSpeech.speak] that fails (the speech
     * engine went away mid-drive) sends no callback at all: it ends at once, or the voice focus
     * stayed taken and the music paused for the rest of the drive.
     */
    private fun speak(engine: TextToSpeech, text: String, ending: (stopped: Boolean) -> Unit) {
        val id = "mycar-${++utterances}"
        endings[id] = ending
        engine.setOnUtteranceProgressListener(progress)
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            MyCarLog.w("assistant: speech engine refused to speak")
            end(id, stopped = false)
        }
    }

    fun stopSpeaking() { tts?.stop() }

    /**
     * Listens once, the same way the assistant does, and hands back what was said without acting
     * on it. Callbacks arrive on the main thread; [onError] gets a sentence fit to show.
     */
    fun dictate(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        cue: Boolean = true,
        onSpeaking: () -> Unit = {},
    ) {
        cancel()
        listenOnce(onPartial, onHeard = {}, onResult = onResult, onError = onError, cue = cue, onSpeaking = onSpeaking)
    }

    /**
     * One phrase, through myCar's own [SpeechEngine], or the phone's recognizer when the driver
     * chose it in settings. Never the phone's on its own: on most phones that's Google's.
     */
    private fun listenOnce(
        onPartial: (String) -> Unit,
        onListening: () -> Unit = {},
        onHeard: () -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        cue: Boolean = true,
        onSpeaking: () -> Unit = {},
    ) {
        val context = appContext ?: return onError("Speech isn't ready yet.")
        if (CarSettings.systemSpeech.value && SpeechRecognizer.isRecognitionAvailable(context)) {
            return systemListen(context, onListening, onPartial, onHeard, onResult, onError)
        }
        SpeechEngine.listen(
            context,
            cue = cue,
            onSpeaking = onSpeaking,
            onPartial = onPartial,
            onListening = onListening,
            onHeard = onHeard,
            onResult = onResult,
            onError = onError,
        )
    }

    private fun systemListen(
        context: Context,
        onListening: () -> Unit,
        onPartial: (String) -> Unit,
        onHeard: () -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = onListening()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() = onHeard()
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onError(error: Int) {
                releaseRecognizer()
                onError(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                releaseRecognizer()
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isBlank()) onError("Didn't catch that.") else onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
            }
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            },
        )
    }

    /**
     * Stops listening, for someone else's sake (a message read, a quick reply). The overlay closes
     * too if it was listening: with nothing left to call back, it stayed on "Listening…" and the
     * microphone and voice key were ignored until its X.
     */
    fun stopListening() {
        releaseRecognizer()
        val phase = _state.value.phase
        if (phase == Phase.LISTENING || phase == Phase.THINKING) {
            _pendingCommand.value = null
            _state.value = State(Phase.IDLE)
        }
    }

    private fun releaseRecognizer() {
        SpeechEngine.cancel()
        recognizer?.let { runCatching { it.cancel() }; it.destroy() }
        recognizer = null
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Needs the microphone permission."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech recognizer is busy."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech recognizer needs a network it doesn't have."
        else -> "Speech recognition failed."
    }

    fun shutdown() {
        releaseRecognizer()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
