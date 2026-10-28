package io.github.sirionero.mycar.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.cinema.Cinema
import io.github.sirionero.mycar.cinema.PhoneVideos
import io.github.sirionero.mycar.cinema.TvList
import io.github.sirionero.mycar.cinema.TvLists

/*
 * Cinema's own sources, set up on the phone beside the sites: the phone's videos (Android's
 * permission for them), more sites to add with a tap, and the TV lists.
 */

/** Whether myCar may show the phone's videos in the car, and the button that asks Android. */
@Composable
internal fun PhoneVideosRow() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(PhoneVideos.allowed(context)) }
    var limited by remember { mutableStateOf(PhoneVideos.limited(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answers ->
        MyCarLog.i("cinema: videos on the phone - ${answers.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" }}")
        allowed = PhoneVideos.allowed(context)
        limited = PhoneVideos.limited(context)
    }
    // Back from Android's settings: it may have changed meanwhile.
    LifecycleResumeEffect(Unit) {
        allowed = PhoneVideos.allowed(context)
        limited = PhoneVideos.limited(context)
        onPauseOrDispose {}
    }
    SettingsRow(
        "Videos on this phone",
        subtitle = when {
            !allowed -> "Allow myCar to show them in the car, under \"On this phone\""
            limited -> "Only the videos picked for myCar show in the car"
            else -> "They show in the car, under \"On this phone\""
        },
        icon = if (allowed) Icons.Filled.Check else Icons.Filled.PlayArrow,
        trailing = {
            if (!allowed || limited) {
                TextButton(onClick = { launcher.launch(PhoneVideos.permissions) }) { Text(if (limited) "Pick" else "Allow") }
            }
        },
    )
}

/** The suggested sites not on the list yet, each added with a tap. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoreSitesRow() {
    val sites by Cinema.sites.collectAsState()
    val more = (Cinema.SUGGESTED + Cinema.MORE).filter { suggested -> sites.none { it.host == suggested.host } }
    if (more.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("More sites", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            more.forEach { site ->
                AssistChip(
                    onClick = { Cinema.add(site) },
                    label = { Text(site.name) },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
        }
    }
}

/**
 * Cinema's TV: IPTV lists (M3U) from a provider, a public list or a TV server at home, each a tile
 * in the car with its channels. Added here with a name and the list's address.
 */
@Composable
internal fun CinemaTvSection() {
    val context = LocalContext.current
    val lists by TvLists.lists.collectAsState()
    val states by TvLists.states.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    val askLocalNetwork = rememberLocalNetworkAsk()

    SettingsSection(
        "Cinema: TV",
        footer = "IPTV lists in the M3U format, from your provider, a public list or a TV server at home: each " +
            "one is a tile in Cinema with its channels. They play on the phone's own player; protected channels " +
            "(DRM) and multicast ones are left out.",
    ) {
        LocalNetworkRow(lists.map { it.url })
        lists.forEach { list ->
            val state = states[list.url]
            SettingsRow(
                list.name,
                subtitle = list.host + when {
                    state == null -> ""
                    state.loading -> " · loading…"
                    state.problem != null -> " · " + state.problem
                    else -> " · ${state.channels.size} channels"
                },
                icon = if (state?.problem != null) Icons.Filled.Warning else Icons.Filled.PlayArrow,
                tint = if (state?.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trailing = {
                    IconButton(onClick = { TvLists.remove(list) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${list.name}")
                    }
                },
            )
            SettingsDivider()
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                name,
                { name = it },
                label = { Text("Name") },
                placeholder = { Text("TV") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                address,
                { address = it },
                label = { Text("List address (M3U)") },
                placeholder = { Text("https://example.com/playlist.m3u") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            val url = Cinema.normalizeUrl(address)
            val list = TvList(name.trim().ifEmpty { "TV" }, url)
            Button(
                onClick = {
                    name = ""
                    address = ""
                    // A list at home (a TV server) needs the home network first: then it loads.
                    askLocalNetwork(listOf(url)) { TvLists.add(list) }
                },
                enabled = list.host.isNotEmpty(),
            ) { Text("Add the list") }
        }
    }
    // Opening the settings is a good moment to see that the lists still load.
    LifecycleResumeEffect(lists) {
        lists.forEach { TvLists.load(context, it) }
        onPauseOrDispose {}
    }
}
