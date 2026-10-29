package io.github.sirionero.mycar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Tailnet
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.car.InsideOrApp
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlanciaSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by HomeAssistant.config.collectAsState()
    var home by rememberSaveable(saved) { mutableStateOf(saved.homeUrl) }
    var remote by rememberSaveable(saved) { mutableStateOf(saved.remoteUrl) }
    var token by rememberSaveable(saved) { mutableStateOf(saved.token) }
    var status by remember { mutableStateOf<String?>(null) }
    var removeAsk by remember { mutableStateOf(false) }
    // Read again on coming back: the Plancia app may have been installed or updated meanwhile.
    val resumed = rememberResumeCount()
    val planciaApp = remember(resumed) { HomeAssistant.planciaAppCanShare(context) }
    val planciaThere = remember(resumed) { InsideOrApp.planciaAppThere(context) }
    val planciaBridge = remember(resumed) { io.github.sirionero.mycar.bridge.Bridges.plancia.available(context) }
    val askLocalNetwork = rememberLocalNetworkAsk()

    fun test(config: HomeAssistant.Config) {
        if (!config.isComplete) {
            status = Words.get(R.string.settings_plancia_missing)
            return
        }
        status = Words.get(R.string.settings_test_trying)
        scope.launch {
            val lines = mutableListOf<String>()
            for ((place, url) in listOf(Words.get(R.string.settings_test_at_home) to config.homeUrl, Words.get(R.string.settings_test_away) to config.remoteUrl)) {
                if (url.isBlank()) continue
                val client = HomeAssistant.newClient()
                lines += try {
                    val version = client.connect(url, config.token)
                    val count = (client.command("get_states") as? JSONArray)?.length() ?: 0
                    Words.get(R.string.settings_test_line, place, Words.plural(R.plurals.settings_plancia_test_ok, count, version, count))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Words.get(R.string.settings_test_line, place, Tailnet.why(context, listOf(url), HaException.failureOf(e), describe(e), LOCAL_NETWORK_REFUSED))
                } finally {
                    client.close()
                }
                status = lines.joinToString("\n")
            }
        }
    }

    SettingsSection(
        "Plancia",
        footer = stringResource(R.string.settings_plancia_footer),
    ) {
        if (saved.isComplete) LocalNetworkRow(saved.urls)
        if (saved.isComplete) TailscaleCableRow(saved.urls, "Home Assistant", "Plancia", planciaThere, planciaBridge)
        if (planciaApp) {
            SettingsRow(
                stringResource(R.string.settings_plancia_use_app),
                Modifier.clickable {
                    status = Words.get(R.string.settings_plancia_asking)
                    scope.launch {
                        val imported = withContext(Dispatchers.IO) { HomeAssistant.fromPlanciaApp(context) }
                        if (imported == null) {
                            status = Words.get(R.string.settings_plancia_app_no_account)
                        } else {
                            HomeAssistant.use(imported)
                            askLocalNetwork(imported.config.urls) { test(imported.config) }
                        }
                    }
                },
                subtitle = stringResource(R.string.settings_plancia_use_app_detail),
                icon = Icons.Filled.Home,
            )
            SettingsDivider()
        }
        if (saved.isComplete) {
            AskHomeAssistantRow()
            SettingsDivider()
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                home,
                { home = it },
                label = { Text(stringResource(R.string.settings_plancia_address_home)) },
                placeholder = { Text("http://homeassistant.local:8123") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                remote,
                { remote = it },
                label = { Text(stringResource(R.string.settings_address_away)) },
                placeholder = { Text("https://example.ui.nabu.casa") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                token,
                { token = it },
                label = { Text(stringResource(R.string.settings_plancia_token)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            // On a narrow phone or with large text, Remove goes under rather than splitting in two.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val config = HomeAssistant.Config(
                        HomeAssistant.normalizeUrl(home),
                        HomeAssistant.normalizeUrl(remote),
                        token.trim(),
                    )
                    // Not saved half done: an emptied token field (a new one about to be pasted)
                    // overwrote the working account, without the question Remove asks.
                    if (!config.isComplete) {
                        status = Words.get(R.string.settings_plancia_missing_remove)
                        return@Button
                    }
                    HomeAssistant.save(config)
                    askLocalNetwork(config.urls) { test(config) }
                }) { Text(stringResource(R.string.settings_save_and_test)) }
                if (saved.isComplete) {
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
            title = stringResource(R.string.settings_plancia_remove_title),
            text = stringResource(R.string.settings_plancia_remove_text),
            confirm = stringResource(R.string.common_remove),
            onConfirm = {
                HomeAssistant.remove()
                status = Words.get(R.string.settings_plancia_removed)
            },
            onDismiss = { removeAsk = false },
        )
    }
}

/** What went wrong, in a line. */
private fun describe(e: Exception): String = when ((e as? HaException)?.reason) {
    HaException.Reason.INVALID_TOKEN -> Words.get(R.string.settings_plancia_error_token)
    HaException.Reason.ACCESS_DENIED -> Words.get(R.string.settings_plancia_error_denied)
    HaException.Reason.NO_RESPONSE -> Words.get(R.string.settings_plancia_error_no_answer)
    HaException.Reason.BAD_ADDRESS -> Words.get(R.string.settings_plancia_error_address)
    else -> e.message ?: Words.get(R.string.settings_plancia_error_other)
}
