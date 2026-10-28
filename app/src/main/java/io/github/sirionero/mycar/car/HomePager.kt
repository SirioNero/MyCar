package io.github.sirionero.mycar.car

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.ProjectedContent
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.design.GlassTile
import io.github.sirionero.mycar.car.design.MyCarPalette
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSize
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.design.WallpaperBackground
import io.github.sirionero.mycar.car.design.rememberWallpaper
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsColors
import io.github.sirionero.mycar.car.theme.GsIconBox
import io.github.sirionero.mycar.car.theme.GsIconButton
import io.github.sirionero.mycar.car.theme.GsTone
import io.github.sirionero.mycar.car.theme.gsColors
import io.github.sirionero.mycar.host.CarAppConnection
import io.github.sirionero.mycar.host.TemplateApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home as a row of pages: the map's own page - the map with the music or an app beside it, as
 * Home always was - and the driver's pages either side of it, each cut up as the driver likes (a
 * whole page, halves, quarters, or a half or a strip beside two quarters), each part
 * holding the weather, the agenda, the browser or a car app such as Plancia. A swipe on anything
 * that doesn't move under the finger itself (the map, the browser and car apps do) turns the
 * page, and so do the dots at the foot; the pencil beside them changes the page in view.
 *
 * Every page stays composed, the map's most of all: dropping it would take the map's surface away
 * (see CarUi), and a page turned back to finds its browser where it was left. Only the page in
 * view runs a car app, though: apps other than the map share one connection
 * ([CarServices.browse]), and it can draw in one place only. [mapPage] is told when its page is
 * the one in view, for the app beside the map.
 */
