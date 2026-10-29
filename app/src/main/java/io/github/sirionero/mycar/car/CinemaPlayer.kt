package io.github.sirionero.mycar.car

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.car.design.MyCarSpace
import io.github.sirionero.mycar.car.theme.GsButton
import io.github.sirionero.mycar.cinema.Cinema
import io.github.sirionero.mycar.cinema.PhoneVideos
import kotlinx.coroutines.delay

/** What Cinema plays with Android's own player: a video on the phone, or a TV channel. */
internal data class CinemaMedia(
    val title: String,
    val uri: Uri,
    val headers: Map<String, String> = emptyMap(),
    /** A TV channel: it goes on, so there's no seeking and no end. */
    val live: Boolean = false,
    /** Under this name it starts again where it was left; null to start from the top. */
    val resumeKey: String? = null,
)

/**
 * A video or a TV channel, full screen, on Android's own player: no app or library of its own.
 * A tap shows the controls (back 10 s, play or pause, forward 10 s, the line to seek along), and
 * they go again a few seconds into playing. With "Video only when parked" the picture is covered
 * while the car moves and the sound carries on, as for the sites. Car's Back closes it.
 */
@Composable
internal fun CinemaPlayer(media: CinemaMedia, onClose: () -> Unit) {
    val context = LocalContext.current
    val navigator = LocalCarNavigator.current
    val playback = remember(media) { Playback(context.applicationContext, media) }
    val parkedOnly by Cinema.parkedOnly.collectAsState()
    val allowed = !parkedOnly || rememberVideoAllowed()
    var controls by remember { mutableStateOf(true) }
    // Touched last at: the controls go a few seconds after that, while playing.
    var touched by remember { mutableLongStateOf(0L) }

    // Another app's sound has to reach the car: myCar's own player is one, as the sites are.
    LaunchedEffect(Unit) { CarAudio.captureNow() }
    DisposableEffect(playback) {
        playback.open()
        onDispose { playback.release() }
    }
    DisposableEffect(Unit) {
        navigator.backInterceptor = {
            onClose()
            true
        }
        onDispose { navigator.backInterceptor = null }
    }
    LaunchedEffect(playback) {
        while (true) {
            playback.tick()
            delay(TICK_MS)
        }
    }
    LaunchedEffect(controls, touched, playback.playing) {
        if (controls && playback.playing) {
            delay(CONTROLS_MS)
            controls = false
        }
    }
    val touch = {
        touched = System.nanoTime()
        controls = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    touched = System.nanoTime()
                    controls = !controls
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val ratio = playback.aspect
        AndroidView(
            modifier = if (ratio > 0f) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) = playback.attach(texture)
                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            playback.detach()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
        )
        val problem = playback.problem
        if (problem != null) {
            PlayerProblem(problem, onRetry = { playback.retry() })
        } else if (playback.buffering) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(56.dp))
        }
        AnimatedVisibility(
            visible = controls || !playback.playing || problem != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Column(Modifier.fillMaxSize()) {
                    CinemaBar(media.title, onBack = onClose, onClose = onClose)
                    Spacer(Modifier.weight(1f))
                    if (problem == null) {
                        PlayerControls(media, playback, onTouch = touch)
                    }
                }
            }
        }
        if (!allowed) {
            ParkedCover()
            // The way out stays in sight, as over a site.
            Column(Modifier.fillMaxSize()) { CinemaBar(media.title, onBack = onClose, onClose = onClose) }
        }
    }
    LaunchedEffect(Unit) { MyCarLog.i("cinema: playing ${if (media.live) "a TV channel" else "a video"}: ${media.title}") }
}

/** Back 10 s, play or pause, forward 10 s, and the line with the times; for TV, play or pause and "Live". */
@Composable
private fun PlayerControls(media: CinemaMedia, playback: Playback, onTouch: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            .padding(horizontal = MyCarSpace.XL, vertical = MyCarSpace.M),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!media.live) {
            ThinSeekBar(
                fraction = if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f,
                onSeek = {
                    onTouch()
                    playback.seekTo((it * playback.durationMs).toLong())
                },
                enabled = playback.durationMs > 0,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(playback.positionMs), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Text(
                    "-" + formatDuration((playback.durationMs - playback.positionMs).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MyCarSpace.XL),
        ) {
            if (!media.live) {
                PlainButton({ onTouch(); playback.seekBy(-SKIP_MS) }, "Back 10 seconds", BUTTON) {
                    Icon(painterResource(R.drawable.sym_replay_10), null, Modifier.size(GLYPH))
                }
            } else {
                Text(
                    "LIVE",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFF4D45))
                        .padding(horizontal = MyCarSpace.S, vertical = 2.dp),
                )
            }
            PlainButton({ onTouch(); playback.toggle() }, if (playback.playing) "Pause" else "Play", BUTTON * 1.25f) {
                Icon(
                    painterResource(if (playback.playing) R.drawable.sym_pause else R.drawable.sym_play_arrow),
                    null,
                    Modifier.size(GLYPH * 1.4f),
                )
            }
            if (!media.live) {
                PlainButton({ onTouch(); playback.seekBy(SKIP_MS) }, "Forward 10 seconds", BUTTON) {
                    Icon(painterResource(R.drawable.sym_forward_10), null, Modifier.size(GLYPH))
                }
            } else {
                // The same room on both sides, so play stays in the middle.
                Spacer(Modifier.width(BUTTON))
            }
        }
    }
}

