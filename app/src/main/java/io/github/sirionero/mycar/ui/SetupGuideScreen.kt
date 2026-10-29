package io.github.sirionero.mycar.ui

import io.github.sirionero.mycar.speech.SpeechModels

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.LinearProgressIndicator

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.KEY_MIC_ASKED
import io.github.sirionero.mycar.audio.AudioRoute
import io.github.sirionero.mycar.car.CarSettings
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import io.github.sirionero.mycar.call.CarCalls
import io.github.sirionero.mycar.car.Prefetch
import io.github.sirionero.mycar.host.AndroidAuto
import io.github.sirionero.mycar.notify.MyCarNotificationListener
import io.github.sirionero.mycar.stats.UsageStats
import androidx.compose.runtime.DisposableEffect

/**
 * One page of the guide. [permissions] drives a system permission dialog; [command] is an adb
 * command to copy; [actionLabel] with [onAction] is a button that does something else (opens a
 * settings screen) without by itself finishing the page. Everything is skippable: none of this
 * can be verified as done, only offered.
 */
private class GuidePage(
    val title: String,
    /** Plain text, or an [AnnotatedString] when part of it needs to stand out. */
    val explanation: CharSequence,
    /** Drawn inside the page's badge, in the badge's content colour. */
    val icon: @Composable (Color) -> Unit,
    /** Marked "Optional" above the title: everything works without it. */
    val optional: Boolean = false,
    val permissions: Array<String>? = null,
    val command: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    /** Drawn under [explanation]. */
    val content: (@Composable () -> Unit)? = null,
    /** Only shown when the car audio page's choice may send the sound through myCar. */
    val usbOnly: Boolean = false,
)

/** A plain Material icon, sized to fill the badge's icon box. */
private fun glyph(icon: ImageVector): @Composable (Color) -> Unit = { tint ->
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize())
}

/** True once every permission in [permissions] is already granted - nothing left for this page to do. */
private fun granted(context: Context, permissions: Array<String>): Boolean =
    permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/** Whether myCar already holds the hidden `PROJECT_MEDIA` app-op an adb command grants -
 * checked the same way the platform checks it, since there is no public permission for it. */
