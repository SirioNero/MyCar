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
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Connected to the car", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MyCarActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.sym_directions_car)
            .setContentTitle("myCar is connected to the car")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        // Refused if myCar was no longer in front by the time this runs (the phone locked at once):
        // the drive goes on as before, without it.
        runCatching {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }.onSuccess {
            running = true
            MyCarLog.i("session: in the foreground for the drive")
            hold(intent?.getBooleanExtra(EXTRA_WIRELESS, false) == true)
        }.onFailure {
            MyCarLog.w("session: couldn't keep the drive in the foreground - ${it.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        wakeLock = null
        wifiLock = null
        super.onDestroy()
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

        /** A car connected: called while myCar is on screen, as its arrival opens it. */
        fun start(context: Context, wireless: Boolean = false) {
            val intent = Intent(context, SessionService::class.java).putExtra(EXTRA_WIRELESS, wireless)
            runCatching { context.startForegroundService(intent) }
                .onFailure { MyCarLog.w("session: Android won't start the drive's foreground service now - ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, SessionService::class.java)) }
        }
    }
}