@Composable
internal fun HomePager(onHome: Boolean, mapPage: @Composable (inView: Boolean) -> Unit) {
    val navigator = LocalCarNavigator.current
    val scope = rememberCoroutineScope()
    val pages = rememberHomePages()
    val latest by rememberUpdatedState(pages)
    val mapIndex = pages.indexOfFirst { it.layout == PageLayout.MAP }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = mapIndex) { pages.size }
    var editing by remember { mutableStateOf<Int?>(null) }
    var choosing by remember { mutableStateOf<Int?>(null) }
    // The list of layouts for the page being changed.
    var choosingLayout by remember { mutableStateOf(false) }
    // A page to turn to once a change has moved it, when the pager has the new pages.
    var follow by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pages, follow) {
        val id = follow ?: return@LaunchedEffect
        pages.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { pager.scrollToPage(it) }
        // Last: clearing it restarts this effect, which would cut the scroll short.
        follow = null
    }
    // The dock's map, the car's map key, "navigate to": back to the map's page.
    LaunchedEffect(navigator.mapPageRequests) {
        val map = latest.indexOfFirst { it.layout == PageLayout.MAP }
        if (map >= 0 && pager.currentPage != map) pager.animateScrollToPage(map)
    }
    // A page to change, asked for from the car's settings.
    LaunchedEffect(navigator.pageToEdit, pages) {
        val id = navigator.pageToEdit ?: return@LaunchedEffect
        val index = pages.indexOfFirst { it.id == id }
        if (index < 0) return@LaunchedEffect
        pager.scrollToPage(index)
        editing = id
        navigator.pageToEdit = null
    }
    // For the dock: its map is lit, and the next turn left out of it, only on the map's page.
    LaunchedEffect(pager) {
        snapshotFlow { latest.getOrNull(pager.currentPage)?.layout == PageLayout.MAP }
            .collect { navigator.onMapPage = it }
    }
    LaunchedEffect(onHome) {
        if (!onHome) {
            editing = null
            choosing = null
        }
    }
    // Each page changed starts without the list of layouts open.
    LaunchedEffect(editing) { choosingLayout = false }
    // Back on one of the driver's pages turns to the map's; while a page is being changed, it closes
    // the list of what can go in it first, then the changing. Only on Home: another screen up may
    // want Back for itself (the browser, for its pages).
    val changing = onHome && editing != null
    val besideMap = onHome && pages.getOrNull(pager.currentPage)?.layout != PageLayout.MAP
    DisposableEffect(changing, besideMap) {
        if (!changing && !besideMap) return@DisposableEffect onDispose { }
        val back: () -> Boolean = {
            when {
                choosingLayout -> choosingLayout = false
                choosing != null -> choosing = null
                editing != null -> editing = null
                else -> navigator.home()
            }
            true
        }
        navigator.backInterceptor = back
        onDispose { if (navigator.backInterceptor === back) navigator.backInterceptor = null }
    }

    Box(Modifier.fillMaxSize()) {
        // Behind the driver's pages, and still while they slide over it; the map's page covers it.
        val wallpaper = rememberWallpaper()
        if (wallpaper != null && (pager.currentPage != mapIndex || pager.isScrollInProgress)) {
            WallpaperBackground(wallpaper, Modifier.matchParentSize())
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = PageGap,
            beyondViewportPageCount = pages.size,
            userScrollEnabled = !changing,
            key = { index -> pages.getOrNull(index)?.id ?: (-1 - index) },
        ) { index ->
            val page = pages.getOrNull(index) ?: return@HorizontalPager
            val inView = onHome && pager.settledPage == index
            if (page.layout == PageLayout.MAP) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { mapPage(inView) }
            } else {
                DriverPage(
                    page,
                    inView = inView && !changing,
                    editing = editing == page.id,
                    onEdit = { editing = page.id },
                    onChoose = { slot ->
                        editing = page.id
                        choosing = slot
                    },
                )
            }
        }

        val edited = pages.firstOrNull { it.id == editing }
        if (edited == null) {
            if (pages.size > 1) {
                PageDots(
                    pages,
                    current = pager.currentPage,
                    onPage = { scope.launch { pager.animateScrollToPage(it) } },
                    onEdit = { pages.getOrNull(pager.currentPage)?.let { editing = it.id } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = MyCarSpace.S),
                )
            }
        } else {
            val index = pages.indexOf(edited)
            EditBar(
                edited,
                canMoveLeft = index > 0,
                canMoveRight = index < pages.lastIndex,
                onLayout = { choosingLayout = true },
                onMove = { by ->
                    HomePages.save(latest.moved(edited.id, by))
                    follow = edited.id
                },
                onAdd = {
                    val (changed, id) = latest.withNewPage(edited.id)
                    HomePages.save(changed)
                    follow = id
                    editing = id
                },
                onDelete = {
                    HomePages.save(latest.without(edited.id))
                    editing = null
                    follow = latest.firstOrNull { it.layout == PageLayout.MAP }?.id
                },
                onDone = { editing = null },
                modifier = Modifier.align(Alignment.TopCenter).padding(MyCarSpace.S),
            )
            if (choosingLayout) {
                LayoutChooser(
                    edited.layout,
                    onChoose = { layout ->
                        HomePages.save(latest.withLayout(edited.id, layout))
                        choosingLayout = false
                    },
                    onClose = { choosingLayout = false },
                )
            }
            choosing?.let { slot ->
                ContentChooser(
                    edited,
                    slot,
                    onChoose = { content ->
                        HomePages.save(latest.withSlot(edited.id, slot, content))
                        choosing = null
                    },
                    onClose = { choosing = null },
                )
            }
        }
    }
}

/** What the browser in a part of a page hands it: what holding its bar does, and its keyboard. */
internal class WebTile(val onHold: () -> Unit, val keyboard: MutableState<(@Composable () -> Unit)?>)

