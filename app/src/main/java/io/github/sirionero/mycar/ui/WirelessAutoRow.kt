package io.github.sirionero.mycar.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import io.github.sirionero.mycar.wireless.WirelessAuto

/** Settings > Connection: whether a car that worked without the cable connects by itself. */
@Composable
internal fun WirelessAutoRow() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(WirelessAuto.isOn(context)) }
    SettingsRow(
        "Connect wirelessly by itself",
        Modifier.toggleable(on, role = Role.Switch) {
            on = it
            WirelessAuto.setOn(context, it)
        },
        subtitle = if (on) {
            "A car you connected to without the cable connects again when it starts. Android asks once."
        } else {
            "Off: tap Connect wirelessly each time"
        },
        icon = Icons.Filled.Refresh,
        trailing = { Switch(checked = on, onCheckedChange = null) },
    )
}
