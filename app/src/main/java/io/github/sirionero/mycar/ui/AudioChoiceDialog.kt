package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.R

/**
 * Shown once, the first time the driver turns down Android's screen-sharing dialog: that dialog
 * is the price of sending music over USB, and most people who refuse it would rather not see it
 * again. Offers the two ways out: Bluetooth, or the one-time adb command.
 */
@Composable
fun AudioChoiceDialog(onUseBluetooth: () -> Unit, onKeepAsking: () -> Unit) {
    var showCommand by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onKeepAsking,
        title = { Text(stringResource(R.string.settings_audio_choice_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.settings_audio_choice_text),
                )
                if (showCommand) {
                    Text(
                        stringResource(R.string.settings_audio_choice_command),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CopyableCommand("adb shell appops set ${BuildConfig.APPLICATION_ID} PROJECT_MEDIA allow")
                } else {
                    TextButton(onClick = { showCommand = true }) { Text(stringResource(R.string.settings_audio_choice_never_ask)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onUseBluetooth) { Text(stringResource(R.string.settings_audio_choice_bluetooth)) } },
        dismissButton = { TextButton(onClick = onKeepAsking) { Text(stringResource(R.string.settings_audio_choice_keep)) } },
    )
}

/**
 * Android lets an app capture other apps' audio only while it holds the microphone permission.
 * Once the driver has refused that twice, Android stops asking, and music silently stays on the
 * phone; this says why and where to turn it back on.
 */
@Composable
fun MicBlockedDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_mic_blocked_title)) },
        text = {
            Text(
                stringResource(R.string.settings_mic_blocked_text),
            )
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_mic_blocked_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_not_now)) } },
    )
}
