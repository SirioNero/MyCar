package io.github.sirionero.mycar.car

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.call.CallUiState
import io.github.sirionero.mycar.call.CarCalls
import io.github.sirionero.mycar.speech.SpeechEngine
import io.github.sirionero.mycar.speech.SpeechModels
import io.github.sirionero.mycar.speech.VoiceFocus
import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguages
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
        /** Home Assistant is being asked (AssistantBrain): the overlay says so while it waits. */
        val brain: Boolean = false,
        /** A line over "Listening…": why the assistant listens in English though myCar is in another language. */
        val hint: String = "",
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
    /** The language the voice was last set to, so it's set again only when that changes. */
    private var voiceIn: VoiceLanguage? = null

    /**
     * The phone's language for the assistant, once a model hears it (VoiceLanguages.effective):
     * what it listens for, what it answers in, and the voice it answers with. Checked at every
     * press and answer, so a model downloaded mid-drive counts at once.
     */
    internal fun syncLanguage() {
        val context = appContext ?: return
        val language = VoiceLanguages.effective(context, CarSettings.systemSpeech.value)
        AssistantLanguage.language = language
        val engine = tts ?: return
        if (!ttsReady || voiceIn == language) return
        voiceIn = language
        val locale = VoiceLanguages.speakingLocale(language)
        val result = runCatching { engine.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (result < TextToSpeech.LANG_AVAILABLE) MyCarLog.w("assistant: the phone's speech engine has no voice for $locale ($result)")
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) mainHandler.post(::syncLanguage)
        }
        syncLanguage()
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
        if (_state.value.phase == Phase.LISTENING) return
        // Still working on the last words (the model, Home Assistant): the microphone starts over
        // instead of doing nothing, which looked like the assistant had hung.
        if (_state.value.phase == Phase.THINKING) {
            MyCarLog.i("assistant: asked again while still working on the last words - starting over")
            releaseRecognizer()
            _pendingCommand.value = null
            _state.value = State(Phase.IDLE)
        }
        syncLanguage()
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
            _state.value = State(Phase.ERROR, reply = AssistantLanguage.answer("Needs the microphone permission."), missingPermission = true)
            return
        }
        // Android hands every app but the dialer a silent microphone during a call.
        if (CarCalls.state.value is CallUiState.Active) {
            _state.value = State(Phase.ERROR, reply = AssistantLanguage.answer("Voice commands work once the call ends."))
            return
        }
        tts?.stop()
        _state.value = State(Phase.LISTENING, hint = languageHint(context))
        watch()
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

    /** Closes [shown], an error left up a while, unless something else has the overlay since. */
    fun dismissError(shown: State) {
        if (_state.value === shown && shown.phase == Phase.ERROR) _state.value = State(Phase.IDLE)
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
    fun reply(english: String) {
        _pendingCommand.value = null
        syncLanguage()
        // AssistantCommands answers in English; the driver hears and reads it in their language.
        speakReply(AssistantLanguage.answer(english))
    }

    /**
     * Home Assistant's answer to [heard] (AssistantBrain), already in the driver's language. Dropped
     * when the driver has moved on meanwhile - closed the assistant, or asked again - as it can take
     * a few seconds. When it asks something back the microphone doesn't open by itself: the driver
     * asked for the assistant to go away after an answer, and answers with the microphone.
     */
    fun replyAsSaid(heard: String, text: String) {
        if (_pendingCommand.value != heard || _state.value.phase != Phase.THINKING) return
        _pendingCommand.value = null
        speakReply(text)
    }

    /** [heard] has gone to Home Assistant (AssistantBrain): the overlay says who it's waiting for. */
    fun askingHome(heard: String) {
        if (_pendingCommand.value == heard && _state.value.phase == Phase.THINKING) _state.value = _state.value.copy(brain = true)
    }

    /** As if the driver had said [text]: for the car preview, where no speech model runs. */
    internal fun heard(text: String) {
        _state.value = State(Phase.THINKING, heard = text)
        _pendingCommand.value = text
        watch()
    }

    /** Shows and says [text], then [then] once it has been said to the end. */
    private fun speakReply(text: String, then: () -> Unit = {}) {
        _state.value = _state.value.copy(phase = Phase.SPEAKING, reply = text, brain = false)
        // Long enough for any voice to say it, so only an engine that never says it's done hits it:
        // longer with the phone's speech rate turned down.
        speakLimitMs = ((SPEAK_BASE_MS + text.length * SPEAK_PER_CHAR_MS) * slowness()).toLong().coerceAtMost(SPEAK_MAX_MS)

        val engine = tts
        if (text.isBlank()) {
            _state.value = _state.value.copy(phase = Phase.IDLE)
            return
        }
        val mine = ++replies
        if (engine == null || !ttsReady) {
            // No voice to say it with (a phone without a speech engine, or one still starting): on
            // screen long enough to read. It used to go at once, and the answer was never seen.
            mainHandler.postDelayed({
                if (mine == replies && _state.value.phase == Phase.SPEAKING) _state.value = _state.value.copy(phase = Phase.IDLE)
            }, SILENT_REPLY_MS)
            return
        }
        appContext?.let(VoiceFocus::acquire)
        speaking = speak(engine, text) { stopped ->
            VoiceFocus.release()
            // Still this answer on screen: done speaking, or cut short by a message read aloud. Not
            // when the microphone or Cancel stopped it, which have moved the overlay on already.
            if (mine == replies && _state.value.phase == Phase.SPEAKING) {
                _state.value = _state.value.copy(phase = Phase.IDLE)
                if (!stopped) mainHandler.post(then)
            }
        }
    }

    /** The answer being said, so the [watchdog] can end it. */
    private var speaking: String? = null
    private var speakLimitMs = SPEAK_MAX_MS
    /** When the speech engine last said it reached a word: still talking, however long the answer. */
    @Volatile private var lastWordAt = 0L

    /** How much slower than normal the phone speaks (Android's speech rate, 100 = normal). */
    private fun slowness(): Float {
        val rate = runCatching {
            android.provider.Settings.Secure.getInt(appContext?.contentResolver, android.provider.Settings.Secure.TTS_DEFAULT_RATE, 100)
        }.getOrDefault(100)
        return if (rate in 1 until 100) 100f / rate else 1f
    }

    /**
     * Never left up by itself: whatever stalls - the final model loading, a phrase the recognizer
     * never finishes, Home Assistant, a speech engine that never says it's done - the overlay goes
     * after a while and the microphone works again. It used to sit on "…" with the microphone
     * deaf, which the driver took for a hang; asked for it to go away by itself instead.
     */
    private val watchdog = object : Runnable {
        private var seen: State? = null
        private var since = 0L

        override fun run() {
            val now = SystemClock.uptimeMillis()
            val s = _state.value
            if (s.phase == Phase.IDLE || s.phase == Phase.ERROR) {
                seen = null
                return
            }
            val last = seen
            if (last == null || last.phase != s.phase || last.ready != s.ready || last.brain != s.brain) {
                seen = s
                since = now
            }
            val limit = when (s.phase) {
                Phase.LISTENING -> if (s.ready) LISTEN_LIMIT_MS else OPEN_LIMIT_MS
                Phase.THINKING -> THINK_LIMIT_MS
                else -> speakLimitMs
            }
            val stillTalking = s.phase == Phase.SPEAKING && now - lastWordAt < STILL_TALKING_MS
            if (now - since < limit || stillTalking) {
                mainHandler.postDelayed(this, WATCH_MS)
                return
            }
            seen = null
            MyCarLog.w("assistant: still ${s.phase.name.lowercase()}${if (s.brain) " for Home Assistant" else ""} after ${limit / 1000} s - closing")
            giveUp(s.phase)
        }
    }

    private fun watch() {
        mainHandler.removeCallbacks(watchdog)
        mainHandler.post(watchdog)
    }

    private fun giveUp(phase: Phase) {
        releaseRecognizer()
        _pendingCommand.value = null
        if (phase == Phase.SPEAKING) {
            // The engine never said it finished: ended here, or the music it paused stayed paused.
            speaking?.let { end(it, stopped = true) }
            tts?.stop()
            _state.value = State(Phase.IDLE)
            return
        }
        tts?.stop()
        val why = if (phase == Phase.LISTENING && !_state.value.ready) "The microphone isn't available right now." else "Speech recognition failed."
        _state.value = State(Phase.ERROR, reply = AssistantLanguage.answer(why))
    }

    /**
     * myCar is in a language its downloaded models don't hear: the assistant listens in English
     * (VoiceLanguages.effective), and Italian said to it came out as nonsense with no word why.
     * Said in myCar's own language, with the model to download.
     */
    private fun languageHint(context: Context): String {
        val phone = VoiceLanguages.phone()
        if (phone == VoiceLanguage.EN || AssistantLanguage.language != VoiceLanguage.EN || CarSettings.systemSpeech.value) return ""
        val model = SpeechModels.recommended(phone).last()
        MyCarLog.w("assistant: no model hears ${phone.code} - listening in English")
        return Words.get(
            R.string.assistant_needs_model,
            phone.locale.getDisplayLanguage(java.util.Locale.getDefault()),
            Words.get(model.title),
        )
    }

    private var replies = 0

    /**
     * Speaks [text] for someone other than the assistant (a message read aloud), then runs
     * [onDone] on the main thread - straight away if speech isn't available. [text] is said as it
     * is: what the assistant itself says in it is already in the driver's language (VoiceReply).
     */
    fun say(text: String, onDone: () -> Unit) {
        syncLanguage()
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
        // Engines that tell each word as they say it keep the watchdog off a long answer.
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            lastWordAt = SystemClock.uptimeMillis()
        }
    }

    private fun end(utteranceId: String?, stopped: Boolean) {
        endings.remove(utteranceId ?: return)?.invoke(stopped)
    }

    /**
     * Speaks [text], then calls [ending] once. A [TextToSpeech.speak] that fails (the speech
     * engine went away mid-drive) sends no callback at all: it ends at once, or the voice focus
     * stayed taken and the music paused for the rest of the drive.
     */
    private fun speak(engine: TextToSpeech, text: String, ending: (stopped: Boolean) -> Unit): String {
        val id = "mycar-${++utterances}"
        endings[id] = ending
        engine.setOnUtteranceProgressListener(progress)
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            MyCarLog.w("assistant: speech engine refused to speak")
            end(id, stopped = false)
        }
        return id
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
        syncLanguage()
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
        // Every error is shown and said in the driver's language.
        val failed: (String) -> Unit = { onError(AssistantLanguage.answer(it)) }
        val context = appContext ?: return failed("Speech isn't ready yet.")
        if (CarSettings.systemSpeech.value && SpeechRecognizer.isRecognitionAvailable(context)) {
            MyCarLog.i("assistant: listening with the phone's recognizer in ${AssistantLanguage.language.code}")
            return systemListen(context, onListening, onPartial, onHeard, onResult, failed)
        }
        val final = SpeechModels.finalFor(context, AssistantLanguage.language)
        MyCarLog.i("assistant: listening in ${AssistantLanguage.language.code}, final model ${final?.label ?: "none"}")
        SpeechEngine.listen(
            context,
            language = AssistantLanguage.language,
            cue = cue,
            onSpeaking = onSpeaking,
            onPartial = onPartial,
            onListening = onListening,
            onHeard = onHeard,
            onResult = onResult,
            onError = failed,
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceLanguages.speakingLocale(AssistantLanguage.language).toLanguageTag())
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

/** An answer with no voice to say it stays on screen this long. */
private const val SILENT_REPLY_MS = 5_000L

/** How often the overlay is checked for being stuck. */
private const val WATCH_MS = 1_000L
/** From the press to the microphone open: the cue, the car's microphone, the live model loading. */
private const val OPEN_LIMIT_MS = 10_000L
/** Listening: the recognizer ends a phrase at 15 s; a little more for the car's microphone to stop. */
private const val LISTEN_LIMIT_MS = 25_000L
/** Working out the words, or waiting for Home Assistant (which gets 15 s of its own). */
private const val THINK_LIMIT_MS = 20_000L
/** Saying an answer: this, and a little more per letter. */
private const val SPEAK_BASE_MS = 8_000L
private const val SPEAK_PER_CHAR_MS = 120L
private const val SPEAK_MAX_MS = 120_000L
/** A word said this recently: the engine is still talking. */
private const val STILL_TALKING_MS = 3_000L
