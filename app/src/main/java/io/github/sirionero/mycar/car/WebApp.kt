package io.github.sirionero.mycar.car

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.MyCarLog
import org.json.JSONObject

/**
 * A browser inside the car UI. [content] is a URL, or raw HTML when it does not look like one.
 * The bottom bar's Back walks the page history first and only leaves the app at the start.
 *
 * A page's own text inputs can't call up the phone's IME: the car UI lives in a Presentation on
 * a virtual display, and the platform's soft keyboard has nowhere sensible to appear there (see
 * [CarKeyboard]'s doc comment - every other text field in this app already draws its own for the
 * same reason). [FOCUS_TRACKING_JS] reports focus/blur on the page's editable elements, and
 * [CarKeyboard] edits the focused one back through injected JS instead of through the DOM's own
 * native input path, which this Presentation can't reach.
 */
@Composable
fun WebApp(content: String) = WebApp(content, tile = null)

/**
 * [WebApp], or with [tile] the browser in a part of a Home page: a slim bar on top instead of the
 * screen's header, the car's Back left alone (the page in view may not be this one), and the
 * keyboard handed to the page, which has the width it needs.
 */
@Composable
internal fun WebApp(content: String, tile: WebTile?) {
    val navigator = LocalCarNavigator.current
    val darkOutside = LocalDarkOutside.current
    val ring = String.format("#%06X", MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF)
    var web by remember { mutableStateOf<WebView?>(null) }
    var fieldText by remember { mutableStateOf<String?>(null) }
    // A new web view after the web engine's page process died (see LoggingClient.onRenderProcessGone).
    var generation by remember { mutableIntStateOf(0) }

    DisposableEffect(web, tile) {
        if (tile != null) return@DisposableEffect onDispose { }
        navigator.backInterceptor = {
            val view = web
            if (view != null && view.canGoBack()) {
                view.goBack()
                true
            } else {
                false
            }
        }
        onDispose { navigator.backInterceptor = null }
    }

    Column(Modifier.fillMaxSize()) {
        if (tile == null) {
            // Back steps through the page's history first (the interceptor above), then leaves.
            ScreenHeader("Browser", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        } else {
            TileBar("Browser", onBack = { web?.takeIf { it.canGoBack() }?.goBack() }, onHold = tile.onHold)
        }
        key(generation) {
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxSize(),
                factory = { context ->
                    // A page's dropdown can't close myCar on the car's screen (see carWebContext).
                    WebView(carWebContext(context)).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = LoggingClient(ring) {
                            web = null
                            fieldText = null
                            generation++
                        }
                        addJavascriptInterface(
                            FieldFocusBridge(onFocus = { fieldText = it }, onBlur = { fieldText = null }),
                            "MyCarField",
                        )
                        if (content.startsWith("http://") || content.startsWith("https://")) {
                            loadUrl(content)
                        } else {
                            loadDataWithBaseURL(null, content, "text/html", "utf-8", null)
                        }
                        web = this
                    }
                },
                // Pages that honour it follow the darkness outside; a first, tweakable use of the signal.
                // Through androidx.webkit: the platform call exists only from Android 13.
                update = {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(it.settings, darkOutside)
                    }
                },
                onRelease = { runCatching { it.destroy() } },
            )
        }
        val text = fieldText
        val keyboard: @Composable () -> Unit = {
            if (text != null) {
                CarKeyboard(
                    text = text,
                    onTextChange = { updated ->
                        fieldText = updated
                        web?.evaluateJavascript(setFieldValueJs(updated), null)
                    },
                    onSubmit = { web?.evaluateJavascript(SUBMIT_FIELD_JS, null) },
                    onDismiss = {
                        fieldText = null
                        web?.evaluateJavascript(BLUR_FIELD_JS, null)
                    },
                )
            }
        }
        if (tile == null) {
            keyboard()
        } else {
            DisposableEffect(text) {
                tile.keyboard.value = if (text != null) keyboard else null
                onDispose { tile.keyboard.value = null }
            }
        }
    }
}

private class LoggingClient(private val ring: String, private val onGone: () -> Unit) : WebViewClient() {
    // The web engine's page process is one for the whole app, Plancia's dashboard included: when
    // it dies, every web view on it must say it handled that, or Android ends myCar - and the drive.
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        MyCarLog.w("car web page process gone (crash: ${detail?.didCrash()}) - opening the browser again")
        if (view != null) {
            (view.parent as? ViewGroup)?.removeView(view)
            runCatching { view.destroy() }
        }
        Handler(Looper.getMainLooper()).post(onGone)
        return true
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        view?.handledAppLink(request) == true

    override fun onPageFinished(view: WebView?, url: String?) {
        MyCarLog.i("car web page loaded: $url")
        view?.evaluateJavascript(FOCUS_TRACKING_JS, null)
        view?.evaluateJavascript(focusRingJs(ring), null)
    }

