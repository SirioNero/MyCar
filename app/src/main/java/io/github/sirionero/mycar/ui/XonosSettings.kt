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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.Tailnet
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
    var home by rememberSaveable(saved) { mutableStateOf(saved.homeUrl) }
    var remote by rememberSaveable(saved) { mutableStateOf(saved.remoteUrl) }
    var username by rememberSaveable(saved) { mutableStateOf(Xonos.settings.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val xonosApp = remember { XonosAccount.xonosAppCanShare(context) }
    val askLocalNetwork = rememberLocalNetworkAsk()

    /** Tries the account kept, at home and away, and says what answered. */
    fun test(config: Settings.Config) {
        status = "Trying…"
        scope.launch {
            val lines = mutableListOf<String>()
            for ((place, url) in listOf("At home" to config.homeUrl, "Away" to config.remoteUrl)) {
                if (url.isBlank()) continue
                val client = Xonos.repo.newClient()
                lines += try {
                    val server = client.connect(url, config.token)
                    "$place: Music Assistant ${server.version}, playing as ${Xonos.settings.playerName}"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "$place: " + Tailnet.why(context, listOf(url), networkFailure(e), describe(context, e), LOCAL_NETWORK_REFUSED)
                } finally {
                    client.close()
                }
                status = lines.joinToString("\n")
            }
        }
    }

    SettingsSection(
        "Xonos",
        footer = "Your Music Assistant library among the car's music apps. With the car audio through myCar, " +
            "its music goes straight to the car, and Android doesn't ask to share the audio while you listen to it.",
    ) {
        if (setUp) LocalNetworkRow(saved.urls)
        if (xonosApp) {
            SettingsRow(
                "Use the Xonos app's account",
                Modifier.clickable {
                    status = "Asking Xonos…"
                    scope.launch {
                        val imported = withContext(Dispatchers.IO) { XonosAccount.fromXonosApp(context) }
                        if (imported == null) {
                            status = "Xonos has no account to share yet: sign in in Xonos first, or below."
                        } else {
                            Xonos.save(context, imported.config, imported.username)
                            askLocalNetwork(imported.config.urls) { test(imported.config) }
                        }
                    }
                },
                subtitle = "Its addresses and sign-in, with nothing to type",
                icon = Icons.Filled.PlayArrow,
            )
            SettingsDivider()
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (setUp) {
                Text(
                    "Signed in" + (Xonos.settings.username?.let { " as $it" } ?: "") + ". Sign in again to change it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                home,
                { home = it },
                label = { Text("Music Assistant address") },
                placeholder = { Text("http://homeassistant.local:8095") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                remote,
                { remote = it },
                label = { Text("Address away (optional)") },
                placeholder = { Text("https://music.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                username,
                { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password,
                { password = it },
                label = { Text("Password") },
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
                        status = "Add the address, your username and your password."
                        return@Button
                    }
                    status = "Signing in…"
                    // Signing in already goes to the address: the home network first, if it's there.
                    askLocalNetwork(listOf(url)) {
                        scope.launch {
                            try {
                                val login = Account(Xonos.repo).login(url, username.trim(), password, "myCar (${Build.MODEL})")
                                password = ""
                                val config = Settings.Config(homeUrl, remoteUrl, login.token)
                                Xonos.save(context, config, login.user)
                                test(config)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                status = Tailnet.why(context, listOf(url), networkFailure(e), describe(context, e), LOCAL_NETWORK_REFUSED)
                            }
                        }
                    }
                }) { Text("Sign in") }
                if (setUp) {
                    TextButton(onClick = {
                        Xonos.remove(context)
                        status = "Xonos is off in myCar: the Xonos app is back in the car, if it's on the phone."
                    }) { Text("Remove") }
                }
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
