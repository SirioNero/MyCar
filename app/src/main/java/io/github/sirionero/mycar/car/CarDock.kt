package io.github.sirionero.mycar.car

import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.design.AppIconShape
import io.github.sirionero.mycar.car.design.GlassTile
import io.github.sirionero.mycar.car.design.MyCarMotion
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import io.github.sirionero.mycar.car.design.wallpaperBackground
import io.github.sirionero.mycar.car.design.rememberWallpaper
import io.github.sirionero.mycar.host.CarAppConnection
import io.github.sirionero.mycar.host.display
import io.github.sirionero.mycar.host.text
import io.github.sirionero.mycar.media.CarMedia
import io.github.sirionero.mycar.media.MediaArt
import io.github.sirionero.mycar.notify.CarNotifications
import java.text.DateFormat
import java.util.Date

/**
 * The dock: a fixed column down the left edge, beside every screen. The clock sits on top - tap it
 * for the dashboard, with the calendar, the weather and the notifications - then the apps that
 * matter right now, and at the bottom the voice assistant, with the app grid button under it.
 *
 * The middle shows, most useful first: the next turn while the map is out of sight, the map, the
 * song playing, the pinned apps and whatever else is open. What doesn't fit on a short screen
 * waits on the app grid. The assistant isn't one of them: it stays put just above the grid button,
 * however many apps there are, so it never moves or drops out. Every slot is a
 * [MyCarSize.TouchTarget] square.
 */
@Composable
internal fun CarDock(navigator: CarNavigator, modifier: Modifier = Modifier) {
    val entries = rememberAllEntries()
    val running = rememberRunningApps()
    val pinnedIds by CarSettings.pinnedApps.collectAsState()
    val frame by CarEnvironment.frame.collectAsState()
    val turn = rememberNextTurn().takeIf { navigator.current != CarScreen.Home || !navigator.onMapPage }
    val media = CarServices.media
    val mediaPhase by media.phase.collectAsState()
    val now by media.now.collectAsState()
    val mediaShown = mediaPhase == CarMedia.Phase.READY && now.isActive

    // A pinned app that's also open keeps its pinned place, marked with a dot. The map has its own
    // slot, and the media app's is the song playing, so neither shows twice.
    val pinned = pinnedIds.mapNotNull { id -> entries.find { it.componentId == id } }
        .filter { running[it.componentId] != RunningSlot.NAV }
    val open = running.keys
        .filter { it !in pinnedIds && running[it] != RunningSlot.NAV && running[it] != RunningSlot.MEDIA }
        .mapNotNull { id -> entries.find { it.componentId == id } }

    // Over a wallpaper the dock is glass: the wallpaper behind it, darkened, and a hairline of
    // light down its edge. Without one, it's a plain dark column.
    val wallpaper = rememberWallpaper()
    Column(
        modifier
            .fillMaxHeight()
            .width(MyCarSize.DockWidth)
            .then(
                if (wallpaper == null) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                else Modifier.wallpaperBackground(wallpaper, dim = DOCK_GLASS_DIM).drawBehind {
                    drawLine(Color.White.copy(alpha = 0.10f), Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height))
                },
            )
            .padding(vertical = MyCarSpace.M),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DockClock(navigator)
        Spacer(Modifier.height(MyCarSpace.S))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val slots = buildList {
                turn?.let { t -> add(DockSlot("turn") { DockTurn(t) { navigator.home() } }) }
                add(DockSlot("map") { DockMap(navigator, entries, running) })
                if (mediaShown) add(DockSlot("media") { DockNowPlaying(navigator) })
                (pinned + open).forEach { entry ->
                    entry.componentId?.let { id -> add(DockSlot(id) { DockApp(entry, running, navigator, frame) }) }
                }
            }
            val fit = ((maxHeight + DockGap) / (MyCarSize.TouchTarget + DockGap)).toInt().coerceAtLeast(0)
            Column(
                verticalArrangement = Arrangement.spacedBy(DockGap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                slots.take(fit).forEach { slot -> key(slot.key) { slot.content() } }
            }
        }
        Spacer(Modifier.height(MyCarSpace.S))
        DockAssistant()
        Spacer(Modifier.height(DockGap))
        // The dock's foot: the app grid, and from the grid back to the map beside the music.
        val onGrid = navigator.current == CarScreen.Apps
        DockItem(
            selected = false,
            onClick = { if (onGrid) navigator.homeWithPlayer() else navigator.apps() },
            description = if (onGrid) "Map and music" else "All apps",
        ) {
            Icon(
                painterResource(if (onGrid) R.drawable.sym_space_dashboard else R.drawable.sym_grid_view),
                contentDescription = null,
                modifier = Modifier.size(DockGlyph),
            )
        }
    }
}

