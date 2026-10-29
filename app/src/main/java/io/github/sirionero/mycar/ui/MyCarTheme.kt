package io.github.sirionero.mycar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/** The phone app's theme: Material You like the phone, or one picked in Settings > General. */
@Composable
fun MyCarTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val theme by PhoneThemes.current(context).collectAsState()
    val chosen = theme ?: PhoneTheme.PHONE
    val scheme = phoneColors(chosen)
    SystemBarsFor(chosen, scheme.background.luminance() < 0.5f)
    MaterialTheme(colorScheme = scheme, content = content)
}
