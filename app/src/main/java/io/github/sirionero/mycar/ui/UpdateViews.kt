package io.github.sirionero.mycar.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.SessionStatus
import io.github.sirionero.mycar.update.UpdateChecker
import io.github.sirionero.mycar.update.UpdateChecker.State

@StringRes private val UNPLUG_FIRST = R.string.update_unplug_first

/** Shown on Home once a newer version is found; hidden otherwise. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val state by UpdateChecker.state.collectAsStateWithLifecycle()
    SessionStatus.state.collectAsStateWithLifecycle().value // redraw when the car comes or goes
    val release = when (val s = state) {
        is State.Available -> s.release
        is State.Downloading -> s.release
        is State.Ready -> s.release
        is State.Failed -> s.release
        else -> null
    } ?: return

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(stringResource(R.string.update_out, release.version), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            when (val s = state) {
                is State.Downloading -> {
                    Text(stringResource(R.string.update_downloading, (s.fraction * 100).toInt()), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth())
                }
                is State.Ready -> {
                    val connected = UpdateChecker.carConnected
                    Text(
                        stringResource(if (connected) UNPLUG_FIRST else R.string.update_downloaded),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { UpdateChecker.install(context) }, enabled = !connected) { Text(stringResource(R.string.update_install)) }
                }
                else -> {
                    Text(
                        (s as? State.Failed)?.message ?: stringResource(R.string.update_download_now),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { UpdateChecker.download(context, release) }) {
                            Text(stringResource(if (s is State.Failed) R.string.common_retry else R.string.update_update))
                        }
                        if (release.pageUrl.isNotBlank()) {
                            TextButton(onClick = {
                                // No browser for it must not close myCar, and the car's session with it.
                                MyCarLinks.open(context, release.pageUrl)
                            }) { Text(stringResource(R.string.whatsnew_title)) }
                        }
                    }
                }
            }
        }
    }
    // Room before whatever Home shows next; nothing when the card is hidden.
    Spacer(Modifier.height(16.dp))
}

/** What the update check found, in a line. */
@Composable
private fun statusLine(state: State): String = when (state) {
    State.Idle -> stringResource(if (BuildConfig.DEBUG) R.string.update_status_debug else R.string.update_status_daily)
    State.Checking -> stringResource(R.string.update_status_checking)
    State.UpToDate -> stringResource(R.string.update_status_latest)
    is State.Available -> stringResource(R.string.update_status_available, state.release.version)
    is State.Downloading -> stringResource(R.string.update_downloading, (state.fraction * 100).toInt())
    is State.Ready -> if (UpdateChecker.carConnected) stringResource(UNPLUG_FIRST) else stringResource(R.string.update_status_ready, state.release.version)
    is State.Failed -> state.message
}

/**
 * The channels the dialog offers. Empty for now: every release so far goes out as GitHub's latest,
 * so Beta and Alpha would offer the same as Stable. Add them back once a pre-release ships:
 *   Triple(UpdateChecker.Channel.STABLE, "Stable", "Finished releases only"),
 *   Triple(UpdateChecker.Channel.BETA, "Beta", "Test versions that are nearly done"),
 *   Triple(UpdateChecker.Channel.ALPHA, "Alpha", "Every test version, first. Expect bugs"),
 */
private val CHANNELS = emptyList<Triple<UpdateChecker.Channel, String, String>>()

/** The one Settings row for updates. Checking, the channel and automatic checks all sit behind it. */
@Composable
internal fun UpdateRow() {
    val context = LocalContext.current
    val state by UpdateChecker.state.collectAsStateWithLifecycle()
    SessionStatus.state.collectAsStateWithLifecycle().value
    var channel by remember { mutableStateOf(UpdateChecker.channel(context)) }
    var open by remember { mutableStateOf(false) }
    val channelName = CHANNELS.firstOrNull { it.first == channel }?.second
    SettingsRow(
        stringResource(R.string.update_title),
        Modifier.clickable { open = true },
        subtitle = listOfNotNull(channelName, statusLine(state)).joinToString(" · "),
        icon = Icons.Filled.CheckCircle,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
    if (open) {
        UpdateDialog(
            state = state,
            channel = channel,
            onChannel = {
                channel = it
                UpdateChecker.setChannel(context, it)
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun UpdateDialog(
    state: State,
    channel: UpdateChecker.Channel,
    onChannel: (UpdateChecker.Channel) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var auto by remember { mutableStateOf(UpdateChecker.autoCheckEnabled(context)) }
    val action: Pair<String, () -> Unit>? = when (state) {
        State.Checking, is State.Downloading -> null
        is State.Available -> stringResource(R.string.update_download) to { UpdateChecker.download(context, state.release) }
        is State.Ready -> if (UpdateChecker.carConnected) null else stringResource(R.string.update_install) to { UpdateChecker.install(context) }
        is State.Failed -> stringResource(R.string.common_retry) to {
            state.release?.let { UpdateChecker.download(context, it) } ?: UpdateChecker.check(context)
        }
        else -> stringResource(R.string.update_check_now) to { UpdateChecker.check(context) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.update_version_status, BuildConfig.VERSION_NAME, statusLine(state)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                CHANNELS.forEach { (value, label, description) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(channel == value, role = Role.RadioButton) { onChannel(value) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = channel == value, onClick = null)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(auto, role = Role.Switch) {
                            auto = it
                            UpdateChecker.setAutoCheckEnabled(context, it)
                        }
                        .padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.update_check_automatically), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (BuildConfig.DEBUG) R.string.update_status_debug else R.string.update_once_a_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = auto, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            action?.let { (label, run) -> TextButton(onClick = run) { Text(label) } }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_done)) } },
    )
}
