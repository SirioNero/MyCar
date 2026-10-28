package io.github.sirionero.mycar.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.CertCatalog
import io.github.sirionero.mycar.CertProvider
import io.github.sirionero.mycar.CertRotation
import io.github.sirionero.mycar.SessionStatus
import io.github.sirionero.mycar.audio.AudioRoute
import io.github.sirionero.mycar.notify.TestMessage
import io.github.sirionero.mycar.stats.UsageStats
import io.github.sirionero.mycar.car.CarSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Where Settings opens: at the top, or at the section a card on Home sent the driver to. */
enum class SettingsAt { TOP, USAGE_NOTES, CERTIFICATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenRecommendedApps: () -> Unit,
    onOpenWhatsNew: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenCarPreview: () -> Unit,
    onOpenLogs: () -> Unit,
    onReplayTutorial: () -> Unit,
    onReplayIntro: () -> Unit,
    at: SettingsAt = SettingsAt.TOP,
) {
    val scroll = rememberScrollState()
    // Opened from Home's "it worked" card: start at usage notes; from its certificate card: at
    // the certificate, second to last.
    var usageNotesY by remember { mutableIntStateOf(-1) }
    var certificateY by remember { mutableIntStateOf(-1) }
    if (at != SettingsAt.TOP) {
        LaunchedEffect(Unit) {
            val y = snapshotFlow { if (at == SettingsAt.CERTIFICATE) certificateY else usageNotesY }.first { it >= 0 }
            snapshotFlow { scroll.maxValue }.first { it > 0 && it < Int.MAX_VALUE }
            scroll.scrollTo(y)
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(0.dp))
            SettingsSection(
                "Support myCar",
                footer = "myCar is free, with no ads and no tracking. Your support keeps it going.",
            ) { DonateRows() }
            ConnectionSection()
            HelpSection(onOpenHelp, onOpenCarPreview, onOpenLogs)
            CarAudioSection()
            PlanciaSection()
            XonosSection()
            CinemaSection()
            CinemaTvSection()
            VoiceModelSection()
            if (SHOW_RECOMMENDED_APPS) AppsSection(onOpenRecommendedApps)
            // A build without a stats server sends nothing: no switch that would do nothing.
            if (UsageStats.sends) {
                SettingsSection(
                    "Usage notes",
                    Modifier.onGloballyPositioned { usageNotesY = it.positionInParent().y.toInt() },
                    header = USAGE_STATS_INTRO,
                ) { UsageStatsRows() }
            }
            SettingsSection("Updates") { UpdateRow() }
            CertificateSection(Modifier.onGloballyPositioned { certificateY = it.positionInParent().y.toInt() })
            AboutSection(onOpenWhatsNew, onReplayTutorial, onReplayIntro)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Arrow() = Icon(
    Icons.AutoMirrored.Filled.KeyboardArrowRight,
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** What's new, the replays, and where myCar lives outside the app. */
@Composable
private fun AboutSection(onOpenWhatsNew: () -> Unit, onReplayTutorial: () -> Unit, onReplayIntro: () -> Unit) {
    SettingsSection("About") {
        SettingsRow(
            "What's new",
            Modifier.clickable(onClick = onOpenWhatsNew),
            subtitle = "Version ${BuildConfig.VERSION_NAME}",
            icon = Icons.Filled.Star,
            trailing = { Arrow() },
        )
        SettingsDivider()
        SettingsRow(
            "Replay the ${Intro.release} intro",
            Modifier.clickable(onClick = onReplayIntro),
            subtitle = "What's in this release, in a few taps",
            icon = Icons.Filled.Star,
            trailing = { Arrow() },
        )
        SettingsDivider()
        SettingsRow(
            "Initial setup",
            Modifier.clickable(onClick = onReplayTutorial),
            subtitle = "What's done and what's left. Nothing is deleted.",
            icon = Icons.Filled.Refresh,
            trailing = { Arrow() },
        )
        SettingsDivider()
        AboutLinkRows()
    }
}

/** Off, a stock Android Auto app or head unit answers the USB connection instead of myCar. */
@Composable
private fun ConnectionSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AppSettings.myCarEnabled(context)) }
    SettingsSection("Connection") {
        SettingsRow(
            "Use myCar in the car",
            Modifier.toggleable(enabled, role = Role.Switch) {
                enabled = it
                AppSettings.setMyCarEnabled(context, it)
            },
            subtitle = if (enabled) "myCar answers when you plug into a car" else "Off: Android Auto can take the connection instead",
            icon = Icons.Filled.Settings,
            trailing = { Switch(checked = enabled, onCheckedChange = null) },
        )
        SettingsDivider()
        WirelessAutoRow()
    }
}

