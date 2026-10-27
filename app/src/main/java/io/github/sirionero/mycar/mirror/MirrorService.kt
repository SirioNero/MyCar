package io.github.sirionero.mycar.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.sirionero.mycar.MyCarLog

/**
 * Holds the screen-capture session for [PhoneMirror].
 *
 * This exists because of an ordering rule: since Android 10 a MediaProjection may only be
 * obtained while a foreground service declaring `mediaProjection` is already running, so the
 * consent result has to be carried into a service before it can be redeemed for a token.
 * Its notification doubles as the driver's way out - stopping capture from the shade works
 * even when the car screen is showing a mirrored app rather than anything of ours.
 */
class MirrorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        if (data == null) {
            MyCarLog.e("mirror service started without a consent result")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID, notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )

        val media: MediaProjection? = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            MyCarLog.e("getMediaProjection failed", t)
            null
        }
        if (media == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // The session owns the encoder Surface, so it performs the swap once a token exists.
        Handler(Looper.getMainLooper()).post { PhoneMirror.onTokenReady?.invoke(media) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        started = false
        Handler(Looper.getMainLooper()).post {
            PhoneMirror.end()
            PhoneMirror.onEnded?.invoke()
        }
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Phone mirroring", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Mirroring to the car")
            .setContentText("The car screen is showing this phone.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "mycar_mirror"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "io.github.sirionero.mycar.STOP_MIRROR"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"

        private var appContext: Context? = null

        /** From [start] until the service ends, so [stop] knows there's something to stop. */
        @Volatile private var started = false

        /** Redeems a screen-capture consent result for a running capture session. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            appContext = context.applicationContext
            started = true
            context.startForegroundService(
                Intent(context, MirrorService::class.java)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, data)
            )
        }

        fun stop() {
            // Starting the service only to stop it would end a mirror that isn't there, and from the
            // background (Android 12 on) it's refused with an exception that would close myCar.
            if (!started) return
            val context = appContext ?: return
            runCatching { context.startService(Intent(context, MirrorService::class.java).setAction(ACTION_STOP)) }
                .onFailure { MyCarLog.w("mirror: could not stop the mirror service: $it") }
        }
    }
}
