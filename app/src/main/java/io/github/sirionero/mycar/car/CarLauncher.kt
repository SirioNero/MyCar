package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.car.theme.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.min
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.design.MyCarMotion
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.design.squareAppIcon
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import io.github.sirionero.mycar.host.KnownApps
import io.github.sirionero.mycar.host.CarAppCatalog
import io.github.sirionero.mycar.host.TemplateApp
import io.github.sirionero.mycar.media.MediaApp
import io.github.sirionero.mycar.media.MediaCatalog
import io.github.sirionero.mycar.notify.MessagingApp
import io.github.sirionero.mycar.notify.MessagingCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One app on the launcher grid, with what tapping and holding it do. */
private class Tile(
    val entry: Entry,
    val label: String,
    /** Known not to work; drawn with a red X instead of being left off the launcher. */
    val broken: Boolean = false,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/** A launcher app's data, kept apart from [Tile] so it can be cached without capturing click state. */
internal class Entry(
    val label: String,
    val icon: ImageBitmap?,
    val template: TemplateApp? = null,
    val media: MediaApp? = null,
    val builtIn: BuiltInApp? = null,
    val messaging: MessagingApp? = null,
) {
    /** What a pin or a nav-bar shortcut remembers this entry as; null for nothing pinnable. */
    val componentId: String?
        get() = builtIn?.let { BUILT_IN_PREFIX + it.id }
            ?: messaging?.let { MESSAGING_PREFIX + it.packageName }
            ?: (template?.component ?: media?.component)?.flattenToString()

    val packageName: String?
        get() = template?.component?.packageName ?: media?.component?.packageName ?: messaging?.packageName
}

/** A messaging app has no component of its own to open, so its pin carries the package instead. */
internal const val MESSAGING_PREFIX = "messages:"

/**
 * The installed car and media apps with their icons. Scanning the package manager and decoding
 * icons is slow, so [Prefetch] does it ahead of time and again at the start of each car session,
 * which is when new installs are picked up.
 */
internal object LauncherCache {
    /** Observed, not read once: the nav bar is drawn before the car session's rescan finishes,
     * and an app installed since the last scan has to show up there when it does. */
    private val flow = MutableStateFlow<List<Entry>?>(null)
    val state: StateFlow<List<Entry>?> = flow.asStateFlow()

    val entries: List<Entry>? get() = flow.value

    /** Returns the scan if there is one; [force] rescans. Synchronized so two callers share one scan. */
    @Synchronized
    fun load(context: Context, force: Boolean = false): List<Entry> {
        if (!force) entries?.let { return it }
        // Square and edge to edge, for the squircle frame every icon in the car UI sits in.
        fun iconOf(pkg: String) = runCatching {
            squareAppIcon(context.packageManager.getApplicationIcon(pkg), ICON_PX).asImageBitmap()
        }.getOrNull()

        val navApps = CarAppCatalog.installed(context)
        val mediaApps = MediaCatalog.installed(context)
        // Some apps (Spotify among them) ship both a Car App Library template and a legacy
        // MediaBrowserService, which otherwise land as two identically-labeled tiles with no
        // way to tell them apart. They're genuinely different screens - browsing versus Now
        // Playing - so only the label needs disambiguating, not the tile itself.
        val mediaPackages = mediaApps.map { it.component.packageName }.toSet()

        val messagingApps = MessagingCatalog.installed(context)
        // Google Messages ships its own car app, which won't start under myCar. myCar's
        // own Messages screen does the same job, so a messenger gets only that tile.
        val messagingPackages = messagingApps.map { it.packageName }.toSet()

        val nav = navApps
            .filter { it.component.packageName !in messagingPackages }
            // An app whose template screen is rejected but whose player works through myCar
            // (Spotify) shows only its player tile - a "· Browse" tile beside it would just report
            // that the car screen isn't supported.
            .filter { !(KnownApps.screenBroken(it.component.packageName) && KnownApps.playerWorks(it.component.packageName)) }
            .map {
                val label = if (it.component.packageName in mediaPackages) "${it.label} · Browse" else it.label
                Entry(label, iconOf(it.component.packageName), template = it)
            }
        val media = mediaApps.map { Entry(it.label, iconOf(it.component.packageName), media = it) }
        // A messenger that is also a car media app keeps only its player tile.
        val messaging = messagingApps
            .filter { it.packageName !in mediaPackages }
            .map { Entry(it.label, iconOf(it.packageName), messaging = it) }
        return (nav + media + messaging).sortedBy { it.label.lowercase() }.also { flow.value = it }
    }
}

/** Every app the launcher knows: myCar's own and the installed ones, rescans included. */
@Composable
internal fun rememberAllEntries(): List<Entry> {
    val context = LocalContext.current
    val installed by LauncherCache.state.collectAsState()
    LaunchedEffect(context) {
        if (LauncherCache.entries == null) withContext(Dispatchers.IO) { LauncherCache.load(context) }
    }
    return remember(installed) { BuiltInApps.entries + installed.orEmpty() }
}

/** The launcher's groups, in the order they're laid out. */
private enum class AppGroup { PHONE, MAPS, MUSIC, MESSAGING, WEB, SCREEN_SHARING, OTHER, SETTINGS, EXIT }

private fun groupOf(entry: Entry, mediaPackages: Set<String>): AppGroup {
    entry.builtIn?.let {
        return when (it) {
            BuiltInApps.Phone -> AppGroup.PHONE
            BuiltInApps.Web -> AppGroup.WEB
            BuiltInApps.ScreenSharing -> AppGroup.SCREEN_SHARING
            BuiltInApps.Exit -> AppGroup.EXIT
            else -> AppGroup.SETTINGS
        }
    }
    if (entry.media != null) return AppGroup.MUSIC
    if (entry.messaging != null) return AppGroup.MESSAGING
    val app = entry.template ?: return AppGroup.OTHER
    // A media app's "· Browse" tile sits next to its player.
    if (app.component.packageName in mediaPackages) return AppGroup.MUSIC
    return when (app.kind) {
        "calling" -> AppGroup.PHONE
        "navigation", "poi", "parking", "charging" -> AppGroup.MAPS
        // Template-only media apps may have no legacy browser/player tile to identify them.
        "media" -> AppGroup.MUSIC
        "messaging" -> AppGroup.MESSAGING
        "settings" -> AppGroup.SETTINGS
        else -> AppGroup.OTHER
    }
}

/**
 * The launcher's tiles in the order they're drawn: grouped by [AppGroup], alphabetical inside a
 * group with Settings last of all, then with the driver's own moves from [custom] laid over it.
 * Moved apps only trade places among themselves, so an app installed later still lands in its group.
 */
internal fun launcherOrder(all: List<Entry>, showVehicleData: Boolean, custom: List<String>): List<Entry> {
    val shown = all.filter { it.builtIn !== BuiltInApps.VehicleData || showVehicleData }
    val mediaPackages = shown.mapNotNull { it.media?.component?.packageName }.toSet()
    val grouped = shown.sortedWith(
        compareBy<Entry>({ groupOf(it, mediaPackages) }, { it.builtIn === BuiltInApps.Settings }, { it.label.lowercase() }),
    )
    if (custom.isEmpty()) return grouped
    val rank = custom.withIndex().associate { (i, id) -> id to i }
    fun Entry.rank() = componentId?.let { rank[it] }
    val moved = grouped.filter { it.rank() != null }.sortedBy { it.rank() }.iterator()
    return grouped.map { if (it.rank() != null) moved.next() else it }
}

/** An app by the id a pin or the running list knows it by, built-in or installed. */
internal fun findEntry(id: String): Entry? =
    BuiltInApps.entries.find { it.componentId == id } ?: LauncherCache.entries?.find { it.componentId == id }

/** Rescans the car and media apps now, replacing the cached list; the launcher keeps showing the old one meanwhile. */
fun warmLauncherApps(context: Context) { LauncherCache.load(context, force = true) }

/**
 * Connects or opens [entry] - what tapping its tile does, shared with the nav bar's pinned
 * shortcuts and its open-app icon so there is exactly one place that knows how to launch either
 * kind of app.
 */
internal fun launchEntry(entry: Entry, navigator: CarNavigator, frame: CarEnvironment.Frame) {
    entry.builtIn?.let { it.open(navigator); return }
    entry.template?.let {
        if (it.isNavigation) {
            CarServices.connectNav(it, frame)
            navigator.home()
        } else {
            CarServices.connectBrowse(it, frame)
            navigator.browse(entry.label)
        }
    }
    entry.media?.let { CarServices.openMedia(it); navigator.media() }
    entry.messaging?.let { navigator.messages(it.packageName, it.label) }
}

/** The app launcher: every app as pages of icons, grouped Phone, Maps, Music, Messaging, Web, Screen sharing, Settings. */
@Composable
fun CarLauncher() {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    val frame by CarEnvironment.frame.collectAsState()

    // Painted from the cache at once; scanned only if [Prefetch] has not got there first.
    val all = rememberAllEntries()
    val running = rememberRunningApps()
    val showVehicleData = rememberVehicleDataShown()

    fun tileOf(entry: Entry): Tile {
        // Only apps that don't work get a mark. The X rates an app's own car screens; a messenger
        // shown on myCar's own Messages screen has none, so it never gets one.
        val pkg = entry.packageName?.takeIf { entry.messaging == null }
        val id = entry.componentId
        return Tile(
            entry, entry.label,
            broken = pkg != null && (KnownApps.isBroken(context, pkg) || (entry.template != null && KnownApps.screenBroken(pkg))),
            onLongClick = id?.let { { navigator.showAppMenu(it) } },
        ) { openApp(entry, id?.let { running[it] }, navigator, frame) }
    }

    val appOrder by CarSettings.appOrder.collectAsState()
    val tiles = launcherOrder(all, showVehicleData, appOrder).map(::tileOf)

    AppGrid(tiles)
}

/** Pixel size the launcher decodes app icons at: sharp at the grid's largest, on a dense screen. */
private const val ICON_PX = 192

/**
 * The launcher's pages: as many columns and rows of icons as the screen holds, swiped sideways,
 * with a dot per page underneath. Icons grow to [MyCarSize.GridIconMax] where there's room.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGrid(tiles: List<Tile>) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = MyCarSpace.L, vertical = MyCarSpace.M)) {
        val dotsHeight = 24.dp
        val columns = (maxWidth / MyCarSize.GridCellMinWidth).toInt().coerceIn(3, 8)
        val rows = ((maxHeight - dotsHeight) / MyCarSize.GridCellMinHeight).toInt().coerceIn(1, 4)
        val perPage = columns * rows
        val pages = ((tiles.size + perPage - 1) / perPage).coerceAtLeast(1)
        val cellWidth = maxWidth / columns
        val cellHeight = (maxHeight - dotsHeight) / rows
        val iconSize = min(MyCarSize.GridIconMax, min(cellWidth * 0.62f, (cellHeight - 44.dp).coerceAtLeast(40.dp)))
        val pagerState = rememberPagerState(pageCount = { pages })
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val pageTiles = tiles.drop(page * perPage).take(perPage)
                Column(Modifier.fillMaxSize()) {
                    repeat(rows) { row ->
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            repeat(columns) { column ->
                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    pageTiles.getOrNull(row * columns + column)?.let { AppCell(it, iconSize) }
                                }
                            }
                        }
                    }
                }
            }
            PageDots(pages, pagerState.currentPage, Modifier.height(dotsHeight).fillMaxWidth())
        }
    }
}

/** A dot per page, the current one solid. Nothing at all for a single page. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count <= 1) {
        Spacer(modifier)
        return
    }
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (i == current) color else color.copy(alpha = 0.3f)),
            )
        }
    }
}

/** A red disc with a white X: marks a tile known not to work with myCar yet. */
@Composable
private fun BrokenBadge(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(Color(0xFFD32F2F), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Close, contentDescription = "Doesn't work with myCar", tint = Color.White, modifier = Modifier.size(size * 0.66f))
    }
}

