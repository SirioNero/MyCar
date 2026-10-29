package io.github.sirionero.mycar.car

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.sirionero.mycar.ProjectedContent
import io.github.sirionero.mycar.R

/** One tile in the launcher and the screen it opens. */
class CarApp(
    val id: String,
    /** In English, or a name the same in every language (Plancia): the car's screen shows [shownLabel]. */
    val label: String,
    val icon: ImageVector,
    /** Its name in myCar's language; null for a [label] that is a name. */
    @StringRes val title: Int? = null,
    val content: @Composable () -> Unit,
) {
    /** What the car's screen calls it: in myCar's language, or [label] as it is. */
    val shownLabel: String
        @Composable get() = title?.let { stringResource(it) } ?: label
}

/** Built-in apps. Third-party (Android Auto) apps are a later step and will register here too. */
object CarApps {

    val all: List<CarApp> = listOf(
        CarApp("web", "Browser", Icons.Filled.Search, R.string.car_apps_browser) {
            WebApp(content = ProjectedContent.resolve(LocalContext.current))
        },
        CarApp("phone", "Screen sharing", Icons.Filled.Share, R.string.car_apps_screen_sharing) {
            PhoneAppsScreen()
        },
        CarApp("dialer", "Phone", Icons.Filled.Call, R.string.car_apps_phone) {
            PhoneDialerScreen()
        },
        CarApp("settings", "Settings", Icons.Filled.Settings, R.string.common_settings) {
            CarSettingsScreen()
        },
        CarApp("plancia", "Plancia", Icons.Filled.Home) {
            PlanciaScreen()
        },
        CarApp("cinema", "Cinema", Icons.Filled.PlayArrow) {
            CinemaScreen()
        },
    )

    fun find(id: String): CarApp? = all.firstOrNull { it.id == id }
}
