package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.cinema.Cinema
import io.github.sirionero.mycar.cinema.CinemaSite

/**
 * Cinema, set up here on the phone: the video sites that show as tiles in the car, each one as on
 * a phone or as on a computer, and whether video shows only with the car parked - on unless the
 * driver turns it off, after a warning.
 */
@Composable
internal fun CinemaSection() {
    val sites by Cinema.sites.collectAsState()
    val parkedOnly by Cinema.parkedOnly.collectAsState()
    var warning by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    val askLocalNetwork = rememberLocalNetworkAsk()

    SettingsSection(
        "Cinema",
        footer = "Video sites as tiles in the car, each opened in a browser made for video. Protected video " +
            "(Netflix, DAZN, Prime Video, Disney+ and the like) most likely stays black in the car: Android " +
            "hides it from a screen made into a video stream, as the car's is.",
    ) {
        // A site at home (Jellyfin, the router) needs the home network, which Android 17 keeps
        // myCar off until it's allowed.
        LocalNetworkRow(sites.map { it.url })
        SettingsRow(
            "Video only when parked",
            Modifier.toggleable(parkedOnly, role = Role.Switch) { on ->
                if (on) Cinema.setParkedOnly(true) else warning = true
            },
            subtitle = if (parkedOnly) {
                "While the car moves the picture is covered, and the sound carries on"
            } else {
                "Off: video shows while the car moves too"
            },
            icon = if (parkedOnly) Icons.Filled.PlayArrow else Icons.Filled.Warning,
            tint = if (parkedOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            trailing = { Switch(checked = parkedOnly, onCheckedChange = null) },
        )
        sites.forEach { site ->
            SettingsDivider()
            SiteRow(site)
        }
        SettingsDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Name") },
                placeholder = { Text("Jellyfin") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                address,
                { address = it },
                label = { Text("Address") },
                placeholder = { Text("https://example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                ) { Text("Add the site") }
                TextButton(onClick = { Cinema.backToSuggested() }) { Text("Suggested sites") }
            }
        }
    }

    if (warning) {
        AlertDialog(
            onDismissRequest = { warning = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Video while driving?") },
            text = {
                Text(
                    "myCar strongly advises against it. Watching video while driving is dangerous for you " +
                        "and for others on the road, and in many countries it's against the law.\n\n" +
                        "With the protection off, video shows on the car's screen while the car moves too, " +
                        "and the responsibility is the driver's alone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Cinema.setParkedOnly(false)
                    warning = false
                }) { Text("Turn it off", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Button(onClick = { warning = false }) { Text("Keep it on") }
            },
        )
    }
}

/** A site: its name and address; the switch asks for its computer layout; the bin takes it off the list. */
@Composable
private fun SiteRow(site: CinemaSite) {
    SettingsRow(
        site.name,
        Modifier.toggleable(site.desktop, role = Role.Switch) { Cinema.setDesktop(site, it) },
        subtitle = site.host + (if (site.desktop) " · computer layout" else " · phone layout") +
            (if (Cinema.isProtected(site)) " · protected video, likely black in the car" else ""),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = site.desktop, onCheckedChange = null)
                IconButton(onClick = { Cinema.remove(site) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${site.name}")
                }
            }
        },
    )
}
