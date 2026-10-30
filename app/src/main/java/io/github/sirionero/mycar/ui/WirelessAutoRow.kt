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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.wireless.WirelessAuto

/** Settings > Connection: whether a car that worked without the cable connects by itself. */
@Composable
internal fun WirelessAutoRow() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(WirelessAuto.isOn(context)) }
    // Which cars Android lets myCar connect to by itself: without one, nothing ever starts by itself,
    // and nothing here said so.
    val cars = remember(on) { if (on) WirelessAuto.allowedCars(context) else emptyList() }
    SettingsRow(
        stringResource(R.string.settings_wireless_auto),
        Modifier.toggleable(on, role = Role.Switch) {
            on = it
            WirelessAuto.setOn(context, it)
        },
        subtitle = when {
            !on -> stringResource(R.string.settings_wireless_auto_off)
            cars.isEmpty() -> stringResource(R.string.settings_wireless_auto_on)
            else -> stringResource(R.string.settings_wireless_auto_cars, cars.joinToString(stringResource(R.string.common_list_separator)))
        },
        icon = Icons.Filled.Refresh,
        trailing = { Switch(checked = on, onCheckedChange = null) },
    )
}