    // onPageFinished fires for error pages too, so without this a blocked load (missing
    // INTERNET permission, no network) looks like a successful one.
    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            MyCarLog.e("car web page FAILED: ${request.url} - ${error?.errorCode} ${error?.description}")
            // A page at home that Android 17 keeps myCar from (see LocalNetwork): say what to allow,
            // rather than the web engine's own "not available".
            val url = request.url.toString()
            if (view != null && LocalNetwork.blocks(view.context, listOf(url))) {
                view.post { view.loadDataWithBaseURL(null, homeNetworkPage(url), "text/html", "utf-8", url) }
            }
        }
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
        if (request?.isForMainFrame == true) {
            MyCarLog.w("car web page HTTP ${response?.statusCode} for ${request.url}")
        }
    }
}

private fun homeNetworkPage(url: String) =
    "<html><head><meta name=\"viewport\" content=\"width=device-width\"></head>" +
        "<body style=\"font-family:sans-serif;padding:32px;text-align:center\">" +
        "<h2>This page is on your home network</h2><p>${TextUtils.htmlEncode(LocalNetwork.HINT)}</p>" +
        "<p style=\"opacity:.6\">${TextUtils.htmlEncode(url)}</p></body></html>"

/** Tells Kotlin when an editable element on the page gains or loses focus, so [CarKeyboard] can show. */
internal class FieldFocusBridge(private val onFocus: (String) -> Unit, private val onBlur: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    // WebView calls into this from its own thread, never Compose's.
    @JavascriptInterface
    fun focused(value: String) { main.post { onFocus(value) } }

    @JavascriptInterface
    fun blurred() { main.post { onBlur() } }
}

// Plain `el.value = x` never reaches a React-controlled input: React replaces the native value
// setter with its own to track edits, so a direct assignment sets the DOM attribute but leaves
// React's own state (and anything bound to it, like a search box's query) unaware. Calling the
// *native* setter through the prototype, the same trick browser extensions use, is what actually
// notifies it once the 'input' event fires.
internal fun setFieldValueJs(value: String) = """
    (function() {
        var el = document.activeElement;
        if (!el) return;
        // A box that's an editable element rather than a field (many search boxes): it has no
        // value, and the field setter on it threw - nothing the driver typed arrived.
        if (el.isContentEditable) {
            el.textContent = ${JSONObject.quote(value)};
            el.dispatchEvent(new InputEvent('input', { bubbles: true }));
            return;
        }
        var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
        var setter = Object.getOwnPropertyDescriptor(proto, 'value');
        if (setter && setter.set) { setter.set.call(el, ${JSONObject.quote(value)}); } else { el.value = ${JSONObject.quote(value)}; }
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
    })();
""".trimIndent()

// Fires a real Enter keydown first - the same event a site's own JS would see from a physical
// keyboard - and only falls back to submitting the form directly if nothing handled it, so a
// search-as-you-type page and a plain <form> both work without submitting twice.
// Dismissing the host keyboard without blurring the page's own field would leave it focused,
// so the next tap anywhere with a text cursor pops the keyboard straight back up.
internal const val BLUR_FIELD_JS = "document.activeElement && document.activeElement.blur();"

internal const val SUBMIT_FIELD_JS = """
    (function() {
        var el = document.activeElement;
        if (!el) return;
        var enter = new KeyboardEvent('keydown', {
            key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true,
        });
        var notHandled = el.dispatchEvent(enter);
        if (notHandled && el.form) {
            if (el.form.requestSubmit) el.form.requestSubmit(); else el.form.submit();
        }
    })();
"""

/**
 * The same ring the rest of the car UI shows, on whatever link or field the knob is on. Many
 * sites hide the browser's own focus outline, which leaves a knob driver with no idea where they are.
 */
internal fun focusRingJs(color: String) = """
    (function() {
        if (document.getElementById('mycar-focus-ring')) return;
        var style = document.createElement('style');
        style.id = 'mycar-focus-ring';
        style.textContent = ':focus-visible { outline: 3px solid $color !important; outline-offset: 2px !important; }';
        (document.head || document.documentElement).appendChild(style);
    })();
"""

/** Reports focus/blur on text inputs, textareas and contenteditable elements to [FieldFocusBridge]. */
internal const val FOCUS_TRACKING_JS = """
    (function() {
        function isEditable(el) {
            if (!el) return false;
            var tag = el.tagName;
            if (tag === 'TEXTAREA') return true;
            if (el.isContentEditable) return true;
            if (tag !== 'INPUT') return false;
            var skip = ['button', 'submit', 'checkbox', 'radio', 'range', 'color', 'file', 'image', 'reset'];
            return skip.indexOf((el.type || 'text').toLowerCase()) === -1;
        }
        document.addEventListener('focusin', function(e) {
            if (isEditable(e.target)) window.MyCarField.focused(e.target.isContentEditable ? (e.target.innerText || '') : (e.target.value || ''));
        });
        document.addEventListener('focusout', function(e) {
            if (isEditable(e.target)) window.MyCarField.blurred();
        });
    })();
"""