/** One of the driver's pages: its parts, and under them, the page's whole width, the browser's keyboard. */
@Composable
private fun DriverPage(page: HomePage, inView: Boolean, editing: Boolean, onEdit: () -> Unit, onChoose: (Int) -> Unit) {
    val entries = rememberAllEntries()
    val keyboard = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    // A page runs one car app, in the first part that holds one; the list of choices keeps it so.
    val runner = page.slots.indexOfFirst { carAppIn(it, entries) != null }
    val part: @Composable (Int, Modifier) -> Unit = { slot, modifier ->
        PagePart(page.slots[slot], entries, inView, inView && slot == runner, editing, onEdit, { onChoose(slot) }, keyboard, modifier)
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (page.layout) {
                PageLayout.HALVES -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PageGap)) {
                    repeat(2) { slot ->
                        PagePart(page.slots[slot], entries, inView, inView && slot == runner, editing, onEdit, { onChoose(slot) }, keyboard, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                PageLayout.GRID -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PageGap)) {
                    repeat(2) { row ->
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PageGap)) {
                            repeat(2) { column ->
                                val slot = row * 2 + column
                                PagePart(page.slots[slot], entries, inView, inView && slot == runner, editing, onEdit, { onChoose(slot) }, keyboard, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
                PageLayout.FULL -> part(0, Modifier.fillMaxSize())
                PageLayout.HALF_QUARTERS -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PageGap)) {
                    part(0, Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PageGap)) {
                        part(1, Modifier.weight(1f).fillMaxWidth())
                        part(2, Modifier.weight(1f).fillMaxWidth())
                    }
                }
                PageLayout.QUARTERS_HALF -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PageGap)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PageGap)) {
                        part(0, Modifier.weight(1f).fillMaxWidth())
                        part(1, Modifier.weight(1f).fillMaxWidth())
                    }
                    part(2, Modifier.weight(1f).fillMaxHeight())
                }
                PageLayout.STRIP_QUARTERS -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PageGap)) {
                    part(0, Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PageGap)) {
                        part(1, Modifier.weight(1f).fillMaxHeight())
                        part(2, Modifier.weight(1f).fillMaxHeight())
                    }
                }
                PageLayout.QUARTERS_STRIP -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PageGap)) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PageGap)) {
                        part(0, Modifier.weight(1f).fillMaxHeight())
                        part(1, Modifier.weight(1f).fillMaxHeight())
                    }
                    part(2, Modifier.weight(1f).fillMaxWidth())
                }
                PageLayout.MAP -> Unit
            }
        }
        keyboard.value?.invoke()
    }
}

/**
 * One part of a page, by what it holds. While the page is being changed, each part is covered by
 * a card that, tapped, offers what else can go there; the browser keeps its page underneath.
 */
@Composable
private fun PagePart(
    content: String?,
    entries: List<Entry>,
    inView: Boolean,
    runs: Boolean,
    editing: Boolean,
    onEdit: () -> Unit,
    onChoose: () -> Unit,
    keyboard: MutableState<(@Composable () -> Unit)?>,
    modifier: Modifier,
) {
    val loaded by LauncherCache.state.collectAsState()
    Box(modifier) {
        val fill = Modifier.fillMaxSize()
        when (val shown = withPlanciaInside(content, entries)) {
            null -> EmptyPart(onChoose, onEdit, fill)
            PageContent.WEATHER -> WeatherWidget(onEdit, fill)
            PageContent.AGENDA -> AgendaWidget(onEdit, fill)
            PageContent.BROWSER -> BrowserPart(inView, onEdit, keyboard, fill)
            // Plancia isn't on the browse connection: it shows whenever its page is in view.
            PageContent.PLANCIA ->
                if (entries.any { it.builtIn == BuiltInApps.Plancia }) PlanciaPart(inView, onEdit, fill)
                else MissingPart(onChoose, onEdit, fill)
            else -> {
                val entry = entries.find { it.componentId == shown }
                val app = carAppIn(shown, entries)
                when {
                    entry != null && app != null -> AppPart(entry, app, runs, onEdit, fill)
                    // Still being looked for: the phone's apps are scanned as the car connects.
                    loaded == null -> WidgetCard(onClick = null, onHold = onEdit, modifier = fill) { }
                    else -> MissingPart(onChoose, onEdit, fill)
                }
            }
        }
        if (editing) EditCover(partName(content, entries), onChoose, Modifier.matchParentSize())
    }
}