/** One slot in the dock's middle, keyed so a slot keeps its state as others come and go above it. */
private class DockSlot(val key: String, val content: @Composable () -> Unit)

private val DockGap = 6.dp
private val DockGlyph = 30.dp

/**
 * One dock slot, a [MyCarSize.TouchTarget] square (wider for the clock): lit while its screen is
 * up, with a dot on its left while its app runs, and shrinking a touch under the finger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockItem(
    selected: Boolean,
    onClick: () -> Unit,
    description: String?,
    running: Boolean = false,
    width: Dp = MyCarSize.TouchTarget,
    container: Color = Color.Unspecified,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) MyCarMotion.PressedScale else 1f,
        tween(MyCarMotion.PressMs, easing = MyCarMotion.Decelerate),
        label = "dockPress",
    )
    val scheme = MaterialTheme.colorScheme
    val background = when {
        container != Color.Unspecified -> container
        selected -> scheme.surfaceContainerHighest
        else -> Color.Transparent
    }
    Box(
        Modifier
            .size(width = width, height = MyCarSize.TouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(MyCarRadius.Card))
            .background(background)
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { description?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (running) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 2.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(scheme.onSurface),
            )
        }
    }
}

/**
 * The time, with the phone's battery and the temperature under it. Also the way to the dashboard,
 * so a dot marks unread notifications.
 */
