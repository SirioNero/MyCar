package io.github.sirionero.mycar.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Tailnet

/**
 * Where Plancia or Xonos is set up with its server on a tailnet. Wirelessly myCar has to be left
 * out of Tailscale - inside a VPN, Android keeps it off the car's Wi-Fi - and then it reaches the
 * server through the [app] app, inside Tailscale, when that app has the bridge ([bridged]: see
 * bridge/AppBridge). Without it, myCar reaches the server with the cable only, and wirelessly the
 * car uses the [app] app in its place, by itself (see car/InsideOrApp), when it's on the phone
 * ([appThere]).
 */
@Composable
internal fun TailscaleCableRow(urls: List<String>, server: String, app: String, appThere: Boolean, bridged: Boolean) {
    val context = LocalContext.current
    if (!remember(urls) { Tailnet.onTailnet(context, urls) }) return
    if (bridged) {
        SettingsRow(
            stringResource(R.string.tailscale_bridged_title, app),
            subtitle = stringResource(R.string.tailscale_bridged_detail, server, app),
            icon = Icons.Filled.Info,
        )
    } else {
        SettingsRow(
            stringResource(R.string.tailscale_cable_title),
            subtitle = if (appThere) {
                stringResource(R.string.tailscale_cable_update_app, server, app)
            } else {
                stringResource(R.string.tailscale_cable_get_app, server, app)
            },
            icon = Icons.Filled.Info,
        )
    }
    SettingsDivider()
}
