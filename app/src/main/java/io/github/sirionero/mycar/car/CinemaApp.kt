package io.github.sirionero.mycar.car

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.car.design.AppIcon
import io.github.sirionero.mycar.car.design.AppIconShape
import io.github.sirionero.mycar.car.design.MyCarRadius
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.design.glassFinish
import io.github.sirionero.mycar.cinema.Cinema
import io.github.sirionero.mycar.cinema.CinemaSite
import io.github.sirionero.mycar.cinema.SiteIcons
import kotlin.math.absoluteValue

/*
 * Cinema, myCar's video app: the video sites chosen on the phone, as tiles, each opened in a
 * browser made for video - full screen when the site asks for it, the car's own keyboard for
 * searching, and the site's computer layout when chosen on the phone. Protected video (DRM)
 * most likely stays black in the car: Android hides it from a screen made into a video stream.
 * With "Video only when parked" on, the picture is covered while the car moves and the sound
 * carries on.
 */

/** Cinema full screen, from the app grid: the sites, or the one open. */
@Composable
internal fun CinemaScreen() {
    val sites by Cinema.sites.collectAsState()
    var openUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val open = sites.firstOrNull { it.url == openUrl }
    if (open == null) {
        CinemaHome(sites, onOpen = { openUrl = it.url })
    } else {
        key(open.url) { CinemaSiteView(open, onClose = { openUrl = null }) }
    }
}

/** The sites, as tiles with their own icons. */
@Composable
private fun CinemaHome(sites: List<CinemaSite>, onOpen: (CinemaSite) -> Unit) {
    val context = LocalContext.current
    val icons by SiteIcons.icons.collectAsState()
    LaunchedEffect(sites) { SiteIcons.load(context, sites) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Cinema", Modifier.padding(horizontal = MyCarSpace.L, vertical = MyCarSpace.S))
        if (sites.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(MyCarSpace.XL), contentAlignment = Alignment.Center) {
                Text(
                    "Add your video sites in myCar's settings on the phone, under Cinema.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else LazyVerticalGrid(
            columns = GridCells.Adaptive(TileWidth),
            contentPadding = PaddingValues(MyCarSpace.L),
            horizontalArrangement = Arrangement.spacedBy(MyCarSpace.L),
            verticalArrangement = Arrangement.spacedBy(MyCarSpace.L),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(sites, key = { it.url }) { site ->
                SiteTile(site, icons[site.host]) { onOpen(site) }
            }
        }
    }
}

@Composable
private fun SiteTile(site: CinemaSite, icon: androidx.compose.ui.graphics.ImageBitmap?, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(MyCarRadius.Card))
            .clickable(onClick = onClick)
            .padding(MyCarSpace.S),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.S),
    ) {
        if (icon != null) {
            AppIcon(icon, builtInId = null, size = IconSize)
        } else {
            InitialTile(site, IconSize)
        }
        Text(site.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (Cinema.isProtected(site)) {
            Text(
                "Protected video: likely black",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A site with no icon yet: its initial, on a colour of its own. */
@Composable
private fun InitialTile(site: CinemaSite, size: androidx.compose.ui.unit.Dp) {
    val color = InitialColors[site.host.hashCode().absoluteValue % InitialColors.size]
    Box(
        Modifier.size(size).clip(AppIconShape).background(color).glassFinish(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            site.name.take(1).uppercase(),
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * One site, under a slim bar: back a page, the site's name, Close. The car's Back leaves a
 * full-screen video first, then steps back through the site, then goes back to the sites.
 */
@Composable
private fun CinemaSiteView(site: CinemaSite, onClose: () -> Unit) {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    val ring = String.format("#%06X", MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF)
    val parkedOnly by Cinema.parkedOnly.collectAsState()
    val allowed = !parkedOnly || rememberVideoAllowed()
    var web by remember { mutableStateOf<WebView?>(null) }
    var fullScreen by remember { mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null) }
    var fieldText by remember { mutableStateOf<String?>(null) }
    var desktopShown by remember { mutableStateOf(site.desktop) }

    // Another app's sound has to reach the car: the capture starts now, if it waited for Xonos.
    LaunchedEffect(Unit) { CarAudio.captureNow() }

    DisposableEffect(web) {
        navigator.backInterceptor = {
            val video = fullScreen
            val view = web
            when {
                video != null -> {
                    video.second.onCustomViewHidden()
                    fullScreen = null
                    true
                }
                view != null && view.canGoBack() -> {
                    view.goBack()
                    true
                }
                else -> {
                    onClose()
                    true
                }
            }
        }
        onDispose {
            navigator.backInterceptor = null
            fullScreen?.second?.onCustomViewHidden()
        }
    }

    // The computer layout chosen on the phone meanwhile: asked for again as one.
    LaunchedEffect(site.desktop) {
        val view = web ?: return@LaunchedEffect
        if (site.desktop == desktopShown) return@LaunchedEffect
        desktopShown = site.desktop
        view.settings.userAgentString = userAgentFor(view, site.desktop)
        view.reload()
    }

    Column(Modifier.fillMaxSize()) {
        if (fullScreen == null) {
            CinemaBar(site.name, onBack = { web?.takeIf { it.canGoBack() }?.goBack() ?: onClose() }, onClose = onClose)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.userAgentString = userAgentFor(this, site.desktop)
                        webViewClient = CinemaClient(ring)
                        webChromeClient = CinemaChrome(
                            onShow = { view, callback -> fullScreen = view to callback },
                            onHide = { fullScreen = null },
                            onIcon = { SiteIcons.offer(ctx, site.host, it) },
                        )
                        addJavascriptInterface(
                            FieldFocusBridge(onFocus = { fieldText = it }, onBlur = { fieldText = null }),
                            "MyCarField",
                        )
                        loadUrl(site.url)
                        web = this
                    }
                },
                onRelease = { it.destroy() },
            )
            fullScreen?.let { (view, _) ->
                AndroidView(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            (view.parent as? ViewGroup)?.removeView(view)
                            addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                    },
                    onRelease = { it.removeAllViews() },
                )
            }
            if (!allowed) ParkedCover()
        }
        val text = fieldText
        if (text != null && fullScreen == null) {
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
    LaunchedEffect(Unit) { MyCarLog.i("cinema: opening ${site.host}${if (site.desktop) " as on a computer" else ""}") }
}

/** The slim bar over a site: back a page, its name, and Close back to the sites. */
@Composable
private fun CinemaBar(title: String, onBack: () -> Unit, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().height(CHROME_BAR),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.fillMaxHeight().width(CHROME_BAR + MyCarSpace.S).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back a page", Modifier.size(22.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.fillMaxHeight().width(CHROME_BAR + MyCarSpace.S).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, "Close", Modifier.size(22.dp))
            }
        }
    }
}

/** Over the picture while the car moves, with "Video only when parked" on: the sound carries on. */
@Composable
private fun ParkedCover() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Taps stay here: nothing under the cover to press while moving.
            .clickable(enabled = true, onClick = {}),
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.M, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.sym_movie), null, Modifier.size(56.dp), tint = Color.White.copy(alpha = 0.7f))
        Text("Video only when parked", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "The sound carries on. The picture comes back when the car stops.",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = MyCarSpace.XL),
        )
    }
}

