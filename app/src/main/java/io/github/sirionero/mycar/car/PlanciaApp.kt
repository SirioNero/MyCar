package io.github.sirionero.mycar.car

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.Tailnet
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.theme.GsIconButton
import io.github.sirionero.mycar.home.Dashboard
import io.github.sirionero.mycar.home.HaConnection
import io.github.sirionero.mycar.home.HomeAssistant
import io.github.sirionero.mycar.home.Panels
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/*
 * Plancia, Home Assistant in the car, as one of myCar's own apps: full screen from the app grid,
 * in a part of a Home page, or beside the map. All three show the one page kept alive through
 * the drive ([PlanciaWeb]), wherever it's on screen.
 */

/** Plancia's dashboard here, with what's wrong over it while it can't show. */
@Composable
internal fun PlanciaView(modifier: Modifier) {
    val web = LocalPlanciaWeb.current
    val connection by HomeAssistant.connection.collectAsState()
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        // Keyed on the page: a new one (the light changed, so the theme did) gets a new place here.
        // Without it the place stayed the old page's, and Plancia an empty box.
        if (web != null) key(web) {
            AndroidView(
                factory = { context -> FrameLayout(context).also(web::show) },
                onRelease = web::hide,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val page = web?.page?.collectAsState()?.value
        // Trying an address that Tailscale leaves myCar out of, or one at home that Android 17 keeps
        // myCar from: say so the whole time, not only in the moments between one try and the next.
        val context = LocalContext.current
        val blocked = (connection as? HaConnection.Connecting)?.let { connectingHint(context, it.url) }
        val (title, detail) = planciaMessage(connection, page, blocked) ?: return@Box
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .clickable(enabled = page?.error != null) { web?.reload() }
                .padding(MyCarSpace.XL),
            verticalArrangement = Arrangement.spacedBy(MyCarSpace.S, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Why [url] won't answer, known while still trying it: Tailscale leaving myCar out, or Android 17's home network. */
private fun connectingHint(context: Context, url: String): String? = when {
    Tailnet.reach(context) == Tailnet.Reach.VPN_LEAVES_MYCAR_OUT -> Tailnet.explain(context, listOf(url), Tailnet.Failure.NO_SUCH_HOST)
    LocalNetwork.mayBlock(context, listOf(url)) -> LocalNetwork.HINT
    else -> null
}

/** What to say over the page while it can't show, as a title and a line; null once it shows. */
private fun planciaMessage(connection: HaConnection, page: PlanciaWeb.Page?, blocked: String?): Pair<String, String?>? = when {
    connection is HaConnection.NotConfigured ->
        "Set up Plancia on the phone" to "In myCar on the phone: Settings, then Plancia - your Home Assistant's address and a token."
    // Set up, but the dashboard is made a moment after the car connects (PLANCIA_AFTER_MS).
    page == null -> "Connecting to Home Assistant" to null
    page.error != null -> "Couldn't open the dashboard" to "${page.error}\nTap to try again."
    page.base != null -> null
    connection is HaConnection.Failed -> "Home Assistant isn't answering" to connection.message
    connection is HaConnection.Connecting -> "Connecting to Home Assistant" to (blocked ?: connection.url)
    else -> "Connecting to Home Assistant" to null
}

/** Plancia full screen, from the app grid: the dashboard under a slim bar, and its menu. */
@Composable
internal fun PlanciaScreen() {
    val navigator = LocalCarNavigator.current
    val web = LocalPlanciaWeb.current
    val menu = web?.menu?.collectAsState()?.value == true
    val dashboards by HomeAssistant.dashboards.collectAsState()
    val haDefault by HomeAssistant.haDefault.collectAsState()
    // Leaving Plancia closes its menu: nothing stays open out of sight.
    DisposableEffect(web) {
        onDispose { web?.closeMenu() }
    }
    // Back closes the menu, then steps back inside the page, then leaves.
    DisposableEffect(web, menu) {
        navigator.backInterceptor = {
            when {
                menu -> {
                    web?.closeMenu()
                    true
                }
                web?.goBack() == true -> true
                else -> false
            }
        }
        onDispose { navigator.backInterceptor = null }
    }
    Box(Modifier.fillMaxSize()) {
        // Home Assistant's own bar, under this one, names the dashboard and opens the menu (☰).
        Column(Modifier.fillMaxSize()) {
            TileBar("Plancia", onBack = navigator::back, onHold = {})
            PlanciaView(Modifier.weight(1f).fillMaxWidth())
        }
        if (menu && web != null) PlanciaMenu(web, dashboards, haDefault, onClose = web::closeMenu)
    }
}

/**
 * The menu, a drawer on the left over the dashboard: Home Assistant's dashboards in its sidebar's
 * order, then the one Plancia opens, the size and Reload. A tap on the dashboard beside closes it.
 */
@Composable
private fun PlanciaMenu(web: PlanciaWeb, dashboards: List<Dashboard>, haDefault: String?, onClose: () -> Unit) {
    val current by web.current.collectAsState()
    val default by HomeAssistant.defaultDashboard.collectAsState()
    val zoom by HomeAssistant.zoom.collectAsState()
    val connection by HomeAssistant.connection.collectAsState()
    var choosingDefault by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { HomeAssistant.refreshDashboards() }
    val open = current ?: haDefault
    val opens = default ?: haDefault
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topEnd = MyCarRadius.Sheet, bottomEnd = MyCarRadius.Sheet),
            modifier = Modifier
                .fillMaxHeight()
                .width(MenuWidth)
                // Taps on the menu stay on it.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            LazyColumn(Modifier.fillMaxSize().padding(MyCarSpace.M)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = MyCarSpace.S)) {
                        Text(
                            if (choosingDefault) "Plancia opens" else "Dashboards",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        GsIconButton(Icons.Filled.Close, "Close", onClose, size = MyCarSize.TouchTarget)
                    }
                }
                if (choosingDefault) {
                    item {
                        MenuRow("As in Home Assistant", haDefault?.let { "Now: ${titleOf(it, dashboards)}" }, selected = default == null) {
                            HomeAssistant.setDefaultDashboard(null)
                            choosingDefault = false
                        }
                    }
                    items(dashboards, key = { "default:" + it.urlPath }) { dashboard ->
                        MenuRow(dashboard.title, null, selected = default == dashboard.urlPath) {
                            HomeAssistant.setDefaultDashboard(dashboard.urlPath)
                            choosingDefault = false
                        }
                    }
                    return@LazyColumn
                }
                if (dashboards.isEmpty()) {
                    item {
                        MenuRow(
                            if (connection is HaConnection.Connected) "No dashboards" else "Connecting to Home Assistant",
                            null,
                            selected = false,
                            onClick = null,
                        )
                    }
                }
                items(dashboards, key = { it.urlPath }) { dashboard ->
                    val notes = listOfNotNull(
                        "Open".takeIf { dashboard.urlPath == open },
                        "Opens first".takeIf { dashboard.urlPath == opens },
                    ).joinToString(" · ")
                    MenuRow(dashboard.title, notes.ifBlank { null }, selected = dashboard.urlPath == open) {
                        web.open(dashboard.urlPath)
                        onClose()
                    }
                }
                item {
                    MenuRow("Plancia opens", opens?.let { titleOf(it, dashboards) } ?: "As in Home Assistant", selected = false) {
                        choosingDefault = true
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = MyCarSpace.S, vertical = MyCarSpace.S)) {
                        Text("Size", style = MaterialTheme.typography.titleMedium)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(top = MyCarSpace.S),
                            horizontalArrangement = Arrangement.spacedBy(MyCarSpace.S),
                        ) {
                            HomeAssistant.ZOOM_LEVELS.forEach { level ->
                                SizeChip("$level%", selected = level == zoom) { HomeAssistant.setZoom(level) }
                            }
                        }
                    }
                }
                item {
                    MenuRow("Reload the dashboard", null, selected = false) {
                        web.reload()
                        onClose()
                    }
                }
            }
        }
    }
}

