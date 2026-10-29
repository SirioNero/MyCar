package io.github.sirionero.mycar.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.sirionero.mycar.car.AssistantBrain

/**
 * Settings > Plancia: whether myCar's voice assistant asks Home Assistant what it can't do itself,
 * and stands in for the phone's assistant while the phone is locked (car/AssistantBrain).
 */
@Composable
internal fun AskHomeAssistantRow() {
    val on by AssistantBrain.enabled.collectAsState()
    SettingsRow(
        "Ask Home Assistant in the car",
        Modifier.toggleable(on, role = Role.Switch) { AssistantBrain.setEnabled(it) },
        subtitle = "What myCar's voice assistant can't do itself goes to the voice assistant set up in Home " +
            "Assistant (Settings > Voice assistants there): Claude, Gemini, ChatGPT, a model at home, or Home " +
            "Assistant's own, which also runs the house. With the phone locked, it answers in place of the " +
            "phone's assistant. Only those words go to your Home Assistant, as text.",
        icon = Icons.Filled.Home,
        trailing = { Switch(checked = on, onCheckedChange = null) },
    )
}