/** The phone's own user agent, or, for a site's computer layout, a computer's with the same Chrome. */
private fun userAgentFor(view: WebView, desktop: Boolean): String? {
    if (!desktop) return null
    val chrome = CHROME_VERSION.find(android.webkit.WebSettings.getDefaultUserAgent(view.context))?.groupValues?.get(1) ?: "130.0.0.0"
    return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
}

private val CHROME_VERSION = Regex("Chrome/([\\d.]+)")

private class CinemaClient(private val ring: String) : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
        view?.evaluateJavascript(FOCUS_TRACKING_JS, null)
        view?.evaluateJavascript(focusRingJs(ring), null)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) {
            MyCarLog.w("cinema: ${request.url} didn't load - ${error?.errorCode} ${error?.description}")
        }
    }
}

/**
 * Full screen when a site's player asks for it; protected video (DRM) allowed to try, which most
 * likely shows black in the car all the same; camera and microphone never. The site's own icon
 * is kept for its tile.
 */
private class CinemaChrome(
    private val onShow: (View, WebChromeClient.CustomViewCallback) -> Unit,
    private val onHide: () -> Unit,
    private val onIcon: (Bitmap) -> Unit,
) : WebChromeClient() {
    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) = onShow(view, callback)

    override fun onHideCustomView() = onHide()

    override fun onPermissionRequest(request: PermissionRequest) {
        val protectedMedia = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
        if (protectedMedia.isEmpty()) request.deny() else request.grant(protectedMedia.toTypedArray())
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        icon?.let(onIcon)
    }
}

private val TileWidth = 168.dp
private val IconSize = 112.dp
private val InitialColors = listOf(
    Color(0xFFD7263D), Color(0xFF6C63FF), Color(0xFF0F9D58), Color(0xFFF4511E), Color(0xFF3D8BFF), Color(0xFF8E24AA),
)