private fun titleOf(urlPath: String, dashboards: List<Dashboard>): String =
    dashboards.firstOrNull { it.urlPath == urlPath }?.title ?: Panels.title(urlPath, null)

@Composable
private fun MenuRow(title: String, note: String?, selected: Boolean, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MyCarSize.TouchTarget)
            .clip(RoundedCornerShape(MyCarRadius.Card))
            .background(if (selected) scheme.secondaryContainer else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = MyCarSpace.M, vertical = MyCarSpace.S),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SizeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .heightIn(min = MyCarSize.TouchTarget)
            .clip(RoundedCornerShape(50))
            .background(if (selected) scheme.primary else scheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = MyCarSpace.L),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/**
 * Plancia in a part of a Home page: the dashboard under a slim bar, while the page is the one in
 * view; out of view its icon, so the page in view can have it. Home Assistant's ☰ opens Plancia
 * full screen, with its menu.
 */
@Composable
internal fun PlanciaPart(runs: Boolean, onHold: () -> Unit, modifier: Modifier) {
    val web = LocalPlanciaWeb.current
    if (!runs) {
        AppPlaceholder("Plancia", null, modifier, onHold, builtInId = BuiltInApps.Plancia.id)
        return
    }
    MenuOpensFullScreen(web)
    Column(modifier.clip(RoundedCornerShape(MyCarRadius.Card))) {
        TileBar("Plancia", onBack = { web?.goBack() }, onHold = onHold)
        PlanciaView(Modifier.weight(1f).fillMaxWidth())
    }
}

/** Plancia beside the map, under a bar with its name and Close, as an app chosen for that place. */
@Composable
internal fun PlanciaPane(modifier: Modifier) {
    val navigator = LocalCarNavigator.current
    val web = LocalPlanciaWeb.current
    MenuOpensFullScreen(web)
    Column(modifier.clip(RoundedCornerShape(MyCarRadius.Card))) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().heightIn(min = CHROME_BAR),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = MyCarSpace.L)) {
                Text("Plancia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "Close",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .clickable(onClick = navigator::closeSidePanel)
                        .padding(horizontal = MyCarSpace.L, vertical = MyCarSpace.S),
                )
            }
        }
        PlanciaView(Modifier.weight(1f).fillMaxWidth())
    }
}

/**
 * Where the dashboard has little room, its menu opens with Plancia full screen. Only a menu that
 * opens here, though: one left open when Plancia closed pulled the driver back to it every time
 * the Home showed - right as a route started, too.
 */
@Composable
private fun MenuOpensFullScreen(web: PlanciaWeb?) {
    val navigator = LocalCarNavigator.current
    LaunchedEffect(web) {
        web?.menu?.drop(1)?.filter { it }?.collect {
            if (navigator.current != CarScreen.App(BuiltInApps.Plancia.id)) navigator.open(BuiltInApps.Plancia.id)
        }
    }
}

private val MenuWidth = 420.dp
