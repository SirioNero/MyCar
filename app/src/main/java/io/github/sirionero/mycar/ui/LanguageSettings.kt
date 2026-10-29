package io.github.sirionero.mycar.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppLanguage
import io.github.sirionero.mycar.R

/**
 * Settings: the language of myCar - the phone's, or one of Xonos's nine - for the phone, the car's
 * screen and the voice assistant (see [AppLanguage]).
 */
@Composable
internal fun LanguageSection() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    // Read again when the language changes: Android 13 applies it without starting myCar again.
    val config = LocalConfiguration.current
    val chosen = remember(open, config) { AppLanguage.chosen(context) }
    val phone = AppLanguage.of(AppLanguage.phoneLocale())
    val phoneLabel = stringResource(R.string.language_phone, phone?.label ?: AppLanguage.ENGLISH.label)
    SettingsSection(stringResource(R.string.language_title), footer = stringResource(R.string.language_footer)) {
        SettingsRow(
            chosen?.label ?: phoneLabel,
            Modifier.clickable { open = true },
            icon = ImageVector.vectorResource(R.drawable.sym_language),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LanguageChoice(phoneLabel, chosen == null) { pick(context, null) { open = false } }
                    AppLanguage.entries.forEach { language ->
                        LanguageChoice(language.label, chosen == language) { pick(context, language) { open = false } }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.language_close)) } },
        )
    }
}

private fun pick(context: android.content.Context, language: AppLanguage?, close: () -> Unit) {
    close()
    (context as? Activity)?.let { AppLanguage.choose(it, language) }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}
