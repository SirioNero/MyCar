package io.github.sirionero.mycar.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.sirionero.mycar.Tailnet

/**
 * Where Plancia or Xonos is set up with its server on a tailnet: inside myCar it reaches it with the
 * cable only, for now. Wirelessly myCar has to be left out of Tailscale - inside a VPN, Android
 * keeps it off the car's Wi-Fi - and then the car uses the [app] app in its place, by itself (see
 * car/InsideOrApp), when it's on the phone ([appThere]).
 */
@Composable
internal fun TailscaleCableRow(urls: List<String>, server: String, app: String, appThere: Boolean) {
    val context = LocalContext.current
    if (!remember(urls) { Tailnet.onTailnet(context, urls) }) return
    SettingsRow(
        "Through Tailscale: with the cable, for now",
        subtitle = "Wirelessly, myCar has to be left out of Tailscale (inside a VPN, Android keeps myCar off the car's " +
            "Wi-Fi), and then it can't reach your $server. " +
            (if (appThere) "The car uses the $app app instead, by itself. " else "With the $app app on the phone, the car uses it instead, by itself. ") +
            "We're working to make it work wirelessly too.",
        icon = Icons.Filled.Info,
    )
    SettingsDivider()
}
