package io.github.sirionero.mycar.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.CarRoutes
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsTone

/**
 * Under Connection in the car's Settings, only for a car that gets no turns on its instrument
 * cluster ([ClusterCars]): that it gets none, why, and a button to send them again.
 */
@Composable
internal fun ClusterTurnsSetting() {
    val off by ClusterCars.off.collectAsState()
    val car = CarRoutes.car ?: return
    if (car !in off) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.car_settings_info_cluster),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(R.string.car_settings_cluster_no_turns), style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            stringResource(R.string.car_settings_cluster_no_turns_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GsButton(onClick = { ClusterCars.turnsAgain(car) }, tone = GsTone.Tonal, modifier = Modifier.height(56.dp)) {
            Text(stringResource(R.string.car_settings_cluster_turns_again), style = MaterialTheme.typography.titleMedium)
        }
    }
}
