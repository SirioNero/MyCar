package io.github.sirionero.mycar.car

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.home.HaConnection
import io.github.sirionero.mycar.home.HomeAssistant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/** The drive's [PlanciaWeb], when Plancia is set up; null otherwise. */
internal val LocalPlanciaWeb = staticCompositionLocalOf<PlanciaWeb?> { null }

/**
 * Plancia's dashboard - Home Assistant's own page - kept alive through the drive, so it opens at
 * once wherever it's shown: the Plancia app, a part of a Home page, beside the map. There's one
 * page, moved to whichever of those is on screen ([show]); out of sight it stays loaded and
 * connected. It signs in with the token as Home Assistant's own app does (window.externalApp), so
 * it never asks for a login, and the token only ever goes to Home Assistant's own address.
 *
 * The car's touches reach it as they are, so a light's slider drags as it does on the phone. From
 * Plancia, the Home Assistant app for the car, which drew the same page on a car app's surface.
 */
internal class PlanciaWeb(context: Context, dark: Boolean) {

    /** What the page is doing: [base] is the address it's loaded from, [error] why it couldn't be. */
    data class Page(val base: String? = null, val error: String? = null)

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val themed = ContextThemeWrapper(
        context,
        if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar,
    )
    private val background = if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND
    private val density = context.resources.displayMetrics.density

    private var web: WebView? = null
    private val hosts = mutableListOf<FrameLayout>()
    private var released = false

    private val _page = MutableStateFlow(Page())
    val page: StateFlow<Page> = _page.asStateFlow()

    /** Plancia's menu is wanted: Home Assistant's ☰, or a part of a page asking for it. */
    private val _menu = MutableStateFlow(false)
    val menu: StateFlow<Boolean> = _menu.asStateFlow()

    /** The dashboard shown: its url_path, or null for the one Home Assistant picks. */
    private val _current = MutableStateFlow(HomeAssistant.defaultDashboard.value)
    val current: StateFlow<String?> = _current.asStateFlow()

    private var frontendReady = false
    private var generation = 0
    private var commandId = 1_000
    private var pendingNavigate = 0

    init {
        web = createWebView()
        // Connected, disconnected, moved from the home address to the one away: the page follows.
        scope.launch { HomeAssistant.connection.collect { onConnectionChanged() } }
        // A new size is a new layout width for the page.
        scope.launch { combine(HomeAssistant.zoom, page) { zoom, _ -> zoom }.collect { applyViewport() } }
    }

    /** Puts the page in [frame], the place on screen it shows in now; the last asked wins. */
    fun show(frame: FrameLayout) {
        hosts.remove(frame)
        hosts.add(frame)
        place()
    }

    /** [frame] is leaving the screen: the page goes back to the place shown before, or waits unseen. */
    fun hide(frame: FrameLayout) {
        hosts.remove(frame)
        place()
    }

    fun openMenu() {
        _menu.value = true
    }

    fun closeMenu() {
        _menu.value = false
    }

    /** Opens a dashboard (null: the one Home Assistant picks). */
    fun open(urlPath: String?) {
        _current.value = urlPath
        showTarget(reload = false)
    }

    /** Loads the page again from the start. */
    fun reload() = showTarget(reload = true)

    /** Back inside the page: closes a dialog or leaves a subview. False when there's nothing to go back to. */
    fun goBack(): Boolean {
        val w = web ?: return false
        if (!w.canGoBack()) return false
        w.goBack()
        return true
    }

    fun destroy() {
        released = true
        scope.cancel()
        main.removeCallbacksAndMessages(null)
        hosts.clear()
        web?.let { w ->
            (w.parent as? ViewGroup)?.removeView(w)
            w.stopLoading()
            w.destroy()
        }
        web = null
    }