@Composable
private fun DockClock(navigator: CarNavigator) {
    val now by rememberNow()
    val battery by CarEnvironment.battery.collectAsState()
    val weather by Weather.state.collectAsState()
    val temp = (weather as? WeatherState.Ready)?.data?.temp
    val unread by CarNotifications.unread.collectAsState()
    val scheme = MaterialTheme.colorScheme
    DockItem(
        selected = navigator.current == CarScreen.Dashboard || navigator.current is CarScreen.Notifications,
        onClick = { navigator.dashboard() },
        description = "Dashboard",
        width = MyCarSize.DockWidth - MyCarSpace.S,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (battery.percent >= 0) {
                    Text("${battery.percent}%", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    if (battery.charging) ChargingGlyph(Modifier.size(11.dp))
                }
                temp?.let {
                    Text(
                        (if (battery.percent >= 0) " · " else "") + "$it°",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
    }
}

/**
 * The map: the running map app's own icon, or a navigation arrow while none runs. Long press
 * offers to close the map app.
 */
@Composable
private fun DockMap(navigator: CarNavigator, entries: List<Entry>, running: Map<String, RunningSlot>) {
    val navId = running.entries.firstOrNull { it.value == RunningSlot.NAV }?.key
    val navEntry = navId?.let { id -> entries.find { it.componentId == id } }
    DockItem(
        selected = navigator.current == CarScreen.Home && navigator.onMapPage,
        onClick = { navigator.home() },
        description = navEntry?.label ?: "Map",
        running = navEntry != null,
        onLongClick = navId?.let { id -> { navigator.showAppMenu(id) } },
    ) {
        val navIcon = navEntry?.icon
        if (navIcon != null) {
            AppIcon(navIcon, null, MyCarSize.DockIcon)
        } else {
            GlassTile(MyCarPalette.Accent, R.drawable.sym_navigation, MyCarSize.DockIcon, glyphSize = DockGlyph)
        }
    }
}

/**
 * The song playing: its cover (or its app's icon), with a small badge saying whether it plays.
 * Tap to put the player beside the map, or take it away again; long press for the full player.
 */
@Composable
private fun DockNowPlaying(navigator: CarNavigator) {
    val now by CarServices.media.now.collectAsState()
    val mediaApp by CarServices.mediaApp.collectAsState()
    val context = LocalContext.current
    val art by produceState(now.art, now.art, now.artUri) { value = now.art ?: MediaArt.load(context, now.artUri) }
    val appIcon = remember(mediaApp) { mediaApp?.let { findEntry(it.component.flattenToString())?.icon } }
    // With an app chosen to sit beside the map instead, the player isn't there to show or hide:
    // the song opens the full player.
    val appBesideMap = rememberSidePaneApp()?.template != null
    DockItem(
        selected = navigator.current == CarScreen.Media ||
            (!appBesideMap && navigator.current == CarScreen.Home && navigator.onMapPage && navigator.sidePanel == SidePanel.CONTROLS),
        onClick = { if (appBesideMap) navigator.media() else navigator.togglePlayerDock() },
        description = listOf(now.title, now.artist).filter { it.isNotBlank() }.joinToString(" - ").ifEmpty { "Now playing" },
        onLongClick = { navigator.media() },
    ) {
        Box(Modifier.size(MyCarSize.DockIcon)) {
            val cover = art
            if (cover != null) {
                Image(
                    cover.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(MyCarSize.DockIcon).clip(AppIconShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AppIcon(appIcon, null, MyCarSize.DockIcon)
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (now.playing) R.drawable.sym_pause else R.drawable.sym_play_arrow),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** A pinned or open app. Tapping brings it back without reconnecting if it's open; long press opens its menu. */
@Composable
private fun DockApp(entry: Entry, running: Map<String, RunningSlot>, navigator: CarNavigator, frame: CarEnvironment.Frame) {
    val id = entry.componentId ?: return
    val slot = running[id]
    DockItem(
        selected = slot != null && slot.isShowing(navigator.current),
        onClick = { openApp(entry, slot, navigator, frame) },
        description = entry.label,
        running = slot != null,
        onLongClick = { navigator.showAppMenu(id) },
    ) {
        AppIcon(entry.icon, entry.builtIn?.id, MyCarSize.DockIcon)
    }
}

/** The voice assistant, lit while it listens or speaks. */
@Composable
private fun DockAssistant() {
    val state by CarAssistant.state.collectAsState()
    val active = state.phase != CarAssistant.Phase.IDLE
    DockItem(selected = active, onClick = { CarAssistant.start() }, description = "Voice assistant") {
        Icon(painterResource(R.drawable.sym_mic), contentDescription = null, modifier = Modifier.size(DockGlyph))
    }
}

/** The next turn while the map is out of sight: the arrow and how far. Tapping goes back to the map. */
@Composable
private fun DockTurn(turn: NextTurn, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    DockItem(
        selected = false,
        onClick = onClick,
        description = listOf(turn.distance, turn.road).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { "Next turn" },
        width = MyCarSize.DockWidth - MyCarSpace.S,
        container = scheme.primary,
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.onPrimary) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CarGlyph(turn.icon, Modifier.size(28.dp))
            if (turn.distance.isNotEmpty()) {
                Text(
                    turn.distance,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
          }
        }
    }
}

/** The next manoeuvre, as the running nav app last described it. */
internal class NextTurn(val icon: CarIcon?, val distance: String, val road: String)

/**
 * Read straight off the nav app's own template: a navigation app keeps sending its routing card
 * while it's running, whether or not the map is the screen being shown.
 */
@Composable
internal fun rememberNextTurn(): NextTurn? {
    val status by CarServices.nav.status.collectAsState()
    val template by CarServices.nav.template.collectAsState()
    // Read so a change of units redraws the distance straight away, not at the next update.
    CarSettings.units.collectAsState().value
    if (status.phase != CarAppConnection.Phase.RUNNING) return null
    val routing = (template as? NavigationTemplate)?.navigationInfo as? RoutingInfo ?: return null
    if (routing.isLoading) return null
    val step = routing.currentStep ?: return null
    return NextTurn(
        icon = step.maneuver?.icon,
        distance = routing.currentDistance.display(),
        road = step.road.text().ifEmpty { step.cue.text() },
    )
}

/**
 * A filled lightning bolt, next to the battery percentage instead of the word "charging" - drawn
 * rather than an icon, as it has to sit inside a line of small text.
 */
@Composable
internal fun ChargingGlyph(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bolt = Path().apply {
            moveTo(w * 0.58f, 0f)
            lineTo(w * 0.12f, h * 0.58f)
            lineTo(w * 0.46f, h * 0.58f)
            lineTo(w * 0.42f, h)
            lineTo(w * 0.88f, h * 0.40f)
            lineTo(w * 0.54f, h * 0.40f)
            close()
        }
        drawPath(bolt, tint)
    }
}

/** Wall-clock time, ticking on the minute: no reason to redraw (and re-encode) more often. */
@Composable
internal fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        kotlinx.coroutines.delay(60_000 - value % 60_000)
    }
}

/** How much darker the wallpaper is behind the dock's glass than on the screen beside it. */
private const val DOCK_GLASS_DIM = 0.55f
