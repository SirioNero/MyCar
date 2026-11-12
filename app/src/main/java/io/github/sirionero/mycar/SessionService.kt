package io.github.sirionero.mycar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.launch

/**
 * Keeps the drive in the foreground while the phone's screen is elsewhere, for as long as a car is
 * connected - Android Auto has its own "connected to the car". Without it myCar is just an app in
 * the background once the phone locks or another app opens: Android 17 then refuses it the audio
 * focus and silences its own sound (Xonos over Bluetooth), and in time the process can be frozen,
 * the car's picture with it. Started while myCar is on screen - the car's arrival opens it - so
 * the service gets what the app had in front, for the whole drive.
 */
class SessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, Words.get(R.string.channel_session), NotificationManager.IMPORTANCE_LOW))
        val notification = build(SessionStatus.state.value)
        // Refused if myCar was no longer in front by the time this runs (the phone locked at once):
        // the drive goes on as before, without it.
        runCatching {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }.onSuccess {
            // The drive ended while this was starting: it could only stop once in the foreground.
            if (!wanted) {
                stopSelf()
                return START_NOT_STICKY
            }
            running = true
            MyCarLog.i("session: in the foreground for the drive")
            // The notification says what Home says, as it changes: "connected" while a start-up was
            // still reaching the car, or after the car turned the certificate down, disagreed with it.
            if (watch == null) {
                watch = kotlinx.coroutines.MainScope().launch {
                    // Not once the service is let go (stop() empties the holders before stopService): the
                    // drive's end posted a "not connected" that belonged to no service and stayed.
                    SessionStatus.state.collect { if (running && wanted) manager.notify(NOTIFICATION_ID, build(it)) }
                }
            }
            hold(intent?.getBooleanExtra(EXTRA_WIRELESS, false) == true)
        }.onFailure {
            MyCarLog.w("session: couldn't keep the drive in the foreground - ${it.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // First, on the main thread as its updates are: none lands after the service is gone.
        watch?.cancel()
        watch = null
        // And should one have slipped through: gone with the service.
        runCatching { getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
        running = false
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        wakeLock = null
        wifiLock = null
        super.onDestroy()
    }

    private var watch: kotlinx.coroutines.Job? = null

    /** "myCar is connected to the car" once the car shows myCar; before that, or after a failure, Home's own words. */
    private fun build(s: SessionStatus.Snapshot): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MyCarActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.sym_directions_car)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (s.phase == SessionStatus.Phase.PROJECTING) {
            builder.setContentTitle(Words.get(R.string.session_notification_title))
        } else {
            builder.setContentTitle(s.headline).setContentText(s.detail.ifEmpty { null })
            // All of it when pulled down: some of Home's lines are advice, longer than a line (a VPN in the way).
            if (s.detail.isNotEmpty()) builder.setStyle(Notification.BigTextStyle().bigText(s.detail))
        }
        return builder.build()
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * Keeps the phone awake for the drive, and its Wi-Fi at full speed when that's the link to the
     * car. With the screen off and nothing playing through the phone, it could doze between two
     * frames: picture and touches stuttered until the car gave up. The cable mostly kept it awake;
     * the car's Wi-Fi doesn't.
     */
    @Suppress("DEPRECATION") // WIFI_MODE_FULL_HIGH_PERF: still keeps the Wi-Fi out of power saving
    private fun hold(wireless: Boolean) {
        if (wakeLock == null) {
            wakeLock = runCatching {
                getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myCar:drive")
                    .apply { setReferenceCounted(false); acquire() }
            }.getOrNull()
        }
        if (wireless && wifiLock == null) {
            wifiLock = runCatching {
                applicationContext.getSystemService(WifiManager::class.java)
                    .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "myCar:drive")
                    .apply { setReferenceCounted(false); acquire() }
            }.getOrNull()
        }
    }

    companion object {
        private const val CHANNEL = "mycar_session"
        private const val NOTIFICATION_ID = 45
        private const val EXTRA_WIRELESS = "wireless"

        @Volatile var running = false
            private set

        /** A drive holds the service, from its picture to its end. */
        const val DRIVE = "drive"
        /** A wireless start-up holds it from the car's Bluetooth channel, for the car's Wi-Fi (WirelessLink). */
        const val STARTUP = "startup"

        /**
         * Who wants the service. It stops when no one does: with one flag for both, the end of a drive
         * stopped the service a start-up had just taken for the car starting over, and a start-up's
         * service taken after a drive was never given back.
         */
        private val holders = java.util.Collections.synchronizedSet(mutableSetOf<String>())

        /** Someone wants the service; false once no one does. */
        private val wanted get() = holders.isNotEmpty()

        /** Someone (a start-up or a drive) holds the service, whether or not it made it to the foreground. */
        val held: Boolean get() = holders.isNotEmpty()

        /** A car connected: called while myCar is on screen, as its arrival opens it. */
        fun start(context: Context, wireless: Boolean = false, holder: String = DRIVE) {
            holders += holder
            val intent = Intent(context, SessionService::class.java).putExtra(EXTRA_WIRELESS, wireless)
            runCatching { context.startForegroundService(intent) }
                .onFailure { MyCarLog.w("session: Android won't start the drive's foreground service now - ${it.message}") }
            // The car map's position for the drive: now, while Android may still count myCar's
            // screen as just created by the car's arrival (only posts, never throws).
            io.github.sirionero.mycar.host.LocationKeepAlive.holdForDrive(context)
        }

        fun stop(context: Context, holder: String = DRIVE) {
            holders -= holder
            if (wanted) return
            io.github.sirionero.mycar.host.LocationKeepAlive.releaseDrive(context)
            // Stopped before it called startForeground, Android takes myCar down with it: a service
            // still starting stops itself in onStartCommand instead.
            if (!running) return
            runCatching { context.stopService(Intent(context, SessionService::class.java)) }
        }
    }
}