private fun hasProjectMediaOp(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
    val mode = runCatching {
        ops.unsafeCheckOpNoThrow("android:project_media", android.os.Process.myUid(), context.packageName)
    }.getOrDefault(AppOpsManager.MODE_IGNORED)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun hasCallScreeningRole(context: Context): Boolean {
    val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
    return runCatching { roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }.getOrDefault(false)
}

/** Whether the driver has already granted myCar's [MyCarNotificationListener] access. */
private fun hasNotificationAccess(context: Context): Boolean {
    val manager = context.getSystemService(NotificationManager::class.java) ?: return false
    val component = ComponentName(context, MyCarNotificationListener::class.java)
    return runCatching { manager.isNotificationListenerAccessGranted(component) }.getOrDefault(false)
}

/**
 * The usage notes question: the setup guide's last page, and on its own the one screen people
 * who set up before the notes existed see once. Leaving it without turning them on is a no.
 */
private fun usageNotesPage(context: Context) = GuidePage(
    title = Words.get(R.string.setup_guide_usage_title),
    explanation = USAGE_STATS_INTRO,
    icon = { HeartIcon(it) },
    content = {
        UsageStatsChoices()
        DisposableEffect(Unit) {
            onDispose { if (!UsageStats.asked(context)) UsageStats.set(context, false) }
        }
    },
)

private fun pages(context: Context, onRequestCallScreening: () -> Unit): List<GuidePage> = buildList {
    // --- Safety: always first, and never skipped by an already-granted check ---------
    add(
        GuidePage(
            title = Words.get(R.string.setup_guide_safety_title),
            explanation = Words.get(R.string.setup_guide_safety_body),
            icon = glyph(Icons.Filled.Warning),
        ),
    )

    // --- Permissions: skipped outright once already granted, nothing left to ask -----
    // Asked whether or not car audio is on: voice reply and the assistant need it too.
    if (!granted(context, arrayOf(Manifest.permission.RECORD_AUDIO))) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_mic_title),
                explanation = Words.get(R.string.setup_guide_mic_body),
                icon = { MicIcon(it) },
                permissions = arrayOf(Manifest.permission.RECORD_AUDIO),
            ),
        )
    }
    if (!granted(context, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_location_title),
                explanation = Words.get(R.string.setup_guide_location_body),
                icon = glyph(Icons.Filled.LocationOn),
                // Both, in one request: from Android 12 one asking for the precise location alone
                // is dropped without a dialog.
                permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            ),
        )
    }
    if (!granted(context, arrayOf(Manifest.permission.READ_CALENDAR))) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_calendar_title),
                explanation = Words.get(R.string.setup_calendar_body),
                icon = glyph(Icons.Filled.DateRange),
                permissions = arrayOf(Manifest.permission.READ_CALENDAR),
            ),
        )
    }
    val callPermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.CALL_PHONE,
    )
    if (!granted(context, callPermissions)) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_calls_title),
                explanation = Words.get(R.string.setup_guide_calls_body),
                icon = glyph(Icons.Filled.Phone),
                permissions = callPermissions,
            ),
        )
    }
    // From Android 17 the home network goes with Bluetooth: both are "Nearby devices" (LocalNetwork).
    val nearbyPermissions = listOfNotNull(
        Manifest.permission.BLUETOOTH_CONNECT,
        LocalNetwork.PERMISSION.takeIf { LocalNetwork.asked },
    ).toTypedArray()
    if (!granted(context, nearbyPermissions)) {
        add(
            GuidePage(
                title = if (LocalNetwork.asked) Words.get(R.string.setup_guide_nearby_title) else "Bluetooth.",
                explanation = if (LocalNetwork.asked) {
                    Words.get(R.string.setup_guide_nearby_body_home)
                } else {
                    Words.get(R.string.setup_guide_nearby_body)
                },
                icon = glyph(Icons.Filled.Call),
                permissions = nearbyPermissions,
            ),
        )
    }

    if (!granted(context, arrayOf(Manifest.permission.POST_NOTIFICATIONS))) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_notifications_title),
                explanation = Words.get(R.string.setup_guide_notifications_body),
                icon = glyph(Icons.Filled.Notifications),
                permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            ),
        )
    }
    if (!hasNotificationAccess(context)) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_messages_title),
                explanation = Words.get(R.string.setup_guide_messages_body),
                icon = glyph(Icons.Filled.Email),
                actionLabel = Words.get(R.string.setup_messages_open),
                onAction = {
                    // Phones without that screen: the app's own settings instead of a crash.
                    runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                        .onFailure {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
                                )
                            }
                        }
                },
            ),
        )
    }

    // --- Android Auto ------------------------------------------------------------------
    val androidAutoPackage = AndroidAuto.installedPackage(context)
        ?.takeIf { AndroidAuto.isActive(context, it) }
    if (androidAutoPackage != null) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_aa_title),
                explanation = Words.get(R.string.setup_guide_aa_body),
                icon = glyph(Icons.Filled.Close),
                actionLabel = Words.get(R.string.setup_guide_aa_open),
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", androidAutoPackage, null)),
                    )
                },
            ),
        )
    }

    // --- Default handler ---------------------------------------------------------------
    add(
        GuidePage(
            title = Words.get(R.string.setup_guide_choose_title),
            explanation = Words.get(R.string.setup_guide_choose_body),
            icon = glyph(Icons.Filled.Check),
        ),
    )

    // --- Car audio: always asked, and it decides whether the adb pages below show ---------
    add(
        GuidePage(
            title = Words.get(R.string.setup_guide_audio_title),
            explanation = Words.get(R.string.setup_guide_audio_body),
            icon = glyph(Icons.Filled.PlayArrow),
            content = { AudioChoice() },
        ),
    )

    // --- Voice: both recommended models in one tap, skipped once they're on the phone ---
    if (!SpeechModels.recommended().all { SpeechModels.isReady(context, it) }) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_voice_title),
                explanation = Words.get(R.string.setup_guide_voice_body, SpeechModels.recommended().sumOf { it.downloadMb }),
                icon = { MicIcon(it) },
                optional = true,
                actionLabel = Words.get(R.string.setup_guide_voice_download),
                onAction = { SpeechModels.downloadRecommended(context) },
                content = { VoiceDownloadStatus() },
            ),
        )
    }

    // --- Optional: each is skipped once its actual effect is already in place --------
    if (!hasProjectMediaOp(context)) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_project_media_title),
                explanation = Words.get(R.string.setup_guide_project_media_body),
                icon = glyph(Icons.Filled.Build),
                optional = true,
                usbOnly = true,
                command = "adb shell appops set ${BuildConfig.APPLICATION_ID} PROJECT_MEDIA allow",
            ),
        )
    }
    if (!granted(context, arrayOf(Manifest.permission.WRITE_SECURE_SETTINGS))) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_secure_settings_title),
                explanation = Words.get(R.string.setup_guide_secure_settings_body),
                icon = glyph(Icons.Filled.Build),
                optional = true,
                usbOnly = true,
                command = "adb shell pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS",
            ),
        )
    }
    if (!hasCallScreeningRole(context)) {
        add(
            GuidePage(
                title = Words.get(R.string.setup_guide_screening_title),
                explanation = Words.get(R.string.setup_guide_screening_body),
                icon = glyph(Icons.Filled.Call),
                optional = true,
                actionLabel = Words.get(R.string.setup_screening_button),
                onAction = onRequestCallScreening,
            ),
        )
    }

    // --- Apps worth installing, once everything above is set up -----------------------
    if (SHOW_RECOMMENDED_APPS) {
        add(
            GuidePage(
                title = Words.get(R.string.intro_apps_title),
                explanation = RECOMMENDED_INTRO,
                icon = { StarIcon(it) },
                content = { RecommendedAppsList() },
            ),
        )
    }

    // --- The very end, every time: usage notes. On a replay, the switch shows what was
    // chosen before.
    if (UsageStats.sends) add(usageNotesPage(context))
}

