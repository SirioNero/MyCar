package io.github.sirionero.mycar.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
            "Through Tailscale: through the $app app too",
            subtitle = "Wirelessly, with myCar left out of Tailscale (inside a VPN, Android keeps myCar off the car's Wi-Fi), " +
                "myCar reaches your $server through the $app app, which is inside Tailscale. Keep the $app app set up " +
                "with the same address.",
            icon = Icons.Filled.Info,
        )
    } else {
        SettingsRow(
            "Through Tailscale: with the cable, for now",
            subtitle = "Wirelessly, myCar has to be left out of Tailscale (inside a VPN, Android keeps myCar off the car's " +
                "Wi-Fi), and then it can't reach your $server by itself. " +
                (if (appThere) "Update the $app app, and myCar reaches it through the app; until then the car uses the $app app instead, by itself."
                else "With an up-to-date $app app on the phone, myCar reaches it through the app."),
            icon = Icons.Filled.Info,
        )
    }
    SettingsDivider()
}
