package io.github.sirionero.mycar.car

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.BoxScope
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import io.github.sirionero.mycar.host.CarAppConnection
import io.github.sirionero.mycar.host.display
import io.github.sirionero.mycar.host.text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.animateContentSize
import android.media.session.PlaybackState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.sirionero.mycar.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import io.github.sirionero.mycar.car.design.MyCarCarTheme
import io.github.sirionero.mycar.car.design.MyCarMotion
import io.github.sirionero.mycar.car.theme.GsColors
import io.github.sirionero.mycar.car.theme.GsIconBox
import io.github.sirionero.mycar.car.theme.GsIconButton
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.car.theme.GsTone
import io.github.sirionero.mycar.car.theme.gsColors
import io.github.sirionero.mycar.car.theme.GsThemes
import io.github.sirionero.mycar.car.theme.LocalGsTheme
import io.github.sirionero.mycar.media.CarMedia
import io.github.sirionero.mycar.media.MediaArt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.notify.CarNotifications
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * The whole car screen: the dock down the left (see [CarDock]), the current screen beside it.
 *
 * Colours, type and shapes are myCar's own (see car/design), dark unless the driver picks light
 * or "follow phone" in the car's settings. The size setting scales everything by
 * changing the density, so text, icons and touch targets grow together.
 */
