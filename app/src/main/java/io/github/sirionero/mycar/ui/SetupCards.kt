package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.setup.SetupSignals
import io.github.sirionero.mycar.setup.SetupSteps
import io.github.sirionero.mycar.setup.SetupSteps.Id

/** The setup steps as the phone stands now, read again whenever the screen comes back. */
@Composable
private fun rememberSetupSteps(): List<SetupSteps.Step> {
    val context = LocalContext.current
    var steps by remember { mutableStateOf(SetupSteps.compute(SetupSignals.read(context))) }
    LifecycleResumeEffect(Unit) {
        steps = SetupSteps.compute(SetupSignals.read(context))
        onPauseOrDispose {}
    }
    return steps
}

@Composable
private fun stepName(id: Id) = when (id) {
    Id.CERTIFICATE -> stringResource(R.string.setup_step_certificate)
    Id.MYCAR_ON -> stringResource(R.string.setup_step_mycar_on)
    Id.ANDROID_AUTO -> "Android Auto"
    else -> id.name.lowercase()
}

/**
 * At the top of "How to connect": where the setup stands, and the way back to it. Going back
 * deletes and resets nothing: the list shows only what's left, with what's done ticked.
 */
@Composable
internal fun BackToSetupCard(onOpenSetup: () -> Unit) {
    val steps = rememberSetupSteps()
    val left = SetupSteps.leftToConnect(steps)
    // Calls, map and music passed over or refused count too: they aren't set up.
    val extras = steps.count {
        it.group != SetupSteps.Group.CONNECT && it.status in setOf(SetupSteps.Status.TODO, SetupSteps.Status.SKIPPED, SetupSteps.Status.BLOCKED)
    }
    val line = when {
        left.isNotEmpty() -> pluralStringResource(
            R.plurals.setup_card_steps_left, left.size, left.size, left.map { stepName(it.id) }.joinToString(stringResource(R.string.common_list_separator)),
        )
        extras > 0 -> pluralStringResource(R.plurals.setup_card_extras_left, extras, extras)
        else -> stringResource(R.string.setup_card_all_done)
    }
    SettingsSection(stringResource(R.string.setup_card_title), footer = stringResource(R.string.setup_card_footer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(line, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onOpenSetup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_card_back)) }
        }
    }
}

/** "Not now" on [SetupLeftCard]: for as long as the app runs, not just until Home comes back. */
private var setupLeftHidden by mutableStateOf(false)

/**
 * On Home, while something needed to connect is still missing (left for later on the first run):
 * what it is, and the way back to the setup. "Not now" hides it until the app opens again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SetupLeftCard(onOpenSetup: () -> Unit) {
    val left = SetupSteps.leftToConnect(rememberSetupSteps())
    if (setupLeftHidden || left.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_left_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.setup_left_body, left.map { stepName(it.id) }.joinToString(stringResource(R.string.common_list_separator))),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Side by side where they fit, one under the other on a narrow phone or with large text.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(onClick = onOpenSetup) { Text(stringResource(R.string.setup_left_continue)) }
                TextButton(onClick = { setupLeftHidden = true }) { Text(stringResource(R.string.common_not_now)) }
            }
        }
    }
}
