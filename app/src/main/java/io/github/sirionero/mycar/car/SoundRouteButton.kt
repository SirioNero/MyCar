package io.github.sirionero.mycar.car

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.AudioRoute

/**
 * In the player: which way the sound goes to the car now, Bluetooth or through myCar, and a tap
 * to send it the other way. The choice is kept for this car (see [io.github.sirionero.mycar.audio.CarRoutes]).
 */
@Composable
internal fun SoundRouteButton(size: Dp) {
    val throughMyCar by CarSettings.pipeAudio.collectAsState()
    PlainButton(
        onClick = { CarSettings.setAudioRoute(if (throughMyCar) AudioRoute.BLUETOOTH else AudioRoute.CAR_LINK) },
        description = stringResource(if (throughMyCar) R.string.car_player_sound_mycar else R.string.car_player_sound_bluetooth),
        size = size,
    ) {
        Icon(
            painterResource(if (throughMyCar) R.drawable.sym_directions_car else R.drawable.sym_bluetooth),
            null,
            Modifier.size(size * 0.52f),
        )
    }
}
