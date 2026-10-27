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
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.BuildConfig

/**
 * Shown once, the first time the driver turns down Android's screen-sharing dialog: that dialog
 * is the price of sending music through myCar, and most people who refuse it would rather not see
 * it again. Offers the ways out: myCar answering it by itself, Bluetooth, or the one-time adb command.
 */
@Composable
fun AudioChoiceDialog(onUseBluetooth: () -> Unit, onKeepAsking: () -> Unit, onLetMyCarAnswer: () -> Unit) {
    var showCommand by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onKeepAsking,
        title = { Text("Stop Android's audio question?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Android treats music sent through myCar like sharing your screen, so it asks every " +
                        "drive. Turn on myCar under Accessibility, once, and myCar answers it for you. Or play " +
                        "over Bluetooth, where Android never asks, if your car takes the phone's music that way.",
                )
                TextButton(onClick = onLetMyCarAnswer) { Text("Let myCar answer it") }
                if (showCommand) {
                    Text(
                        "Or keep it and stop the question for good: plug the phone into a " +
                            "computer with USB debugging on, and run this once.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CopyableCommand("adb shell appops set ${BuildConfig.APPLICATION_ID} PROJECT_MEDIA allow")
                } else {
                    TextButton(onClick = { showCommand = true }) { Text("Never ask, using a computer") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onUseBluetooth) { Text("Use Bluetooth") } },
        dismissButton = { TextButton(onClick = onKeepAsking) { Text("Keep asking") } },
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
        title = { Text("Car audio needs the microphone permission") },
        text = {
            Text(
                "Android lets an app send other apps' sound to the car only if it has the " +
                    "microphone permission. It's turned off for myCar, so music plays on the " +
                    "phone. Turn it on under Permissions, then Microphone.",
            )
        },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open app settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
