package io.github.sirionero.mycar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.content.res.Configuration
import io.github.sirionero.mycar.audio.AudioCaptureService
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.mirror.MirrorService
import io.github.sirionero.mycar.mirror.PhoneMirror
import io.github.sirionero.mycar.mirror.TouchRelayService
import io.github.sirionero.mycar.car.CarEnvironment
import io.github.sirionero.mycar.car.CarSettings
import io.github.sirionero.mycar.car.CarUi
import io.github.sirionero.mycar.car.Prefetch
import io.github.sirionero.mycar.stats.UsageStats
import io.github.sirionero.mycar.ui.MyCarApp
import io.github.sirionero.mycar.ui.MyCarTheme
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Hosts the phone UI and, for now, the car session itself.
 *
 * Launched by the system when the head unit switches the phone into accessory mode. The
 * session lives in this activity, so [android:configChanges] in the manifest keeps a
 * rotation or theme change from recreating it and dropping the connection.
 */
class MyCarActivity : ComponentActivity(), Projection {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())


    private var descriptor: ParcelFileDescriptor? = null
    /** The wireless session's socket, when the car came over Wi-Fi instead of USB. */
    private var wirelessSocket: java.net.Socket? = null
    private var runner: MyCarRunner? = null
    private var screenProjector: ScreenProjector? = null
    private var worker: Thread? = null

    /** The encoder's input Surface, kept so the car's picture can be switched to the mirror. */
    private var surface: Surface? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var frameDensity = 0

    /**
     * Screen capture always begins with a consent dialog, which only an activity can show -
     * so the car UI asks, and the request surfaces here on the phone.
     */
    private val captureConsent = registerForActivityResult(StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            // Answered once the car had gone: there's no screen to share with.
            if (surface != null) MirrorService.start(this, result.resultCode, data)
            else MyCarLog.i("screen capture allowed, but the car is gone")
        } else {
            MyCarLog.w("screen capture consent declined")
        }
    }

    /** Media audio needs the microphone permission (a platform rule for playback capture) first. */
    private val audioPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) askAudioCapture()
        else if (!shouldShowRequestPermissionRationale(android.Manifest.permission.RECORD_AUDIO)) {
            // Refused twice: Android now says no without showing anything, so the phone must.
            MyCarLog.w("audio: record permission blocked - Android no longer asks")
            CarAudio.micBlocked()
            micBlockedNotice.value = true
        } else {
            MyCarLog.w("audio: record permission declined")
            CarAudio.denied()
        }
    }

    private val audioConsent = registerForActivityResult(StartActivityForResult()) { result ->
        io.github.sirionero.mycar.audio.CaptureConsent.answered()
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            // Answered after the car went away, or after the sound moved to Bluetooth: starting now
            // would mute the phone and send its music nowhere.
            if (CarAudio.stillWanted) AudioCaptureService.start(this, result.resultCode, data)
            else MyCarLog.i("audio: capture allowed, but the car no longer wants it")
        } else {
            MyCarLog.w("audio: capture consent declined")
            CarAudio.denied()
            if (!CarSettings.audioChoiceAsked) offerBluetooth.value = true
        }
    }

    /** Raised when the microphone permission is blocked; see [MicBlockedDialog]. */
    private val micBlockedNotice = kotlinx.coroutines.flow.MutableStateFlow(false)

    /** Raised once, after the first "no" to the capture dialog; see [AudioChoiceDialog]. */
    private val offerBluetooth = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_ACCESSORY_DETACHED) {
                MyCarLog.w("accessory detached")
                // A wireless session has no accessory to lose; leave it running.
                if (wirelessSocket == null) closeAccessory()
            }
        }
    }

    /** Setting myCar as the phone's caller ID and spam app - what lets it decline a ringing call. */
    private val callScreeningRole = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) MyCarLog.w("calls: call screening role declined")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Location, calendar, the phone/contacts group, and the microphone are asked for one at
        // a time by PermissionsSetupScreen instead of here, each only after the driver taps
        // Allow on a screen explaining what it is for. Firing them all from onCreate, unprompted,
        // is both a bad first impression and unreliable: Android does not consistently show more
        // than one system permission dialog fired back-to-back, which was silently dropping some.
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        MyCarLog.init(this)
        MyCarLog.installCrashHandler()
        MyCarLog.i("myCar ready")
        io.github.sirionero.mycar.wireless.WirelessLink.onConnected = { socket ->
            runOnUiThread { startWireless(socket) }
        }
        CarSettings.init(this)
        AppSettings.applyMyCarEnabled(this)
        // A capture that died without tidying up would leave notifications exposed to every
        // later screen share, and the phone's music muted. Nothing is capturing if the service is
        // not running, so undo both.
        if (!io.github.sirionero.mycar.audio.AudioCaptureService.running) {
            io.github.sirionero.mycar.audio.ScreenShareGuard.restore(this)
            AudioCaptureService.restorePhoneIfLeftMuted(this)
        }
        Prefetch.warm(this)
        CarEnvironment.setPhoneTheme(resources.configuration)

        setContent {
            MyCarTheme {
                MyCarApp(onDisconnect = { closeAccessory() }, onRequestCallScreening = ::requestCallScreeningRole)
                val offer by offerBluetooth.collectAsState()
                if (offer) {
                    fun close() {
                        CarSettings.markAudioChoiceAsked()
                        offerBluetooth.value = false
                    }
                    io.github.sirionero.mycar.ui.AudioChoiceDialog(
                        onUseBluetooth = {
                            CarSettings.setAudioRoute(io.github.sirionero.mycar.audio.AudioRoute.BLUETOOTH)
                            close()
                        },
                        onKeepAsking = ::close,
                        onLetMyCarAnswer = {
                            io.github.sirionero.mycar.mirror.PhoneApps.openAccessibilitySettings(this@MyCarActivity)
                            close()
                        },
                    )
                }
                val micNotice by micBlockedNotice.collectAsState()
                if (micNotice) {
                    io.github.sirionero.mycar.ui.MicBlockedDialog(
                        onOpenSettings = {
                            micBlockedNotice.value = false
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                            )
                        },
                        onDismiss = { micBlockedNotice.value = false },
                    )
                }
            }
        }

        PhoneMirror.onStartRequested = { runOnUiThread { requestCapture() } }
        PhoneMirror.onTokenReady = { token -> runOnUiThread { showMirror(token) } }
        PhoneMirror.onEnded = { runOnUiThread { showCarUi() } }
        CarAudio.onConsentRequested = { runOnUiThread { requestAudioCapture() } }

        registerReceiver(detachReceiver, IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED), RECEIVER_NOT_EXPORTED)
        handleIntent(intent)
    }

    // The manifest opts out of recreation on uiMode changes, so a light/dark switch on the phone
    // arrives here, and the car UI follows it.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        CarEnvironment.setPhoneTheme(newConfig)
    }

    // Throttled inside Prefetch, so this only does work if the last run was a while ago.
    override fun onStart() {
        super.onStart()
        Prefetch.warm(this)
        UsageStats.onAppOpened(this)
        io.github.sirionero.mycar.update.UpdateChecker.onAppOpened(this)
        // Back from the app's settings with the microphone turned on: try car audio again.
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            CarAudio.micAllowed()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        io.github.sirionero.mycar.wireless.WirelessLink.onConnected = null
        runCatching { unregisterReceiver(detachReceiver) }
        PhoneMirror.onStartRequested = null
        PhoneMirror.onTokenReady = null
        PhoneMirror.onEnded = null
        CarAudio.onConsentRequested = null
        CarAudio.release()
        PhoneMirror.requestStop()
        closeAccessory()
        MyCarLog.flush()
    }

    /** Launches the system dialog that offers myCar the call-screening role, if it isn't held already. */
    private fun requestCallScreeningRole() {
        val roleManager = getSystemService(android.app.role.RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING)) {
            MyCarLog.w("calls: call screening role unavailable on this phone")
            return
        }
        if (roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)) return
        runCatching {
            callScreeningRole.launch(roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING))
        }.onFailure { MyCarLog.e("could not ask for the call screening role", it) }
    }

    // --- Media audio --------------------------------------------------------------------

    private fun requestAudioCapture() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askAudioCapture()
        } else {
            audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun askAudioCapture() {
        runCatching { audioConsent.launch(io.github.sirionero.mycar.audio.CaptureConsent.intent(this)) }
            .onFailure {
                MyCarLog.e("could not ask for audio capture", it)
                CarAudio.denied()
            }
    }

    // --- Phone mirroring ----------------------------------------------------------------

    private fun requestCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        runCatching { captureConsent.launch(manager.createScreenCaptureIntent()) }
            .onFailure { MyCarLog.e("could not ask for screen capture", it) }
    }

    /** A Surface has a single producer, so our own display must go before the mirror arrives. */
    private fun showMirror(token: MediaProjection) {
        val target = surface ?: run {
            // The car went away while sharing started: end it, or the phone stays "casting" with
            // its notification up until stopped by hand.
            runCatching { token.stop() }
            MirrorService.stop()
            return
        }
        screenProjector?.stop()
        screenProjector = null
        PhoneMirror.begin(token, target, frameWidth, frameHeight, frameDensity)
        if (PhoneMirror.active.value) PhoneMirror.launchPending(this) else showCarUi()
    }

    private fun showCarUi() {
        val target = surface ?: return
        if (screenProjector != null) return
        val projector = ScreenProjector(this).also { screenProjector = it }
        projector.start(target, frameWidth, frameHeight, frameDensity) { CarUi() }
    }

    /**
     * Sends a head-unit touch to the phone. Anything landing on the mirror's letterbox padding
     * maps to nothing on the phone and is dropped rather than clamped onto a screen edge.
     */
    private fun relayTouch(action: Int, x: Float, y: Float) {
        val point = PhoneMirror.mapToPhone(this, x, y)
        if (point == null) {
            // The letterbox bars belong to no window, which makes them a safe way back: a tap
            // on the padding leaves the mirror and returns the car to myCar's own UI.
            if (action == MotionEvent.ACTION_UP) PhoneMirror.requestStop()
            return
        }
        val relay = TouchRelayService.instance ?: return
        when (action) {
            MotionEvent.ACTION_DOWN -> relay.down(point.x, point.y)
            MotionEvent.ACTION_MOVE -> relay.move(point.x, point.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> relay.up(point.x, point.y)
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_ACCESSORY_ATTACHED) return
        // Switched off in Settings: the system shouldn't route the car here at all, but if it
        // does, leave the accessory alone.
        if (!AppSettings.myCarEnabled(this)) {
            MyCarLog.i("accessory attached, but myCar is switched off - not connecting")
            return
        }
        val accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY, UsbAccessory::class.java)
        if (accessory == null) {
            MyCarLog.e("attach intent carried no accessory")
            SessionStatus.failed("Accessory missing", "The system reported a USB attach but did not include the accessory.")
            return
        }
        MyCarLog.i("accessory attached by the system - permission is implicit on this path")
        start(accessory)
    }

    private fun start(accessory: UsbAccessory) {
        // A fresh ATTACHED intent means the system wants a new session, whatever state the old
        // one is in - closing first guarantees openAccessory() below never fights a descriptor
        // this same process forgot to release. The old worker thread often isn't alive by this
        // point anyway (its read loop already died when the head unit dropped the transport),
        // but the accessory it opened was never explicitly closed, which is what actually blocks
        // a re-open - a dead thread does not release the file descriptor on its own.
        closeAccessory()
        describe(accessory)

        val manager = getSystemService(UsbManager::class.java)
        val pfd = manager.openAccessory(accessory)
        if (pfd == null) {
            MyCarLog.e("openAccessory returned null - the session was claimed by someone else")
            SessionStatus.failed("Accessory unavailable", "Another app claimed the USB accessory before myCar could open it.")
            return
        }
        descriptor = pfd

        val runner = MyCarRunner(
            input = FileInputStream(pfd.fileDescriptor),
            output = FileOutputStream(pfd.fileDescriptor),
            identityProvider = { CertProvider.load(this) },
            projection = this,
            vehicleProfileFor = { info -> VehicleProfiles.find(VehicleProfiles.load(this), info) },
        )
        this.runner = runner
        worker = Thread { runner.run() }.apply { name = "mycar"; start() }
    }

    /** A wireless session: the same runner, on the socket [WirelessLink] opened to the car. */
    private fun startWireless(socket: java.net.Socket) {
        closeAccessory(stopWireless = false)
        if (!AppSettings.myCarEnabled(this)) {
            MyCarLog.i("wireless: myCar is switched off - not connecting")
            runCatching { socket.close() }
            return
        }
        MyCarLog.i("wireless: starting a session over Wi-Fi to ${socket.inetAddress?.hostAddress}:${socket.port}")
        wirelessSocket = socket
        val runner = MyCarRunner(
            input = socket.getInputStream(),
            output = socket.getOutputStream(),
            identityProvider = { CertProvider.load(this) },
            projection = this,
            vehicleProfileFor = { info -> VehicleProfiles.find(VehicleProfiles.load(this), info) },
            wireless = true,
        )
        this.runner = runner
        worker = Thread { runner.run() }.apply { name = "mycar"; start() }
    }

    private fun describe(accessory: UsbAccessory) {
        MyCarLog.i("accessory: manufacturer=${accessory.manufacturer} model=${accessory.model}")
        MyCarLog.i("           version=${accessory.version} description=${accessory.description}")
    }

    private fun closeAccessory() = closeAccessory(stopWireless = true)

    /** Ends the session, wired or wireless. [stopWireless] also drops the car's Bluetooth and Wi-Fi. */
    private fun closeAccessory(stopWireless: Boolean) {
        runner?.stop()
        SessionReport.print()
        runCatching { descriptor?.close() }
        descriptor = null
        runCatching { wirelessSocket?.close() }
        wirelessSocket = null
        if (stopWireless) io.github.sirionero.mycar.wireless.WirelessLink.stop(this)
        runner = null
    }

    // --- Projection ---------------------------------------------------------------------

    override fun onSurfaceReady(surface: Surface, width: Int, height: Int, densityDpi: Int) {
        runOnUiThread {
            this.surface = surface
            frameWidth = width
            frameHeight = height
            frameDensity = densityDpi
            CarEnvironment.setDisplay(width, height, densityDpi)
            CarEnvironment.start(this)
            showCarUi()
            io.github.sirionero.mycar.car.CarKeys.focusSink = { code, down ->
                runOnUiThread { screenProjector?.dispatchKey(code, down) }
            }
        }
    }

    override fun onTouch(action: Int, actionIndex: Int, points: List<TouchPoint>) {
        // To the front of the main thread's queue: a touch waiting behind other work is lag the
        // driver feels, while the work it jumps ahead of can wait a frame.
        mainHandler.postAtFrontOfQueue {
            // Mirroring drives one phone touch at a time, so it follows the first finger only.
            if (PhoneMirror.active.value) points.firstOrNull()?.let { relayTouch(action, it.x, it.y) }
            else screenProjector?.dispatchTouch(action, actionIndex, points)
        }
    }

    override fun onProjectionStopped() {
        // Before the car UI goes, while the media connection is still up.
        io.github.sirionero.mycar.car.CarServices.pausePhoneMedia()
        runOnUiThread {
            PhoneMirror.requestStop()
            io.github.sirionero.mycar.car.CarKeys.focusSink = null
            screenProjector?.stop()
            screenProjector = null
            surface = null
            CarEnvironment.stop()
        }
    }
}