/** The car app [content] names, if it's one that can sit in a part of a page. */
private fun carAppIn(content: String?, entries: List<Entry>): TemplateApp? =
    content?.let { id -> entries.find { it.componentId == id && it.fitsBesideMap }?.template }

/** What a part holds, in a word or two, for the card that covers it while the page is changed. */
private fun partName(content: String?, entries: List<Entry>): String = when (content) {
    null -> "Nothing yet"
    PageContent.WEATHER -> "Weather"
    PageContent.AGENDA -> "Agenda"
    PageContent.BROWSER -> "Browser"
    PageContent.PLANCIA -> "Plancia"
    else -> entries.find { it.componentId == withPlanciaInside(content, entries) }?.label ?: "App not on the phone"
}

/**
 * A car app in a part of a page, as beside the map but without Close: the app belongs to the page.
 * Out of view it waits as its icon, so the connection is free for the page in view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppPart(entry: Entry, app: TemplateApp, runs: Boolean, onHold: () -> Unit, modifier: Modifier) {
    if (!runs) {
        AppPlaceholder(entry.label, entry.icon, modifier, onHold)
        return
    }
    val frame by CarEnvironment.frame.collectAsState()
    val status by CarServices.browse.status.collectAsState()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(app.component) {
        val live = CarServices.browse.status.value.phase in LIVE_PHASES
        val closed = app.component in CarServices.closedBrowse
        if (!closed && (!live || CarServices.browse.connectedComponent != app.component)) CarServices.connectBrowse(app, frame)
        started = true
    }
    Box(modifier.clip(RoundedCornerShape(MyCarRadius.Card)), contentAlignment = Alignment.Center) {
        if (status.phase == CarAppConnection.Phase.REJECTED || status.phase == CarAppConnection.Phase.FAILED) {
            Text(
                "${app.label} would not start.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(MyCarSpace.XL),
            )
        } else if (started && (status.phase == CarAppConnection.Phase.IDLE || app.component in CarServices.closedBrowse)) {
            // Closed from the dock's app menu: it waits to be asked back.
            GsButton(onClick = { CarServices.connectBrowse(app, frame) }, tone = GsTone.Tonal) {
                Text("Open ${app.label}")
            }
        } else {
            CarAppStage(CarServices.browse, status, frame, endLabel = null, onDisconnect = {})
        }
        // The app's bar, held, changes the page; everything under it is the app's own.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(CHROME_BAR)
                .combinedClickable(onClick = {}, onLongClick = onHold),
        )
    }
}

private val LIVE_PHASES = setOf(CarAppConnection.Phase.BINDING, CarAppConnection.Phase.HANDSHAKE, CarAppConnection.Phase.RUNNING)

/**
 * An app that isn't running where it sits - its page is out of view, or the connection is in use
 * elsewhere - as its icon and name, so turning to it shows what's coming rather than a gap.
 */
