package io.github.sirionero.mycar.car

import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.car.app.CarToast
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words

/**
 * A link a page in the car can't open as a page: intent://, market://, tel: and the other app
 * schemes, which left the web engine's "address not supported" page. A phone number asks to call
 * it, as the dialer does; an intent:// link with a page to fall back on (YouTube's and Twitch's
 * "open the app") loads that page; the others stay where they are, and the car says why. Only
 * for a tap on the page itself: a page or a frame jumping to an app link by itself is just held
 * back. True when the web view mustn't load [request].
 */
internal fun WebView.handledAppLink(request: WebResourceRequest?): Boolean {
    val uri = request?.url ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme in WEB_SCHEMES) return false
    if (!request.isForMainFrame || !request.hasGesture()) return true
    when (scheme) {
        "tel" -> uri.schemeSpecificPart?.takeIf { it.isNotBlank() }?.let { number ->
            CallPrompt.ask(title?.takeIf { it.isNotBlank() } ?: number, number)
            return true
        }
        "intent" -> {
            val fallback = runCatching {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
            }.getOrNull()
            if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) {
                loadUrl(fallback)
                return true
            }
        }
    }
    CarToasts.show(this, Words.get(R.string.car_ui_link_opens_app), CarToast.LENGTH_SHORT)
    return true
}

private val WEB_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript", "file", "content")
