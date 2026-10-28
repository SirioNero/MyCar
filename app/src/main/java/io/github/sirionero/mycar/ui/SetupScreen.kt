package io.github.sirionero.mycar.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.CertCatalog
import io.github.sirionero.mycar.CertProvider
import io.github.sirionero.mycar.KEY_MIC_ASKED
import io.github.sirionero.mycar.call.CarCalls
import io.github.sirionero.mycar.car.Prefetch
import io.github.sirionero.mycar.host.AndroidAuto
import io.github.sirionero.mycar.setup.FirstSetup
import io.github.sirionero.mycar.setup.SetupSignals
import io.github.sirionero.mycar.setup.SetupSteps
import io.github.sirionero.mycar.setup.SetupSteps.Id
import io.github.sirionero.mycar.setup.SetupSteps.Status
import io.github.sirionero.mycar.stats.UsageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the setup list was opened from: the first run, or "Take me back to the initial setup". */
enum class SetupEntry { FIRST_RUN, RETURN }

/**
 * Setting up myCar as one list: what's needed to connect, what the drive needs, and the rest,
 * with a tick on everything done. The first run starts here, and "Take me back to the initial
 * setup" comes back to the same list later, with nothing reset or deleted. One big button always
 * does the next thing left; nothing asks by itself.
 */
