package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.car.theme.*
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.content.ComponentName
import android.media.session.PlaybackState
import io.github.sirionero.mycar.media.MediaArt
import io.github.sirionero.mycar.media.Lyrics
import io.github.sirionero.mycar.media.LyricsAnswer
import io.github.sirionero.mycar.media.lyricsSong
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.design.raisedPanel
import io.github.sirionero.mycar.host.CarAppConnection
import io.github.sirionero.mycar.host.KnownApps
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
    AutoplayWhenReady(phase, now.state == android.media.session.PlaybackState.STATE_PLAYING)

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
    // The music's column closes only after a short hold: an app that relinks, a change of music app,
    // the gap between two songs or a web page that reloads would otherwise close it and open it again,
    // and every width of the map is a new surface for its app (two hand-overs: Vela redraws the map).
    // Opening stays immediate; music closed by the driver (no music app any more) closes it at once.
    val mediaApp by CarServices.mediaApp.collectAsStateWithLifecycle()
    val musicOn = phase == CarMedia.Phase.READY && now.isActive
    var musicShown by remember { mutableStateOf(musicOn) }
    LaunchedEffect(musicOn, mediaApp) {
        if (musicOn) musicShown = true
        else if (mediaApp == null) musicShown = false
        else { delay(MUSIC_HOLD_MS); musicShown = false }
    }
    // The last song with a title and a live state, drawn during the hold only - and only the same
    // music app's: another app's song over this one's buttons would play when it says Pause. Beside
    // a chosen app, always what plays now (its pane remembers the music app by it).
    val lastActive = remember(mediaApp) { arrayOf(now) }
    if (now.isActive) lastActive[0] = now
    val holding = sideApp == null && musicShown && !musicOn
    val shownNow = if (holding && !now.isActive && lastActive[0].isActive) lastActive[0].playingAs(now) else now
    // The music beside the map (the player, the lyrics), not an app's own screen there.
    val music = sideApp?.template == null && sideApp?.builtIn != BuiltInApps.Plancia
    // Lyrics that gave the column back for a song without any come back with the next song that has some,
    // until the driver closes them (their X, the dock's song, the player's X). Not looked up with an app
    // beside the map, where they can't show (no searches in the background).
    if (navigator.lyricsWanted && panel == SidePanel.CONTROLS && shownNow.isActive && music) {
        val context = LocalContext.current
        val inside = mediaApp?.component?.let(io.github.sirionero.mycar.xonos.Xonos::isInside) == true
        val song = remember(shownNow.trackTitle, shownNow.title, shownNow.trackArtist, shownNow.artist, shownNow.album, shownNow.durationMs, shownNow.mediaId, inside) { shownNow.lyricsSong(inside) }
        LaunchedEffect(song.key) {
            // Not the song they just gave way for: looked up again at once, it asked LRCLIB as many times more.
            if (song.key == navigator.lyricsGaveWayFor) return@LaunchedEffect
            delay(Lyrics.SETTLE_MS)
            if (Lyrics.find(context, song).getOrNull() is LyricsAnswer.Found) navigator.lyricsBack()
        }
    }
    // The drive's music on its way: its column from the start (see CarServices.musicStarting). Only with the
    // music beside the map: a chosen app's column is there anyway, and one not loaded yet would come at its
    // own width. Not for music that won't connect. After the musicShown effect above: as the song comes,
    // musicShown is true before the wait ends, and no frame closes the column.
    val starting by CarServices.musicStarting.collectAsStateWithLifecycle()
    val sideChoice by SidePaneChoice.current.collectAsStateWithLifecycle()
    LaunchedEffect(musicOn) { if (musicOn) CarServices.musicCame() }
    val reserved = starting && sideChoice == SidePaneChoice.MUSIC && sideApp == null && mediaApp != null &&
        phase != CarMedia.Phase.REJECTED
    val splitScreen = panel != SidePanel.NONE && (sideApp != null || musicShown || reserved)
    // The music beside the map at the size chosen for it (Small, Medium, Large): an app's own screen
    // there keeps its size.
    val playerSize by CarSettings.playerSize.collectAsStateWithLifecycle()
    // The map alone with music on (the column closed with its X, the dock's song, or Close beside an
    // app): the mini player floats over it instead (see MapMiniPlayer); not while the map is panned.
    // Its song as the column would draw it: the last one during the column's hold, whatever is beside the map.
    val miniOn by MiniPlayerSetting.on.collectAsStateWithLifecycle()
    val panning by CarServices.nav.panMode.collectAsStateWithLifecycle()
    val miniNow = if (musicShown && !musicOn && !now.isActive && lastActive[0].isActive) lastActive[0].playingAs(now) else now

    // The map's page among the driver's own (see HomePager). The map alone fills it, edge to edge.
    // Split, the map and what's beside it are panels raised off the wallpaper (see Panels.kt), with the
    // same small gap round them as between them; HomePager draws the wallpaper behind them.
    val planciaWeb = LocalPlanciaWeb.current
    val planciaBeside = splitScreen && sideApp?.builtIn == BuiltInApps.Plancia
    HomePager(onHome, mapSplit = splitScreen) { inView ->
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
    // Plancia's page beside the map stays a frame after it goes (out of view, Close, another choice),
    // so a tap's focus can leave it first (see LeaveWithoutFocus.kt); the column with it.
    val planciaShown = leavesWithoutFocus(planciaBeside && inView) { planciaWeb?.dropFocus() }
    val split = splitScreen || planciaShown
    val gap = if (split) MyCarSpace.Panel else 0.dp
    // As far as it fits: the map keeps room for an app's card over it, the column for its X over the glass.
    val sideScale = if (music) sideScaleFor(playerSize, (maxWidth - gap * 3).value, (maxHeight - gap * 2).value, MAP_PANE_MIN_EDGE.value + 24f) else 1f
    Row(
        Modifier.fillMaxSize().padding(gap),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        Box(
            Modifier
                // The side a third of the screen, as it always was, times its size: the map takes the rest.
                .weight(if (split) MAP_SHARE / sideScale - 1f else 1f)
                .fillMaxHeight()
                // The map app's surface lies under the window, seen through the hole its view clears in
                // it: that hole keeps to the panel's rounded clip, and the wallpaper covers the rest.
                .then(if (split) Modifier.raisedPanel().background(MaterialTheme.colorScheme.surface) else Modifier),
        ) {
            MapPane(navStatus, frame, inView, mini = miniNow.takeIf { miniPlayerShows(miniOn, navigator.miniPlayerHidden, musicShown, split, inView, panning) })
        }
        // The column's panel at the screen's own density, outside the player's size: its corners, rim and
        // shadow match the map's at any size. Painted over at the corners for Plancia's web page.
        if (split) Box(Modifier.weight(1f).fillMaxHeight().raisedPanel(coverCorners = planciaShown)) {
            CompositionLocalProvider(LocalPlayerInView provides inView, LocalDensity provides LocalDensity.current.scaledBy(sideScale)) {
                val side = Modifier.fillMaxSize()
                val sideTemplate = sideApp?.template
                val sideMedia = sideApp?.media
                when {
                    // Plancia inside myCar: its one page goes wherever it's on screen, so here too only in
                    // view (and a frame more, see planciaShown).
                    planciaShown -> PlanciaPane(side)
                    // Only while its page is in view on Home: an app opened full screen from the grid,
                    // or on another page, uses the same connection, and two stages on it would pull its
                    // surface back and forth.
                    sideTemplate != null ->
                        if (inView) SideAppPane(sideTemplate, frame, side) else AppPlaceholder(sideApp?.shownLabel.orEmpty(), sideApp?.icon, side, raised = false)
                    sideApp?.builtIn == BuiltInApps.Plancia -> AppPlaceholder("Plancia", null, side, builtInId = BuiltInApps.Plancia.id, raised = false)
                    panel == SidePanel.LYRICS -> SideLyrics(shownNow, side)
                    sideMedia != null -> SideMediaPane(sideMedia, shownNow, side)
                    else -> GlassDockedPlayer(shownNow, side, startingApp = mediaApp?.label?.takeIf { reserved && !shownNow.isActive })
                }
            }
        }
    }
    }
    }
}