/** Why it didn't play, and a button to try again. */
@Composable
private fun PlayerProblem(problem: String, onRetry: () -> Unit) {
    Column(
        Modifier.padding(MyCarSpace.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MyCarSpace.M),
    ) {
        Text(problem, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        GsButton(onClick = onRetry) { Text("Try again") }
    }
}

/**
 * Android's MediaPlayer for one video or channel: its state for the screen, the picture on a
 * TextureView's surface, the phone's audio focus while playing (a call pauses it, and it goes on
 * after), and where a video was left.
 */
private class Playback(private val context: Context, private val media: CinemaMedia) {

    private val player = MediaPlayer()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { onFocus(it) }
        .build()
    private var surface: Surface? = null
    private var released = false
    private var pausedForFocus = false
    private val main = Handler(Looper.getMainLooper())
    /** A channel tries again by itself after a gap in the signal: how many times since it last played. */
    private var autoRetries = 0
    private val autoRetry = Runnable { if (problem != null) retry(auto = true) }
    /** When a call paused a channel: back after a long one, it opens again at the live point. */
    private var pausedAt = 0L
    /** The network back (out of a tunnel): a video or channel that stopped tries again. */
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(net: Network) {
            main.post { if (!released && problem != null && media.uri.scheme?.startsWith("http") == true) retry(auto = true) }
        }
    }
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    init {
        runCatching { connectivity?.registerDefaultNetworkCallback(network) }
    }

    var prepared by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(true)
        private set
    var problem by mutableStateOf<String?>(null)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    private var width by mutableIntStateOf(0)
    private var height by mutableIntStateOf(0)
    private var ended = false

    /** Width over height, once the player knows; 0 until then. */
    val aspect: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 0f

    fun open() {
        player.setAudioAttributes(attributes)
        player.setOnPreparedListener { onPrepared() }
        player.setOnVideoSizeChangedListener { _, w, h ->
            width = w
            height = h
        }
        player.setOnInfoListener { _, what, _ ->
            when (what) {
                MediaPlayer.MEDIA_INFO_BUFFERING_START -> buffering = true
                MediaPlayer.MEDIA_INFO_BUFFERING_END, MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> buffering = false
            }
            false
        }
        player.setOnErrorListener { _, what, extra ->
            failed(what, extra)
            true
        }
        player.setOnCompletionListener { onEnd() }
        surface?.let { player.setSurface(it) }
        try {
            player.setDataSource(context, media.uri, media.headers)
            player.prepareAsync()
        } catch (e: Exception) {
            MyCarLog.w("cinema: ${media.title} didn't open - ${e.javaClass.simpleName}: ${e.message}")
            problem = "It didn't open: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun onPrepared() {
        if (released) return
        prepared = true
        buffering = false
        durationMs = if (media.live) 0L else player.duration.toLong().coerceAtLeast(0L)
        autoRetries = 0
        // Where it was left last time, or, after "Try again", where it stopped.
        val saved = media.resumeKey?.let { PhoneVideos.resumeAt(context, it, durationMs) } ?: 0L
        val resume = if (media.live) 0L else maxOf(saved, positionMs)
        if (resume > 0) {
            player.seekTo(resume, MediaPlayer.SEEK_PREVIOUS_SYNC)
            positionMs = resume
        }
        play()
    }

    private fun onEnd() {
        // Android tells "finished" after an error too: the problem stays, and so does the point
        // where it stopped (said "finished", a film started over from the beginning).
        if (released || problem != null) return
        playing = false
        val at = runCatching { player.currentPosition.toLong() }.getOrDefault(positionMs)
        if (!media.live && media.uri.scheme?.startsWith("http") == true && durationMs > 0 && at < durationMs - EARLY_END_MS) {
            // Well before its end: the stream stopped coming, it didn't finish. (A file on the
            // phone ends where it ends: a recording cut short is not the connection's fault.)
            positionMs = at
            leave()
            problem = "It stopped before the end: check the phone's connection."
            return
        }
        if (media.live) {
            // A channel has no end: it stopped coming. It tries again by itself, as after an error.
            problem = "The channel stopped."
            if (autoRetries < AUTO_RETRY_MS.size) main.postDelayed(autoRetry, AUTO_RETRY_MS[autoRetries++])
        } else {
            ended = true
            positionMs = durationMs
        }
        leave()
    }

    fun play() {
        if (released || !prepared || problem != null) return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            MyCarLog.i("cinema: the phone's sound is busy (a call?) - not playing now")
            return
        }
        if (ended) {
            player.seekTo(0)
            positionMs = 0L
            ended = false
        }
        runCatching { player.start() }.onSuccess { playing = true }
        pausedForFocus = false
    }

    fun pause() {
        if (released || !playing) return
        runCatching { player.pause() }
        playing = false
        leave()
    }

    fun toggle() = if (playing) pause() else play()

    fun seekBy(deltaMs: Long) = seekTo(positionMs + deltaMs)

    fun seekTo(ms: Long) {
        if (released || !prepared || media.live || problem != null) return
        val to = ms.coerceIn(0L, durationMs.coerceAtLeast(0L))
        runCatching { player.seekTo(to, MediaPlayer.SEEK_CLOSEST_SYNC) }
        positionMs = to
        ended = false
    }

    /** The position, for the line and the times. */
    fun tick() {
        if (released || !prepared || problem != null || ended) return
        runCatching { player.currentPosition.toLong() }.onSuccess { positionMs = it }
    }

    /** After a problem: the same video (where it stopped) or channel, on the same picture. */
    fun retry(auto: Boolean = false) {
        if (released) return
        main.removeCallbacks(autoRetry)
        if (!auto) autoRetries = 0
        problem = null
        prepared = false
        playing = false
        buffering = true
        ended = false
        runCatching { player.reset() }
        open()
    }

    fun attach(texture: SurfaceTexture) {
        if (released) return
        surface?.release()
        surface = Surface(texture).also { runCatching { player.setSurface(it) } }
    }

    fun detach() {
        if (!released) runCatching { player.setSurface(null) }
        surface?.release()
        surface = null
    }

    fun release() {
        if (released) return
        leave()
        released = true
        main.removeCallbacks(autoRetry)
        runCatching { connectivity?.unregisterNetworkCallback(network) }
        audio.abandonAudioFocusRequest(focus)
        runCatching { player.release() }
        surface?.release()
        surface = null
    }

    /** A call or another app took the sound: paused, and on again after a passing one. */
    private fun onFocus(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (playing) {
                pause()
                pausedForFocus = true
                pausedAt = SystemClock.elapsedRealtime()
            }
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedForFocus) {
                // A channel after a long call: minutes behind, often out of the stream's window
                // by now - opened again at the live point instead.
                if (media.live && SystemClock.elapsedRealtime() - pausedAt > LIVE_STALE_MS) {
                    pausedForFocus = false
                    retry(auto = true)
                } else {
                    play()
                }
            }
        }
    }

    /** Where a video was left, for the next time it opens. */
    private fun leave() {
        val key = media.resumeKey ?: return
        if (!prepared) return
        PhoneVideos.leftAt(context, key, if (ended) 0L else positionMs)
    }

    private fun failed(what: Int, extra: Int) {
        MyCarLog.w("cinema: ${media.title} didn't play - error $what/$extra")
        // Where it stopped (the last tick: in its error state the player can't be asked), for
        // "Try again" and for next time.
        leave()
        playing = false
        // A channel through a gap in the signal: it tries again by itself, a few times.
        if (media.live && autoRetries < AUTO_RETRY_MS.size) {
            main.postDelayed(autoRetry, AUTO_RETRY_MS[autoRetries++])
        }
        problem = when {
            media.uri.scheme?.startsWith("http") == true && LocalNetwork.blocks(context, listOf(media.uri.toString())) -> LocalNetwork.HINT
            extra == MediaPlayer.MEDIA_ERROR_IO -> "Couldn't reach it: check the phone's connection."
            extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "It didn't answer in time."
            extra == MediaPlayer.MEDIA_ERROR_UNSUPPORTED || extra == MediaPlayer.MEDIA_ERROR_MALFORMED ->
                "The phone's player can't play this format."
            what == MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "The phone's player stopped."
            else -> "It didn't play (error $what, $extra)."
        }
    }
}

private val BUTTON = 64.dp
private val GLYPH = 36.dp
private const val SKIP_MS = 10_000L
private const val TICK_MS = 500L
private const val CONTROLS_MS = 4_000L

/** A film from the network that "finishes" this long before its end stopped coming: shown as a problem, not the end. */
private const val EARLY_END_MS = 10_000L

/** A channel's own tries after a problem: 3, 10, then 30 seconds later. */
private val AUTO_RETRY_MS = longArrayOf(3_000L, 10_000L, 30_000L)

/** Paused for a call longer than this, a channel opens again at the live point. */
private const val LIVE_STALE_MS = 20_000L
