package io.github.sirionero.mycar.xonos.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.sirionero.mycar.BuildConfig
import io.github.sirionero.mycar.MyCarActivity
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.xonos.Xonos
import io.github.sirionero.mycar.xonos.describe
import io.github.sirionero.mycar.xonos.data.Connection
import io.github.sirionero.mycar.xonos.sendspin.AndroidAudio
import io.github.sirionero.mycar.xonos.sendspin.AudioEngine
import io.github.sirionero.mycar.xonos.sendspin.CarLinkOutput
import io.github.sirionero.mycar.xonos.sendspin.SendspinClient
import io.github.sirionero.mycar.xonos.sendspin.StreamFormat
import io.github.sirionero.mycar.xonos.sendspin.TimeFilter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Il cuore di Xonos dentro myCar: è il "lettore musicale" che le schermate della musica di myCar
 * trovano e aprono in auto, come quello di un'app musicale qualsiasi, con il menu della libreria
 * e i comandi. Tiene il telefono collegato a Music Assistant come lettore e lo fa suonare.
 *
 * myCar si collega quando si apre Xonos in auto; mentre la musica suona il servizio resta
 * in primo piano (con la sua notifica) anche a schermo spento.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private val app = Xonos
    private lateinit var engine: AudioEngine
    private lateinit var sendspin: SendspinClient
    private lateinit var player: MaPlayer
    private lateinit var tree: BrowseTree
    private lateinit var session: MediaLibrarySession
    private lateinit var focus: AudioFocus
    private lateinit var connectivity: ConnectivityManager

    private val scope = MainScope()
    private val main = Handler(Looper.getMainLooper())
    private val searches = mutableMapOf<String, List<MediaItem>>()

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var noisyRegistered = false
    private var resumeAfterInterruption = false
    private var networkChanged = false
    private var lastNetwork: Network? = null
    private var announcedName: String? = null
    private val abandonFocus = Runnable { focus.abandon() }

    override fun onCreate() {
        super.onCreate()
        Xonos.init(this)
        connectivity = getSystemService(ConnectivityManager::class.java)
        val settings = app.settings
        val artwork = Artwork(this)

        // Con l'audio all'auto tramite myCar si suona dritto nel suo canale musicale; altrimenti sul
        // telefono, cioè in Bluetooth verso l'auto.
        engine = AudioEngine({ rate, channels -> CarLinkOutput.open(rate, channels) ?: AndroidAudio.output(rate, channels) }, AndroidAudio::decoder, TimeFilter()) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        }
        engine.setVolume(settings.volume, settings.muted)
        sendspin = SendspinClient(app.repo.http, engine, ::identity) { volume, muted ->
            settings.volume = volume
            settings.muted = muted
        }
        player = MaPlayer(mainLooper, app.repo, settings.playerId, app.playerReady, engine.outputting, artwork::uriFor, { engine.lateMs }) { connection ->
            describe(this, connection)
        }
        tree = BrowseTree(Menu(app.library, ResourceLabels(this)) { settings.playerId }, artwork)
        player.onPlayRequest = { item -> tree.play(item) }
        player.onUserPause = {
            resumeAfterInterruption = false
            // Music Assistant non sente la pausa senza rete: l'audio già ricevuto si ferma qui.
            if (app.repo.connection.value !is Connection.Connected) engine.end()
        }

        // Toccando la notifica si apre myCar sul telefono.
        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MyCarActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, Callback())
            .setSessionActivity(openPlayer)
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(R.drawable.xonos_notification) },
        )
        focus = AudioFocus(this, ::onFocusChange)

        app.repo.acquire()
        scope.launch { app.repo.connection.collect(::onConnection) }
        scope.launch {
            combine(app.repo.connection, sendspin.phase) { connection, phase ->
                connection is Connection.Connected && phase == SendspinClient.Phase.READY
            }.collect { app.playerReady.value = it }
        }
        scope.launch { engine.outputting.collect(::onOutputting) }
        // L'auto si collega o se ne va, o l'audio passa al Bluetooth: l'uscita cambia strada.
        scope.launch { CarAudio.direct.drop(1).collect { engine.reroute() } }
        connectivity.registerDefaultNetworkCallback(networkCallback)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
        app.playerReady.value = false
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        main.removeCallbacksAndMessages(null)
        setNoisyReceiver(false)
        releaseLocks()
        focus.abandon()
        session.release()
        player.release()
        sendspin.stop()
        engine.release()
        app.repo.release()
        scope.cancel()
        super.onDestroy()
    }

    /** Come ci presentiamo a Music Assistant, deciso a ogni connessione (la rete può essere cambiata). */
    private fun identity(): SendspinClient.Identity {
        val settings = app.settings
        val metered = connectivity.isActiveNetworkMetered
        val formats = buildList {
            // Opus pesa un decimo del PCM: in rete mobile fa la differenza. Il PCM resta come riserva.
            if (AndroidAudio.canDecode(MediaFormat.MIMETYPE_AUDIO_OPUS)) add(StreamFormat("opus", RATE, 2, 16))
            add(StreamFormat("pcm", RATE, 2, 16))
        }
        return SendspinClient.Identity(
            clientId = settings.playerId,
            name = settings.playerName,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.titlecase() },
            softwareVersion = "myCar ${BuildConfig.VERSION_NAME}",
            formats = formats,
            // Il margine si paga a ogni play e a ogni salto (misurato: con 800 ms la musica partiva 0,65 s
            // dopo, con 1500 ms 1,3 s dopo). Le gallerie non le copre questo ma l'audio che il server manda
            // in anticipo durante il brano: il margine resta piccolo, un po' più largo in rete mobile.
            leadTimeMs = if (metered) 600 else 300,
            minBufferMs = if (metered) 400 else 150,
            volume = settings.volume,
            muted = settings.muted,
        )
    }

    private fun onConnection(connection: Connection) {
        when (connection) {
            is Connection.Connected -> {
                // Prima l'API, poi l'audio: così Music Assistant lega il lettore a questa sessione.
                // Si riapre anche l'audio se è cambiata la rete o il nome del lettore.
                val name = app.settings.playerName
                val force = networkChanged || connection.server.limitedPlayers || name != announcedName
                networkChanged = false
                announcedName = name
                sendspin.start(connection.url, app.settings.load().token, force)
            }
            Connection.NotConfigured -> sendspin.stop()
            // Mentre si ricollega, l'audio già ricevuto continua a suonare.
            else -> Unit
        }
    }

    private fun onOutputting(on: Boolean) {
        if (on) {
            main.removeCallbacks(abandonFocus)
            acquireLocks()
            setNoisyReceiver(true)
            if (!focus.request()) {
                when {
                    // Dritto nel canale dell'auto il focus di Android non serve; Android 17 lo nega
                    // anche a un'app non in vista, col telefono in tasca: si continua a suonare.
                    CarAudio.direct.value -> Log.i(TAG, "Focus audio negato, ma la musica va dritta all'auto: continuo")
                    inCall() -> {
                        Log.i(TAG, "Focus audio negato durante una telefonata: metto in pausa")
                        player.pauseFromSystem()
                    }
                    else -> Log.w(TAG, "Focus audio negato senza telefonate (Android 17 in background?): continuo")
                }
            }
        } else {
            releaseLocks()
            setNoisyReceiver(false)
            // Ferma per un po': si lascia l'audio alle altre app, ma non durante una telefonata.
            if (!resumeAfterInterruption) main.postDelayed(abandonFocus, FOCUS_GRACE_MS)
        }
    }

    /** Una telefonata in corso, o che squilla: l'unico motivo per cui Xonos cede la musica da sé. */
    private fun inCall(): Boolean = getSystemService(AudioManager::class.java)?.mode in setOf(
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_CALL_SCREENING,
    )

    private fun onFocusChange(change: AudioFocus.Change) {
        when (change) {
            AudioFocus.Change.PAUSED -> if (player.isPlayingOnServer) {
                // Subito zitto, poi la pausa vera; riprende da sola alla fine della telefonata.
                engine.setDuck(0f)
                resumeAfterInterruption = true
                player.pauseFromSystem()
            }
            AudioFocus.Change.RESUMED -> {
                engine.setDuck(1f)
                if (resumeAfterInterruption) player.resumeFromSystem()
                resumeAfterInterruption = false
            }
            AudioFocus.Change.LOST -> {
                engine.setDuck(1f)
                resumeAfterInterruption = false
                player.pauseFromSystem()
            }
        }
    }

    /** Il Bluetooth dell'auto si stacca: la musica si ferma invece di passare all'altoparlante del telefono. */
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Nel canale dell'auto il Bluetooth che si stacca non c'entra.
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && !CarAudio.direct.value) player.pauseFromSystem()
        }
    }

    private fun setNoisyReceiver(on: Boolean) {
        if (on == noisyRegistered) return
        if (on) {
            ContextCompat.registerReceiver(
                this,
                noisy,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            runCatching { unregisterReceiver(noisy) }
        }
        noisyRegistered = on
    }

    /** A schermo spento il telefono non deve addormentarsi né spegnere il Wi-Fi mentre suona. */
    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        val wake = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Xonos:audio")
            .apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        wake.acquire(LOCK_TIMEOUT_MS)
        val wifi = wifiLock ?: applicationContext.getSystemService(WifiManager::class.java)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Xonos:audio")
            ?.apply { setReferenceCounted(false) }
            ?.also { wifiLock = it }
        wifi?.acquire()
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    /** Uscendo di casa il Wi-Fi lascia il posto alla rete mobile: le connessioni vecchie sono morte, si riapre tutto. */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post {
                val previous = lastNetwork
                lastNetwork = network
                if (previous != null && previous != network) {
                    Log.i(TAG, "Rete cambiata: mi ricollego")
                    networkChanged = true
                    app.repo.reconnect()
                }
            }
        }
    }

    private inner class Callback : MediaLibrarySession.Callback {

        // Gearslip non è un'app di sistema: senza questo Media3 gli lascerebbe solo guardare, non comandare.
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> =
            Futures.immediateFuture(MediaSession.ConnectionResult.AcceptedResultBuilder(session).build())

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(tree.root(), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = tree.item(mediaId)
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                LibraryResult.ofItemList(tree.children(parentId).page(page, pageSize), params)
            } catch (e: Exception) {
                Log.w(TAG, "Elenco $parentId non disponibile: ${e.message}")
                LibraryResult.ofError(SessionError.ERROR_IO)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = scope.future {
            val results = try {
                tree.search(query)
            } catch (e: Exception) {
                Log.w(TAG, "Ricerca non riuscita: ${e.message}")
                emptyList()
            }
            searches[query] = results
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                val results = searches[query] ?: tree.search(query)
                LibraryResult.ofItemList(results.page(page, pageSize), params)
            } catch (e: Exception) {
                LibraryResult.ofError(SessionError.ERROR_IO)
            }
        }

        // Titolo e copertina della voce scelta, così l'auto li mostra subito mentre Music Assistant prepara la coda.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(mediaItems.map(tree::resolve), startIndex, startPositionMs),
        )

        // L'auto preme play e il telefono non ha ancora la coda: si riprende quella di Music Assistant.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(
                listOf(MediaItem.Builder().setMediaId(Menu.RESUME).build()),
                0,
                C.TIME_UNSET,
            ),
        )

        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            rating: Rating,
        ): ListenableFuture<SessionResult> = scope.future { setFavorite(rating) }

        override fun onSetRating(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaId: String,
            rating: Rating,
        ): ListenableFuture<SessionResult> = scope.future { setFavorite(rating) }
    }

    /** Il cuore di Gearslip: aggiunge o toglie il brano dai preferiti di Music Assistant. */
    private suspend fun setFavorite(rating: Rating): SessionResult {
        val heart = (rating as? HeartRating)?.takeIf { it.isRated }?.isHeart
            ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val item = player.currentItem ?: return SessionResult(SessionError.ERROR_INVALID_STATE)
        val uri = item.uri ?: return SessionResult(SessionError.ERROR_INVALID_STATE)
        player.showFavorite(item, heart)
        return try {
            app.library.setFavorite(uri, item.mediaType ?: "track", item.provider, item.itemId, heart)
            SessionResult(SessionResult.RESULT_SUCCESS)
        } catch (e: Exception) {
            Log.w(TAG, "Preferito non salvato: ${e.message}")
            player.showFavorite(item, !heart)
            SessionResult(SessionError.ERROR_UNKNOWN)
        }
    }

    private fun List<MediaItem>.page(page: Int, pageSize: Int): List<MediaItem> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return this
        val from = page.toLong() * pageSize
        if (from >= size) return emptyList()
        return subList(from.toInt(), min(size.toLong(), from + pageSize).toInt())
    }

    private companion object {
        const val TAG = "Xonos"
        const val RATE = 48_000
        const val FOCUS_GRACE_MS = 30_000L
        const val LOCK_TIMEOUT_MS = 6 * 60 * 60 * 1000L
    }
}