@Composable
internal fun AppPlaceholder(
    label: String,
    icon: ImageBitmap?,
    modifier: Modifier,
    onHold: () -> Unit = {},
    builtInId: String? = null,
) {
    WidgetCard(onClick = null, onHold = onHold, modifier = modifier) {
        Column(
            Modifier.align(Alignment.Center).padding(MyCarSpace.L),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
        ) {
            AppIcon(icon, builtInId, PlaceholderIcon)
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * myCar's browser in a part of a page, on the start page set on the phone. Built the first time
 * its page is turned to, then kept: the first browser of a session holds up the whole car screen
 * for a few seconds as it starts, which must not happen as the car connects, and most drives
 * never turn to it.
 */
@Composable
private fun BrowserPart(inView: Boolean, onHold: () -> Unit, keyboard: MutableState<(@Composable () -> Unit)?>, modifier: Modifier) {
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(inView) { if (inView) opened = true }
    if (!opened) {
        AppPlaceholder(BuiltInApps.Web.label, null, modifier, onHold, builtInId = BuiltInApps.Web.id)
        return
    }
    val context = LocalContext.current
    val start = remember { ProjectedContent.resolve(context) }
    val hold by rememberUpdatedState(onHold)
    val tile = remember(keyboard) { WebTile({ hold() }, keyboard) }
    Box(modifier.clip(RoundedCornerShape(MyCarRadius.Card))) {
        WebApp(start, tile)
    }
}

/**
 * The slim bar over the browser in a part of a page: back a page, the name, and, held, a way to
 * change the page. As tall as a car app's bar beside it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TileBar(title: String, onBack: () -> Unit, onHold: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .height(CHROME_BAR)
            .combinedClickable(onClick = {}, onLongClick = onHold),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.fillMaxHeight().width(CHROME_BAR + MyCarSpace.S).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back a page", Modifier.size(22.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A part with nothing in it: tapped, it offers what can go there. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmptyPart(onChoose: () -> Unit, onHold: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MyCarRadius.Card)
    Column(
        modifier
            .clip(shape)
            .border(1.5.dp, scheme.onSurface.copy(alpha = 0.25f), shape)
            .combinedClickable(onClick = onChoose, onLongClick = onHold)
            .padding(MyCarSpace.L),
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.S, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.sym_add), null, Modifier.size(40.dp), tint = scheme.onSurfaceVariant)
        Text(
            "Tap to choose what goes here",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A part whose app has gone from the phone. */
@Composable
private fun MissingPart(onChoose: () -> Unit, onHold: () -> Unit, modifier: Modifier) {
    WidgetCard(onClick = onChoose, onHold = onHold, modifier = modifier) {
        Text(
            "This app isn't on the phone any more. Tap to choose something else.",
            Modifier.align(Alignment.Center).padding(MyCarSpace.XL),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Over a part while its page is changed: what it holds, and that a tap changes it. */
@Composable
private fun EditCover(name: String, onChoose: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MyCarRadius.Card)
    Box(
        modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.5f))
            .border(2.dp, scheme.primary, shape)
            .clickable(onClick = onChoose),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(scheme.primary)
                .padding(horizontal = MyCarSpace.XL, vertical = MyCarSpace.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MyCarSpace.S),
        ) {
            Icon(painterResource(R.drawable.sym_edit), null, Modifier.size(20.dp), tint = scheme.onPrimary)
            Text(name, style = MaterialTheme.typography.titleMedium, color = scheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A dot a page, the map's page an arrow, the one in view lit; any of them, tapped, turns to its
 * page. On the driver's pages a pencil follows, to change the page in view. Over the map the bar
 * fades with the map's own buttons, to leave the map clear, and comes back at a touch.
 */
@Composable
private fun PageDots(pages: List<HomePage>, current: Int, onPage: (Int) -> Unit, onEdit: () -> Unit, modifier: Modifier) {
    val onMap = pages.getOrNull(current)?.layout == PageLayout.MAP
    val wake = LocalMapControlsWake.current
    AnimatedVisibility(
        visible = !onMap || wake.awake,
        modifier = modifier,
        enter = fadeIn(tween(DotsFadeMs)),
        exit = fadeOut(tween(DotsFadeMs)),
    ) {
        Row(
            Modifier
                .height(DotSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = MyCarSpace.XS),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEachIndexed { index, page ->
                val tint = Color.White.copy(alpha = if (index == current) 1f else 0.45f)
                Box(
                    Modifier
                        .size(DotSize)
                        .clip(CircleShape)
                        .clickable { onPage(index) }
                        .semantics { contentDescription = if (page.layout == PageLayout.MAP) "Map" else "Page ${index + 1}" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (page.layout == PageLayout.MAP) {
                        Icon(painterResource(R.drawable.sym_navigation), null, Modifier.size(14.dp), tint = tint)
                    } else {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                    }
                }
            }
            if (!onMap) {
                Box(Modifier.padding(horizontal = MyCarSpace.XS).width(1.dp).height(20.dp).background(Color.White.copy(alpha = 0.3f)))
                Box(
                    Modifier.size(DotSize).clip(CircleShape).clickable(onClick = onEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.sym_edit), "Change this page", Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
    }
}

/**
 * Across the top while a page is changed: its layout (the list of them, [LayoutChooser]), move it
 * left or right (past the map's page too), a new page after it, delete it (a second tap to be
 * sure), and Done.
 */
@Composable
private fun EditBar(
    page: HomePage,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onLayout: () -> Unit,
    onMove: (Int) -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    var deleting by remember(page.id) { mutableStateOf(false) }
    LaunchedEffect(deleting) {
        if (deleting) {
            delay(DELETE_ARMED_MS)
            deleting = false
        }
    }
    Row(
        modifier
            .clip(RoundedCornerShape(MyCarRadius.Sheet))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .horizontalScroll(rememberScrollState())
            .padding(MyCarSpace.S),
        horizontalArrangement = Arrangement.spacedBy(MyCarSpace.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LayoutButton(page.layout, onLayout)
        Spacer(Modifier.width(MyCarSpace.S))
        BarButton(R.drawable.sym_chevron_left, "Move left", enabled = canMoveLeft) { onMove(-1) }
        BarButton(R.drawable.sym_chevron_right, "Move right", enabled = canMoveRight) { onMove(1) }
        Spacer(Modifier.width(MyCarSpace.S))
        BarButton(R.drawable.sym_add, "Add a page") { onAdd() }
        if (deleting) {
            GsButton(onClick = onDelete, tone = GsTone.Danger, modifier = Modifier.height(MyCarSize.TouchTarget)) {
                Text("Delete page", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            BarButton(R.drawable.sym_delete, "Delete this page") { deleting = true }
        }
        Spacer(Modifier.width(MyCarSpace.S))
        GsButton(onClick = onDone, modifier = Modifier.height(MyCarSize.TouchTarget)) {
            Icon(painterResource(R.drawable.sym_check), null, Modifier.size(24.dp))
            Spacer(Modifier.width(MyCarSpace.S))
            Text("Done", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BarButton(
    @DrawableRes icon: Int,
    description: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    GsIconBox(
        onClick = onClick,
        modifier = Modifier.size(MyCarSize.TouchTarget).semantics { contentDescription = description },
        colors = if (selected) GsColors(scheme.primaryContainer, scheme.onPrimaryContainer) else gsColors(GsTone.Neutral),
        enabled = enabled,
        latched = selected,
    ) {
        Icon(painterResource(icon), null, Modifier.size(28.dp))
    }
}

/**
 * What can go in part [slot] of [page], as big tiles: the weather, the agenda, the browser, every
 * car app that fits beside the map, or nothing. A page runs one car app, so with another already
 * on it, the rest wait their turn here, greyed out.
 */
@Composable
private fun ContentChooser(page: HomePage, slot: Int, onChoose: (String?) -> Unit, onClose: () -> Unit) {
    val entries = rememberAllEntries()
    val current = page.slots.getOrNull(slot)
    val otherApp = page.slots.withIndex().firstOrNull { (i, id) -> i != slot && carAppIn(id, entries) != null }?.value
    val apps = entries.filter { it.fitsBesideMap && it.template != null && it.componentId != null }
        .sortedBy { it.label.lowercase() }
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(MyCarSpace.L)
                .clip(RoundedCornerShape(MyCarRadius.Sheet))
                .background(scheme.surfaceContainerHigh)
                // Taps on the sheet itself stay on it.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(MyCarSpace.L),
            verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Show here",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                GsIconButton(Icons.Filled.Close, "Close", onClose, size = MyCarSize.TouchTarget)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(ChoiceWidth),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MyCarSpace.M),
                verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
            ) {
                item {
                    Choice("Weather", current == PageContent.WEATHER, onClick = { onChoose(PageContent.WEATHER) }) {
                        GlassTile(MyCarPalette.Accent, R.drawable.sym_partly_cloudy_day, ChoiceIcon)
                    }
                }
                item {
                    Choice("Agenda", current == PageContent.AGENDA, onClick = { onChoose(PageContent.AGENDA) }) {
                        GlassTile(MyCarPalette.Negative, R.drawable.sym_calendar_month, ChoiceIcon)
                    }
                }
                item {
                    Choice("Browser", current == PageContent.BROWSER, onClick = { onChoose(PageContent.BROWSER) }) {
                        AppIcon(null, BuiltInApps.Web.id, ChoiceIcon)
                    }
                }
                if (entries.any { it.builtIn == BuiltInApps.Plancia }) {
                    item {
                        Choice("Plancia", withPlanciaInside(current, entries) == PageContent.PLANCIA, onClick = { onChoose(PageContent.PLANCIA) }) {
                            AppIcon(null, BuiltInApps.Plancia.id, ChoiceIcon)
                        }
                    }
                }
                items(apps, key = { it.componentId!! }) { app ->
                    val id = app.componentId!!
                    Choice(
                        app.label,
                        current == id,
                        enabled = otherApp == null || otherApp == id,
                        onClick = { onChoose(id) },
                    ) {
                        AppIcon(app.icon, null, ChoiceIcon)
                    }
                }
                item {
                    Choice("Nothing", current == null, onClick = { onChoose(null) }) {
                        Box(
                            Modifier.size(ChoiceIcon).border(1.5.dp, scheme.onSurfaceVariant, RoundedCornerShape(MyCarRadius.Button)),
                        )
                    }
                }
            }
            if (otherApp != null) {
                Text(
                    "A page runs one car app at a time: ${partName(otherApp, entries)} is already on this one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    GsIconBox(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(ChoiceHeight),
        colors = if (selected) GsColors(scheme.primaryContainer, scheme.onPrimaryContainer) else GsColors(scheme.surfaceContainerHighest, scheme.onSurface),
        enabled = enabled,
        latched = selected,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(MyCarSpace.S)) {
            icon()
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = MyCarSpace.S),
            )
        }
    }
}

/**
 * The car settings' word on the pages beside the map: how to reach and change them, a new one
 * (open at once for choosing what goes in it), and the ready-made ones back.
 */
@Composable
internal fun HomePagesSettings() {
    val navigator = LocalCarNavigator.current
    val pages = rememberHomePages()
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pages beside the map", style = MaterialTheme.typography.titleMedium, color = scheme.primary)
        Text(
            "Swipe across Home, or tap the dots at its foot, for your pages: the weather, the agenda, the browser " +
                "or a car app such as Plancia, as a whole page, halves, quarters or a mix. The pencil beside the dots changes the page in view.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GsButton(
                onClick = {
                    // Just right of the map's page, the first a swipe from the map reaches.
                    pages.firstOrNull { it.layout == PageLayout.MAP }?.let { map ->
                        val (changed, id) = pages.withNewPage(map.id)
                        HomePages.save(changed)
                        navigator.editHomePage(id)
                    }
                },
                tone = GsTone.Tonal,
                modifier = Modifier.height(MyCarSize.TouchTarget),
            ) {
                Text("Add a page", style = MaterialTheme.typography.titleMedium)
            }
            GsButton(onClick = HomePages::reset, tone = GsTone.Tonal, modifier = Modifier.height(MyCarSize.TouchTarget)) {
                Text("Ready-made pages", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Between the parts of a page, and between pages as they slide: the gap beside the map. */
private val PageGap = 8.dp
private val DotSize = 44.dp
private const val DotsFadeMs = 250
private const val DELETE_ARMED_MS = 4_000L
private val PlaceholderIcon = 72.dp
internal val ChoiceWidth = 150.dp
private val ChoiceHeight = 120.dp
private val ChoiceIcon = 52.dp
