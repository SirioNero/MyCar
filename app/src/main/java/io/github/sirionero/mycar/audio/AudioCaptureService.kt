package io.github.sirionero.mycar.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.os.Process
import io.github.sirionero.mycar.MyCarLog
import kotlin.math.abs

/**
 * Hears one app's audio so it can be played on the car.
 *
 * Playback capture is the one public route to another app's sound, and it hangs off a
 * MediaProjection - so, as with [io.github.sirionero.mycar.mirror.MirrorService], the consent
 * result is carried into a foreground service of type mediaProjection before it can be redeemed.
 * Capture is scoped to media playback: notification pings, calls and alarms stay on the phone.
 * The grant lasts as long as this service does, which is as long as the car is connected, so the
 * driver answers the consent dialog once per drive rather than once per app.
 */
class AudioCaptureService : Service() {

    @Volatile private var reading = false
    private var projection: MediaProjection? = null
    private var thread: Thread? = null
    private var mutedPhone = false

    /**
     * Silence the phone's own speaker while the car is being fed.
     *
     * Playback capture copies the stream instead of moving it, so without this the music plays in
     * two places at once. Muting the stream does not affect what is captured - the tap sits before
     * the volume the speaker gets - so the car still receives full-level audio.
     */
    private fun mutePhone() {
        if (mutedPhone) return
        runCatching {
            getSystemService(android.media.AudioManager::class.java)
                .adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_MUTE, 0)
            mutedPhone = true
            // Remembered past this process: killed mid-drive, nothing here runs to unmute, and the
            // next start does it instead (see restorePhoneIfLeftMuted).
            io.github.sirionero.mycar.AppSettings.putString(this, KEY_LEFT_MUTED, "true")
            MyCarLog.i("audio: muted the phone's speaker for the drive")
        }.onFailure { MyCarLog.w("audio: could not mute the phone: ${it.message}") }
    }

    private fun unmutePhone() {
        if (!mutedPhone) return
        mutedPhone = false
        unmute(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Which capture is the current one, so an old capture's end doesn't stop a new one. */
    @Volatile private var captureRun = 0

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // While still in the foreground: Android 17 ignores volume calls from an app that
            // isn't, and the phone stayed muted after the drive.
            unmutePhone()
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let { androidx.core.content.IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        if (data == null) {
            MyCarLog.e("audio: started without a consent result")
            CarAudio.denied()
            stopSelf()
            return START_NOT_STICKY
        }

        // Android silences the microphone for apps in the background unless a running service
        // claims it. Claiming it here lets voice replies hear the driver with the phone locked.
        // Without the permission, claiming it would crash, so it's left off.
        val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
            (if (micGranted) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        startForeground(NOTIFICATION_ID, notification(), type)
        ScreenShareGuard.lift(this)
        // Asked again while a capture was still here (the last one's reading had ended): let it go
        // first. Android stops it anyway for the new one, and its end used to stop this service,
        // the new capture with it.
        projection?.let { old ->
            reading = false
            runCatching { thread?.join(500) }
            projection = null
            runCatching { old.stop() }
        }
        projection = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            MyCarLog.e("audio: getMediaProjection failed", t)
            null
        }
        val token = projection
        if (token == null) {
            CarAudio.denied()
            stopSelf()
            return START_NOT_STICKY
        }
        token.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                if (token !== projection) return
                MyCarLog.w("audio: projection stopped")
                unmutePhone()
                stopSelf()
            }
        }, null)
        beginCapture(token)
        return START_NOT_STICKY
    }

    private fun beginCapture(token: MediaProjection) {
        val rate = CarAudio.sampleRate
        val channels = CarAudio.channels
        val record = try {
            // Matched by usage, not by app. A uid filter would have to be rebuilt every time the
            // driver changed media app, and rebuilding means a fresh consent dialog - so the
            // filter is what the sound is for, and any app playing media reaches the car.
            val config = AudioPlaybackCaptureConfiguration.Builder(token)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val mask = if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(mask)
                .build()
            val minimum = AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_16BIT)
            AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minimum, CHUNK_BYTES * BUFFER_CHUNKS))
                .build()
        } catch (t: Throwable) {
            MyCarLog.e("audio: could not set up playback capture", t)
            CarAudio.denied()
            stopSelf()
            return
        }

        reading = true
        val run = ++captureRun
        thread = Thread({
            // A busy phone (a map app drawing, the video encoder) would otherwise starve this
            // loop, and the car hears every late read as a skip.
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ByteArray(CHUNK_BYTES)
            var peak = 0
            var heardAt = System.currentTimeMillis()
            val startedAt = heardAt
            var everHeard = false
            var warnedSilent = false
            try {
                record.startRecording()
                MyCarLog.i("audio: capturing media playback at ${rate}Hz x$channels")
                mutePhone()
                CarAudio.captureStarted()
                while (reading) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) {
                        MyCarLog.e("audio: AudioRecord.read returned $n - capture has stopped")
                        break
                    }
                    for (i in 0 until n - 1 step 2) {
                        val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                        peak = maxOf(peak, abs(sample.toShort().toInt()))
                    }
                    if (peak > 0) everHeard = true
                    val now = System.currentTimeMillis()
                    val level = peak / 32768f
                    if (now - heardAt >= LEVEL_LOG_MS) {
                        MyCarLog.i("audio: peak %.3f".format(level))
                        heardAt = now
                        peak = 0
                    }
                    // Capture never fails for an app that opts out; it just hands back digital
                    // silence. Say so, because the symptom is identical to a broken car link.
                    if (!everHeard && !warnedSilent && now - startedAt >= SILENCE_MS) {
                        warnedSilent = true
                        MyCarLog.w(
                            "audio: ${SILENCE_MS / 1000}s of capture and every sample is zero. " +
                                "Either nothing is playing, or the app does not allow its audio " +
                                "to be captured.",
                        )
                    }
                    CarAudio.deliver(buffer, n, level)
                }
            } catch (t: Throwable) {
                MyCarLog.e("audio: capture loop ended", t)
            } finally {
                runCatching { record.stop() }
                record.release()
                unmutePhone()
                CarAudio.captureStopped()
                // Ended on its own (the audio server restarted): the service goes too, so the next
                // question starts clean instead of finding this one's projection still here.
                if (reading && run == captureRun) main.post { if (run == captureRun) stopSelf() }
            }
        }, "mycar-audio").apply { isDaemon = true; start() }
    }

    override fun onDestroy() {
        super.onDestroy()
        reading = false
        runCatching { thread?.join(500) }
        // Belt and braces: a phone left muted after the drive is a bug the driver blames on their
        // phone, not on myCar, so unmute on every way out of here.
        unmutePhone()
        runCatching { projection?.stop() }
        projection = null
        ScreenShareGuard.restore(this)
        running = false
        started = false
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Car audio", NotificationManager.IMPORTANCE_LOW),
        )
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, AudioCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Playing to the car")
            .setContentText("myCar is sending the phone's audio to the car.")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "mycar_audio"
        private const val NOTIFICATION_ID = 43
        private const val ACTION_STOP = "io.github.sirionero.mycar.STOP_AUDIO"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"

        /** About 21 ms of 48 kHz stereo: small enough for lip-sync, big enough not to flood the link. */
        private const val CHUNK_BYTES = 4096
        /** About half a second: room to wait out a slow ack from the car without losing sound. */
        private const val BUFFER_CHUNKS = 24
        private const val LEVEL_LOG_MS = 3_000L
        private const val SILENCE_MS = 5_000L

        private var appContext: Context? = null

        private const val KEY_LEFT_MUTED = "audio_phone_left_muted"

        private fun unmute(context: Context) {
            runCatching {
                val audio = context.getSystemService(android.media.AudioManager::class.java)
                audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
                // Android 17 can ignore the call without a word: the flag stays, for the next try.
                if (audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC)) {
                    MyCarLog.w("audio: the phone's speaker is still muted - trying again when myCar opens")
                } else {
                    io.github.sirionero.mycar.AppSettings.putString(context, KEY_LEFT_MUTED, "false")
                    MyCarLog.i("audio: gave the phone's speaker back")
                }
            }.onFailure { MyCarLog.w("audio: could not unmute the phone: ${it.message}") }
        }

        /**
         * A capture that ended without its service shutting down - myCar closed by a crash, or by
         * Android - left the phone's music muted. Gives it back at the next start.
         */
        fun restorePhoneIfLeftMuted(context: Context) {
            if (running) return
            if (io.github.sirionero.mycar.AppSettings.getString(context, KEY_LEFT_MUTED, "false") != "true") return
            MyCarLog.i("audio: the phone was left muted by an earlier drive")
            unmute(context.applicationContext)
        }

        /** True between onCreate and onDestroy; lets the activity tell a live capture from a crashed one. */
        @Volatile var running = false
            private set

        /** From [start] until the service ends, so [stop] knows there's something to stop. */
        @Volatile private var started = false

        fun start(context: Context, resultCode: Int, data: Intent) {
            appContext = context.applicationContext
            started = true
            context.startForegroundService(
                Intent(context, AudioCaptureService::class.java)
                    .putExtra(EXTRA_RESULT_CODE, resultCode)
                    .putExtra(EXTRA_RESULT_DATA, data),
            )
        }

        fun stop() {
            // Starting the service only to stop it, when it never ran, is refused while myCar is in
            // the background (Android 12 on) - as at the end of a drive with the phone locked - and
            // the refusal is an exception that would close myCar.
            if (!started) return
            val context = appContext ?: return
            runCatching { context.startService(Intent(context, AudioCaptureService::class.java).setAction(ACTION_STOP)) }
                .onFailure { MyCarLog.w("audio: could not stop the capture service: $it") }
        }
    }
}