@Composable
fun CarUi() {
    val scale by CarSettings.scale.collectAsState()
    val phoneDark by CarEnvironment.phoneDark.collectAsState()
    val appTheme by CarSettings.appTheme.collectAsState()
    val uiThemeId by CarSettings.uiTheme.collectAsState()
    val mediaApp by CarServices.mediaApp.collectAsState()
    val darkOutside by CarEnvironment.darkOutside.collectAsState()
    val appContext = LocalContext.current.applicationContext
    val dark = when (appTheme) {
        AppTheme.PHONE -> phoneDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val base = LocalDensity.current
    val insets by CarEnvironment.insets.collectAsState()
    val navigator = remember { CarNavigator(CarSettings.openOnConnect.value) }
    val controlsWake = remember { MapControlsWake() }
    LaunchedEffect(controlsWake) {
        while (true) {
            delay(250)
            controlsWake.tick()
        }
    }
    // The knob counts as a touch: the map's buttons can't be found while they're faded out.
    LaunchedEffect(controlsWake) { CarKeys.focusMoves.collect { controlsWake.touch() } }
    remember(appContext) { Prefetch.warm(appContext, force = true); CarServices.init(appContext) }
    DisposableEffect(Unit) { onDispose { CarServices.shutdown() } }
    // Plancia's page, alive through the drive once Plancia is set up, drawn at the car screen's
    // density (this context's) and in the car's light or dark.
    val carContext = LocalContext.current
    val homeSetUp = io.github.sirionero.mycar.home.HomeAssistant.config.collectAsState().value.isComplete
    val plancia = remember(homeSetUp, dark) { if (homeSetUp) PlanciaWeb(carContext, dark) else null }
    DisposableEffect(plancia) { onDispose { plancia?.destroy() } }
    // Ask for the phone's sound as soon as the car connects, not only when a media app opens:
    // a video in the web browser or a map's voice needs it too. Asks once, after the car offers
    // somewhere to play it.
    LaunchedEffect(Unit) { if (CarSettings.pipeAudio.value) io.github.sirionero.mycar.audio.CarAudio.request() }
    // A new version gets one card in the top right, a few seconds in and only while parked.
    LaunchedEffect(Unit) { io.github.sirionero.mycar.update.UpdateChecker.onCarConnected(appContext) }
    // The map follows the light outside, whatever the app's own theme is set to.
    LaunchedEffect(darkOutside) { CarServices.nav.pushConfiguration() }

    // The one place with a navigator and a frame to act a voice command on - CarAssistant itself
    // only knows how to listen and speak, not what "open Spotify" means.
    val frameForAssistant by CarEnvironment.frame.collectAsState()
    LaunchedEffect(navigator) {
        CarAssistant.pendingCommand.collect { heard ->
            if (heard != null) {
                val reply = AssistantCommands.handle(appContext, navigator, frameForAssistant, heard)
                CarAssistant.reply(reply)
            }
        }
    }

    // The car's own back, home, map, media and phone keys.
    LaunchedEffect(navigator) {
        CarKeys.screenKeys.collect { key ->
            when (key) {
                CarKeys.ScreenKey.BACK -> when {
                    navigator.appMenu != null -> navigator.dismissAppMenu()
                    VoiceReply.state.value.phase != VoiceReply.Phase.IDLE -> VoiceReply.cancel()
                    else -> navigator.back()
                }
                // Home is the dashboard, over the map, so Back from it lands on the map.
                CarKeys.ScreenKey.HOME -> { navigator.home(); navigator.dashboard() }
                CarKeys.ScreenKey.MAP -> navigator.home()
                CarKeys.ScreenKey.MEDIA ->
                    if (CarServices.mediaApp.value != null || CarServices.reopenLastMedia()) navigator.media()
                    else navigator.apps()
                CarKeys.ScreenKey.PHONE -> BuiltInApps.Phone.open(navigator)
            }
        }
    }

    // The car screen is drawn on the phone, so every long press or text-handle drag would buzz
    // the phone in its mount. Nobody feels that while driving; it's only noise.
    val view = LocalView.current
    DisposableEffect(view) {
        view.isHapticFeedbackEnabled = false
        onDispose { }
    }

    CompositionLocalProvider(
        LocalHapticFeedback provides NoHaptics,
        LocalDensity provides Density(base.density * scale, base.fontScale),
        LocalCarNavigator provides navigator,
        LocalDarkOutside provides darkOutside,
        LocalMapControlsWake provides controlsWake,
        LocalGsTheme provides GsThemes.byId(uiThemeId),
        LocalPlanciaWeb provides plancia,
    ) {
        MyCarCarTheme(dark) {
          CompositionLocalProvider(LocalIndication provides FocusRingIndication(MaterialTheme.colorScheme.primary)) {
            Surface(
                Modifier
                    .fillMaxSize()
                    // Watches every touch without taking any: first in line, consumes nothing.
                    .pointerInput(controlsWake) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                controlsWake.touch()
                            }
                        }
                    },
                color = MaterialTheme.colorScheme.background,
            ) {
                // Insets are in video-frame pixels, so convert with the un-scaled density.
                val pad = with(base) {
                    PaddingValues(
                        start = insets.left.toDp(), top = insets.top.toDp(),
                        end = insets.right.toDp(), bottom = insets.bottom.toDp(),
                    )
                }
                val screen = navigator.current
                val safetyWarningSeen by CarSettings.hasSeenSafetyWarning.collectAsState()
                Box(Modifier.fillMaxSize().padding(pad)) {
                Row(Modifier.fillMaxSize()) {
                    if (!safetyWarningSeen) {
                        CarSafetyWizard(onDone = CarSettings::markSafetyWarningSeen)
                        return@Row
                    }
                    // Header navigation (breadcrumb trail) - off for now, kept to tweak later.
                    // BreadcrumbBar(navigator)
                    // The dock stays put down the left, the keyboard included: it takes no height
                    // from the screen beside it, so there is nothing to give back while typing.
                    CarDock(navigator)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        // Home never leaves the composition; other screens cover it. Dropping it
                        // would tear down the map's surface, and coming back would then show black
                        // while the nav app redrew on a new one.
                        CarHome()
                        if (screen != CarScreen.Home) {
                            // Each new screen settles in: a short fade and a slight grow, over an
                            // opaque background that's there at once, so the map never flashes
                            // through between two screens.
                            val enter = remember(screen) { Animatable(0f) }
                            LaunchedEffect(screen) {
                                enter.animateTo(1f, tween(MyCarMotion.ScreenMs, easing = MyCarMotion.Decelerate))
                            }
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    // Keeps touches that miss this screen's controls off the map.
                                    .pointerInput(Unit) { detectTapGestures { } }
                                    .graphicsLayer {
                                        alpha = enter.value
                                        val grow = SCREEN_ENTER_SCALE + (1f - SCREEN_ENTER_SCALE) * enter.value
                                        scaleX = grow
                                        scaleY = grow
                                    },
                            ) {
                                when (screen) {
                                    CarScreen.Home -> Unit
                                    CarScreen.Apps -> CarLauncher()
                                    CarScreen.Media -> mediaApp?.let { MediaScreen(it) { navigator.back() } }
                                    CarScreen.Browse -> BrowseAppScreen(navigator)
                                    CarScreen.Settings -> CarSettingsScreen()
                                    CarScreen.Dashboard -> CarDashboardScreen()
                                    CarScreen.Weather -> WeatherScreen()
                                    CarScreen.VehicleData -> VehicleDataScreen()
                                    is CarScreen.Notifications -> NotificationsScreen(screen.replyTo)
                                    is CarScreen.Messages -> MessagesScreen(screen.packageName)
                                    is CarScreen.App -> CarApps.find(screen.id)?.content?.invoke()
                                }
                            }
                        }
                        CarToastOverlay(Modifier.align(Alignment.BottomCenter))
                        CallPromptOverlay()
                        BluetoothPairOverlay()
                        PopupTimer()
                        // The dashboard is already listing them, so it needs no popup.
                        NotificationPopup(
                            showNew = screen !is CarScreen.Notifications && screen != CarScreen.Dashboard,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                        // Above the popup: a call is more urgent than any notification.
                        VoiceHelpOverlay(Modifier.align(Alignment.Center))
                        CallOverlay(Modifier.align(Alignment.TopCenter))
                        AssistantOverlay(Modifier.align(Alignment.BottomCenter))
                        if (io.github.sirionero.mycar.BuildConfig.DEBUG) {
                            // Bottom corner, not top: a screen's own header almost always puts
                            // something in the top-right (settings, search, a Liked Songs
                            // shortcut) and this badge used to sit right on top of it, hiding
                            // whatever the screen drew there.
                            DebugBuildBadge(Modifier.align(Alignment.BottomEnd))
                        }
                    }
                }
                // Over everything, dock included: a long press on an app, anywhere.
                AppMenu(navigator, rememberRunningApps())
                if (safetyWarningSeen) CarWhatsNew()
                }
            }
          }
        }
    }
}