/**
 * Bluetooth or USB for the car's music, as two cards. Bluetooth comes first and is picked for a
 * driver who hasn't chosen yet: it never asks for consent, and the map app's voice reaches the car.
 */
/** How the voice download is going: a bar while it runs, a line once it's done or failed. */
@Composable
private fun VoiceDownloadStatus() {
    val context = LocalContext.current
    val progress by SpeechModels.progress.collectAsState()
    val states = SpeechModels.recommended().map { SpeechModels.state(it).collectAsState().value }
    val line = when {
        progress != null -> stringResource(R.string.setup_guide_voice_progress, progress!!)
        states.all { it == SpeechModels.State.Ready } -> stringResource(R.string.setup_guide_voice_ready)
        states.any { it is SpeechModels.State.Failed } -> stringResource(R.string.setup_guide_voice_failed)
        else -> null
    }
    LaunchedEffect(Unit) { SpeechModels.init(context) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
        line?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun AudioChoice() {
    val route by CarSettings.audioRoute.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AudioCard(
            stringResource(R.string.setup_audio_automatic), stringResource(R.string.setup_audio_recommended),
            stringResource(R.string.setup_audio_automatic_body),
            selected = route == AudioRoute.AUTO,
        ) { CarSettings.setAudioRoute(AudioRoute.AUTO) }
        AudioCard(
            "Bluetooth", null,
            stringResource(R.string.setup_audio_bluetooth_body),
            selected = route == AudioRoute.BLUETOOTH,
        ) { CarSettings.setAudioRoute(AudioRoute.BLUETOOTH) }
        AudioCard(
            stringResource(R.string.setup_audio_through_mycar), null,
            stringResource(R.string.setup_audio_through_mycar_body),
            selected = route == AudioRoute.CAR_LINK,
        ) { CarSettings.setAudioRoute(AudioRoute.CAR_LINK) }
    }
}

@Composable
private fun AudioCard(title: String, tag: String?, body: String, selected: Boolean, onClick: () -> Unit) {
    val c = introColors()
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) c.primaryC else Color.Transparent)
            .border(BorderStroke(2.dp, if (selected) c.primary else c.outline), shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, color = if (selected) c.onPrimaryC else c.onSurface, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold))
            tag?.let { Text(it, color = c.primary, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)) }
            Text(
                body, color = if (selected) c.onPrimaryC else c.onVariant,
                style = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The first-run guide, one page at a time: permissions, then removing Android Auto if it is still installed, then the optional extras. Nothing
 * here can be verified as done except a permission grant, so every other page is a "Next" a
 * driver takes on trust, with "Skip" sitting right next to it.
 */
@Composable
fun SetupGuideScreen(onRequestCallScreening: () -> Unit, onDone: () -> Unit, onlyUsageNotes: Boolean = false) {
    val context = LocalContext.current
    val allPages = remember { if (onlyUsageNotes) listOf(usageNotesPage(context)) else pages(context, onRequestCallScreening) }
    // The pages for sound through myCar all come after the car audio page, so dropping them never
    // moves the page the driver is on. Automatic may send it that way too.
    val route by CarSettings.audioRoute.collectAsState()
    val throughMyCar = route != AudioRoute.BLUETOOTH
    val pages = remember(throughMyCar) { allPages.filter { throughMyCar || !it.usbOnly } }
    var index by remember { mutableIntStateOf(0) }

    // Only ours to handle once there is a previous page to return to; at the first page, the
    // system back press falls through to whatever the screen beneath this one does with it.
    BackHandler(enabled = index > 0) { index -= 1 }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // A "no" here counts as asked: refused again in the car, Android stops asking, and the car
        // must say why instead of staying silent (MyCarActivity's audioPermission).
        if (result[Manifest.permission.RECORD_AUDIO] == false) AppSettings.putString(context, KEY_MIC_ASKED, "1")
        if (result.values.any { it }) {
            Prefetch.warm(context, force = true)
            CarCalls.start(context)
        }
        index += 1
    }

    val page = pages.getOrNull(index)
    if (page == null) {
        AppSettings.setSeenPermissionsSetup(context)
        onDone()
        return
    }

    val alreadyGranted = page.permissions?.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    } ?: false

    val c = introColors()
    // The badges take turns through the phone's three accent colours, as the release intro does.
    val (badge, onBadge) = listOf(c.primaryC to c.onPrimaryC, c.secondaryC to c.onSecondaryC, c.tertiaryC to c.onTertiaryC)[index % 3]
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "spin",
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(c.surface).systemBarsPadding()) {
        val w = maxWidth
        // One percent of the screen's width, as in the intro, so it scales the same way.
        fun cq(n: Float): Dp = w * (n / 100f)
        val density = LocalDensity.current
        fun cqSp(n: Float): TextUnit = with(density) { cq(n).toSp() }

        Column(Modifier.fillMaxSize().padding(horizontal = cq(8f))) {
            // The page scrolls above the buttons, so a tall one (the usage notes' choices) never
            // pushes them off the screen. Short pages sit in the middle.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                key(index) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = maxHeight)
                            .padding(vertical = cq(6f)),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(cq(28f)).pop(entrance(index, 50, 800)), contentAlignment = Alignment.Center) {
                            Cookie(badge, Modifier.fillMaxSize().graphicsLayer { rotationZ = spin })
                            Box(Modifier.size(cq(12f))) { page.icon(onBadge) }
                        }
                        Spacer(Modifier.height(cq(7f)))
                        if (page.optional) {
                            Text(
                                stringResource(R.string.setup_guide_optional), color = c.primary,
                                style = TextStyle(fontSize = cqSp(3.4f), fontWeight = FontWeight.Bold, letterSpacing = cqSp(0.4f)),
                                modifier = Modifier.up(entrance(index, 150, 700), cq(7f)),
                            )
                            Spacer(Modifier.height(cq(1.5f)))
                        }
                        Text(
                            page.title, color = c.onSurface,
                            // Long titles step down a size so they stay on two lines.
                            style = TextStyle(
                                fontSize = cqSp(if (page.title.length > 16) 8f else 10f), fontWeight = FontWeight.Black,
                                letterSpacing = cqSp(-0.4f), lineHeight = cqSp(if (page.title.length > 16) 8.6f else 10.5f),
                            ),
                            modifier = Modifier.up(entrance(index, 200, 700), cq(7f)),
                        )
                        Spacer(Modifier.height(cq(4f)))
                        Column(
                            Modifier.up(entrance(index, 350, 700), cq(7f)),
                            verticalArrangement = Arrangement.spacedBy(cq(3f)),
                        ) {
                            Text(
                                page.explanation as? AnnotatedString ?: AnnotatedString(page.explanation.toString()),
                                color = c.onVariant,
                                style = TextStyle(fontSize = cqSp(4.5f), lineHeight = cqSp(6.5f)),
                            )
                            page.command?.let { CopyableCommand(it) }
                            page.content?.invoke()
                            if (page.permissions != null && alreadyGranted) {
                                Text(
                                    stringResource(R.string.setup_guide_already_allowed), color = c.primary,
                                    style = TextStyle(fontSize = cqSp(4.2f), fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                    }
                }
            }

            // A single page has nothing to count.
            if (pages.size > 1) {
                Dots(c, pages.size, index, ::cq, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(cq(5f)))
            }
            val back = if (index > 0) ({ index -= 1 }) else null
            val next = { index += 1 }
            when {
                page.permissions != null && !alreadyGranted -> {
                    PillRow(c, ::cq, ::cqSp, back, stringResource(R.string.common_allow)) { launcher.launch(page.permissions) }
                    Pill(c, ::cqSp, stringResource(R.string.setup_guide_skip_for_now), PillStyle.TEXT, Modifier.align(Alignment.CenterHorizontally), next)
                }
                page.onAction != null -> {
                    Pill(c, ::cqSp, page.actionLabel ?: stringResource(R.string.common_open), PillStyle.OUTLINED, Modifier.fillMaxWidth().padding(bottom = cq(3f)), page.onAction)
                    PillRow(c, ::cq, ::cqSp, back, stringResource(R.string.common_next), next)
                }
                else -> PillRow(
                    c, ::cq, ::cqSp, back,
                    if (index == pages.lastIndex) stringResource(R.string.common_done) else stringResource(R.string.common_next), next,
                )
            }
            Spacer(Modifier.height(cq(6f)))
        }
    }
}

