package io.github.sirionero.mycar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.github.sirionero.mycar.home.HaException
import io.github.sirionero.mycar.home.HomeAssistant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Plancia, Home Assistant inside myCar, set up here on the phone, never in the car: the address at
 * home, the one away, and a long-lived token. With the Plancia app on the phone, its account comes
 * over with one tap - and on its own the first time, when nothing's been set here yet.
 */
@Composable
internal fun PlanciaSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by HomeAssistant.config.collectAsState()
    var home by rememberSaveable(saved) { mutableStateOf(saved.homeUrl) }
    var remote by rememberSaveable(saved) { mutableStateOf(saved.remoteUrl) }
    var token by rememberSaveable(saved) { mutableStateOf(saved.token) }
    var status by remember { mutableStateOf<String?>(null) }
    val planciaApp = remember { HomeAssistant.planciaAppCanShare(context) }

    fun test(config: HomeAssistant.Config) {
        if (!config.isComplete) {
            status = "Add an address and a token."
            return
        }
        status = "Trying…"
        scope.launch {
            val lines = mutableListOf<String>()
            for ((place, url) in listOf("At home" to config.homeUrl, "Away" to config.remoteUrl)) {
                if (url.isBlank()) continue
                val client = HomeAssistant.newClient()
                lines += try {
                    val version = client.connect(url, config.token)
                    val count = (client.command("get_states") as? JSONArray)?.length() ?: 0
                    "$place: Home Assistant $version, $count entities"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "$place: ${describe(e)}"
                } finally {
                    client.close()
                }
                status = lines.joinToString("\n")
            }
        }
    }

    SettingsSection(
        "Plancia",
        footer = "Your Home Assistant dashboards in the car, ready the moment you open them: from the app grid, " +
            "a page beside the map, or beside it. Make the token in Home Assistant: your profile, Security, " +
            "Long-lived access tokens.",
    ) {
        if (planciaApp) {
            SettingsRow(
                "Use the Plancia app's account",
                Modifier.clickable {
                    status = "Asking Plancia…"
                    scope.launch {
                        val imported = withContext(Dispatchers.IO) { HomeAssistant.fromPlanciaApp(context) }
                        if (imported == null) {
                            status = "Plancia has no account to share yet: set it up in Plancia first, or type it below."
                        } else {
                            HomeAssistant.use(imported)
                            test(imported.config)
                        }
                    }
                },
                subtitle = "Its addresses and token, with nothing to type",
                icon = Icons.Filled.Home,
            )
            SettingsDivider()
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                home,
                { home = it },
                label = { Text("Address at home") },
                placeholder = { Text("http://homeassistant.local:8123") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                remote,
                { remote = it },
                label = { Text("Address away (optional)") },
                placeholder = { Text("https://example.ui.nabu.casa") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                token,
                { token = it },
                label = { Text("Long-lived access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val config = HomeAssistant.Config(
                        HomeAssistant.normalizeUrl(home),
                        HomeAssistant.normalizeUrl(remote),
                        token.trim(),
                    )
                    HomeAssistant.save(config)
                    test(config)
                }) { Text("Save and test") }
                if (saved.isComplete) {
                    TextButton(onClick = {
                        HomeAssistant.save(HomeAssistant.Config())
                        status = "Plancia is off: the Plancia app is back in the car, if it's on the phone."
                    }) { Text("Remove") }
                }
            }
            status?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** What went wrong, in a line. */
private fun describe(e: Exception): String = when ((e as? HaException)?.reason) {
    HaException.Reason.INVALID_TOKEN -> "the token isn't valid"
    HaException.Reason.ACCESS_DENIED -> "access denied"
    HaException.Reason.NO_RESPONSE -> "no answer"
    HaException.Reason.BAD_ADDRESS -> "not a valid address"
    else -> e.message ?: "it didn't work"
}
