package io.github.sirionero.mycar.ui

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
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
    var askedAt by remember { mutableLongStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { answers ->
        MyCarLog.i("cinema: videos on the phone - ${answers.entries.joinToString { "${it.key.substringAfterLast('.')}=${it.value}" }}")
        allowed = PhoneVideos.allowed(context)
        limited = PhoneVideos.limited(context)
        // An answer with no question shown: Android won't ask any more (twice "Don't allow"), and
        // Allow did nothing. myCar's page in Android's settings, where it's turned on by hand.
        if (!allowed && SystemClock.uptimeMillis() - askedAt < NO_QUESTION_MS) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }
        }
    }
    // Back from Android's settings: it may have changed meanwhile.
    LifecycleResumeEffect(Unit) {
        allowed = PhoneVideos.allowed(context)
        limited = PhoneVideos.limited(context)
        onPauseOrDispose {}
    }
    SettingsRow(
        stringResource(R.string.settings_cinema_videos),
        subtitle = when {
            !allowed -> stringResource(R.string.settings_cinema_videos_ask)
            limited -> stringResource(R.string.settings_cinema_videos_limited)
            else -> stringResource(R.string.settings_cinema_videos_allowed)
        },
        icon = if (allowed) Icons.Filled.Check else Icons.Filled.PlayArrow,
        trailing = {
            if (!allowed || limited) {
                TextButton(onClick = {
                    askedAt = SystemClock.uptimeMillis()
                    launcher.launch(PhoneVideos.permissions)
                }) { Text(stringResource(if (limited) R.string.settings_cinema_videos_pick else R.string.common_allow)) }
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
        Text(stringResource(R.string.settings_cinema_more_sites), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        stringResource(R.string.settings_cinema_tv_title),
        footer = stringResource(R.string.settings_cinema_tv_footer),
    ) {
        LocalNetworkRow(lists.map { it.url })
        // A list's address is long and often holds the provider's user and password: asked first.
        var removeAsk by remember { mutableStateOf<TvList?>(null) }
        removeAsk?.let { list ->
            ConfirmDialog(
                title = stringResource(R.string.settings_cinema_remove_title, list.name),
                text = stringResource(R.string.settings_cinema_tv_remove_text),
                confirm = stringResource(R.string.common_remove),
                onConfirm = { TvLists.remove(list) },
                onDismiss = { removeAsk = null },
            )
        }
        lists.forEach { list ->
            val state = states[list.url]
            SettingsRow(
                list.name,
                subtitle = list.host + when {
                    state == null -> ""
                    state.loading -> " · " + stringResource(R.string.settings_cinema_tv_loading)
                    state.problem != null -> " · " + state.problem
                    else -> " · " + pluralStringResource(R.plurals.settings_cinema_tv_channels, state.channels.size, state.channels.size)
                },
                icon = if (state?.problem != null) Icons.Filled.Warning else Icons.Filled.PlayArrow,
                tint = if (state?.problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trailing = {
                    IconButton(onClick = { removeAsk = list }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_cinema_remove, list.name))
                    }
                },
            )
            SettingsDivider()
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The name a list gets when none is typed, and the field's example.
            val defaultName = stringResource(R.string.settings_cinema_tv_default_name)
            OutlinedTextField(
                name,
                { name = it },
                label = { Text(stringResource(R.string.settings_cinema_name)) },
                placeholder = { Text(defaultName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                address,
                { address = it },
                label = { Text(stringResource(R.string.settings_cinema_tv_address)) },
                placeholder = { Text("https://example.com/playlist.m3u") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            val url = Cinema.normalizeUrl(address)
            val list = TvList(name.trim().ifEmpty { defaultName }, url)
            Button(
                onClick = {
                    name = ""
                    address = ""
                    // A list at home (a TV server) needs the home network first: then it loads.
                    askLocalNetwork(listOf(url)) { TvLists.add(list) }
                },
                enabled = list.host.isNotEmpty(),
            ) { Text(stringResource(R.string.settings_cinema_tv_add)) }
        }
    }
    // Opening the settings is a good moment to see that the lists still load.
    LifecycleResumeEffect(lists) {
        lists.forEach { TvLists.load(context, it) }
        onPauseOrDispose {}
    }
}

/** An answer sooner than this after asking came with no question on screen. */
private const val NO_QUESTION_MS = 400L
