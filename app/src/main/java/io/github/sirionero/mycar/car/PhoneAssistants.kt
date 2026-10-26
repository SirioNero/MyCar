package io.github.sirionero.mycar.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.car.app.CarToast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.car.theme.GsColors
import io.github.sirionero.mycar.car.theme.GsIconBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Voice assistants other than myCar's own: the phone's default one, or a particular assistant app
 * (Google, Gemini, Alexa, ChatGPT...). They run on the phone - their own screen, the phone's
 * microphone, and their voice in the car only over Bluetooth: Android gives no app a way to put
 * another app's assistant on the car screen. The car says where to look.
 */
object PhoneAssistants {
    /** myCar's own assistant, as it always was. */
    const val MYCAR = "mycar"
    /** Whatever assistant the phone uses by default. */
    const val PHONE = "phone"

    class App(val packageName: String, val label: String)

    /**
     * What an assistant answers to, voice first: the hands-free voice search made for cars and
     * headsets, which starts listening at once, then the voice command and voice assist keys.
     * Plain ASSIST comes last - Google's and Gemini's open their typed chat for it, not the voice
     * the driver pressed the button for.
     */
    private val VOICE_FIRST = listOf(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE, Intent.ACTION_VOICE_COMMAND, VOICE_ASSIST, Intent.ACTION_ASSIST)

    /** Long-press-home's voice assistant: not public in the SDK, but sent and answered all the same. */
    private const val VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"

    /** Android's own "which app?" chooser, which nobody can use from the driver's seat. */
    private val CHOOSERS = setOf("android", "com.android.intentresolver")

    /** Assistant apps on the phone, by name. */
    fun installed(context: Context): List<App> {
        val pm = context.packageManager
        val packages = LinkedHashSet<String>()
        VOICE_FIRST.forEach { action ->
            pm.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
                .mapTo(packages) { it.activityInfo.packageName }
        }
        packages.remove(context.packageName)
        packages.removeAll(CHOOSERS)
        return packages.mapNotNull { pkg ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()?.let { App(pkg, it) }
        }.sortedBy { it.label.lowercase() }
    }

    /** The name to show for [choice]: myCar, the phone's assistant, or the app's own. */
    fun label(context: Context, choice: String): String = when (choice) {
        MYCAR -> "myCar"
        PHONE -> "the phone's assistant"
        else -> runCatching {
            context.packageManager.let { it.getApplicationLabel(it.getApplicationInfo(choice, 0)).toString() }
        }.getOrDefault(choice)
    }

    /**
     * Starts [choice] - [PHONE] or an app's package - listening on the phone. False when nothing
     * could be started: the app was uninstalled, say, and the caller falls back to myCar's own.
     */
    fun launch(context: Context, choice: String): Boolean {
        if (choice != PHONE) return start(context, choice)
        // The phone's own: aimed at the app that holds the assistant role, when Android says which,
        // so the voice requests reach it rather than whichever app answers them first.
        val default = defaultAssistant(context)
        return start(context, default) || (default != null && start(context, null))
    }

    /** Sends [app] - or, with null, whichever app answers - the first voice request it takes. */
    private fun start(context: Context, app: String?): Boolean {
        val pm = context.packageManager
        for (action in VOICE_FIRST) {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Asked for anyway, to say it's voice the driver wants rather than the keyboard.
            if (action == Intent.ACTION_ASSIST) intent.putExtra(Intent.EXTRA_ASSIST_INPUT_HINT_KEYBOARD, false)
            app?.let(intent::setPackage)
            val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: continue
            // Several apps answer and none is the default: take the first rather than a chooser.
            if (resolved.activityInfo.packageName in CHOOSERS) {
                val first = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    .firstOrNull { it.activityInfo.packageName != context.packageName && it.activityInfo.packageName !in CHOOSERS }
                    ?: continue
                intent.setClassName(first.activityInfo.packageName, first.activityInfo.name)
            }
            val started = runCatching { context.startActivity(intent) }
                .onFailure { MyCarLog.w("assistant: $action for ${app ?: "the phone"} failed: ${it.message}") }
                .isSuccess
            if (started) {
                MyCarLog.i("assistant: started ${app ?: "the phone's assistant"} with $action")
                return true
            }
        }
        return false
    }
}

/**
 * The package of the phone's default assistant - the one the corner swipe opens - or null when
 * Android won't say. Gemini answers through the Google app, so that's the one named for it.
 */
private fun defaultAssistant(context: Context): String? = listOf("assistant", "voice_interaction_service").firstNotNullOfOrNull { key ->
    runCatching { Settings.Secure.getString(context.contentResolver, key) }.getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.let { ComponentName.unflattenFromString(it)?.packageName }
}

/** Which assistant the dock's microphone and the steering wheel's voice button start. */
object AssistantChoice {
    private const val KEY = "car_assistant"
    private var app: Context? = null
    private val flow = MutableStateFlow(PhoneAssistants.MYCAR)
    val current: StateFlow<String> = flow

    fun init(context: Context) {
        val app = context.applicationContext.also { this.app = it }
        flow.value = AppSettings.getString(app, KEY, PhoneAssistants.MYCAR)
    }

    fun set(choice: String) {
        flow.value = choice
        app?.let { AppSettings.putString(it, KEY, choice) }
    }

    /**
     * For [CarAssistant.start]: when another assistant is chosen, starts it and says so on the car
     * screen, and returns true. False means myCar's own should listen - it's the one chosen, or the
     * other couldn't start.
     */
    fun startOther(context: Context): Boolean {
        val choice = flow.value
        if (choice == PhoneAssistants.MYCAR) return false
        val name = PhoneAssistants.label(context, choice)
        if (!PhoneAssistants.launch(context, choice)) {
            CarToasts.show(this, "Couldn't start $name. myCar is listening instead.", CarToast.LENGTH_LONG)
            return false
        }
        val where = if (choice == PhoneAssistants.PHONE) "Listening on the phone" else "$name is listening on the phone"
        CarToasts.show(this, where, CarToast.LENGTH_LONG)
        return true
    }
}

/**
 * The car settings' assistant row: myCar's own, the phone's default, then each assistant app on
 * the phone, in a strip that scrolls sideways when they don't all fit.
 */
@Composable
internal fun AssistantPicker() {
    val context = LocalContext.current
    val current by AssistantChoice.current.collectAsState()
    val apps = remember { PhoneAssistants.installed(context) }
    val options = listOf(PhoneAssistants.MYCAR to "myCar", PhoneAssistants.PHONE to "Phone's assistant") +
        apps.map { it.packageName to it.label }
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Voice assistant", style = MaterialTheme.typography.titleMedium, color = scheme.primary)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { (id, label) ->
                val on = id == current
                GsIconBox(
                    onClick = { AssistantChoice.set(id) },
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
            "Another assistant listens and answers on the phone, and its voice reaches the car only " +
                "over Bluetooth. myCar's own works offline and shows on the car screen.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
