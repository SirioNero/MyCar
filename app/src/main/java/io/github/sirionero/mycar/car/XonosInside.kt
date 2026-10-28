package io.github.sirionero.mycar.car

import android.content.ComponentName
import io.github.sirionero.mycar.xonos.Xonos

/**
 * What the place beside the map shows, with the Xonos app read as Xonos inside myCar once that's
 * set up: a choice saved with the app keeps showing Xonos. And the other way, while the app plays
 * in place of the one inside myCar this drive (see InsideOrApp).
 */
internal fun withXonosInside(id: String?, entries: List<Entry>): String? {
    if (id == null) return null
    if (id.substringBefore('/') == Xonos.APP_PACKAGE) {
        return entries.firstOrNull { entry -> entry.media?.component?.let(Xonos::isInside) == true }?.componentId ?: id
    }
    if (ComponentName.unflattenFromString(id)?.let(Xonos::isInside) != true) return id
    return entries.firstOrNull { it.media?.component?.packageName == Xonos.APP_PACKAGE }?.componentId ?: id
}
