package io.github.sirionero.mycar

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.sirionero.mycar.audio.AudioCaptureService
import io.github.sirionero.mycar.audio.CarAudio
import io.github.sirionero.mycar.car.CarEnvironment
import io.github.sirionero.mycar.car.CarSettings
import io.github.sirionero.mycar.car.CarUi
import java.io.File
import java.io.FileOutputStream

/**
 * Debug-only harness: runs the real car UI on a virtual display (the same path a head unit
 * gets), taps where told, and saves what it rendered as PNGs in the external files dir.
 *
 *   adb shell am start -n io.github.sirionero.mycar/.CarPreviewActivity \
 *       --ei w 800 --ei h 480 --ei dpi 160 --es taps "400,440;90,440"
 *
 * Writes preview-0.png (before any tap), then one more per tap. Add --ez skipIntro true to go
 * straight to the car UI, past the safety card and the what's-new card, or --ez player true for
 * the docked player alone, playing a made-up song.
 */
class CarPreviewActivity : ComponentActivity() {

    private val main = Handler(Looper.getMainLooper())
    private var projector: ScreenProjector? = null
    private var reader: ImageReader? = null
    @Volatile private var latest: Bitmap? = null

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) askAudioCapture() else CarAudio.denied()
    }

    private val audioConsent = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            AudioCaptureService.start(this, result.resultCode, data)
        } else {
            MyCarLog.w("preview: audio capture consent declined")
            CarAudio.denied()
        }
    }

    private fun askAudioCapture() {
        val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        runCatching { audioConsent.launch(manager.createScreenCaptureIntent()) }
            .onFailure { MyCarLog.e("preview: could not ask for audio capture", it); CarAudio.denied() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MyCarLog.init(this)
        // --ez demoSong true: no music app to bring back, or an earlier shot's would reopen on Home
        // and take the made-up song's place. Cleared before the settings are read.
        if (intent.getBooleanExtra("demoSong", false)) io.github.sirionero.mycar.AppSettings.putString(this, "car_last_media", "")
        CarSettings.init(this)
        if (intent.getBooleanExtra("skipIntro", false)) {
            CarSettings.markSafetyWarningSeen()
            Changelog.current?.let { CarSettings.closeWhatsNew(it.versionCode) }
        }
        // --es assistant phone: the dock's microphone wakes that assistant. Set every run, so
        // other shots keep myCar's own.
        io.github.sirionero.mycar.car.AssistantChoice.set(
            intent.getStringExtra("assistant") ?: io.github.sirionero.mycar.car.PhoneAssistants.MYCAR,
        )
        // --es sidePane it.plancia.ha: that app beside the map on Home - its car app service, or
        // its music service. Set every run, so other shots keep the music player there.
        val sidePane = intent.getStringExtra("sidePane")?.let { pkg ->
            listOf(androidx.car.app.CarAppService.SERVICE_INTERFACE, android.service.media.MediaBrowserService.SERVICE_INTERFACE)
                .firstNotNullOfOrNull { action ->
                    packageManager.queryIntentServices(android.content.Intent(action).setPackage(pkg), 0).firstOrNull()
                }?.serviceInfo?.let { android.content.ComponentName(it.packageName, it.name).flattenToString() }
        }
        io.github.sirionero.mycar.car.SidePaneChoice.set(sidePane ?: io.github.sirionero.mycar.car.SidePaneChoice.MUSIC)
        // Home's ready-made pages every run, so a shot that changed one leaves the next alone.
        io.github.sirionero.mycar.car.HomePages.reset()
        // --es wallpaper waves: that wallpaper. Set every run, so other shots keep the default.
        io.github.sirionero.mycar.car.design.WallpaperSetting.set(
            intent.getStringExtra("wallpaper") ?: io.github.sirionero.mycar.car.design.Wallpapers.DEFAULT,
        )
        val w = intent.getIntExtra("w", 800)
        val h = intent.getIntExtra("h", 480)
        val dpi = intent.getIntExtra("dpi", 160)
        // As a car session does: the screen's size and density, which car apps' surfaces get too.
        CarEnvironment.setDisplay(w, h, dpi)
        CarEnvironment.start(this)

        val taps = intent.getStringExtra("taps").orEmpty().split(";").filter { it.isNotBlank() }
            .map { it.split(",").let { p -> p[0].toFloat() to p[1].toFloat() } }

        val imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        reader = imageReader
        imageReader.setOnImageAvailableListener({ r ->
            r.acquireLatestImage()?.use { image ->
                val plane = image.planes[0]
                val padded = plane.rowStride / plane.pixelStride
                val bmp = Bitmap.createBitmap(padded, h, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(plane.buffer)
                latest = Bitmap.createBitmap(bmp, 0, 0, w, h)
            }
        }, main)

        val p = ScreenProjector(this).also { projector = it }
        // --ez player true: the docked player alone, with a made-up song (see PlayerPreview).
        // --ez wallpapers true: the wallpaper picker alone, below the fold in the car's settings.
        val playerOnly = intent.getBooleanExtra("player", false)
        val wallpapersOnly = intent.getBooleanExtra("wallpapers", false)
        p.start(imageReader.surface, w, h, dpi) {
            when {
                playerOnly -> io.github.sirionero.mycar.car.PlayerPreview()
                wallpapersOnly -> io.github.sirionero.mycar.car.WallpaperPickerPreview()
                else -> CarUi()
            }
        }

        // Stand in for the car's media sink, so the phone half of the audio path runs for real.
        if (intent.getBooleanExtra("audio", false)) {
            CarAudio.onConsentRequested = {
                runOnUiThread {
                    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        askAudioCapture()
                    } else {
                        audioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                }
            }
            CarAudio.attach(BenchAudioSink())
        }

        // --ez demoSong true: a made-up song "plays", once CarUi has started the car's services, so
        // Home shows the player beside the real map (see showDemoSong).
        if (intent.getBooleanExtra("demoSong", false)) {
            var tries = 0
            fun tryDemo() {
                if (io.github.sirionero.mycar.car.showDemoSong()) MyCarLog.i("preview: demo song playing")
                else if (++tries < 50) main.postDelayed(::tryDemo, 100)
                else MyCarLog.e("preview: no media to show the demo song in")
            }
            main.post(::tryDemo)
        }
        MyCarLog.i("preview: displayId=${p.displayId}")
        if (intent.getBooleanExtra("hide", false)) p.setOverlayVisible(false)

        intent.getStringExtra("launch")?.let { pkg ->
            main.postDelayed({
                packageManager.getLaunchIntentForPackage(pkg)?.let { p.launchApp(it) }
                    ?: MyCarLog.e("preview: no launch intent for $pkg")
            }, 1500)
        }

        var step = 0
        fun snap() {
            val bmp = latest ?: return MyCarLog.e("preview: no frame yet for step $step")
            val out = File(getExternalFilesDir(null), "preview-$step.png")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            MyCarLog.i("preview: wrote ${out.name}")
            step++
        }
        // A two-finger pinch: "cx,cy,startGap,endGap" in projected pixels, after "pinchAfter" ms.
        intent.getStringExtra("pinch")?.let { spec ->
            val (cx, cy, gap0, gap1) = spec.split(",").map { it.toFloat() }
            val pair = { gap: Float -> listOf(TouchPoint(3, cx - gap / 2, cy), TouchPoint(7, cx + gap / 2, cy)) }
            val after = intent.getIntExtra("pinchAfter", 9000).toLong()
            main.postDelayed({
                snap()
                p.dispatchTouch(MotionEvent.ACTION_DOWN, 0, listOf(pair(gap0)[0]))
                p.dispatchTouch(MotionEvent.ACTION_POINTER_DOWN, 1, pair(gap0))
                val steps = 14
                for (i in 1..steps) main.postDelayed({
                    p.dispatchTouch(MotionEvent.ACTION_MOVE, 0, pair(gap0 + (gap1 - gap0) * i / steps))
                }, 40L * i)
                main.postDelayed({
                    p.dispatchTouch(MotionEvent.ACTION_POINTER_UP, 1, pair(gap1))
                    p.dispatchTouch(MotionEvent.ACTION_UP, 0, listOf(pair(gap1)[0]))
                }, 40L * (steps + 1))
                main.postDelayed({ snap() }, 40L * (steps + 1) + 2000)
            }, after)
        }

        // Free-running mode: keep snapshotting on a timer so the display can be driven from
        // outside (adb `am start --display` / `input -d`) while the harness records it.
        val snaps = intent.getIntExtra("snaps", 0)
        if (snaps > 0) {
            val every = intent.getIntExtra("every", 3000).toLong()
            repeat(snaps) { i -> main.postDelayed({ snap() }, 2000L + every * i) }
            return
        }

        main.postDelayed({
            snap()
            taps.forEachIndexed { i, (x, y) ->
                main.postDelayed({
                    val point = listOf(TouchPoint(0, x, y))
                    p.dispatchTouch(MotionEvent.ACTION_DOWN, 0, point)
                    main.postDelayed({ p.dispatchTouch(MotionEvent.ACTION_UP, 0, point) }, 80)
                }, 2500L * (i + 1))
                main.postDelayed({ snap() }, 2500L * (i + 1) + 1500)
            }
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        projector?.stop()
        reader?.close()
        CarAudio.onConsentRequested = null
        CarAudio.release()
        CarEnvironment.stop()
    }
}
