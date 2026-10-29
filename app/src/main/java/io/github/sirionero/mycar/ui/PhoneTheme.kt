package io.github.sirionero.mycar.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.car.design.myCarDarkColors
import io.github.sirionero.mycar.car.design.myCarLightColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The phone app's look. The car's screen has its own, in the car's settings. */
enum class PhoneTheme(val key: String) { PHONE("phone"), LIGHT("light"), DARK("dark"), BLACK("black"), MONOKAI("monokai") }

/** The chosen [PhoneTheme], kept in myCar's settings and read by [MyCarTheme]. */
object PhoneThemes {
    private const val PREFS = "mycar_settings"
    private const val KEY = "phone_theme"
    private val state = MutableStateFlow<PhoneTheme?>(null)

    fun current(context: Context): StateFlow<PhoneTheme?> {
        if (state.value == null) {
            val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            state.value = PhoneTheme.entries.firstOrNull { it.key == saved } ?: PhoneTheme.PHONE
        }
        return state
    }

    fun set(context: Context, theme: PhoneTheme) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme.key).apply()
        state.value = theme
    }
}

/** The colours of [theme]: Material You for the phone's, myCar's own palette for the rest. */
@Composable
internal fun phoneColors(theme: PhoneTheme): ColorScheme {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    return when (theme) {
        PhoneTheme.PHONE -> if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        PhoneTheme.LIGHT -> remember { myCarLightColors().copy(primaryContainer = Color(0xFFD8E6FF), onPrimaryContainer = Color(0xFF002E6E)) }
        PhoneTheme.BLACK -> remember { myCarDarkColors().copy(primaryContainer = Color(0xFF12305F), onPrimaryContainer = Color(0xFFD6E4FF)) }
        PhoneTheme.DARK -> remember {
            myCarDarkColors().copy(
                primaryContainer = Color(0xFF12305F), onPrimaryContainer = Color(0xFFD6E4FF),
                background = Color(0xFF17171A), surface = Color(0xFF17171A), surfaceDim = Color(0xFF17171A),
                surfaceContainerLowest = Color(0xFF111113), surfaceContainerLow = Color(0xFF1C1C1F),
                surfaceContainer = Color(0xFF222225), surfaceContainerHigh = Color(0xFF29292D),
                surfaceContainerHighest = Color(0xFF323236), surfaceBright = Color(0xFF3A3A3E),
            )
        }
        PhoneTheme.MONOKAI -> remember { monokaiColors() }
    }
}

/** Monokai's dark olive grey with its yellow-green, cyan, orange and pink. */
private fun monokaiColors(): ColorScheme = darkColorScheme(
    primary = Color(0xFFA6E22E), onPrimary = Color(0xFF1E1F1C),
    primaryContainer = Color(0xFF3B4A1A), onPrimaryContainer = Color(0xFFD8F5A2),
    inversePrimary = Color(0xFF4F7A00),
    secondary = Color(0xFF66D9EF), onSecondary = Color(0xFF1E1F1C),
    secondaryContainer = Color(0xFF49483E), onSecondaryContainer = Color(0xFFF8F8F2),
    tertiary = Color(0xFFFD971F), onTertiary = Color(0xFF1E1F1C),
    tertiaryContainer = Color(0xFF5A3A12), onTertiaryContainer = Color(0xFFFFD8A8),
    error = Color(0xFFF92672), onError = Color(0xFF1E1F1C),
    errorContainer = Color(0xFF5E1730), onErrorContainer = Color(0xFFFFC2D5),
    background = Color(0xFF272822), onBackground = Color(0xFFF8F8F2),
    surface = Color(0xFF272822), onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF3E3D32), onSurfaceVariant = Color(0xFFB8B39A),
    surfaceTint = Color(0xFFA6E22E),
    inverseSurface = Color(0xFFF8F8F2), inverseOnSurface = Color(0xFF272822),
    outline = Color(0xFF75715E), outlineVariant = Color(0xFF3E3D32), scrim = Color.Black,
    surfaceBright = Color(0xFF49483E), surfaceDim = Color(0xFF1E1F1C),
    surfaceContainerLowest = Color(0xFF1E1F1C), surfaceContainerLow = Color(0xFF2D2E27),
    surfaceContainer = Color(0xFF32332B), surfaceContainerHigh = Color(0xFF3A3B32),
    surfaceContainerHighest = Color(0xFF43443A),
)

/** Whether the phone app is showing a dark theme now. */
@Composable
internal fun phoneDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * The status and navigation bar icons follow the theme, not the phone's dark mode: a light theme
 * on a dark phone would otherwise get white icons on white.
 */
@Composable
internal fun SystemBarsFor(theme: PhoneTheme, dark: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    LaunchedEffect(theme, dark) {
        if (theme == PhoneTheme.PHONE) {
            activity.enableEdgeToEdge()
        } else {
            val clear = android.graphics.Color.TRANSPARENT
            val bars = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
            activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Settings > General: the theme, in a list to pick from. */
@Composable
internal fun ThemeSection() {
    val context = LocalContext.current
    val theme by PhoneThemes.current(context).collectAsState()
    var open by remember { mutableStateOf(false) }
    SettingsSection(stringResource(R.string.theme_title), footer = stringResource(R.string.theme_footer)) {
        SettingsRow(themeName(theme ?: PhoneTheme.PHONE), Modifier.clickable { open = true })
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.theme_title)) },
            text = {
                Column {
                    PhoneTheme.entries.forEach { choice ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(choice == theme, role = Role.RadioButton) { PhoneThemes.set(context, choice); open = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choice == theme, onClick = null)
                            Text(themeName(choice), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.common_close)) } },
        )
    }
}

@Composable
private fun themeName(theme: PhoneTheme): String = when (theme) {
    PhoneTheme.PHONE -> stringResource(R.string.theme_phone)
    PhoneTheme.LIGHT -> stringResource(R.string.theme_light)
    PhoneTheme.DARK -> stringResource(R.string.theme_dark)
    PhoneTheme.BLACK -> stringResource(R.string.theme_black)
    PhoneTheme.MONOKAI -> "Monokai"
}