internal enum class PillStyle { FILLED, OUTLINED, TEXT }

/** The intro's rounded buttons: filled for the step's main action, outlined beside it, bare text to skip. */
@Composable
internal fun Pill(c: IntroColors, cqSp: (Float) -> TextUnit, label: String, style: PillStyle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .then(
                when (style) {
                    PillStyle.FILLED -> Modifier.background(c.primary)
                    PillStyle.OUTLINED -> Modifier.border(BorderStroke(2.dp, c.outline), shape)
                    PillStyle.TEXT -> Modifier
                },
            )
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (style == PillStyle.FILLED) c.onPrimary else c.primary,
            style = TextStyle(fontSize = cqSp(4.4f), fontWeight = FontWeight.Bold),
        )
    }
}

/** Back on the left once there's a page to go back to, and the page's main action filling the rest. */
@Composable
internal fun PillRow(
    c: IntroColors, cq: (Float) -> Dp, cqSp: (Float) -> TextUnit,
    onBack: (() -> Unit)?, label: String, onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cq(3f))) {
        if (onBack != null) Pill(c, cqSp, stringResource(R.string.common_back), PillStyle.OUTLINED, Modifier.weight(1f), onBack)
        Pill(c, cqSp, label, PillStyle.FILLED, Modifier.weight(if (onBack != null) 1.4f else 1f), onClick)
    }
}
