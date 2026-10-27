package io.github.sirionero.mycar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.MyCarLog
import java.net.URI

/**
 * Asks Android for the home network (see [LocalNetwork]) when the addresses about to be tried may
 * be there ([block]: any address of Home Assistant or Music Assistant, by default) and myCar may
 * not reach them; then runs what was waiting, whatever the answer.
 */
@Composable
internal fun rememberLocalNetworkAsk(
    block: (Context, List<String>) -> Boolean = LocalNetwork::mayBlock,
): (urls: List<String>, then: () -> Unit) -> Unit {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        MyCarLog.i("local network: ${if (granted) "allowed" else "refused"}")
        waiting?.invoke()
        waiting = null
    }
    return remember(launcher) {
        { urls: List<String>, then: () -> Unit ->
            if (block(context, urls)) {
                waiting = then
                launcher.launch(LocalNetwork.PERMISSION)
            } else {
                then()
            }
        }
    }
}

/**
 * A row that says so when Android keeps myCar off the home network where [urls] are: a tap asks
 * for it, and once Android has stopped asking, opens myCar's own permissions instead.
 */
@Composable
internal fun LocalNetworkRow(urls: List<String>, block: (Context, List<String>) -> Boolean = LocalNetwork::mayBlock) {
    val context = LocalContext.current
    var blocked by remember(urls) { mutableStateOf(block(context, urls)) }
    var refused by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        MyCarLog.i("local network: ${if (granted) "allowed" else "refused"}")
        blocked = block(context, urls)
        refused = !granted
    }
    // Back from Android's settings, or from anywhere else: it may have changed meanwhile.
    LifecycleResumeEffect(urls) {
        blocked = block(context, urls)
        onPauseOrDispose {}
    }
    if (!blocked) return

    val host = urls.firstOrNull(LocalNetwork::isLocal)?.let { runCatching { URI(it).host }.getOrNull() } ?: "your home network"
    SettingsRow(
        "Allow the home network",
        Modifier.clickable {
            if (!refused) {
                launcher.launch(LocalNetwork.PERMISSION)
            } else {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                }
            }
        },
        subtitle = if (!refused) {
            "Android keeps myCar from reaching $host until you allow Nearby devices. Tap to allow."
        } else {
            "Android said no. Tap, then Permissions, Nearby devices, Allow."
        },
        icon = Icons.Filled.Warning,
        tint = MaterialTheme.colorScheme.error,
    )
    SettingsDivider()
}

/** A test line's ending, when what failed is Android keeping myCar off the home network. */
internal const val LOCAL_NETWORK_REFUSED = "Android keeps myCar off your home network: allow Nearby devices, above."