/**
 * One app on the grid: its icon in the squircle frame with its name underneath. Tap opens it, a long
 * press opens its menu (pin to the dock, move, close), and it shrinks a touch under the finger.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(tile: Tile, iconSize: Dp) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) MyCarMotion.PressedScale else 1f,
        tween(MyCarMotion.PressMs, easing = MyCarMotion.Decelerate),
        label = "appPress",
    )
    Column(
        Modifier
            .widthIn(min = MyCarSize.TouchTarget)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(MyCarRadius.Card))
            .combinedClickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onLongClick = tile.onLongClick,
                onClick = tile.onClick,
            )
            .padding(MyCarSpace.S),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(tile.entry.icon, tile.entry.builtIn?.id, iconSize)
            if (tile.broken) {
                // Pinned to the icon's lower corner, ringed in the background's colour so it
                // reads as sitting on top of the icon.
                BrokenBadge(
                    22.dp,
                    Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 4.dp)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(MyCarSpace.S))
        // "YouTube Music · Browse" won't fit on one line under an icon, and cut short it reads
        // just like the player tile beside it: the name goes on top, the "Browse" part under it.
        val name = tile.label.substringBefore(" · ")
        val detail = tile.label.substringAfter(" · ", "")
        Text(
            name,
            modifier = Modifier.widthIn(max = iconSize + MyCarSpace.XXL),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.isNotEmpty()) {
            Text(
                detail,
                modifier = Modifier.widthIn(max = iconSize + MyCarSpace.XXL),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
