package io.github.sirionero.mycar.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsTone
import io.github.sirionero.mycar.host.CarAppConnection
import io.github.sirionero.mycar.host.HostRefusals
import io.github.sirionero.mycar.host.TemplateApp

/**
 * Why a car app that turned myCar down as its host (see HostRefusals) isn't started where it would
 * start by itself - beside the map, in a part of a page - or null for one that hasn't. Read again as
 * its connection moves on ([phase]): an app that accepts myCar is forgotten.
 */
@Composable
internal fun rememberRefusal(app: TemplateApp, phase: CarAppConnection.Phase): String? {
    val context = LocalContext.current
    return remember(app.component, app.label, phase) { HostRefusals.why(context, app.component.packageName, app.label) }
}

/**
 * A car app in a part of a page that turned myCar down as its host: why, in plain words, and a tap
 * to try it all the same. It scrolls in a small part rather than lose the button.
 */
@Composable
internal fun RefusedAppPart(label: String, why: String, onRetry: () -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MyCarSpace.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
    ) {
        Text(stringResource(R.string.car_home_app_failed, label), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        GsButton(onClick = onRetry, tone = GsTone.Tonal) { Text(stringResource(R.string.common_retry)) }
    }
}
