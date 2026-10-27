package io.github.sirionero.mycar.car

import io.github.sirionero.mycar.xonos.Xonos

/**
 * What the place beside the map shows, with the Xonos app read as Xonos inside myCar once that's
 * set up: a choice saved with the app keeps showing Xonos.
 */
internal fun withXonosInside(id: String?, entries: List<Entry>): String? {
    if (id == null || id.substringBefore('/') != Xonos.APP_PACKAGE) return id
    return entries.firstOrNull { entry -> entry.media?.component?.let(Xonos::isInside) == true }?.componentId ?: id
}