/**
 * A single blocking screen, shown once per install before anything else the car screen can show -
 * Home included. The phone's own setup guide carries the same warning, but a driver who plugs in
 * without ever opening the phone app would otherwise never see it, on the one screen they are
 * actually about to use while driving. [onDone] fires once, from its own button; there is no way
 * to skip past this without tapping it.
 */
@Composable
private fun CarSafetyWizard(onDone: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The card and its button share one width, capped so neither sprawls across a wide screen.
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Drive safely", style = ChromeType.headline, fontWeight = FontWeight.Bold)
                    Text(
                        "myCar is unofficial software: it is not made or reviewed by any " +
                            "carmaker, and it can fail without warning.",
                        style = ChromeType.body,
                    )
                    Text(
                        "Never interact with this screen while driving. Set the route or the " +
                            "song before you go, then keep your attention on the road.",
                        style = ChromeType.body,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            GsButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("I understand", style = ChromeType.body)
            }
        }
    }
}

/**
 * A templated app that isn't navigation, full size with its own title bar and Stop button - the
 * same [CarAppStage] chrome the map uses on Home, just not embedded there. [CarServices.browse]
 * is a connection of its own, so opening one of these never bumps the map off Home.
 */
@Composable
private fun BrowseAppScreen(navigator: CarNavigator) {
    val status by CarServices.browse.status.collectAsState()
    val frame by CarEnvironment.frame.collectAsState()
    val template by CarServices.browse.template.collectAsState()
    val leave = {
        CarServices.stopBrowse()
        navigator.back()
    }
    // An app that never answers is as stuck as one that says no; don't leave the driver on "Loading".
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(template == null) {
        timedOut = false
        if (template == null) {
            delay(APP_START_TIMEOUT_MS)
            timedOut = true
        }
    }
    val failed = status.phase == CarAppConnection.Phase.REJECTED || status.phase == CarAppConnection.Phase.FAILED
    if (failed || timedOut) {
        AppWontStart(status.app ?: "This app", leave)
        return
    }
    CarAppStage(CarServices.browse, status, frame, onDisconnect = leave)
}

private const val APP_START_TIMEOUT_MS = 15_000L

/** How small a screen starts as it settles in, before growing to full size. */
private const val SCREEN_ENTER_SCALE = 0.97f

/** What a car app that won't run under myCar gets instead of a loading screen that never ends. */
@Composable
private fun AppWontStart(app: String, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$app doesn't work with myCar yet.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "It didn't start. Some apps only run on Google's Android Auto.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GsButton(onClick = onBack, tone = GsTone.Tonal) { Text("Back") }
    }
}

/**
 * Marks a debug build on the car screen itself, not just the phone's Home screen - the one
 * that matters while driving, since a debug build is slower and less tested than what ships.
 */
@Composable
private fun DebugBuildBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(6.dp),
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            "DEBUG BUILD",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * "Home > Apps > Settings", like a file manager's address bar: hidden at Home itself (nothing to
 * show yet), and grows by one crumb for every screen pushed from there. The back arrow undoes one
 * step; a crumb jumps straight to it, dropping everything after - this is the only on-screen way
 * back at all, since a car touchscreen has no hardware button for it.
 */
@Composable
private fun BreadcrumbBar(navigator: CarNavigator) {
    val trail = navigator.trail
    if (trail.size <= 1) return
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(start = 2.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GsIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", navigator::back, size = 32.dp, iconSize = 20.dp)
        Spacer(Modifier.width(2.dp))
        trail.forEachIndexed { index, crumb ->
            val isLast = index == trail.lastIndex
            Text(
                crumb.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isLast) Modifier else Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { navigator.jumpTo(index) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            if (!isLast) {
                Text(
                    "›",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/** Stands in for the phone's vibration motor on the car screen: does nothing. */
private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}
