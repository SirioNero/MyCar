package io.github.sirionero.mycar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

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
        }.onFailure {
            MyCarLog.w("session: couldn't keep the drive in the foreground - ${it.message}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "mycar_session"
        private const val NOTIFICATION_ID = 45

        @Volatile var running = false
            private set

        /** A car connected: called while myCar is on screen, as its arrival opens it. */
        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, SessionService::class.java)) }
                .onFailure { MyCarLog.w("session: Android won't start the drive's foreground service now - ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, SessionService::class.java)) }
        }
    }
}