@Composable
private fun MapPane(status: CarAppConnection.Status, frame: CarEnvironment.Frame, inView: Boolean, mini: NowPlaying? = null) {
    val navigator = LocalCarNavigator.current
    if (status.phase == CarAppConnection.Phase.RUNNING) {
        // The car's Back key reaches the map's app (its search results, a list of places) while
        // the map is the page in view; under another screen it goes on closing that screen.
        AppBackKey(CarServices.nav, active = inView)
        CarAppStage(
            CarServices.nav, status, frame, embedded = true,
            // Over the map, under the app's own cards and buttons: they stay on top of it.
            overMap = { if (mini != null) MapMiniPlayer(mini, Modifier.fillMaxSize()) },
        ) { CarServices.stopNav() }
        return
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Not one the drive leaves be (see KnownApps.wontStart, an app that turned myCar down): its
        // "Reopen" would do nothing, so the choice instead.
        val context = LocalContext.current
        val last = CarSettings.lastNav.value
        val hasLast = remember(last, status.phase) {
            last != null && ComponentName.unflattenFromString(last)?.let { KnownApps.wontStart(context, it.packageName) } != true
        }
        val hasNavApps by CarServices.hasNavApps.collectAsStateWithLifecycle()
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        when (status.phase) {
            CarAppConnection.Phase.BINDING, CarAppConnection.Phase.HANDSHAKE ->
                Text(
                    status.app?.let { stringResource(R.string.car_map_starting, it) } ?: stringResource(R.string.car_map_starting_unknown),
                    style = MaterialTheme.typography.titleMedium,
                )
            CarAppConnection.Phase.REJECTED, CarAppConnection.Phase.FAILED -> {
                Text(
                    status.app?.let { stringResource(R.string.car_home_app_failed, it) } ?: stringResource(R.string.car_map_failed_unknown),
                    style = MaterialTheme.typography.titleMedium,
                )
                status.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                GsButton(onClick = navigator::apps, tone = GsTone.Tonal) { Text(stringResource(R.string.car_map_choose_another)) }
            }
            else -> if (!hasNavApps) {
                // Nothing to auto-select and nothing to pick from Car apps either - this is the
                // only case worth a distinct screen, since every other IDLE case resolves itself.
                Text(stringResource(R.string.car_map_none_installed), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.car_map_none_installed_detail),
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
                    Text(stringResource(R.string.car_map_stopped), style = MaterialTheme.typography.titleMedium)
                    GsButton(onClick = { scope.launch { CarServices.reconnectNav(frame) } }, tone = GsTone.Tonal) { Text(stringResource(R.string.car_map_reopen)) }
                    GsButton(onClick = { navigator.apps() }, tone = GsTone.Tonal) { Text(stringResource(R.string.car_map_car_apps)) }
                }
            } else {
                // Autostart is still picking one - a navigation app is installed, so this resolves
                // to a "Starting..." state itself, in the next frame or two. If it doesn't (the only
                // map apps installed are ones known not to work here), the choice, not an empty half.
                val nothing by produceState(false) {
                    delay(MAP_START_GRACE_MS)
                    value = true
                }
                if (nothing) {
                    Text(stringResource(R.string.car_map_choose), style = MaterialTheme.typography.titleMedium)
                    GsButton(onClick = { navigator.apps() }, tone = GsTone.Tonal) { Text(stringResource(R.string.car_map_car_apps)) }
                }
            }
        }
    }
    // No map drawn yet (starting, stopped, failed): the mini player over the message, which keeps the middle.
    if (mini != null) MapMiniPlayer(mini, Modifier.fillMaxSize())
}