    private fun place() {
        val w = web ?: return
        val target = hosts.lastOrNull()
        if (w.parent === target) return
        (w.parent as? ViewGroup)?.removeView(w)
        target?.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ---------- The page ----------

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(themed)
        webView.setBackgroundColor(background)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        // The layout width the page is given (see PlanciaScripts) counts, fitted to the place it has.
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.setSupportZoom(false)
        s.builtInZoomControls = false
        s.displayZoomControls = false
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        webView.addJavascriptInterface(ExternalAppBridge(), "externalApp")
        webView.addJavascriptInterface(PlanciaBridge(), "plancia")
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            // On every origin: it only ever goes to Home Assistant, even after a redirect from http to https.
            WebViewCompat.addDocumentStartJavaScript(webView, PlanciaScripts.SETUP, setOf("*"))
        }
        // Moved to a wider or narrower place: the page is given the width it now has.
        webView.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) applyViewport()
        }
        return webView
    }

    private fun onConnectionChanged() {
        val connected = (HomeAssistant.connection.value as? HaConnection.Connected)?.url
        val page = _page.value
        if (web != null && connected != null && (connected != page.base || page.error != null)) hardLoad(connected)
    }

    /** The dashboard's path; "/" lets Home Assistant pick. */
    private fun targetPath(): String = "/" + (_current.value ?: HomeAssistant.haDefault.value ?: "")

    private fun showTarget(reload: Boolean) {
        val connected = (HomeAssistant.connection.value as? HaConnection.Connected)?.url ?: return
        val page = _page.value
        if (!reload && connected == page.base && page.error == null && navigateInPage(targetPath())) return
        hardLoad(connected)
    }

    private fun hardLoad(baseUrl: String) {
        val w = web ?: return
        generation++
        frontendReady = false
        pendingNavigate = 0
        _page.value = Page(base = baseUrl)
        MyCarLog.i("plancia: opening the dashboard at $baseUrl${targetPath()}")
        w.loadUrl(baseUrl.trimEnd('/') + targetPath())
    }

    /** Another dashboard inside the page already open, without reloading it: the official app's command. */
    private fun navigateInPage(path: String): Boolean {
        if (!frontendReady || path == "/") return false
        val id = ++commandId
        pendingNavigate = id
        val message = JSONObject()
            .put("type", "command")
            .put("command", "navigate")
            .put("id", id)
            .put("payload", JSONObject().put("path", path).put("options", JSONObject().put("replace", true)))
        runInPage("window.externalBus && window.externalBus($message);")
        main.postDelayed({
            // No answer: the dashboard opens by reloading the page.
            if (pendingNavigate == id) {
                pendingNavigate = 0
                _page.value.base?.let { hardLoad(it) }
            }
        }, NAVIGATE_TIMEOUT_MS)
        return true
    }

    /** The width, in the page's pixels, to lay out at: the place it has, made wider to draw it smaller. */
    private fun applyViewport() {
        val w = web ?: return
        if (w.width <= 0) return
        val cssWidth = w.width / density * 100f / HomeAssistant.zoom.value
        runInPage("window.__planciaViewport && window.__planciaViewport($cssWidth);")
    }

    private fun runInPage(script: String) {
        web?.evaluateJavascript(script, null)
    }

    private fun isHomeAssistantPage(url: String?): Boolean = sameHost(url, _page.value.base)

    // ---------- What the frontend expects of Home Assistant's own app ----------

    /** Arrives on one of the WebView's threads. */
    private inner class ExternalAppBridge {
        @JavascriptInterface
        fun getExternalAuth(payload: String) {
            val callback = callbackName(payload, AUTH_CALLBACK)
            main.post { answerAuth(callback) }
        }

        @JavascriptInterface
        fun revokeExternalAuth(payload: String) {
            // A long-lived token isn't revoked from here: it's just confirmed.
            val callback = callbackName(payload, REVOKE_CALLBACK)
            main.post { if (isHomeAssistantPage(web?.url)) runInPage("window.$callback && window.$callback(true);") }
        }

        @JavascriptInterface
        fun externalBus(message: String) {
            main.post { onBusMessage(message) }
        }
    }

    private inner class PlanciaBridge {
        @JavascriptInterface
        fun openMenu() {
            main.post { if (isHomeAssistantPage(web?.url)) this@PlanciaWeb.openMenu() }
        }
    }

    private fun answerAuth(callback: String) {
        val w = web ?: return
        // The token only ever goes to Home Assistant's page, never to another site.
        if (!isHomeAssistantPage(w.url)) {
            MyCarLog.w("plancia: token refused to ${w.url}")
            return
        }
        val token = HomeAssistant.config.value.token
        val data = JSONObject().put("access_token", token).put("expires_in", TOKEN_SECONDS)
        w.evaluateJavascript("window.$callback && window.$callback(true, $data);", null)
    }

    private fun onBusMessage(raw: String) {
        if (!isHomeAssistantPage(web?.url)) return
        val message = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            return
        }
        when (message.optString("type")) {
            "config/get" -> {
                val answer = JSONObject()
                    .put("id", message.optInt("id"))
                    .put("type", "result")
                    .put("success", true)
                    .put("result", externalConfig())
                runInPage("window.externalBus && window.externalBus($answer);")
            }
            "frontend/loaded" -> frontendReady = true
            "sidebar/show" -> openMenu()
            "result" -> if (pendingNavigate != 0 && message.optInt("id") == pendingNavigate) {
                pendingNavigate = 0
                if (!message.optBoolean("success")) _page.value.base?.let { hardLoad(it) }
            }
        }
    }

    /** What "the app" can do: none of a phone's things, and Plancia's menu stands in for the sidebar. */
    private fun externalConfig(): JSONObject = JSONObject()
        .put("hasSettingsScreen", false)
        .put("hasSidebar", false)
        .put("canWriteTag", false)
        .put("hasExoPlayer", false)
        .put("canCommissionMatter", false)
        .put("canImportThreadCredentials", false)
        .put("hasAssist", false)
        .put("hasBarCodeScanner", 0)
        .put("appVersion", "myCar ${BuildConfig.VERSION_NAME}")

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // It stays on Home Assistant: other sites don't open, and never see the token.
            if (!request.isForMainFrame) return false
            return !sameHost(request.url.toString(), _page.value.base)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (view !== web) return
            frontendReady = false
            _page.value = _page.value.copy(error = null)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (view !== web) return
            view.evaluateJavascript(PlanciaScripts.SETUP, null)
            view.evaluateJavascript(PlanciaScripts.HIDE_SIDEBAR, null)
            applyViewport()
            // Versions of Home Assistant that don't say when they're ready are, after a while.
            val loaded = generation
            main.postDelayed({ if (generation == loaded && _page.value.error == null) frontendReady = true }, READY_FALLBACK_MS)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (view !== web || !request.isForMainFrame) return
            val why = if (LocalNetwork.blocks(view.context, listOf(request.url.toString()))) {
                LocalNetwork.HINT
            } else {
                error.description?.toString()?.ifBlank { null } ?: "Error ${error.errorCode}"
            }
            MyCarLog.w("plancia: the dashboard didn't load: $why")
            _page.value = _page.value.copy(error = why)
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (view !== web || !request.isForMainFrame || errorResponse.statusCode < 500) return
            _page.value = _page.value.copy(error = "Home Assistant answered with error ${errorResponse.statusCode}")
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            MyCarLog.w("plancia: the page closed (crash: ${detail.didCrash()}) - opening it again")
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            if (view === web && !released) {
                web = createWebView()
                _page.value = Page()
                place()
                main.post { onConnectionChanged() }
            }
            return true
        }
    }

    private class Chrome : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                MyCarLog.w("plancia: page: ${consoleMessage.message()}")
            }
            return true
        }
    }

    private companion object {
        const val AUTH_CALLBACK = "externalAuthSetToken"
        const val REVOKE_CALLBACK = "externalAuthRevokeToken"
        const val TOKEN_SECONDS = 1800
        const val NAVIGATE_TIMEOUT_MS = 2_000L
        const val READY_FALLBACK_MS = 4_000L

        /** Plancia's own colours, behind the page while it loads. */
        const val DARK_BACKGROUND = 0xFF0E1420.toInt()
        const val LIGHT_BACKGROUND = 0xFFEEF2F7.toInt()

        /** Only the two names the frontend uses: any other mustn't become code to run. */
        fun callbackName(payload: String, fallback: String): String {
            val name = try {
                JSONObject(payload).optString("callback")
            } catch (e: JSONException) {
                ""
            }
            return if (name == AUTH_CALLBACK || name == REVOKE_CALLBACK) name else fallback
        }

        /** The same server as Home Assistant's, even if a redirect went from http to https. */
        fun sameHost(a: String?, b: String?): Boolean {
            if (a == null || b == null) return false
            val host = Uri.parse(a).host ?: return false
            return host.equals(Uri.parse(b).host, ignoreCase = true)
        }
    }
}
