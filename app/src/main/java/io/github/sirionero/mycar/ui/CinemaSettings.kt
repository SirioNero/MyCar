package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.cinema.Cinema
import io.github.sirionero.mycar.cinema.CinemaSite

/**
 * Cinema, set up here on the phone: the video sites that show as tiles in the car, each one as on
 * a phone or as on a computer, and whether video shows only with the car parked - on unless the
 * driver turns it off, after a warning.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CinemaSection() {
    val sites by Cinema.sites.collectAsState()
    val parkedOnly by Cinema.parkedOnly.collectAsState()
    var warning by remember { mutableStateOf(false) }
    var resetAsk by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    val askLocalNetwork = rememberLocalNetworkAsk()

    SettingsSection(
        "Cinema",
        footer = stringResource(R.string.settings_cinema_footer),
    ) {
        // A site at home (Jellyfin, the router) needs the home network, which Android 17 keeps
        // myCar off until it's allowed.
        LocalNetworkRow(sites.map { it.url })
        SettingsRow(
            stringResource(R.string.settings_cinema_parked_only),
            Modifier.toggleable(parkedOnly, role = Role.Switch) { on ->
                if (on) Cinema.setParkedOnly(true) else warning = true
            },
            subtitle = if (parkedOnly) {
                stringResource(R.string.settings_cinema_parked_only_on)
            } else {
                stringResource(R.string.settings_cinema_parked_only_off)
            },
            icon = if (parkedOnly) Icons.Filled.PlayArrow else Icons.Filled.Warning,
            tint = if (parkedOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            trailing = { Switch(checked = parkedOnly, onCheckedChange = null) },
        )
        SettingsDivider()
        PhoneVideosRow()
        sites.forEach { site ->
            SettingsDivider()
            SiteRow(site)
        }
        SettingsDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(R.string.settings_cinema_name)) },
                placeholder = { Text("Jellyfin") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                address,
                { address = it },
                label = { Text(stringResource(R.string.settings_cinema_address)) },
                placeholder = { Text("https://example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            // Reset goes under on a narrow phone or with large text, instead of three lines of it.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val url = Cinema.normalizeUrl(address)
                        Cinema.add(CinemaSite(name.trim().ifEmpty { CinemaSite("", url).host }, url))
                        name = ""
                        address = ""
                        askLocalNetwork(listOf(url)) {}
                    },
                    // Only an address with a host: "my site" made a tile with no name that opened nothing.
                    enabled = CinemaSite("", Cinema.normalizeUrl(address)).host.isNotEmpty(),
                ) { Text(stringResource(R.string.settings_cinema_add_site)) }
                // The chips below add back a suggested site that's missing; this only ever takes
                // the driver's own sites away, so it asks first.
                if (sites != Cinema.SUGGESTED) {
                    TextButton(onClick = { resetAsk = true }) { Text(stringResource(R.string.settings_cinema_reset)) }
                }
            }
        }
        MoreSitesRow()
    }

    if (resetAsk) {
        val own = sites.filter { site -> Cinema.SUGGESTED.none { it.url == site.url } }
        val sep = stringResource(R.string.common_list_separator)
        AlertDialog(
            onDismissRequest = { resetAsk = false },
            title = { Text(stringResource(R.string.settings_cinema_reset_title)) },
            text = {
                Text(
                    if (own.isEmpty()) {
                        stringResource(R.string.settings_cinema_reset_text, Cinema.SUGGESTED.joinToString(sep) { it.name })
                    } else {
                        stringResource(R.string.settings_cinema_reset_text_own, Cinema.SUGGESTED.joinToString(sep) { it.name }, own.joinToString(sep) { it.name })
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Cinema.backToSuggested()
                    resetAsk = false
                }) { Text(stringResource(R.string.settings_cinema_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { resetAsk = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (warning) {
        AlertDialog(
            onDismissRequest = { warning = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.settings_cinema_warning_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_cinema_warning_text),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Cinema.setParkedOnly(false)
                    warning = false
                }) { Text(stringResource(R.string.settings_cinema_warning_off), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Button(onClick = { warning = false }) { Text(stringResource(R.string.settings_cinema_warning_keep)) }
            },
        )
    }
}

/** A site: its name and address; the switch asks for its computer layout; the bin takes it off the list. */
@Composable
private fun SiteRow(site: CinemaSite) {
    // A suggested site comes back from "More sites"; one of the driver's own (Jellyfin at home)
    // would have to be typed in again: asked first.
    var removeAsk by remember { mutableStateOf(false) }
    val own = (Cinema.SUGGESTED + Cinema.MORE).none { it.url == site.url }
    if (removeAsk) {
        ConfirmDialog(
            title = stringResource(R.string.settings_cinema_remove_title, site.name),
            text = stringResource(R.string.settings_cinema_remove_site_text),
            confirm = stringResource(R.string.common_remove),
            onConfirm = { Cinema.remove(site) },
            onDismiss = { removeAsk = false },
        )
    }
    SettingsRow(
        site.name,
        Modifier.toggleable(site.desktop, role = Role.Switch) { Cinema.setDesktop(site, it) },
        subtitle = site.host + " · " +
            stringResource(if (site.desktop) R.string.settings_cinema_layout_computer else R.string.settings_cinema_layout_phone) +
            (if (Cinema.isProtected(site)) " · " + stringResource(R.string.settings_cinema_protected) else ""),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = site.desktop, onCheckedChange = null)
                IconButton(onClick = { if (own) removeAsk = true else Cinema.remove(site) }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_cinema_remove, site.name))
                }
            }
        },
    )
}