@Composable
fun SetupScreen(entry: SetupEntry, onDone: () -> Unit, onRequestCallScreening: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signals by remember { mutableStateOf(SetupSignals.read(context)) }
    fun refresh() {
        signals = SetupSignals.read(context)
    }
    // Back from Android's screens (Android Auto, notification access, app info): read it again.
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose {}
    }
    val steps = SetupSteps.compute(signals)
    fun status(id: Id) = steps.first { it.id == id }.status
    val next = SetupSteps.next(steps)

    /** [passOver]: "Done" with calls, map and music still off, which then aren't offered first again. */
    fun finish(passOver: Boolean = false) {
        if (passOver && status(Id.PERMISSIONS) == Status.TODO) FirstSetup.skip(context, Id.PERMISSIONS)
        FirstSetup.finish(context)
        onDone()
    }
    BackHandler { finish() }

    // --- The certificate ---------------------------------------------------------------------
    var certBusy by remember { mutableStateOf(false) }
    var certNote by remember { mutableStateOf<String?>(null) }
    fun getCertificates() {
        if (certBusy) return
        certBusy = true
        certNote = null
        scope.launch {
            val downloads = withContext(Dispatchers.IO) {
                runCatching {
                    CertCatalog.downloadAll(context).also { result ->
                        result.ready.firstOrNull()?.let { CertCatalog.activate(context, it) }
                    }
                }.getOrNull()
            }
            certBusy = false
            val ready = downloads?.ready.orEmpty()
            certNote = when {
                ready.isEmpty() && downloads?.failed?.values?.any { "not the expected certificate" in it } == true ->
                    "A downloaded file wasn't the expected certificate, so myCar left it out. Try again later."
                ready.isEmpty() -> "Couldn't download the certificates. Check your internet connection, then try again."
                ready.size < CertCatalog.entries.size ->
                    "Got ${ready.size} of ${CertCatalog.entries.size}. ${label(ready.first())} is ready; you can get the spare later in Settings."
                else -> null
            }
            refresh()
        }
    }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingUri = uri
    }

    // --- Android Auto and myCar's own switch ---------------------------------------------------
    // Shown as soon as Android Auto's page has been opened: where it can't be turned off (on many
    // phones it's part of Android), coming back finds the way to keep it already in sight.
    var showKeep by rememberSaveable { mutableStateOf(false) }
    fun openAndroidAuto() {
        val pkg = AndroidAuto.installedPackage(context) ?: return
        showKeep = true
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
        }
    }
    fun turnOn() {
        AppSettings.setMyCarEnabled(context, true)
        AppSettings.applyMyCarEnabled(context)
        refresh()
    }

    // --- Calls, map and music: one request for all of it ------------------------------------
    val driveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        FirstSetup.markAsked(context, result.keys)
        // Refused here, the microphone counts as asked: refused again in the car, the phone says why.
        if (result[Manifest.permission.RECORD_AUDIO] == false) AppSettings.putString(context, KEY_MIC_ASKED, "1")
        if (result.values.any { it }) {
            Prefetch.warm(context, force = true)
            CarCalls.start(context)
        }
        refresh()
    }
    fun allowDrive() {
        val missing = SetupSignals.missingDrivePermissions(context)
        if (missing.isEmpty()) refresh() else driveLauncher.launch(missing)
    }
    fun openAppSettings() {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }
    }
    val oneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            BottomBar(
                steps = steps,
                next = next,
                entry = entry,
                busy = certBusy,
                onNext = {
                    when (next?.id) {
                        Id.CERTIFICATE -> getCertificates()
                        Id.MYCAR_ON -> turnOn()
                        Id.ANDROID_AUTO -> openAndroidAuto()
                        Id.PERMISSIONS -> allowDrive()
                        else -> finish()
                    }
                },
                onFinish = { finish(passOver = SetupSteps.leftToConnect(steps).isEmpty()) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Set up myCar",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                if (entry == SetupEntry.FIRST_RUN) "A few taps before your first drive."
                else "What's left to do. Nothing you've set up changes.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "myCar is unofficial: set it up while parked, then keep your eyes on the road.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("To connect")
            StepCard(
                "Car certificate",
                status(Id.CERTIFICATE),
                emphasized = next?.id == Id.CERTIFICATE,
                body = if (status(Id.CERTIFICATE) == Status.DONE) certificateSummary(context)
                else "Cars only talk to phones with a certificate they trust. myCar doesn't include one: it downloads " +
                    "two public ones from open-source projects on GitHub (about 10 KB). If your car turns down the " +
                    "first, myCar uses the other.",
            ) {
                certNote?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                // One under the other: side by side, a narrow phone squeezed the second to nothing.
                if (status(Id.CERTIFICATE) != Status.DONE) {
                    Button(onClick = ::getCertificates, enabled = !certBusy) {
                        Text(if (certBusy) "Getting certificates…" else if (certNote != null) "Try again" else "Get certificates")
                    }
                    TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !certBusy) {
                        Text("Use my own file")
                    }
                }
            }
            if (status(Id.MYCAR_ON) != Status.HIDDEN) {
                StepCard(
                    "Turn myCar on for the car",
                    status(Id.MYCAR_ON),
                    emphasized = next?.id == Id.MYCAR_ON,
                    body = "It's switched off in Settings, so the car can't reach it.",
                ) {
                    Button(onClick = ::turnOn) { Text("Turn on") }
                }
            }
            if (status(Id.ANDROID_AUTO) != Status.HIDDEN) AndroidAutoStep(
                status = status(Id.ANDROID_AUTO),
                emphasized = next?.id == Id.ANDROID_AUTO,
                off = signals.androidAuto == SetupSteps.AndroidAuto.OFF,
                showKeep = showKeep,
                onShowKeep = { showKeep = !showKeep },
                onOpen = ::openAndroidAuto,
                onKeep = {
                    FirstSetup.setKeepAndroidAuto(context, true)
                    refresh()
                },
            )

            SectionLabel("For your drive")
            val permissions = status(Id.PERMISSIONS)
            StepCard(
                "Calls, map and music",
                permissions,
                emphasized = next?.id == Id.PERMISSIONS,
                body = when (permissions) {
                    Status.DONE -> "Allowed."
                    Status.BLOCKED -> "Android won't ask again for ${signals.driveBlocked.joinToString()}. " +
                        "Turn it on in myCar's app info, under Permissions."
                    Status.SKIPPED -> "Skipped. Tap Allow when you're ready."
                    else -> if (signals.driveMissing.size < SetupSignals.driveGroups().size && FirstSetup.asked(context).isNotEmpty()) {
                        "Still off: ${signals.driveMissing.joinToString()}."
                    } else {
                        "Your car shows who's calling and can dial your contacts, the map knows where you are, and " +
                            "music can reach the car without Bluetooth. Android asks a few times: tap Allow each " +
                            "time. myCar only reads, and listens only when you tap the mic."
                    }
                },
            ) {
                when (permissions) {
                    Status.BLOCKED -> Button(onClick = ::openAppSettings) { Text("Open settings") }
                    Status.DONE -> Unit
                    else -> Button(onClick = ::allowDrive) { Text("Allow") }
                }
            }

            MoreSection(
                steps = steps,
                expandedAtStart = entry == SetupEntry.RETURN,
                onMessages = {
                    // Phones without that screen: the app's own settings instead of a crash.
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        .onFailure { openAppSettings() }
                },
                onCalendar = { oneLauncher.launch(Manifest.permission.READ_CALENDAR) },
                onNotifications = { oneLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onScreening = onRequestCallScreening,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    pendingUri?.let { uri ->
        CertificatePasswordDialog(
            onDismiss = { pendingUri = null },
            onConfirm = { password, reportError ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { CertProvider.importFrom(context, uri, password) } }
                    result.onSuccess {
                        pendingUri = null
                        certNote = null
                        refresh()
                    }.onFailure {
                        reportError("Wrong password, or not a .p12 file with a private key.")
                    }
                }
            },
        )
    }
}

