package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.car.theme.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.media.session.PlaybackState
import io.github.sirionero.mycar.media.MediaArt
import kotlinx.coroutines.delay
import androidx.compose.material3.Icon
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.sirionero.mycar.media.ArtColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.host.CarAppConnection
import io.github.sirionero.mycar.media.CarMedia
import io.github.sirionero.mycar.media.NowPlaying
import kotlinx.coroutines.launch

/**
 * The home screen: the map on the left, the docked player on the right when asked for.
 *
 * The map is whatever navigation app was used last, brought back on its own. The player sits in
 * the nav bar's pill until its dock button moves it here; the column closes itself back to a
 * full-width map once nothing is playing.
 */
@Composable
fun CarHome() {
    val frame by CarEnvironment.frame.collectAsStateWithLifecycle()
    val navStatus by CarServices.nav.status.collectAsStateWithLifecycle()
    val phase by CarServices.media.phase.collectAsStateWithLifecycle()
    val now by CarServices.media.now.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { CarServices.autostart(frame) }
    LaunchedEffect(phase) { if (phase == CarMedia.Phase.READY) CarServices.autoplayIfDue() }

    val navigator = LocalCarNavigator.current
    val panel = navigator.sidePanel

    // Coming back to the map from another screen: the map stayed loaded underneath, so its surface
    // is only replaced if it died meanwhile. The first composition is skipped, since the surface
    // is only just being lent.
    val onHome = navigator.current == CarScreen.Home
    val seenHome = remember { booleanArrayOf(false) }
    LaunchedEffect(onHome) {
        if (onHome && seenHome[0]) CarServices.nav.replaceDeadSurface()
        if (onHome) seenHome[0] = true
    }
    // Beside the map: the app chosen for it in the car's settings, always; or, by default, the
    // music player while something plays.
    val sideApp = rememberSidePaneApp()
    val splitScreen = panel != SidePanel.NONE && (sideApp != null || (phase == CarMedia.Phase.READY && now.isActive))

    // The map's page among the driver's own (see HomePager). No outer padding on any side, split
    // screen or not - only the gap between the two panes when the player is sharing the screen
    // with the map.
    HomePager(onHome) { inView ->
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .weight(if (splitScreen) 2.1f else 1f)
                .fillMaxHeight(),
        ) {
            MapPane(navStatus, frame)
        }
        if (splitScreen) {
            val side = Modifier.weight(1f).fillMaxHeight()
            val sideTemplate = sideApp?.template
            val sideMedia = sideApp?.media
            when {
                // Only while its page is in view on Home: an app opened full screen from the grid,
                // or on another page, uses the same connection, and two stages on it would pull its
                // surface back and forth.
                sideTemplate != null ->
                    if (inView) SideAppPane(sideTemplate, frame, side) else AppPlaceholder(sideApp?.label.orEmpty(), sideApp?.icon, side)
                // Plancia inside myCar: its one page goes wherever it's on screen, so here too only in view.
                sideApp?.builtIn == BuiltInApps.Plancia ->
                    if (inView) PlanciaPane(side) else AppPlaceholder("Plancia", null, side, builtInId = BuiltInApps.Plancia.id)
                panel == SidePanel.LYRICS -> SideLyrics(now, side)
                sideMedia != null -> SideMediaPane(sideMedia, now, side)
                else -> GlassDockedPlayer(now, side)
            }
        }
    }
    }
}

@Composable
private fun MapPane(status: CarAppConnection.Status, frame: CarEnvironment.Frame) {
    val navigator = LocalCarNavigator.current
    if (status.phase == CarAppConnection.Phase.RUNNING) {
        CarAppStage(CarServices.nav, status, frame, embedded = true) { CarServices.stopNav() }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val hasLast = CarSettings.lastNav.value != null
        val hasNavApps by CarServices.hasNavApps.collectAsStateWithLifecycle()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        when (status.phase) {
            CarAppConnection.Phase.BINDING, CarAppConnection.Phase.HANDSHAKE ->
                Text("Starting ${status.app ?: "the map"}…", style = MaterialTheme.typography.titleMedium)
            CarAppConnection.Phase.REJECTED, CarAppConnection.Phase.FAILED -> {
                Text("${status.app ?: "The map app"} would not start.", style = MaterialTheme.typography.titleMedium)
                status.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                GsButton(onClick = navigator::apps, tone = GsTone.Tonal) { Text("Choose another app") }
            }
            else -> if (!hasNavApps) {
                // Nothing to auto-select and nothing to pick from Car apps either - this is the
                // only case worth a distinct screen, since every other IDLE case resolves itself.
                Text("No mapping apps found.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Install a navigation app that supports Android Auto and it will start here on its own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (hasLast) {
                // IDLE with a last app on record means the driver stopped it themselves - it will
                // not come back on its own, so the screen must offer a way to bring it back rather
                // than sit on a "starting" message that never resolves. Right after connecting it
                // means autostart hasn't picked the map yet: that gets a moment first, or every
                // drive would open on "Map stopped." for an instant.
                val stopped by produceState(CarServices.navStoppedByDriver) {
                    delay(MAP_START_GRACE_MS)
                    value = true
                }
                if (stopped) {
                    Text("Map stopped.", style = MaterialTheme.typography.titleMedium)
                    GsButton(onClick = { scope.launch { CarServices.reconnectNav(frame) } }, tone = GsTone.Tonal) { Text("Reopen") }
                    GsButton(onClick = { navigator.apps() }, tone = GsTone.Tonal) { Text("Car apps") }
                }
            } else {
                // Autostart is still picking one - a navigation app is installed, so this resolves
                // to a "Starting..." state itself, in the next frame or two.
            }
        }
    }
}

/** How long the map may sit idle at the start of a drive before Home offers to reopen it. */
private const val MAP_START_GRACE_MS = 2_000L

/**
 * Lyrics alone beside the map, big enough to read at a glance. No art: that space goes to a
 * larger title and artist instead. The player goes back to the nav bar meanwhile, whose dock
 * button swaps these back out for the controls.
 */
@Composable
private fun SideLyrics(now: NowPlaying, modifier: Modifier) {
    val navigator = LocalCarNavigator.current
    val context = LocalContext.current
    val art by produceState(now.art, now.art, now.artUri) { value = now.art ?: MediaArt.load(context, now.artUri) }
    Surface(modifier = modifier, color = rememberArtColor(art, MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        now.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        now.artist, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                GsIconButton(Icons.Filled.Close, "Hide lyrics", navigator::closeSidePanel, size = 36.dp, iconSize = 20.dp)
            }
            Spacer(Modifier.height(12.dp))
            LyricsPanel(now, Modifier.weight(1f).fillMaxWidth(), lineStyle = MaterialTheme.typography.headlineSmall)
        }
    }
}
