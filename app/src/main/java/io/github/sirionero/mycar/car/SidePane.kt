package io.github.sirionero.mycar.car

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsColors
import io.github.sirionero.mycar.car.theme.GsIconBox
import io.github.sirionero.mycar.car.theme.GsTone
import io.github.sirionero.mycar.host.CarAppConnection
import io.github.sirionero.mycar.host.TemplateApp
import io.github.sirionero.mycar.media.MediaApp
import io.github.sirionero.mycar.media.NowPlaying
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow

/**
 * What shares the Home screen with the map: the music player whenever something plays, as it
 * always did, or one app the driver picked in the car's settings - a templated app such as
 * Plancia, or a music app such as Xonos - always there beside the map.
 */
object SidePaneChoice {
    /** The music player beside the map while something plays: the default. */
    const val MUSIC = "music"
    private const val KEY = "car_side_pane"
    private var app: Context? = null
    private val flow = MutableStateFlow(MUSIC)
    val current: StateFlow<String> = flow

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        flow.value = AppSettings.getString(app, KEY, MUSIC)
    }

    fun set(choice: String) {
        flow.value = choice
        app?.let { AppSettings.putString(it, KEY, choice) }
    }
}

/** Templated apps other than maps, whose side the map already is, music apps, and Plancia inside myCar. */
internal val Entry.fitsBesideMap: Boolean
    get() = (template != null && !template.isNavigation) || media != null || builtIn == BuiltInApps.Plancia

/** The app chosen to sit beside the map, or null for the music player - or once that app is gone. */
@Composable
internal fun rememberSidePaneApp(): Entry? {
    val choice by SidePaneChoice.current.collectAsState()
    val entries = rememberAllEntries()
    if (choice == SidePaneChoice.MUSIC) return null
    return remember(choice, entries) {
        val id = withXonosInside(withPlanciaInside(choice, entries), entries)
        entries.find { it.componentId == id && it.fitsBesideMap }
    }
}

/**
 * A templated app beside the map, under a bar with its name and Close. It runs on the connection
 * an app opened full screen from the grid uses too: on Home the pane takes it back, so it always
 * shows the app chosen for it. Close puts the pane away for the drive, as the player's X does.
 */
@Composable
internal fun SideAppPane(app: TemplateApp, frame: CarEnvironment.Frame, modifier: Modifier) {
    val navigator = LocalCarNavigator.current
    val status by CarServices.browse.status.collectAsState()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(app.component) {
        val live = CarServices.browse.status.value.phase in LIVE
        if (!live || CarServices.browse.connectedComponent != app.component) CarServices.connectBrowse(app, frame)
        started = true
    }
    Box(modifier.clip(RoundedCornerShape(MyCarRadius.Card)), contentAlignment = Alignment.Center) {
        if (status.phase == CarAppConnection.Phase.REJECTED || status.phase == CarAppConnection.Phase.FAILED) {
            Text(
                "${app.label} would not start.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        } else if (started && status.phase == CarAppConnection.Phase.IDLE) {
            // Closed from the dock's app menu: the pane waits to be asked, rather than starting
            // the app straight back up against the driver's wishes.
            GsButton(onClick = { CarServices.connectBrowse(app, frame) }, tone = GsTone.Tonal) {
                Text("Open ${app.label}")
            }
        } else {
            CarAppStage(CarServices.browse, status, frame, endLabel = "Close", onDisconnect = navigator::closeSidePanel)
        }
    }
}

/**
 * A music app beside the map: its player, there even before anything plays, so the first tap
 * starts it. It never takes the car from another music app already in use.
 */
@Composable
internal fun SideMediaPane(app: MediaApp, now: NowPlaying, modifier: Modifier) {
    LaunchedEffect(app.component) {
        // After the drive has picked its music: what's already playing on the phone comes first.
        CarServices.mediaDecided.first { it }
        if (CarServices.mediaApp.value == null) CarServices.openMedia(app)
    }
    GlassDockedPlayer(now, modifier)
}

private val LIVE = setOf(CarAppConnection.Phase.BINDING, CarAppConnection.Phase.HANDSHAKE, CarAppConnection.Phase.RUNNING)

/** The car settings' row for what shares the screen with the map: music, then each app that fits. */
@Composable
internal fun SidePanePicker() {
    val current by SidePaneChoice.current.collectAsState()
    val apps = rememberAllEntries().filter { it.fitsBesideMap && it.componentId != null }.sortedBy { it.label.lowercase() }
    val options = listOf(SidePaneChoice.MUSIC to "Music") + apps.mapNotNull { e -> e.componentId?.let { it to e.label } }
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Beside the map", style = MaterialTheme.typography.titleMedium, color = scheme.primary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { (id, label) ->
                val on = id == current
                GsIconBox(
                    onClick = { SidePaneChoice.set(id) },
                    modifier = Modifier.widthIn(min = 150.dp).height(64.dp),
                    colors = if (on) GsColors(scheme.primaryContainer, scheme.onPrimaryContainer)
                    else GsColors(scheme.surfaceContainerHigh, scheme.onSurface),
                    latched = on,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
        Text(
            "Music puts the player beside the map while something plays. An app stays beside the map; " +
                "close it for the drive, and the button at the foot of the dock brings it back from the app grid.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
