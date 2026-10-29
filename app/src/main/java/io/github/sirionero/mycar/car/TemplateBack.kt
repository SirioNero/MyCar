package io.github.sirionero.mycar.car

import androidx.car.app.media.model.MediaPlaybackTemplate
import androidx.car.app.model.Action
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.SectionedItemTemplate
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.car.app.model.signin.SignInTemplate
import androidx.car.app.navigation.model.MapTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.navigation.model.PlaceListNavigationTemplate
import androidx.car.app.navigation.model.RoutePreviewNavigationTemplate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.sirionero.mycar.host.CarAppConnection

/**
 * The Back arrow a template shows at the start of its header, read where [HeaderBar] and the
 * search and tab rows read it (TemplateContent, TemplateChrome); null when the app's screen has
 * none, as on its first screen.
 */
internal fun Template?.backAction(): Action? {
    val action = when (this) {
        is ListTemplate -> header?.startHeaderAction ?: headerAction
        is GridTemplate -> header?.startHeaderAction ?: headerAction
        is PaneTemplate -> header?.startHeaderAction ?: headerAction
        is MessageTemplate -> header?.startHeaderAction ?: headerAction
        is LongMessageTemplate -> headerAction
        is SignInTemplate -> headerAction
        is SearchTemplate -> headerAction
        is TabTemplate -> headerAction
        is SectionedItemTemplate -> header?.startHeaderAction
        is MediaPlaybackTemplate -> header?.startHeaderAction
        is MapTemplate -> header?.startHeaderAction
        is PlaceListNavigationTemplate -> header?.startHeaderAction ?: headerAction
        is PlaceListMapTemplate -> headerAction
        is RoutePreviewNavigationTemplate -> header?.startHeaderAction ?: headerAction
        is MapWithContentTemplate -> return contentTemplate.backAction()
        else -> null
    }
    return action?.takeIf { it.type == Action.TYPE_BACK }
}

/**
 * The car's Back key, or its knob's, for an app with templates on screen ([active]): while the
 * app shows its Back arrow, the key does what the arrow does - the app goes back a screen -
 * instead of closing the app, or doing nothing beside the map. Without the arrow (the app's first
 * screen) the key goes on as before.
 */
@Composable
internal fun AppBackKey(connection: CarAppConnection, active: Boolean) {
    val navigator = LocalCarNavigator.current
    val template by connection.template.collectAsState()
    val arrow = active && template.backAction() != null
    DisposableEffect(connection, arrow) {
        if (!arrow) return@DisposableEffect onDispose { }
        // Read again when pressed: Stop may have closed the app a moment before this went.
        val back: () -> Boolean = {
            if (connection.template.value.backAction() != null) {
                connection.backPressed()
                true
            } else {
                false
            }
        }
        navigator.backInterceptor = back
        onDispose { if (navigator.backInterceptor === back) navigator.backInterceptor = null }
    }
}
