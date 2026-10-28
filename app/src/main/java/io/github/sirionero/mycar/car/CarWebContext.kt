package io.github.sirionero.mycar.car

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager

/**
 * The context for a WebView on the car's screen. That screen is a Presentation, and Android lets
 * only windows of the Presentation's own type be added through its context: a page's dropdown
 * (<select>) opens a dialog, a window of another type, and adding it failed with "Window type
 * mismatch", which the WebView doesn't expect - myCar closed, and the drive with it.
 *
 * Here the WebView's windows go through the car display's plain WindowManager instead, where a
 * dialog fails the way the WebView does expect (BadTokenException: no token for an app window), so
 * the dropdown just doesn't open; it couldn't be used anyway, as the car's touches reach only the
 * car screen's own window (ScreenProjector). Popups attached to the page's own window (text
 * selection, suggestions) carry its token and are added as before.
 */
internal fun carWebContext(context: Context): Context = CarWebContext(context)

private class CarWebContext(base: Context) : ContextWrapper(base) {
    private val windows: Any? by lazy {
        val display = runCatching { base.display }.getOrNull()
        if (display == null) base.getSystemService(WINDOW_SERVICE)
        else base.applicationContext.createDisplayContext(display).getSystemService(WindowManager::class.java)
    }

    override fun getSystemService(name: String): Any? =
        if (name == WINDOW_SERVICE) windows else super.getSystemService(name)
}
