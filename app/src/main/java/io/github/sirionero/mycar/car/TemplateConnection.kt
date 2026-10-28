package io.github.sirionero.mycar.car

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.sirionero.mycar.host.CarAppConnection

/**
 * The app whose template is being drawn. Its Back, pan and icon are its own: wired to the map
 * app's connection, the Back of Plancia's menu sent Vela back a screen, in the middle of a route,
 * and left the menu open.
 */
internal val LocalCarAppConnection = staticCompositionLocalOf<CarAppConnection?> { null }

/** The connection of the template being drawn; the map app's outside any. */
@Composable
internal fun templateConnection(): CarAppConnection = LocalCarAppConnection.current ?: CarServices.nav