/** Connection help, and the tools for checking what myCar does. */
@Composable
private fun HelpSection(onOpenHelp: () -> Unit, onOpenCarPreview: () -> Unit, onOpenLogs: () -> Unit) {
    val context = LocalContext.current
    val status by SessionStatus.state.collectAsState()
    val live = status.phase == SessionStatus.Phase.CONNECTING || status.phase == SessionStatus.Phase.PROJECTING
    SettingsSection("Help and tools") {
        SettingsRow(
            "How to connect",
            Modifier.clickable(onClick = onOpenHelp),
            subtitle = "Which cars work, and what's left to set up",
            icon = Icons.Filled.Info,
            trailing = { Arrow() },
        )
        // The preview draws the car screen on the phone, so it waits until no car is using it.
        if (!live) {
            SettingsDivider()
            SettingsRow(
                "Car preview",
                Modifier.clickable(onClick = onOpenCarPreview),
                subtitle = "See the car screen on your phone",
                icon = Icons.Filled.PlayArrow,
                trailing = { Arrow() },
            )
        }
        SettingsDivider()
        SettingsRow(
            "Live logs",
            Modifier.clickable(onClick = onOpenLogs),
            subtitle = "What myCar and the car are saying to each other",
            icon = Icons.AutoMirrored.Filled.List,
            trailing = { Arrow() },
        )
        if (BuildConfig.DEBUG) {
            SettingsDivider()
            SettingsRow(
                "Send a test message",
                Modifier.clickable { TestMessage.post(context) },
                subtitle = "Debug builds only. Posts a message notification to read on the car",
                icon = Icons.AutoMirrored.Filled.Send,
            )
        }
    }
}

/** How music reaches the car: Automatic, by Bluetooth as usual, or through myCar's own link. Same choice as setup. */
@Composable
private fun CarAudioSection() {
    val route by CarSettings.audioRoute.collectAsState()
    SettingsSection(
        "Car audio",
        footer = if (route != AudioRoute.BLUETOOTH) {
            "Calls go over Bluetooth either way. Run the command in How to connect once, and myCar stops asking to share audio."
        } else {
            "Calls go over Bluetooth either way."
        },
    ) {
        AudioRouteRow(
            AudioRoute.AUTO, route, "Automatic (Recommended)",
            "Bluetooth when the car takes the phone's music that way, otherwise through myCar",
        )
        SettingsDivider()
        AudioRouteRow(
            AudioRoute.BLUETOOTH, route, "Bluetooth",
            "No prompt each drive, and your map app's voice reaches the car. Some cars take only calls over it",
        )
        SettingsDivider()
        AudioRouteRow(
            AudioRoute.CAR_LINK, route, "Through myCar",
            "Over the cable or Wi-Fi, as Android Auto does. Android asks to share audio once each drive",
        )
    }
}

@Composable
private fun AudioRouteRow(value: AudioRoute, route: AudioRoute, title: String, subtitle: String) {
    SettingsRow(
        title,
        Modifier.selectable(route == value, role = Role.RadioButton) { CarSettings.setAudioRoute(value) },
        subtitle = subtitle,
        trailing = { RadioButton(selected = route == value, onClick = null) },
    )
}

@Composable
private fun AppsSection(onOpenRecommendedApps: () -> Unit) {
    SettingsSection("Apps") {
        SettingsRow(
            "Recommended apps",
            Modifier.clickable(onClick = onOpenRecommendedApps),
            icon = Icons.Filled.Star,
            trailing = { Arrow() },
        )
    }
}

