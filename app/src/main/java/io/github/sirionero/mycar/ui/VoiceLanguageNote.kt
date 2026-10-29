package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.AssistantLanguage
import io.github.sirionero.mycar.speech.SpeechModels
import io.github.sirionero.mycar.speech.VoiceLanguage
import io.github.sirionero.mycar.speech.VoiceLanguages
import java.util.Locale

/**
 * The language the car's assistant listens and answers in: the phone's, once a model that knows
 * it is on the phone. Nothing to say for a phone in English, or in a language it doesn't speak.
 */
@Composable
internal fun VoiceLanguageNote() {
    val context = LocalContext.current
    val phone = VoiceLanguages.phone()
    if (phone == VoiceLanguage.EN) return
    val name = phone.locale.getDisplayLanguage(Locale.ENGLISH)
    val needed = SpeechModels.recommended(phone).last()
    // Read for its changes: a download that finishes is the moment the language changes.
    val state by SpeechModels.state(needed).collectAsState()
    val final by SpeechModels.final.collectAsState()
    val model = remember(state, final) { SpeechModels.finalFor(context, phone) }
    val example = AssistantLanguage.help(phone)?.firstOrNull()?.first
    SettingsSection(title = "Voice: $name") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (model != null) {
                    "Your phone is in $name, so the car's assistant listens and answers in $name, with ${model.label}." +
                        (example?.let { " Try “$it”." } ?: "")
                } else {
                    "Your phone is in $name. With ${needed.label} (${needed.downloadMb} MB) the car's assistant listens " +
                        "and answers in $name; until then it understands English only."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "It answers with the phone's own text-to-speech voice for $name. If it sounds wrong, pick or download " +
                    "that voice in Android's text-to-speech settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val s = state) {
                is SpeechModels.State.Downloading -> LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                SpeechModels.State.Ready -> Unit
                else -> if (model == null) {
                    Button(onClick = {
                        SpeechModels.download(context, needed)
                        SpeechModels.selectFinal(context, needed)
                    }) { Text("Download ${needed.label}") }
                }
            }
        }
    }
}