/** How long the music's column waits, once nothing plays, before giving the map its width back. */
private const val MUSIC_HOLD_MS = 6_000L

/**
 * The song held through the column's wait, with its play button as the app is now: an app that stops
 * on pause (STATE_STOPPED) shows Play, which is what a touch does (togglePlay reads the app's own state);
 * before, it went on saying Pause and a second touch started the music again. Still a song in play, so
 * the column and the mini player keep drawing it.
 */
private fun NowPlaying.playingAs(live: NowPlaying): NowPlaying =
    if (live.playing == playing) this else NowPlaying(
        title = title, artist = artist, album = album, art = art, artUri = artUri,
        state = if (live.playing) live.state else PlaybackState.STATE_PAUSED,
        positionMs = positionMs, durationMs = durationMs, speed = speed, updatedAt = updatedAt, actions = actions,
        custom = custom, error = error, activeQueueItemId = activeQueueItemId, heart = heart,
        trackTitle = trackTitle, trackArtist = trackArtist, mediaId = mediaId,
    )

/** Everything drawn [scale] times bigger: the player beside the map at Medium or Large. */
private fun Density.scaledBy(scale: Float): Density = if (scale == 1f) this else Density(density * scale, fontScale)

/** How long the map may sit idle at the start of a drive before Home offers to reopen it. */
private const val MAP_START_GRACE_MS = 2_000L

/**
 * Lyrics alone beside the map, big enough to read at a glance. No art: that space goes to a
 * larger title and artist instead. Their X, like the dock's song, puts the player back.
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
                // The lyrics go, the player comes back: before, the X took both away, pause and skip with them.
                GsIconButton(Icons.Filled.Close, stringResource(R.string.car_home_hide_lyrics), navigator::showSideControls, size = 52.dp, iconSize = 24.dp)
            }
            Spacer(Modifier.height(12.dp))
            // The next song ahead only while these are what the driver sees: not under another screen or on another page.
            LyricsPanel(
                now, Modifier.weight(1f).fillMaxWidth(), lineStyle = MaterialTheme.typography.headlineSmall, art = art,
                onNothing = navigator::lyricsGaveWay, ahead = LocalPlayerInView.current && navigator.current == CarScreen.Home,
            )
        }
    }
}
