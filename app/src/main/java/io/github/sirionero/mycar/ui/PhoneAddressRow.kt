package io.github.sirionero.mycar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.CarBluetooth

/**
 * Settings > Connection: the phone's own Bluetooth address, written by hand, which Android doesn't
 * give apps. Only for the few cars that need it to tell the phone connecting from the one paired
 * with them (CarBluetooth); empty, as by default, nothing changes. A car that, told it, stopped
 * taking the phone's music over Bluetooth isn't told it any more (PhoneAddressCheck): said under
 * the address, until it's saved again.
 */
@Composable
internal fun PhoneAddressRow() {
    val context = LocalContext.current
    var address by remember { mutableStateOf(CarBluetooth.writtenAddress(context)) }
    var keptFromCar by remember { mutableStateOf(CarBluetooth.keptFromSomeCar(context)) }
    var editing by remember { mutableStateOf(false) }
    SettingsRow(
        stringResource(R.string.settings_phone_bt_address),
        Modifier.clickable { editing = true },
        subtitle = address?.let { if (keptFromCar) stringResource(R.string.settings_phone_bt_address_not_told, it) else it }
            ?: stringResource(R.string.settings_phone_bt_address_off),
        icon = ImageVector.vectorResource(R.drawable.sym_bluetooth),
    )
    if (editing) {
        PhoneAddressDialog(
            initial = address.orEmpty(),
            onDismiss = { editing = false },
            onSave = { text ->
                CarBluetooth.writeAddress(context, text).also { saved ->
                    if (saved) {
                        address = CarBluetooth.writtenAddress(context)
                        keptFromCar = CarBluetooth.keptFromSomeCar(context)
                        editing = false
                    }
                }
            },
        )
    }
}

/** The address typed in, checked on Save: [onSave] says whether it was one. Remove forgets the one kept. */
@Composable
private fun PhoneAddressDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Boolean) {
    var text by remember { mutableStateOf(initial) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_phone_bt_address)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    invalid = false
                },
                singleLine = true,
                placeholder = { Text(EXAMPLE) },
                isError = invalid,
                supportingText = {
                    Text(stringResource(if (invalid) R.string.settings_phone_bt_address_invalid else R.string.settings_phone_bt_address_hint))
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { invalid = !onSave(text) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (initial.isNotEmpty()) {
                    TextButton(onClick = { onSave("") }) { Text(stringResource(R.string.common_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/** How an address looks: the same in every language. */
private const val EXAMPLE = "12:34:56:78:9A:BC"
