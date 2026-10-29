package io.github.sirionero.mycar.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Tailnet
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.car.InsideOrApp
import io.github.sirionero.mycar.xonos.Xonos
import io.github.sirionero.mycar.xonos.XonosAccount
import io.github.sirionero.mycar.xonos.data.Account
import io.github.sirionero.mycar.xonos.data.Settings
import io.github.sirionero.mycar.xonos.describe
import io.github.sirionero.mycar.xonos.networkFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Xonos, Music Assistant inside myCar, set up here on the phone, never in the car: the address at
 * home, the one away, and a sign-in. myCar keeps a token of its own, never the password. With the
 * Xonos app on the phone, its account comes over with one tap - and on its own the first time.
 */
@Composable
internal fun XonosSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember { Xonos.refresh(context) }
    val setUp by Xonos.setUp.collectAsState()
    LaunchedEffect(Unit) { XonosAccount.adoptIfUnset(context) }
    val saved = remember(setUp) { Xonos.settings.load() }
    // Read again on coming back: the Xonos app may have been installed or updated meanwhile.
    val resumed = rememberResumeCount()
    val xonosThere = remember(resumed) { InsideOrApp.xonosAppThere(context) }
    val xonosBridge = remember(resumed) { io.github.sirionero.mycar.bridge.Bridges.xonos.available(context) }
    var home by rememberSaveable(saved) { mutableStateOf(saved.homeUrl) }
    var remote by rememberSaveable(saved) { mutableStateOf(saved.remoteUrl) }
    var username by rememberSaveable(saved) { mutableStateOf(Xonos.settings.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var removeAsk by remember { mutableStateOf(false) }
    val xonosApp = remember(resumed) { XonosAccount.xonosAppCanShare(context) }
    val askLocalNetwork = rememberLocalNetworkAsk()

    /** Tries the account kept, at home and away, and says what answered. */
    fun test(config: Settings.Config) {
        status = Words.get(R.string.settings_test_trying)
        scope.launch {
            val lines = mutableListOf<String>()
            for ((place, url) in listOf(Words.get(R.string.settings_test_at_home) to config.homeUrl, Words.get(R.string.settings_test_away) to config.remoteUrl)) {
                if (url.isBlank()) continue
                val client = Xonos.repo.newClient()
                lines += try {
                    val server = client.connect(url, config.token)
                    Words.get(R.string.settings_test_line, place, Words.get(R.string.settings_xonos_test_ok, server.version, Xonos.settings.playerName))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Words.get(R.string.settings_test_line, place, Tailnet.why(context, listOf(url), networkFailure(e), describe(context, e), LOCAL_NETWORK_REFUSED))
                } finally {
                    client.close()
                }
                status = lines.joinToString("\n")
            }
        }
    }

    SettingsSection(
        "Xonos",
        footer = stringResource(R.string.settings_xonos_footer),
    ) {
        if (setUp) LocalNetworkRow(saved.urls)
        if (setUp) TailscaleCableRow(saved.urls, "Music Assistant", "Xonos", xonosThere, xonosBridge)
        if (xonosApp) {
            SettingsRow(
                stringResource(R.string.settings_xonos_use_app),
                Modifier.clickable {
                    status = Words.get(R.string.settings_xonos_asking)
                    scope.launch {
                        val imported = withContext(Dispatchers.IO) { XonosAccount.fromXonosApp(context) }
                        if (imported == null) {
                            status = Words.get(R.string.settings_xonos_app_no_account)
                        } else {
                            Xonos.save(context, imported.config, imported.username)
                            askLocalNetwork(imported.config.urls) { test(imported.config) }
                        }
                    }
                },
                subtitle = stringResource(R.string.settings_xonos_use_app_detail),
                icon = Icons.Filled.PlayArrow,
            )
            SettingsDivider()
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (setUp) {
                Text(
                    Xonos.settings.username?.let { stringResource(R.string.settings_xonos_signed_in_as, it) }
                        ?: stringResource(R.string.settings_xonos_signed_in),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                home,
                { home = it },
                label = { Text(stringResource(R.string.settings_xonos_address)) },
                placeholder = { Text("http://homeassistant.local:8095") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                remote,
                { remote = it },
                label = { Text(stringResource(R.string.settings_address_away)) },
                placeholder = { Text("https://music.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                username,
                { username = it },
                label = { Text(stringResource(R.string.settings_xonos_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password,
                { password = it },
                label = { Text(stringResource(R.string.settings_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val homeUrl = Settings.normalizeUrl(home)
                    val remoteUrl = Settings.normalizeUrl(remote)
                    val url = homeUrl.ifBlank { remoteUrl }
                    if (url.isBlank() || username.isBlank() || password.isEmpty()) {
                        status = Words.get(R.string.settings_xonos_missing)
                        return@Button
                    }
                    status = Words.get(R.string.settings_xonos_signing_in)
                    // Signing in already goes to the address: the home network first, if it's there.
                    askLocalNetwork(listOf(url)) {
                        scope.launch {
                            try {
                                val device = "myCar (${Build.MODEL})"
                                val login = try {
                                    Account(Xonos.repo).login(url, username.trim(), password, device)
                                } catch (e: Exception) {
                                    // Away from home the home address can't answer: the one away is
                                    // tried too, in the order the drive uses.
                                    val away = remoteUrl.isNotBlank() && remoteUrl != url && e !is CancellationException &&
                                        networkFailure(e) != Tailnet.Failure.OTHER
                                    if (!away) throw e
                                    Account(Xonos.repo).login(remoteUrl, username.trim(), password, device)
                                }
                                password = ""
                                val config = Settings.Config(homeUrl, remoteUrl, login.token)
                                Xonos.save(context, config, login.user)
                                test(config)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                status = Tailnet.why(context, listOf(homeUrl, remoteUrl).filter { it.isNotBlank() }, networkFailure(e), describe(context, e), LOCAL_NETWORK_REFUSED)
                            }
                        }
                    }
                }) { Text(stringResource(R.string.settings_xonos_sign_in)) }
                if (setUp) {
                    TextButton(onClick = { removeAsk = true }) { Text(stringResource(R.string.common_remove)) }
                }
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (removeAsk) {
        ConfirmDialog(
            title = stringResource(R.string.settings_xonos_remove_title),
            text = stringResource(R.string.settings_xonos_remove_text),
            confirm = stringResource(R.string.common_remove),
            onConfirm = {
                Xonos.remove(context)
                status = Words.get(R.string.settings_xonos_removed)
            },
            onDismiss = { removeAsk = false },
        )
    }
}
