package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.speech.SpeechModels
import io.github.sirionero.mycar.speech.SpeechModels.Model
import io.github.sirionero.mycar.speech.SpeechModels.State

/**
 * Which speech model the voice assistant listens with, and downloading it. Each is fetched once
 * from sherpa-onnx's author ([SpeechModels] says where); nothing is sent anywhere while listening.
 */
@Composable
internal fun VoiceModelSection() {
    val context = LocalContext.current
    remember { SpeechModels.init(context) }
    val selected by SpeechModels.selected.collectAsState()
    val final by SpeechModels.final.collectAsState()
    VoiceLanguageNote()
    SettingsSection(title = stringResource(R.string.voice_model_live_title)) {
        Text(
            stringResource(R.string.voice_model_live_about, SpeechModels.recommended().sumOf { it.downloadMb }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Model.entries.filter { it.role == SpeechModels.Role.LIVE }.forEachIndexed { i, model ->
            if (i > 0) SettingsDivider()
            ModelRow(model, model == selected)
        }
    }
    SettingsSection(title = stringResource(R.string.voice_model_final_title)) {
        Text(
            stringResource(R.string.voice_model_final_about),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        val context = LocalContext.current
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(final == null, role = Role.RadioButton) { SpeechModels.selectFinal(context, null) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(final == null, onClick = null, modifier = Modifier.padding(8.dp))
            Column {
                Text(stringResource(R.string.voice_model_none), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.voice_model_none_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Model.entries.filter { it.role == SpeechModels.Role.FINAL }.forEach { model ->
            SettingsDivider()
            ModelRow(model, model == final)
        }
    }
}

@Composable
private fun ModelRow(model: Model, chosen: Boolean) {
    val context = LocalContext.current
    // Tens of megabytes to download again, and the car's assistant without a voice meanwhile if
    // it's the one in use: asked first.
    var deleteAsk by remember { mutableStateOf(false) }
    if (deleteAsk) {
        ConfirmDialog(
            title = stringResource(R.string.voice_model_delete_title, stringResource(model.title)),
            text = if (chosen) {
                stringResource(R.string.voice_model_delete_in_use, model.downloadMb)
            } else {
                stringResource(R.string.voice_model_delete_again, model.downloadMb)
            },
            confirm = stringResource(R.string.common_delete),
            onConfirm = { SpeechModels.delete(context, model) },
            onDismiss = { deleteAsk = false },
        )
    }
    val state by SpeechModels.state(model).collectAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .selectable(chosen, role = Role.RadioButton) { SpeechModels.select(context, model) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(chosen, onClick = null, modifier = Modifier.padding(8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.voice_model_size, stringResource(model.title), model.downloadMb), style = MaterialTheme.typography.titleMedium)
                Text(
                    when (val s = state) {
                        State.Ready -> stringResource(R.string.voice_model_downloaded, stringResource(model.detail))
                        is State.Downloading -> stringResource(R.string.voice_model_downloading, s.percent)
                        is State.Failed -> stringResource(R.string.voice_model_failed, s.reason)
                        State.Missing -> stringResource(model.detail)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (state) {
                    State.Ready -> TextButton(onClick = { deleteAsk = true }) { Text(stringResource(R.string.common_delete)) }
                    is State.Downloading -> TextButton(onClick = { SpeechModels.delete(context, model) }) { Text(stringResource(R.string.common_cancel)) }
                    else -> OutlinedButton(onClick = { SpeechModels.download(context, model) }) { Text(stringResource(R.string.voice_model_download)) }
                }
            }
        }
        (state as? State.Downloading)?.let {
            LinearProgressIndicator(
                progress = { it.percent / 100f },
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 8.dp, top = 4.dp),
            )
        }
    }
}
