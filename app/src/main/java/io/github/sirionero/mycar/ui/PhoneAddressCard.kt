package io.github.sirionero.mycar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.PhoneAddressCheck

/**
 * On Home once myCar stopped telling a car the phone's Bluetooth address, because told it, the car
 * stopped taking the phone's music over Bluetooth (PhoneAddressCheck): what happened, what comes next,
 * and the way back. A quiet card, as nothing is broken any more; it stays until OK.
 */
@Composable
internal fun PhoneAddressCard() {
    val context = LocalContext.current
    val shown by remember { PhoneAddressCheck.notice(context) }.collectAsStateWithLifecycle()
    if (!shown) return
    Spacer(Modifier.height(16.dp))
    SettingsCard {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.phone_address_not_told_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.phone_address_not_told_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = { PhoneAddressCheck.noticeSeen(context) }, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.common_ok))
            }
        }
    }
}
