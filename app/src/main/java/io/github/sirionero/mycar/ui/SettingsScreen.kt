package io.github.sirionero.mycar.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.car.PhoneTyping
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.CertCatalog
import io.github.sirionero.mycar.CertProvider
import io.github.sirionero.mycar.CertRotation
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.SessionStatus
import io.github.sirionero.mycar.Words
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

/** Where Settings opens: at its list, or at the group a card on Home (or a screen opened from it) is about. */
enum class SettingsAt { TOP, USAGE_NOTES, CERTIFICATE, HELP, ABOUT }

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
    // The groups, each a page of its own; Home's cards open straight at theirs.
    var page by rememberSaveable { mutableStateOf(startPage(at)) }
    BackHandler(enabled = page != null) { page = null }
    val typingForCar by PhoneTyping.field.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(page?.let { pageTitle(it) } ?: stringResource(R.string.common_settings)) },
                navigationIcon = {
                    IconButton(onClick = { if (page != null) page = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        key(page) {
            val scroll = rememberScrollState()
            // Opened from Home's "it worked" card: at usage notes; from its certificate card: at the
            // certificate.
            var usageNotesY by remember { mutableIntStateOf(-1) }
            var certificateY by remember { mutableIntStateOf(-1) }
            if ((at == SettingsAt.USAGE_NOTES || at == SettingsAt.CERTIFICATE) && page == startPage(at)) {
                LaunchedEffect(Unit) {
                    val y = snapshotFlow { if (at == SettingsAt.CERTIFICATE) certificateY else usageNotesY }.first { it >= 0 }
                    snapshotFlow { scroll.maxValue }.first { it > 0 && it < Int.MAX_VALUE }
                    scroll.scrollTo(y)
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Edge to edge, the window no longer shrinks for the keyboard: without this, a field
                    // in the lower half (Plancia's token, Xonos's password) stays under it. Not while the
                    // bar for typing for the car is up: that one already sits above the keyboard.
                    .consumeWindowInsets(padding)
                    .then(if (typingForCar == null) Modifier.imePadding() else Modifier)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.height(0.dp))
                when (page) {
                    null -> SettingsCard {
                        SettingsPage.entries.forEachIndexed { i, group ->
                            if (i > 0) SettingsDivider()
                            SettingsRow(
                                pageTitle(group),
                                Modifier.clickable { page = group },
                                subtitle = pageDetail(group),
                                icon = pageIcon(group),
                                trailing = { Arrow() },
                            )
                        }
                    }
                    SettingsPage.GENERAL -> {
                        LanguageSection()
                        ThemeSection()
                        SettingsSection(stringResource(R.string.settings_updates_title)) { UpdateRow() }
                    }
                    SettingsPage.CONNECTION -> {
                        ConnectionSection()
                        CarAudioSection()
                        CertificateSection(Modifier.onGloballyPositioned { certificateY = it.positionInParent().y.toInt() })
                    }
                    SettingsPage.VOICE -> VoiceModelSection()
                    SettingsPage.PLANCIA -> PlanciaSection()
                    SettingsPage.XONOS -> XonosSection()
                    SettingsPage.CINEMA -> {
                        CinemaSection()
                        CinemaTvSection()
                    }
                    SettingsPage.HELP -> {
                        HelpSection(onOpenHelp, onOpenCarPreview, onOpenLogs)
                        if (SHOW_RECOMMENDED_APPS) AppsSection(onOpenRecommendedApps)
                    }
                    SettingsPage.ABOUT -> {
                        SettingsSection(
                            stringResource(R.string.settings_support_title),
                            footer = stringResource(R.string.settings_support_footer),
                        ) { DonateRows() }
                        // A build without a stats server sends nothing: no switch that would do nothing.
                        if (UsageStats.sends) {
                            SettingsSection(
                                stringResource(R.string.settings_usage_notes_title),
                                Modifier.onGloballyPositioned { usageNotesY = it.positionInParent().y.toInt() },
                                header = USAGE_STATS_INTRO,
                            ) { UsageStatsRows() }
                        }
                        AboutSection(onOpenWhatsNew, onReplayTutorial, onReplayIntro)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** The groups of Settings, in the order listed. */
enum class SettingsPage { GENERAL, CONNECTION, VOICE, PLANCIA, XONOS, CINEMA, HELP, ABOUT }

private fun startPage(at: SettingsAt): SettingsPage? = when (at) {
    SettingsAt.TOP -> null
    SettingsAt.USAGE_NOTES, SettingsAt.ABOUT -> SettingsPage.ABOUT
    SettingsAt.CERTIFICATE -> SettingsPage.CONNECTION
    SettingsAt.HELP -> SettingsPage.HELP
}

@Composable
private fun pageTitle(page: SettingsPage): String = when (page) {
    SettingsPage.GENERAL -> stringResource(R.string.settings_group_general)
    SettingsPage.CONNECTION -> stringResource(R.string.settings_connection_title)
    SettingsPage.VOICE -> stringResource(R.string.settings_group_voice)
    SettingsPage.PLANCIA -> "Plancia"
    SettingsPage.XONOS -> "Xonos"
    SettingsPage.CINEMA -> "Cinema"
    SettingsPage.HELP -> stringResource(R.string.settings_help_title)
    SettingsPage.ABOUT -> stringResource(R.string.settings_about_title)
}

@Composable
private fun pageDetail(page: SettingsPage): String = stringResource(
    when (page) {
        SettingsPage.GENERAL -> R.string.settings_group_general_detail
        SettingsPage.CONNECTION -> R.string.settings_group_connection_detail
        SettingsPage.VOICE -> R.string.settings_group_voice_detail
        SettingsPage.PLANCIA -> R.string.settings_group_plancia_detail
        SettingsPage.XONOS -> R.string.settings_group_xonos_detail
        SettingsPage.CINEMA -> R.string.settings_group_cinema_detail
        SettingsPage.HELP -> R.string.settings_group_help_detail
        SettingsPage.ABOUT -> R.string.settings_group_about_detail
    },
)

@Composable
private fun pageIcon(page: SettingsPage): ImageVector = when (page) {
    SettingsPage.GENERAL -> ImageVector.vectorResource(R.drawable.sym_settings)
    SettingsPage.CONNECTION -> ImageVector.vectorResource(R.drawable.sym_directions_car)
    SettingsPage.VOICE -> ImageVector.vectorResource(R.drawable.sym_mic)
    SettingsPage.PLANCIA -> ImageVector.vectorResource(R.drawable.sym_space_dashboard)
    SettingsPage.XONOS -> ImageVector.vectorResource(R.drawable.sym_play_arrow)
    SettingsPage.CINEMA -> ImageVector.vectorResource(R.drawable.sym_movie)
    SettingsPage.HELP -> Icons.Filled.Build
    SettingsPage.ABOUT -> Icons.Filled.Info
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
    SettingsSection(stringResource(R.string.settings_about_title)) {
        SettingsRow(
            stringResource(R.string.settings_about_whats_new),
            Modifier.clickable(onClick = onOpenWhatsNew),
            subtitle = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
            icon = Icons.Filled.Star,
            trailing = { Arrow() },
        )
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.settings_about_replay_intro, Intro.release),
            Modifier.clickable(onClick = onReplayIntro),
            subtitle = stringResource(R.string.settings_about_replay_intro_detail),
            icon = Icons.Filled.Star,
            trailing = { Arrow() },
        )
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.settings_about_setup),
            Modifier.clickable(onClick = onReplayTutorial),
            subtitle = stringResource(R.string.settings_about_setup_detail),
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
    SettingsSection(stringResource(R.string.settings_connection_title)) {
        SettingsRow(
            stringResource(R.string.settings_use_mycar),
            Modifier.toggleable(enabled, role = Role.Switch) {
                enabled = it
                AppSettings.setMyCarEnabled(context, it)
            },
            subtitle = stringResource(if (enabled) R.string.settings_use_mycar_on else R.string.settings_use_mycar_off),
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
    SettingsSection(stringResource(R.string.settings_help_title)) {
        SettingsRow(
            stringResource(R.string.settings_help_connect),
            Modifier.clickable(onClick = onOpenHelp),
            subtitle = stringResource(R.string.settings_help_connect_detail),
            icon = Icons.Filled.Info,
            trailing = { Arrow() },
        )
        // The preview draws the car screen on the phone, so it waits until no car is using it.
        if (!live) {
            SettingsDivider()
            SettingsRow(
                stringResource(R.string.settings_help_preview),
                Modifier.clickable(onClick = onOpenCarPreview),
                subtitle = stringResource(R.string.settings_help_preview_detail),
                icon = Icons.Filled.PlayArrow,
                trailing = { Arrow() },
            )
        }
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.settings_help_logs),
            Modifier.clickable(onClick = onOpenLogs),
            subtitle = stringResource(R.string.settings_help_logs_detail),
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
        stringResource(R.string.settings_audio_title),
        footer = if (route != AudioRoute.BLUETOOTH) {
            stringResource(R.string.settings_audio_footer_ask)
        } else {
            stringResource(R.string.settings_audio_footer)
        } + " " + stringResource(R.string.settings_audio_per_car),
    ) {
        AudioRouteRow(
            AudioRoute.AUTO, route, stringResource(R.string.settings_audio_auto),
            stringResource(R.string.settings_audio_auto_detail),
        )
        SettingsDivider()
        AudioRouteRow(
            AudioRoute.BLUETOOTH, route, stringResource(R.string.common_bluetooth),
            stringResource(R.string.settings_audio_bluetooth_detail),
        )
        SettingsDivider()
        AudioRouteRow(
            AudioRoute.CAR_LINK, route, stringResource(R.string.settings_audio_mycar),
            stringResource(R.string.settings_audio_mycar_detail),
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
    SettingsSection(stringResource(R.string.settings_apps_title)) {
        SettingsRow(
            stringResource(R.string.settings_apps_recommended),
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

    SettingsSection(stringResource(R.string.settings_cert_title), modifier, footer = notice ?: stringResource(R.string.settings_cert_footer)) {
        val s = summary
        val loaded = s != null && s.kind != CertProvider.Kind.SELF_SIGNED
        val hasDetails = s?.subject != null || s?.issuer != null
        SettingsRow(
            s?.headline ?: stringResource(R.string.settings_cert_checking),
            if (hasDetails) Modifier.clickable { detailsOpen = !detailsOpen } else Modifier,
            subtitle = s?.validUntil?.let { stringResource(R.string.settings_cert_valid_until, it) },
            icon = if (s == null || loaded) Icons.Filled.Lock else Icons.Filled.Warning,
            tint = if (s == null || loaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            trailing = if (hasDetails) {
                {
                    Icon(
                        if (detailsOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(if (detailsOpen) R.string.settings_cert_hide_details else R.string.settings_cert_show_details),
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
                s?.subject?.let { Detail(stringResource(R.string.settings_cert_subject), it) }
                s?.issuer?.let { Detail(stringResource(R.string.settings_cert_issuer), it) }
            }
        }
        // The public certificates: the recommended one and the spares, picked here by hand.
        val present = remember(refresh) { CertCatalog.present(context) }
        // When the ones that change (CarService) run out: opening the file takes a moment.
        val until by produceState<Map<String, java.util.Date>>(emptyMap(), refresh) {
            value = withContext(Dispatchers.IO) {
                CertCatalog.entries.filter { it.family != null }
                    .mapNotNull { entry -> CertCatalog.validUntil(context, entry.id)?.let { entry.id to it } }.toMap()
            }
        }
        CertCatalog.entries.forEach { entry ->
            val inUse = s?.kind == CertProvider.Kind.DOWNLOADED && s?.catalogId == entry.id
            val stored = entry.id in present
            SettingsDivider()
            SettingsRow(
                entry.label + " · " + entry.role,
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
                                if (s?.kind == CertProvider.Kind.IMPORTED) Words.get(R.string.settings_cert_ready_own_first, entry.label)
                                else Words.get(R.string.settings_cert_in_use_next, entry.label)
                            },
                            onFailure = { Words.get(R.string.settings_cert_download_failed, entry.label) },
                        )
                        refresh++
                    }
                },
                subtitle = entry.about +
                    (until[entry.id]?.let { " " + stringResource(R.string.settings_cert_valid_until_sentence, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(it)) } ?: "") +
                    when {
                        inUse -> " " + stringResource(R.string.settings_cert_in_use)
                        stored -> " " + stringResource(R.string.settings_cert_downloaded)
                        downloading -> ""
                        else -> " " + stringResource(R.string.settings_cert_not_downloaded)
                    },
                icon = Icons.Filled.Refresh,
                trailing = { RadioButton(selected = inUse, onClick = null) },
            )
        }
        SettingsDivider()
        SettingsRow(
            stringResource(R.string.settings_cert_import),
            Modifier.clickable { picker.launch(arrayOf("*/*")) },
            subtitle = stringResource(R.string.settings_cert_import_detail),
            icon = Icons.Filled.Add,
        )
        val removable = when (s?.kind) {
            CertProvider.Kind.IMPORTED -> stringResource(R.string.settings_cert_remove_imported)
            CertProvider.Kind.DOWNLOADED -> stringResource(R.string.settings_cert_remove_downloaded)
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
            title = stringResource(if (imported) R.string.settings_cert_remove_imported_title else R.string.settings_cert_remove_downloaded_title),
            text = if (imported) {
                stringResource(R.string.settings_cert_remove_imported_text)
            } else {
                stringResource(R.string.settings_cert_remove_downloaded_text)
            },
            confirm = stringResource(R.string.common_remove),
            onConfirm = {
                if (imported) CertProvider.removeImported(context) else CertProvider.removeDownloaded(context)
                // What Home's card asked for: done, so the card goes.
                if (imported && io.github.sirionero.mycar.CertRotation.notice.value.kind ==
                    io.github.sirionero.mycar.CertRotationPolicy.Notice.OWN_REFUSED
                ) {
                    io.github.sirionero.mycar.CertRotation.dismiss(context)
                }
                notice = Words.get(if (imported) R.string.settings_cert_removed_imported else R.string.settings_cert_removed_downloaded)
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
                        notice = Words.get(R.string.settings_cert_imported)
                        refresh++
                    }.onFailure {
                        reportError(Words.get(R.string.settings_cert_import_failed))
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
        title = { Text(stringResource(R.string.settings_cert_password_title)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                supportingText = { Text(error ?: stringResource(R.string.settings_cert_password_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) { error = it } }) { Text(stringResource(R.string.settings_cert_import_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
