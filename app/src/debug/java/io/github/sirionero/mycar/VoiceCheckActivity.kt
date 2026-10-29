package io.github.sirionero.mycar

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import io.github.sirionero.mycar.car.AssistantLanguage
import io.github.sirionero.mycar.speech.SpeechModels
import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguages
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Debug-only: what the Car preview workflow can check of the voice without running it (the speech
 * library is arm64 only, the emulator x86_64). Every model file where SpeechModels downloads it,
 * with its size; an answer in each language; and which languages the phone's speech engine has a
 * voice for. Writes "voice check: …" lines to the log, then "voice check: done".
 *
 *   adb shell am start -n io.github.sirionero.mycar.dev/io.github.sirionero.mycar.VoiceCheckActivity
 */
class VoiceCheckActivity : ComponentActivity() {

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyCarLog.init(this)
        thread(name = "voice-check") {
            for (model in SpeechModels.Model.entries) for (name in model.files) {
                MyCarLog.i("voice check: ${model.label} $name: ${sizeOf(model.fileUrl(name))}")
            }
            for (l in VoiceLanguage.entries) {
                MyCarLog.i("voice check: ${l.code}: ${AssistantLanguage.answer("Around 14:30, in 1 hour 5 minutes.", l)}")
            }
            runOnUiThread(::voices)
        }
    }

    /** The file's size from the first byte's answer, as the download would find it. */
    private fun sizeOf(url: String): String = runCatching {
        (URL(url).openConnection() as HttpURLConnection).run {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Range", "bytes=0-0")
            val code = responseCode
            val total = getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull()
            disconnect()
            if (code == 206 && total != null) "${"%.1f".format(total / 1_048_576.0)} MB" else "the server answered $code"
        }
    }.getOrElse { "failed: ${it.message}" }

    private fun voices() {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) {
                MyCarLog.i("voice check: no speech engine on this phone ($status)")
            } else {
                for (l in VoiceLanguage.entries) {
                    val locale = VoiceLanguages.speakingLocale(l)
                    MyCarLog.i("voice check: voice for $locale: ${tts?.isLanguageAvailable(locale)}")
                }
            }
            MyCarLog.i("voice check: done")
            tts?.shutdown()
        }
    }
}
