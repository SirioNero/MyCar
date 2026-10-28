package io.github.sirionero.mycar.ui

import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.car.CarSettings
import io.github.sirionero.mycar.audio.AudioRoute
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * One step: a sentence that says what to do, and optionally how.
 *
 * [command] is for a step that cannot be done on the phone; it is shown as something to copy
 * rather than to read, because a mistyped package name fails silently. [onClick] is for a step
 * that can, in one tap, in which case it is shown as a button instead.
 */
private class Step(
    val action: String,
    val detail: String? = null,
    val command: String? = null,
    val buttonLabel: String? = null,
    val onClick: (() -> Unit)? = null,
)

private fun inTheCar(onRequestCallScreening: () -> Unit) = listOf(
    Step(
        "Plug in, or connect wirelessly.",
        "myCar opens by itself when the car connects. For wireless, pair the phone with the " +
            "car first, then tap Connect wirelessly on Home.",
    ),
    Step(
        "Pair over Bluetooth for calls.",
        "Calls play through the car once your phone and the car are paired. myCar offers to " +
            "pair the first time you plug in.",
    ),
    Step(
        "Turn down unknown callers before they ring.",
        "Set myCar as your caller ID and spam app. Without it, the car still shows every call, " +
            "and you answer or decline it there.",
        buttonLabel = "Set up",
        onClick = onRequestCallScreening,
    ),
)

private val USB_AUDIO = listOf(
    Step(
        "Stop the audio prompt each drive.",
        "Connect the phone to a computer with USB debugging on, and run this once. Skip it and " +
            "you tap Start each time you set off.",
        command = "adb shell appops set ${BuildConfig.APPLICATION_ID} PROJECT_MEDIA allow",
    ),
    Step(
        "Keep notifications readable.",
        "Android hides what notifications say while it shares audio. Run this too, and myCar " +
            "lifts that only while music goes to the car.",
        command = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS",
    ),
)

/** Which cars work, and the setup the one-time guide walks through, to reread at any time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit, onRequestCallScreening: () -> Unit, onOpenSetup: () -> Unit) {
    val car = remember(onRequestCallScreening) { inTheCar(onRequestCallScreening) }
    val route by CarSettings.audioRoute.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("How to connect") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(0.dp))
            // What used to be the steps before the first drive: the setup list itself, as it stands.
            BackToSetupCard(onOpenSetup)
            SettingsSection("Which cars work") {
                Text(
                    "Most cars work. Starting around 2020, some cars added newer security " +
                        "standards that reject myCar's certificate, and myCar can't run in those cars.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
                FilledTonalButton(
                    onClick = { MyCarLinks.open(context, MyCarLinks.CARS) },
                    modifier = Modifier.padding(16.dp),
                ) { Text("See the full list") }
            }
            Steps("In the car", car)
            if (route != AudioRoute.BLUETOOTH) Steps("Sound through myCar", USB_AUDIO)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A titled card of numbered steps. */
@Composable
private fun Steps(title: String, steps: List<Step>) {
    SettingsSection(title) {
        steps.forEachIndexed { index, step ->
            if (index > 0) SettingsDivider()
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(step.action, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    step.detail?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    step.command?.let { CopyableCommand(it) }
                    if (step.onClick != null) {
                        FilledTonalButton(onClick = step.onClick, modifier = Modifier.padding(top = 4.dp)) {
                            Text(step.buttonLabel ?: "Open")
                        }
                    }
                }
            }
        }
    }
}