@Composable
private fun CertificateSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var refresh by remember { mutableIntStateOf(0) }
    // The cached summary shows at once; the read still runs, in case the cert changed since.
    val summary by produceState(CertSummary.cached, refresh) {
        value = withContext(Dispatchers.Default) { CertSummary.read(context) }
    }

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var detailsOpen by remember { mutableStateOf(false) }
    var removeAsk by remember { mutableStateOf<CertProvider.Kind?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingUri = uri
    }

    SettingsSection("Certificate", modifier, footer = notice ?: "Changes take effect on the next connection.") {
        val s = summary
        val loaded = s != null && s.kind != CertProvider.Kind.SELF_SIGNED
        val hasDetails = s?.subject != null || s?.issuer != null
        SettingsRow(
            s?.headline ?: "Checking…",
            if (hasDetails) Modifier.clickable { detailsOpen = !detailsOpen } else Modifier,
            subtitle = s?.validUntil?.let { "Valid until $it" },
            icon = if (s == null || loaded) Icons.Filled.Lock else Icons.Filled.Warning,
            tint = if (s == null || loaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            trailing = if (hasDetails) {
                {
                    Icon(
                        if (detailsOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (detailsOpen) "Hide details" else "Show details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else null,
        )
        AnimatedVisibility(detailsOpen && hasDetails) {
            Column(
                Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                s?.subject?.let { Detail("Subject", it) }
                s?.issuer?.let { Detail("Issuer", it) }
            }
        }
        // The public certificates: the recommended one and the spare, picked here by hand.
        val present = remember(refresh) { CertCatalog.present(context) }
        CertCatalog.entries.forEachIndexed { index, entry ->
            val inUse = s?.kind == CertProvider.Kind.DOWNLOADED && s?.catalogId == entry.id
            val stored = entry.id in present
            SettingsDivider()
            SettingsRow(
                entry.label + if (index == 0) " · Recommended" else " · Spare",
                Modifier.selectable(inUse, enabled = !downloading, role = Role.RadioButton) {
                    if (inUse) return@selectable
                    downloading = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                if (!stored) CertCatalog.downloadAll(context)
                                check(entry.id in CertCatalog.present(context)) { "it couldn't be downloaded" }
                                CertRotation.choose(context, entry.id)
                            }
                        }
                        downloading = false
                        notice = result.fold(
                            onSuccess = {
                                // An imported certificate comes before the downloaded ones.
                                if (s?.kind == CertProvider.Kind.IMPORTED) "${entry.label} is ready, but your own certificate comes first: remove it to use ${entry.label}."
                                else "${entry.label} is in use from the next connection."
                            },
                            onFailure = { "Couldn't get ${entry.label}: check your internet connection, then try again." },
                        )
                        refresh++
                    }
                },
                subtitle = (if (index == 0) "Works in most cars tried so far. From the aasdk project." else "Valid until 2044, for cars that turn down the first one. From the Headunit Revived project.") +
                    when {
                        inUse -> " In use."
                        stored -> " Downloaded."
                        downloading -> ""
                        else -> " Not downloaded yet: tap to get it."
                    },
                icon = Icons.Filled.Refresh,
                trailing = { RadioButton(selected = inUse, onClick = null) },
            )
        }
        SettingsDivider()
        SettingsRow(
            "Import my own file",
            Modifier.clickable { picker.launch(arrayOf("*/*")) },
            subtitle = "A .p12 file with its private key, from wherever you choose",
            icon = Icons.Filled.Add,
        )
        val removable = when (s?.kind) {
            CertProvider.Kind.IMPORTED -> "Remove imported certificate"
            CertProvider.Kind.DOWNLOADED -> "Remove downloaded certificates"
            else -> null
        }
        if (removable != null) {
            SettingsDivider()
            SettingsRow(
                removable,
                Modifier.clickable { removeAsk = s?.kind },
                icon = Icons.Filled.Delete,
                tint = MaterialTheme.colorScheme.error,
                titleColor = MaterialTheme.colorScheme.error,
            )
        }
    }

    removeAsk?.let { kind ->
        val imported = kind == CertProvider.Kind.IMPORTED
        ConfirmDialog(
            title = if (imported) "Remove the imported certificate?" else "Remove the downloaded certificates?",
            text = if (imported) {
                "myCar goes back to the downloaded certificates, if it has them. Without one, it can't connect to a car."
            } else {
                "myCar can't connect to a car until it downloads them again, which needs the internet."
            },
            confirm = "Remove",
            onConfirm = {
                if (imported) CertProvider.removeImported(context) else CertProvider.removeDownloaded(context)
                // What Home's card asked for: done, so the card goes.
                if (imported && io.github.sirionero.mycar.CertRotation.notice.value.kind ==
                    io.github.sirionero.mycar.CertRotationPolicy.Notice.OWN_REFUSED
                ) {
                    io.github.sirionero.mycar.CertRotation.dismiss(context)
                }
                notice = if (imported) "Removed the imported certificate." else "Removed the downloaded certificates."
                refresh++
            },
            onDismiss = { removeAsk = null },
        )
    }

    pendingUri?.let { uri ->
        PasswordDialog(
            onDismiss = { pendingUri = null },
            onConfirm = { password, reportError ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { CertProvider.importFrom(context, uri, password) }
                    }
                    result.onSuccess {
                        pendingUri = null
                        notice = "Certificate imported."
                        refresh++
                    }.onFailure {
                        reportError("Could not import: wrong password, or not a PKCS#12 file with a private key.")
                    }
                }
            },
        )
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String, (String) -> Unit) -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Certificate password") },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                supportingText = { Text(error ?: "Leave empty if the file has none.") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) { error = it } }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
