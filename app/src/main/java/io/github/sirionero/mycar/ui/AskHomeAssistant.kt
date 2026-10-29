package io.github.sirionero.mycar.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.AssistantBrain

/**
 * Settings > Plancia: whether myCar's voice assistant asks Home Assistant what it can't do itself,
 * and stands in for the phone's assistant while the phone is locked (car/AssistantBrain).
 */
@Composable
internal fun AskHomeAssistantRow() {
    val on by AssistantBrain.enabled.collectAsState()
    SettingsRow(
        stringResource(R.string.settings_ask_ha),
        Modifier.toggleable(on, role = Role.Switch) { AssistantBrain.setEnabled(it) },
        subtitle = stringResource(R.string.settings_ask_ha_detail),
        icon = Icons.Filled.Home,
        trailing = { Switch(checked = on, onCheckedChange = null) },
    )
}