private fun label(id: String) = CertCatalog.entry(id)?.label ?: id

/** What the phone holds, without unlocking it (that takes a second). */
private fun certificateSummary(context: android.content.Context): String {
    if (CertProvider.hasImported(context)) return "Ready: your own certificate."
    val present = CertCatalog.present(context)
    return when {
        present.size >= 2 -> "Ready: ${label(present[0])}. Spare: ${label(present[1])}."
        present.size == 1 -> "Ready: ${label(present[0])}."
        else -> "Ready."
    }
}

@Composable
private fun AndroidAutoStep(
    status: Status,
    emphasized: Boolean,
    off: Boolean,
    showKeep: Boolean,
    onShowKeep: () -> Unit,
    onOpen: () -> Unit,
    onKeep: () -> Unit,
) {
    StepCard(
        "Turn off Android Auto",
        status,
        emphasized = emphasized,
        body = when {
            off -> "Android Auto is off."
            status == Status.DONE -> "You'll choose myCar when you plug in."
            else -> "Android Auto is on, and it can take the car's connection before myCar does. On the screen this " +
                "opens, tap Disable, then OK, then come back here."
        },
    ) {
        if (status != Status.DONE) {
            Button(onClick = onOpen) { Text("Open Android Auto") }
            TextButton(onClick = onShowKeep) { Text("No Disable button?") }
            if (showKeep) {
                Text(
                    "Then keep it. When you plug in, Android asks which app to open: choose myCar and tap Always. " +
                        "If Android Auto opens by itself, it was set to always open: on its app info, tap Open by " +
                        "default, then Clear defaults.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onKeep) { Text("Keep Android Auto") }
            }
        }
    }
}

@Composable
private fun MoreSection(
    steps: List<SetupSteps.Step>,
    expandedAtStart: Boolean,
    onMessages: () -> Unit,
    onCalendar: () -> Unit,
    onNotifications: () -> Unit,
    onScreening: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(expandedAtStart) }
    fun status(id: Id) = steps.first { it.id == id }.status
    val left = SetupSteps.moreLeft(steps)
    Row(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (left > 0) "More ($left)" else "More",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Show less" else "Show more",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        "Everything here also works later.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!expanded) return
    StepCard("Messages", status(Id.MESSAGES), body = "The car shows your messages, reads them aloud and lets you reply. Turn myCar on in the list this opens.") {
        if (status(Id.MESSAGES) != Status.DONE) Button(onClick = onMessages) { Text("Open notification access") }
    }
    StepCard("Calendar", status(Id.CALENDAR), body = "The car screen shows your next event. myCar only reads your calendar.") {
        if (status(Id.CALENDAR) != Status.DONE) Button(onClick = onCalendar) { Text("Allow") }
    }
    StepCard("Notifications", status(Id.NOTIFICATIONS), body = "While myCar is connected, it shows a notification with a button to stop.") {
        if (status(Id.NOTIFICATIONS) != Status.DONE) Button(onClick = onNotifications) { Text("Allow") }
    }
    StepCard(
        "Screen unknown callers",
        status(Id.SCREENING),
        body = "With the car connected, calls from numbers outside your contacts wait for you to answer them from the car.",
    ) {
        if (status(Id.SCREENING) != Status.DONE) Button(onClick = onScreening) { Text("Set up") }
    }
    StepCard("Car audio", Status.DONE, body = "How music reaches the car. Calls go over Bluetooth either way.") {
        AudioChoice()
    }
    if (UsageStats.sends) {
        StepCard("Help other drivers", if (UsageStats.asked(context)) Status.DONE else Status.TODO, body = USAGE_STATS_INTRO.text) {
            UsageStatsChoices()
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/** One step: a tick or what's left, its words, and what to tap. The next one has a frame. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepCard(
    title: String,
    status: Status,
    body: String,
    emphasized: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val requester = remember { BringIntoViewRequester() }
    // The step the big button does next comes into sight, so its words are read before it's done:
    // when it becomes the next one, not on opening the list, which starts at its title.
    val emphasizedAtStart = remember { emphasized }
    if (emphasized && !emphasizedAtStart) LaunchedEffect(Unit) { requester.bringIntoView() }
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        border = if (emphasized) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StepMark(status)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                content()
            }
        }
    }
}

@Composable
private fun StepMark(status: Status) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        Status.DONE -> Surface(color = scheme.primary, shape = CircleShape, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = "Done", tint = scheme.onPrimary, modifier = Modifier.size(18.dp))
            }
        }
        Status.BLOCKED -> Icon(Icons.Filled.Warning, contentDescription = "Blocked", tint = scheme.error, modifier = Modifier.size(28.dp))
        else -> Surface(
            color = scheme.surfaceContainerHigh,
            shape = CircleShape,
            border = BorderStroke(2.dp, if (status == Status.SKIPPED) scheme.outlineVariant else scheme.outline),
            modifier = Modifier.size(28.dp),
        ) {}
    }
}

@Composable
private fun BottomBar(
    steps: List<SetupSteps.Step>,
    next: SetupSteps.Step?,
    entry: SetupEntry,
    busy: Boolean,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    val leftToConnect = SetupSteps.leftToConnect(steps).size
    val (line, action) = when {
        leftToConnect > 0 -> (if (leftToConnect == 1) "1 step left before your first drive." else "$leftToConnect steps left before your first drive.") to
            when (next?.id) {
                Id.CERTIFICATE -> if (busy) "Getting certificates…" else "Get certificates"
                Id.MYCAR_ON -> "Turn myCar on"
                else -> "Open Android Auto"
            }
        next?.id == Id.PERMISSIONS -> "You're ready to connect." to "Allow calls, map and music"
        else -> "You're ready. Plug into the car; if Android asks, choose myCar and tap Always." to "Done"
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(line, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onNext, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(action) }
            // Without the big button's step done: leave it for later, it stays on Home.
            if (next != null) {
                TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(if (leftToConnect > 0) (if (entry == SetupEntry.FIRST_RUN) "Finish later" else "Close") else "Done")
                }
            }
        }
    }
}

@Composable
private fun CertificatePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, (String) -> Unit) -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Certificate password") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                supportingText = { Text(error ?: "Leave empty if the file has none.") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) { error = it } }) { Text("Use it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
