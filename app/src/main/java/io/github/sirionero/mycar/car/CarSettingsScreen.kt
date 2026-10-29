package io.github.sirionero.mycar.car


import io.github.sirionero.mycar.car.theme.*
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.car.theme.GsColors
import io.github.sirionero.mycar.car.theme.GsIconBox
import io.github.sirionero.mycar.car.theme.GsThemes
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.AudioRoute
import io.github.sirionero.mycar.ui.CertSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings that make sense while sitting in the car. The web page URL is edited here, with the
 * car's own on-screen keyboard ([CarKeyboard]); certificate management still needs a real
 * keyboard and file picker, so it stays on the phone's Settings screen.
 */
@Composable
fun CarSettingsScreen() {
    val scale by CarSettings.scale.collectAsState()
    val open by CarSettings.openOnConnect.collectAsState()
    val night by CarSettings.nightMode.collectAsState()
    val units by CarSettings.units.collectAsState()
    val whatsNew by CarSettings.whatsNewAfterUpdates.collectAsState()
    val audioRoute by CarSettings.audioRoute.collectAsState()
    val speakDirections by CarSettings.speakDirections.collectAsState()
    val bluetoothWarning by CarSettings.bluetoothWarning.collectAsState()
    val locationWarning by CarSettings.locationWarning.collectAsState()
    val autoplay by CarSettings.autoplay.collectAsState()
    val theme by CarSettings.appTheme.collectAsState()
    val uiTheme by CarSettings.uiTheme.collectAsState()
    val experimentalFeatures by CarSettings.experimentalFeaturesEnabled.collectAsState()
    val carSensors by CarSettings.carSensorsEnabled.collectAsState()
    val systemSpeech by CarSettings.systemSpeech.collectAsState()
    val display by CarEnvironment.display.collectAsState()
    val vehicle by CarEnvironment.vehicle.collectAsState()
    val context = LocalContext.current
    val cert by produceState<CertSummary?>(null) {
        value = withContext(Dispatchers.Default) { CertSummary.read(context) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(stringResource(R.string.common_settings))

        ChoiceRow(
            stringResource(R.string.car_settings_open_on_connect),
            listOf(CarSettings.HOME to stringResource(R.string.car_settings_open_home)) + CarApps.all.filter { it.id != "settings" }.map { it.id to it.shownLabel },
            open,
        ) { CarSettings.setOpenOnConnect(it) }

        SidePanePicker()

        HomePagesSettings()

        ChoiceRow(
            stringResource(R.string.car_settings_size),
            listOf(
                0.83f to stringResource(R.string.car_settings_size_extra_small),
                0.9f to stringResource(R.string.car_settings_size_small),
                1f to stringResource(R.string.car_settings_size_normal),
                1.25f to stringResource(R.string.car_settings_size_large),
                1.5f to stringResource(R.string.car_settings_size_extra_large),
            ),
            scale,
        ) { CarSettings.setScale(it) }

        ChoiceRow(
            stringResource(R.string.car_settings_app_theme),
            listOf(
                AppTheme.PHONE to stringResource(R.string.car_settings_app_theme_phone),
                AppTheme.LIGHT to stringResource(R.string.car_settings_app_theme_light),
                AppTheme.DARK to stringResource(R.string.car_settings_app_theme_dark),
            ),
            theme,
        ) { CarSettings.setAppTheme(it) }

        WallpaperPicker()

        ChoiceRow(
            stringResource(R.string.car_settings_controls_theme),
            GsThemes.builtIn.map { it.id to it.shownName },
            uiTheme,
        ) { CarSettings.setUiTheme(it) }

        ChoiceRow(
            stringResource(R.string.car_settings_map_day_night),
            listOf(
                NightMode.AUTO to stringResource(R.string.car_settings_map_auto),
                NightMode.DAY to stringResource(R.string.car_settings_map_day),
                NightMode.NIGHT to stringResource(R.string.car_settings_map_night),
            ),
            night,
        ) { CarSettings.setNightMode(it) }

        ChoiceRow(
            stringResource(R.string.car_settings_units),
            listOf(
                Units.AUTO to stringResource(R.string.car_settings_units_auto),
                Units.IMPERIAL to stringResource(R.string.car_settings_units_imperial),
                Units.METRIC to stringResource(R.string.car_settings_units_metric),
            ),
            units,
        ) { CarSettings.setUnits(it) }

        ChoiceRow(
            stringResource(R.string.car_settings_autoplay),
            listOf(false to stringResource(R.string.common_off), true to stringResource(R.string.common_on)),
            autoplay,
        ) { CarSettings.setAutoplay(it) }

        ChoiceRow(
            stringResource(R.string.car_settings_car_audio),
            listOf(
                AudioRoute.AUTO to stringResource(R.string.car_settings_car_audio_auto),
                AudioRoute.BLUETOOTH to "Bluetooth",
                AudioRoute.CAR_LINK to stringResource(R.string.car_settings_car_audio_through_mycar),
            ),
            audioRoute,
        ) { CarSettings.setAudioRoute(it) }
        Text(
            stringResource(R.string.car_settings_car_audio_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Shown with USB audio too: calls reach the car only over Bluetooth either way.
        ChoiceRow(
            stringResource(R.string.car_settings_bluetooth_warning),
            listOf(false to stringResource(R.string.common_off), true to stringResource(R.string.common_on)),
            bluetoothWarning,
        ) { CarSettings.setBluetoothWarning(it) }
        ChoiceRow(
            stringResource(R.string.car_settings_location_warning),
            listOf(false to stringResource(R.string.common_off), true to stringResource(R.string.common_on)),
            locationWarning,
        ) { CarSettings.setLocationWarning(it) }

        ChoiceRow(
            stringResource(R.string.car_settings_spoken_directions),
            listOf(false to stringResource(R.string.common_off), true to stringResource(R.string.common_on)),
            speakDirections,
        ) { CarSettings.setSpeakDirections(it) }
        Text(
            stringResource(R.string.car_settings_spoken_directions_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AssistantPicker()

        ExperimentalFeatureRow(
            title = stringResource(R.string.car_settings_system_speech),
            description = stringResource(R.string.car_settings_system_speech_note),
            checked = systemSpeech,
        ) { CarSettings.setSystemSpeech(it) }

        WebPageSetting()

        ScreenFit(vehicle)

        ChoiceRow(
            stringResource(R.string.car_settings_experimental),
            listOf(false to stringResource(R.string.car_settings_experimental_hide), true to stringResource(R.string.car_settings_experimental_show)),
            experimentalFeatures,
        ) { CarSettings.setExperimentalFeaturesEnabled(it) }
        if (experimentalFeatures) {
            ExperimentalFeaturesList(carSensors)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.car_settings_connection), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Info(stringResource(R.string.car_settings_info_phone), "${Build.MANUFACTURER} ${Build.MODEL}")
            Info(
                stringResource(R.string.car_settings_info_vehicle),
                if (vehicle == UNKNOWN_VEHICLE) stringResource(R.string.car_settings_unknown_vehicle) else vehicle.ifEmpty { "-" },
            )
            Info(stringResource(R.string.car_settings_info_video), display.ifEmpty { "-" })
            Info(stringResource(R.string.car_settings_info_certificate), cert?.headline ?: stringResource(R.string.car_settings_checking))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.car_settings_about), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Info(stringResource(R.string.car_settings_version), io.github.sirionero.mycar.BuildConfig.VERSION_NAME)
            Info(stringResource(R.string.car_settings_build), io.github.sirionero.mycar.BuildConfig.VERSION_CODE.toString())
            ExperimentalFeatureRow(
                stringResource(R.string.car_settings_whats_new_after_updates),
                stringResource(R.string.car_settings_whats_new_after_updates_note),
                whatsNew,
            ) { CarSettings.setWhatsNewAfterUpdates(it) }
            GsButton(onClick = CarSettings::openWhatsNew, tone = GsTone.Tonal, modifier = Modifier.height(56.dp)) {
                Text(stringResource(R.string.car_settings_whats_new), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The narrowest a choice gets before the row scrolls instead. */
private val ROOMY_CHOICE = 110.dp

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        // Too narrow for each name whole (six choices at Extra large broke "Browse/r"): wider choices
        // that scroll, as the ones beside the map do.
        var width by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val roomy = width == 0 || with(density) { (width.toDp() - 12.dp * (options.size - 1)) / options.size } >= ROOMY_CHOICE
        val scroll = rememberScrollState()
        Row(
            Modifier.fillMaxWidth().onSizeChanged { width = it.width }.then(if (roomy) Modifier else Modifier.horizontalScroll(scroll)),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { (value, label) ->
                val on = value == selected
                val scheme = MaterialTheme.colorScheme
                GsIconBox(
                    onClick = { onSelect(value) },
                    modifier = (if (roomy) Modifier.weight(1f) else Modifier.widthIn(min = ROOMY_CHOICE))
                        .height(64.dp),
                    colors = if (on) GsColors(scheme.primaryContainer, scheme.onPrimaryContainer)
                    else GsColors(scheme.surfaceContainerHigh, scheme.onSurface),
                    latched = on,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = if (roomy) Modifier else Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Individually-switched experimental features, shown once the driver has opted into
 * experimental features as a whole. Each is its own row - a title, what it does, and a switch -
 * so the list reads fine whether there's one entry or several.
 */
@Composable
private fun ExperimentalFeaturesList(carSensors: Boolean) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ExperimentalFeatureRow(
            title = stringResource(R.string.car_vehicle_title),
            description = stringResource(R.string.car_settings_vehicle_data_note),
            checked = carSensors,
        ) { CarSettings.setCarSensorsEnabled(it) }
    }
}

@Composable
private fun ExperimentalFeatureRow(
    title: String,
    description: String,
    checked: Boolean,
    warning: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            GsSwitch(checked, onCheckedChange)
        }
    }
}

/** The URL (or raw HTML) the Browser app shows. Edited with [CarKeyboard] - no phone in hand needed. */
@Composable
private fun WebPageSetting() {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(AppSettings.startupUrl(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.car_settings_web_page), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Surface(
            onClick = { editing = !editing },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                url.ifEmpty { stringResource(R.string.car_settings_web_page_default) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (url.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Text(
            stringResource(R.string.car_settings_web_page_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editing) {
            CarKeyboard(
                url,
                onTextChange = {
                    url = it
                    AppSettings.setStartupUrl(context, it)
                },
                onSubmit = { editing = false },
                onDismiss = { editing = false },
            )
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The vehicle's name when no profile matched (MyCarRunner): not a profile to save to. Shown in words. */
private const val UNKNOWN_VEHICLE = "Unknown vehicle"

/**
 * Nudges how much of the frame the UI keeps clear at the top and bottom, live, so a car's
 * display can be fitted from the driver's seat. Save writes it to this vehicle's profile.
 */
@Composable
private fun ScreenFit(vehicle: String) {
    val insets by CarEnvironment.insets.collectAsState()
    val frame by CarEnvironment.frame.collectAsState()
    val context = LocalContext.current.applicationContext
    var saved by remember { mutableStateOf<String?>(null) }
    val known = vehicle.isNotEmpty() && vehicle != UNKNOWN_VEHICLE
    val savedText = stringResource(R.string.car_settings_saved_to, vehicle)
    val notSavedText = stringResource(R.string.car_settings_save_failed)

    fun change(new: io.github.sirionero.mycar.Insets) {
        saved = null
        CarEnvironment.setInsets(new)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.car_settings_screen_fit), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Stepper(stringResource(R.string.car_settings_fit_top), insets.top) { change(insets.copy(top = (insets.top + it).coerceAtLeast(0))) }
        Stepper(stringResource(R.string.car_settings_fit_bottom), insets.bottom) { change(insets.copy(bottom = (insets.bottom + it).coerceAtLeast(0))) }
        Text(
            stringResource(R.string.car_settings_usable_height, frame.height - insets.top - insets.bottom, frame.height),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GsButton(
            onClick = {
                val ok = io.github.sirionero.mycar.VehicleProfiles.saveInsets(context, vehicle, insets)
                saved = if (ok) savedText else notSavedText
            },
            enabled = known,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(stringResource(if (known) R.string.car_settings_save_for_vehicle else R.string.car_settings_no_vehicle_profile)) }
        saved?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1.1f))
        StepButton("−5", Modifier.weight(1f)) { onStep(-5) }
        StepButton("−1", Modifier.weight(1f)) { onStep(-1) }
        Text(
            stringResource(R.string.car_settings_pixels, value),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1.3f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepButton("+1", Modifier.weight(1f)) { onStep(1) }
        StepButton("+5", Modifier.weight(1f)) { onStep(5) }
    }
}

@Composable
private fun StepButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    GsIconBox(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        colors = GsColors(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

