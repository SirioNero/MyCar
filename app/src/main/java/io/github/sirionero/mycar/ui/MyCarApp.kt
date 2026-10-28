package io.github.sirionero.mycar.ui

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.CertProvider
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.home.HomeAssistant
import io.github.sirionero.mycar.stats.UsageStats
import io.github.sirionero.mycar.xonos.Xonos
import io.github.sirionero.mycar.xonos.XonosAccount

private enum class Screen { INTRO, HOME, LOGS, SETTINGS, CAR_PREVIEW, CERT_SETUP, SETUP_GUIDE, USAGE_NOTES, HELP, WHATS_NEW, RECOMMENDED_APPS }

/** Three screens and a back stack of depth one: no navigation library needed. */
@Composable
fun MyCarApp(onDisconnect: () -> Unit, onRequestCallScreening: () -> Unit) {
    val context = LocalContext.current
    val needsCertSetup = remember {
        !AppSettings.hasSkippedCertSetup(context) && !CertProvider.hasAnyCert(context)
    }
    val needsSetupGuide = remember { !AppSettings.hasSeenPermissionsSetup(context) }
    // Where launch goes once the intro (if any) is done.
    val afterIntro = remember {
        when {
            needsCertSetup -> Screen.CERT_SETUP
            needsSetupGuide -> Screen.SETUP_GUIDE
            // Set up before usage notes existed: ask once, on the setup guide's own page.
            UsageStats.sends && !UsageStats.asked(context) -> Screen.USAGE_NOTES
            else -> Screen.HOME
        }
    }
    var screen by rememberSaveable { mutableStateOf(if (Intro.due(context)) Screen.INTRO else afterIntro) }
    // A replay from About goes back to Settings; the launch one carries on into setup.
    var introReturn by rememberSaveable { mutableStateOf(afterIntro) }
    // Home's "it worked" card opens Settings at usage notes rather than at the top.
    var settingsAtUsageNotes by rememberSaveable { mutableStateOf(false) }
    // What's new and Connection help open from Home and from Settings; back returns to whichever.
    var backTo by rememberSaveable { mutableStateOf(Screen.SETTINGS) }
    BackHandler(enabled = screen != Screen.HOME && screen != Screen.INTRO) {
        screen = when (screen) {
            Screen.RECOMMENDED_APPS, Screen.LOGS, Screen.CAR_PREVIEW -> Screen.SETTINGS
            Screen.HELP, Screen.WHATS_NEW -> backTo
            else -> Screen.HOME
        }
    }

    // Home stays upright; every other screen turns with the phone.
    LaunchedEffect(screen) {
        (context as? Activity)?.requestedOrientation = if (screen == Screen.HOME) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // The guide asks for this now, but anyone who finished it before that never saw the page.
    // Android shows one permission question at a time and drops one asked meanwhile: the home
    // network's waits for this one's answer.
    var notificationAsking by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationAsking = false
    }
    // Reads the certificate once in the background, so Settings has it ready when opened.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { CertSummary.read(context) } }
    }
    LaunchedEffect(Unit) {
        if (!needsSetupGuide &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationAsking = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Android 17 keeps myCar off the home network until it's allowed (see LocalNetwork), and
    // Plancia's and Xonos's servers are usually there, whatever their addresses look like: asked
    // once a run, on Home, when either is set up. With Bluetooth allowed, Android allows it
    // without a word.
    val localNetworkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        MyCarLog.i("local network: ${if (granted) "allowed" else "refused"}")
    }
    val plancia by HomeAssistant.config.collectAsState()
    val xonos by Xonos.setUp.collectAsState()
    LaunchedEffect(Unit) { XonosAccount.adoptIfUnset(context) }
    LaunchedEffect(plancia, xonos, screen, notificationAsking) {
        if (notificationAsking || screen != Screen.HOME || LocalNetwork.askedAtStart || LocalNetwork.allowed(context)) return@LaunchedEffect
        val urls = plancia.urls + if (xonos) Xonos.settings.load().urls else emptyList()
        if (LocalNetwork.mayBlock(context, urls)) {
            LocalNetwork.askedAtStart = true
            localNetworkPermission.launch(LocalNetwork.PERMISSION)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .consumeWindowInsets(WindowInsets.statusBars),
    ) {
        if (BuildConfig.DEBUG && screen != Screen.CAR_PREVIEW && screen != Screen.INTRO) DebugBuildBanner()

        Column(Modifier.weight(1f)) {
            when (screen) {
                Screen.INTRO -> IntroScreen(onDone = { screen = introReturn })
                Screen.HOME -> HomeScreen(
                    onOpenSettings = { settingsAtUsageNotes = false; screen = Screen.SETTINGS },
                    onOpenWhatsNew = { backTo = Screen.HOME; screen = Screen.WHATS_NEW },
                    onOpenHelp = { backTo = Screen.HOME; screen = Screen.HELP },
                    onOpenUsageNotes = { settingsAtUsageNotes = true; screen = Screen.SETTINGS },
                    onDisconnect = onDisconnect,
                )
                Screen.WHATS_NEW -> WhatsNewScreen(onBack = { screen = backTo })
                Screen.LOGS -> LogsScreen(onBack = { screen = Screen.SETTINGS })
                Screen.SETTINGS -> SettingsScreen(
                    onBack = { screen = Screen.HOME },
                    onOpenRecommendedApps = { settingsAtUsageNotes = false; screen = Screen.RECOMMENDED_APPS },
                    onOpenWhatsNew = { settingsAtUsageNotes = false; backTo = Screen.SETTINGS; screen = Screen.WHATS_NEW },
                    onOpenHelp = { settingsAtUsageNotes = false; backTo = Screen.SETTINGS; screen = Screen.HELP },
                    onOpenCarPreview = { settingsAtUsageNotes = false; screen = Screen.CAR_PREVIEW },
                    onOpenLogs = { settingsAtUsageNotes = false; screen = Screen.LOGS },
                    onReplayTutorial = { screen = Screen.SETUP_GUIDE },
                    onReplayIntro = { Intro.replay(context); introReturn = Screen.SETTINGS; screen = Screen.INTRO },
                    atUsageNotes = settingsAtUsageNotes,
                )
                Screen.RECOMMENDED_APPS -> RecommendedAppsScreen(onBack = { screen = Screen.SETTINGS })
                Screen.CAR_PREVIEW -> CarPreviewScreen(onBack = { screen = Screen.SETTINGS })
                Screen.HELP -> HelpScreen(
                    onBack = { screen = backTo },
                    onRequestCallScreening = onRequestCallScreening,
                )
                Screen.CERT_SETUP -> CertSetupScreen(
                    onDone = {
                        screen = if (AppSettings.hasSeenPermissionsSetup(context)) Screen.HOME else Screen.SETUP_GUIDE
                    },
                )
                Screen.SETUP_GUIDE -> SetupGuideScreen(
                    onRequestCallScreening = onRequestCallScreening,
                    onDone = { screen = Screen.HOME },
                )
                Screen.USAGE_NOTES -> SetupGuideScreen(
                    onRequestCallScreening = onRequestCallScreening,
                    onDone = { screen = Screen.HOME },
                    onlyUsageNotes = true,
                )
            }
        }
        // A car keyboard is open: offer the phone's own, for a passenger.
        if (screen != Screen.CAR_PREVIEW) PhoneTypingBar()
    }
}

/**
 * Debug builds only ([BuildConfig.DEBUG]); a release build never composes this. Sits above every
 * phone screen, below the status bar - not the car screen, which is a separate render target
 * ([io.github.sirionero.mycar.car.CarUi] carries its own corner badge) - so a debug install is
 * never mistaken for a release one.
 */
@Composable
private fun DebugBuildBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        color = Color(0xFFF5C518),
        contentColor = Color.Black,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "DEBUG BUILD",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
