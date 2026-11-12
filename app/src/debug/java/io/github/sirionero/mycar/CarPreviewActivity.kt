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
import io.github.sirionero.mycar.car.withLayout
import io.github.sirionero.mycar.car.withSlot

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
        // How the shot before ended, as myCar's own screen writes it at the top of a drive's log.
        LastExit.log(this)
        // --ei crashAfterMs 6000: myCar's process takes a SIGSEGV that long in, a native crash with a
        // tombstone, for the next shot's "last exit" lines (the last-exit shots).
        intent.getIntExtra("crashAfterMs", -1).takeIf { it >= 0 }?.let { ms ->
            main.postDelayed({
                MyCarLog.w("preview: a native crash on purpose")
                MyCarLog.flush()
                android.os.Process.sendSignal(android.os.Process.myPid(), 11)
            }, ms.toLong())
        }
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
            // One of myCar's own (mycar:plancia) as it is.
            if (pkg.startsWith(io.github.sirionero.mycar.car.BUILT_IN_PREFIX)) return@let pkg
            listOf(androidx.car.app.CarAppService.SERVICE_INTERFACE, android.service.media.MediaBrowserService.SERVICE_INTERFACE)
                .firstNotNullOfOrNull { action ->
                    packageManager.queryIntentServices(android.content.Intent(action).setPackage(pkg), 0).firstOrNull()
                }?.serviceInfo?.let { android.content.ComponentName(it.packageName, it.name).flattenToString() }
        }
        io.github.sirionero.mycar.car.SidePaneChoice.set(sidePane ?: io.github.sirionero.mycar.car.SidePaneChoice.MUSIC)
        // Home's ready-made pages every run, so a shot that changed one leaves the next alone.
        io.github.sirionero.mycar.car.HomePages.reset()
        // Cinema's "Continue" would come first and move the tiles the shots tap.
        io.github.sirionero.mycar.car.CinemaContinue.reset(this)
        // --es pageLayout <id>: the page left of the map in that layout (the weather, the agenda, then
        // empty parts), with an empty page right of it as Plancia's would be, for the layout shots.
        intent.getStringExtra("pageLayout")?.let { io.github.sirionero.mycar.car.PageLayout.of(it) }?.let { layout ->
            val pages = io.github.sirionero.mycar.car.readyMade(null) +
                io.github.sirionero.mycar.car.HomePage(2, io.github.sirionero.mycar.car.PageLayout.HALVES)
            var laid = pages.withLayout(1, layout)
            // --es pageParts plancia,agenda,weather: what that page shows, part by part (weather, agenda,
            // browser, plancia; anything else leaves the part empty): the plancia-swipe shots.
            intent.getStringExtra("pageParts")?.split(',')?.forEachIndexed { slot, name ->
                val content = when (name.trim()) {
                    "weather" -> io.github.sirionero.mycar.car.PageContent.WEATHER
                    "agenda" -> io.github.sirionero.mycar.car.PageContent.AGENDA
                    "browser" -> io.github.sirionero.mycar.car.PageContent.BROWSER
                    "plancia" -> io.github.sirionero.mycar.car.PageContent.PLANCIA
                    // "app:it.plancia.ha": that car app's service (the crash-grid-app shot).
                    else -> name.trim().takeIf { it.startsWith("app:") }?.let { app ->
                        packageManager.queryIntentServices(
                            android.content.Intent(androidx.car.app.CarAppService.SERVICE_INTERFACE).setPackage(app.removePrefix("app:")), 0,
                        ).firstOrNull()?.serviceInfo?.let { android.content.ComponentName(it.packageName, it.name).flattenToString() }
                    }
                }
                laid = laid.withSlot(1, slot, content)
            }
            io.github.sirionero.mycar.car.HomePages.save(laid)
        }
        // --ez bridge true: Plancia's and Xonos's connections through the Plancia and Xonos apps'
        // bridges, as with myCar left out of Tailscale - the emulator has no Tailscale. Every run.
        io.github.sirionero.mycar.bridge.Bridges.init(this)
        io.github.sirionero.mycar.bridge.Bridges.forced = intent.getBooleanExtra("bridge", false)
        // --es haUrl http://10.0.2.2:8123 --es haToken ...: Plancia inside myCar on that Home
        // Assistant (the workflow's own, in Docker beside the emulator). Set every run: without
        // them Plancia is off, and the Plancia app stays as it was.
        io.github.sirionero.mycar.home.HomeAssistant.save(
            intent.getStringExtra("haUrl")?.let { url ->
                io.github.sirionero.mycar.home.HomeAssistant.Config(url, "", intent.getStringExtra("haToken").orEmpty())
            } ?: io.github.sirionero.mycar.home.HomeAssistant.Config(),
        )
        // --es maUrl http://10.0.2.2:8095 --es maToken ...: Xonos inside myCar on that Music
        // Assistant (the workflow's own, in Docker beside the emulator). Set every run: without
        // them Xonos is off in myCar, and the Xonos app stays as it was.
        io.github.sirionero.mycar.xonos.Xonos.save(
            this,
            io.github.sirionero.mycar.xonos.data.Settings.Config(
                intent.getStringExtra("maUrl").orEmpty(),
                "",
                intent.getStringExtra("maToken").orEmpty(),
            ),
            null,
        )
        // --ez moving true: the car says it's moving, so Cinema keeps its video for when it stops
        // ("Video only when parked"). Set every run, so other shots ask the car, or the GPS.
        io.github.sirionero.mycar.car.CarDriving.override = if (intent.getBooleanExtra("moving", false)) false else null
        // --es tvList http://10.0.2.2:8001/tv.m3u: Cinema's TV with that list alone, named "TV". Set
        // every run, so other shots have none and Cinema's tiles stay where they were.
        io.github.sirionero.mycar.cinema.TvLists.lists.value.forEach { io.github.sirionero.mycar.cinema.TvLists.remove(it) }
        intent.getStringExtra("tvList")?.let { io.github.sirionero.mycar.cinema.TvLists.add(io.github.sirionero.mycar.cinema.TvList("TV", it)) }
        // --es startUrl http://10.0.2.2:8002/select.html: the car's browser starts on that page. Set
        // every run, so other shots start on the usual one.
        io.github.sirionero.mycar.AppSettings.setStartupUrl(this, intent.getStringExtra("startUrl").orEmpty())
        // Xonos on in the car every run (Music, on the phone): the shots tap it where it is on the grid.
        io.github.sirionero.mycar.xonos.Xonos.setEnabled(this, true)
        // --es playerSize 1.2: the player beside the map at that size (Medium 1.1, Large 1.2); Small
        // every other run.
        io.github.sirionero.mycar.car.CarSettings.setPlayerSize(
            intent.getStringExtra("playerSize")?.toFloatOrNull()?.takeIf { it in io.github.sirionero.mycar.car.CarSettings.PLAYER_SIZES }
                ?: io.github.sirionero.mycar.car.CarSettings.PLAYER_SMALL,
        )
        // --es carScale 1.25: the car's screen at that size (Large 1.25, Extra large 1.5); Normal every other run.
        io.github.sirionero.mycar.car.CarSettings.setScale(
            intent.getStringExtra("carScale")?.toFloatOrNull()?.takeIf { it in 0.8f..1.5f } ?: 1f,
        )
        // --es miniAt 0.5,0: the mini player over the map starts there (0,0 top left, 1,1 bottom right);
        // where it starts unmoved every other run. --ez miniPlayer false: off. Set every run.
        io.github.sirionero.mycar.car.MiniPlayerSetting.setOn(intent.getBooleanExtra("miniPlayer", true))
        io.github.sirionero.mycar.car.MiniPlayerSetting.moveTo(
            io.github.sirionero.mycar.car.MiniPlace.parse(intent.getStringExtra("miniAt").orEmpty()),
        )
        // --es pillAt 0.5,0: a call's pill starts there (0,0 top left, 1,1 bottom right); where it starts
        // unmoved every other run, as a shot that dragged it leaves it where it was let go.
        io.github.sirionero.mycar.car.CallPillSetting.moveTo(
            io.github.sirionero.mycar.car.MiniPlace.parse(intent.getStringExtra("pillAt").orEmpty()),
        )
        // --es videoCover http://10.0.2.2:8003/canvas.mp4: that video as the cover of whatever plays, as if
        // the car's music had video covers (the button on the cover shows); --ez videoCoverOn true: video
        // covers on from the start, still every other run. Both set every run.
        io.github.sirionero.mycar.car.VideoCoverPreview.url = intent.getStringExtra("videoCover")
        io.github.sirionero.mycar.car.VideoCoverSetting.setOn(intent.getBooleanExtra("videoCoverOn", false))
        // --es carTheme LIGHT: the car's screen in that theme (PHONE, LIGHT, DARK); dark every other run.
        CarSettings.setAppTheme(
            intent.getStringExtra("carTheme")?.let { runCatching { io.github.sirionero.mycar.car.AppTheme.valueOf(it) }.getOrNull() }
                ?: io.github.sirionero.mycar.car.AppTheme.DARK,
        )
        // No app hidden from the grid to start with: the shots tap the apps where they always are.
        // --es hideApps mycar:web,mycar:cinema: those off it.
        io.github.sirionero.mycar.car.HiddenApps.reset()
        intent.getStringExtra("hideApps")?.split(',')?.filter { it.isNotBlank() }?.forEach(io.github.sirionero.mycar.car.HiddenApps::hide)
        // --es jolly say: the Jolly key set to a made-up command of that kind (see JollyPreview); nothing
        // every other run, so a tap on it opens its chooser. --ez jollyKey false: off the dock.
        io.github.sirionero.mycar.car.JollyPreview.set(
            intent.getStringExtra("jolly"),
            intent.getStringExtra("jollySay"),
            intent.getStringExtra("jollyLang"),
            intent.getBooleanExtra("jollyKey", true),
            intent.getStringExtra("jollyHome"),
        )
        // --ez webMusicPlay true: a music service's page presses its first "Play" a few seconds in (no
        // account plays here). Set every run.
        io.github.sirionero.mycar.car.WebMusicPreview.play = intent.getBooleanExtra("webMusicPlay", false)
        // --es webMusic spotify: that music service's web player on in the car, alone, and opened as
        // the car's music a moment in, with the car's media key: its page loads as in a drive. Set
        // every run, so other shots have none.
        val webMusic = intent.getStringExtra("webMusic")?.let { io.github.sirionero.mycar.webmusic.WebMusic.byId(it) }
        io.github.sirionero.mycar.webmusic.WebMusic.only(webMusic?.id)
        if (webMusic != null) {
            CarSettings.setLastMedia(io.github.sirionero.mycar.webmusic.WebMusic.mediaApp(this, webMusic).component.flattenToString())
            // --ez webMusicKey false: no media key, so the drive's own start opens it as the car's music,
            // as in a car (the key, if first, opens it as the driver's pick: the map-start shot).
            if (intent.getBooleanExtra("webMusicKey", true)) main.postDelayed({
                io.github.sirionero.mycar.car.CarKeys.onKey(io.github.sirionero.mycar.car.CarKeys.KEYCODE_MEDIA, down = true, longPress = false)
            }, 3_000)
            // --es webMusicSearch "lofi": later, "play lofi" as the voice assistant asks the car's
            // music app for it - the service's search, then the Play of what it finds first.
            intent.getStringExtra("webMusicSearch")?.let { words ->
                main.postDelayed({ io.github.sirionero.mycar.car.CarServices.media.playBestMatch(words) }, 14_000)
            }
            // What myCar's player gets from the page, every 5 s: for the log of the run.
            for (at in 5_000L..60_000L step 5_000L) {
                main.postDelayed({
                    val media = io.github.sirionero.mycar.car.CarServices.media
                    val now = media.now.value
                    io.github.sirionero.mycar.MyCarLog.i(
                        "preview: the car's player: ${media.phase.value}, active ${now.isActive}, playing ${now.playing}, " +
                            "title ${now.title.length} characters, state ${now.state}",
                    )
                }, at)
            }
        }
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

        // "x,y" taps, "x,y,long" holds the finger down long enough for a long press (an app's menu).
        // "x,y" taps, "x,y,long" holds the finger down 0.9 s (an app's menu; a slow tap), "x,y,drag,x2,y2"
        // holds it that long, then drags it to x2,y2 in 10 steps (the mini player over the map);
        // "x,y,swipe,x2,y2" goes to x2,y2 at once, in 8 steps of 16 ms, as a finger turning a page.
        val taps = intent.getStringExtra("taps").orEmpty().split(";").filter { it.isNotBlank() }.map { it.split(",") }

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

        // The crash-* shots: Compose's composition stack on a crash (see CrashProbe), before any composition.
        if (intent.hasExtra("script")) CrashProbe.traceCompositions()
        val p = ScreenProjector(this).also { projector = it }
        // --ez player true: the docked player alone, with a made-up song (see PlayerPreview).
        // --ez wallpapers true: the wallpaper picker alone, below the fold in the car's settings.
        // --ez hiddenApps true: the settings' hidden apps alone, below the fold too.
        val playerOnly = intent.getBooleanExtra("player", false)
        val wallpapersOnly = intent.getBooleanExtra("wallpapers", false)
        val hiddenAppsOnly = intent.getBooleanExtra("hiddenApps", false)
        // --ez jollySettings true: the car settings' Jolly key alone, below the fold in the car's settings.
        val jollySettingsOnly = intent.getBooleanExtra("jollySettings", false)
        p.start(imageReader.surface, w, h, dpi) {
            when {
                playerOnly -> io.github.sirionero.mycar.car.PlayerPreview()
                wallpapersOnly -> io.github.sirionero.mycar.car.WallpaperPickerPreview()
                hiddenAppsOnly -> io.github.sirionero.mycar.car.HiddenAppsPreview()
                jollySettingsOnly -> io.github.sirionero.mycar.car.JollySettingsPreview()
                else -> CarUi()
            }
        }

        // --ez clusterTurns true: the pictures a cluster that wants turns as images gets (issue #23),
        // as cluster-*.png beside the shots (see ClusterPreview).
        if (intent.getBooleanExtra("clusterTurns", false)) {
            val dir = requireNotNull(getExternalFilesDir(null))
            Thread({ io.github.sirionero.mycar.car.ClusterPreview.write(this, dir) }, "cluster-preview").start()
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
            // --ez musicNotReaching true: the music goes through myCar and Android's question was turned
            // down, so the player says the music isn't reaching the car ("Ask again").
            val notReaching = intent.getBooleanExtra("musicNotReaching", false)
            var tries = 0
            fun tryDemo() {
                if (io.github.sirionero.mycar.car.showDemoSong(this)) {
                    MyCarLog.i("preview: demo song playing")
                    if (notReaching) {
                        CarSettings.useCarLink(true)
                        CarAudio.denied()
                        MyCarLog.i("preview: the music through myCar, Android's question turned down")
                    }
                } else if (++tries < 50) main.postDelayed(::tryDemo, 100)
                else MyCarLog.e("preview: no media to show the demo song in")
            }
            main.post(::tryDemo)
        }
        // --ez alert true: an app's alert across the top of the car's screen, as Waze asks whether a
        // hazard is still there (see showDemoAlert), once the car UI is up.
        if (intent.getBooleanExtra("alert", false)) main.postDelayed({ io.github.sirionero.mycar.car.showDemoAlert() }, 1_000)
        // --ez fakeCall true: a call under way, picked up 1:23 ago, with no modem behind it (the call-bubble
        // shots: its pill over every screen), once the car's services watch the phone. --es fakeCaller
        // "<name>": who it's with ("" for a number alone). Set again a moment later: the phone's own first
        // word, idle, can come after it.
        if (intent.getBooleanExtra("fakeCall", false)) {
            val name = (intent.getStringExtra("fakeCaller") ?: "Giulia Bianchi").ifBlank { null }
            // On an emulator up for less than that, as soon as it started: a time before boot is "not known".
            val since = (android.os.SystemClock.elapsedRealtime() - 83_000).coerceAtLeast(1)
            var tries = 0
            fun tryCall() {
                if (io.github.sirionero.mycar.car.showFakeCall(name, "3471112233", since)) {
                    MyCarLog.i("preview: a call under way")
                    main.postDelayed({ io.github.sirionero.mycar.car.showFakeCall(name, "3471112233", since) }, 1_500)
                } else if (++tries < 50) {
                    main.postDelayed(::tryCall, 100)
                } else {
                    MyCarLog.e("preview: the car's services never watched the phone, no call to show")
                }
            }
            main.postDelayed(::tryCall, 500)
        }
        // --es voiceLang it: the assistant in that language, as with its model on the phone (no model
        // runs here: the speech library is arm64 only). --ez voiceHelp true opens its help, --es
        // voiceReply "Around 14:30." shows one of its answers. Set every run, so other shots are in English.
        io.github.sirionero.mycar.speech.VoiceLanguages.override = intent.getStringExtra("voiceLang")
            ?.let { code -> io.github.sirionero.mycar.speech.VoiceLanguage.entries.firstOrNull { it.code == code } }
        if (intent.getBooleanExtra("voiceHelp", false)) {
            main.postDelayed({ io.github.sirionero.mycar.car.CarAssistant.syncLanguage(); io.github.sirionero.mycar.car.VoiceHelp.show() }, 1_000)
        }
        intent.getStringExtra("voiceReply")?.let { english -> main.postDelayed({ io.github.sirionero.mycar.car.CarAssistant.reply(english) }, 1_000) }
        // --es voiceHeard "what's the date today": as if the driver had said it, once Home Assistant
        // (--es haUrl) has had time to connect: what isn't a command goes to its Assist.
        intent.getStringExtra("voiceHeard")?.let { said ->
            main.postDelayed({
                io.github.sirionero.mycar.car.CarAssistant.syncLanguage()
                MyCarLog.i("preview: heard, with Home Assistant ${io.github.sirionero.mycar.home.HomeAssistant.connection.value}")
                io.github.sirionero.mycar.car.CarAssistant.heard(said)
            }, 8_000)
        }
        // --ei locationProbeMs 1000: the drive's hold on the car map's position (LocationKeepAlive) taken
        // that long after this screen was created, as the car's arrival takes it (the location-locked
        // shot, with the phone locked: Android lets it run only within 10 s of a screen being created).
        intent.getIntExtra("locationProbeMs", -1).takeIf { it >= 0 }?.let { ms ->
            MyCarLog.i("preview: location probe in ${ms}ms")
            main.postDelayed({
                MyCarLog.i("preview: location probe now")
                io.github.sirionero.mycar.host.LocationKeepAlive.holdForDrive(this, untilStartUpEnds = true)
            }, ms.toLong())
        }
        MyCarLog.i("preview: displayId=${p.displayId}")
        // --es script "wait:9000;tap:48,116;...": the car's touches and events from a script, with the
        // car's timing (see PreviewScript); --ei seed for its random numbers. The crash-* shots.
        intent.getStringExtra("script")?.let { script ->
            PreviewScript(p, { latest }, requireNotNull(getExternalFilesDir(null)), intent.getIntExtra("seed", 7)) {
                p.start(imageReader.surface, w, h, dpi) { CarUi() }
            }.start(script)
        }
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
            taps.forEachIndexed { i, tap ->
                val x = tap[0].toFloat()
                val y = tap[1].toFloat()
                main.postDelayed({
                    val point = listOf(TouchPoint(0, x, y))
                    p.dispatchTouch(MotionEvent.ACTION_DOWN, 0, point)
                    if (tap.getOrNull(2) == "drag") {
                        val toX = tap[3].toFloat()
                        val toY = tap[4].toFloat()
                        val steps = 10
                        for (s in 1..steps) main.postDelayed({
                            p.dispatchTouch(MotionEvent.ACTION_MOVE, 0, listOf(TouchPoint(0, x + (toX - x) * s / steps, y + (toY - y) * s / steps)))
                        }, 900L + 40L * s)
                        main.postDelayed({ p.dispatchTouch(MotionEvent.ACTION_UP, 0, listOf(TouchPoint(0, toX, toY))) }, 900L + 40L * (steps + 1))
                    } else if (tap.getOrNull(2) == "swipe") {
                        val toX = tap[3].toFloat()
                        val toY = tap[4].toFloat()
                        val steps = 8
                        for (s in 1..steps) main.postDelayed({
                            p.dispatchTouch(MotionEvent.ACTION_MOVE, 0, listOf(TouchPoint(0, x + (toX - x) * s / steps, y + (toY - y) * s / steps)))
                        }, 16L * s)
                        main.postDelayed({ p.dispatchTouch(MotionEvent.ACTION_UP, 0, listOf(TouchPoint(0, toX, toY))) }, 16L * (steps + 1))
                    } else {
                        main.postDelayed({ p.dispatchTouch(MotionEvent.ACTION_UP, 0, point) }, if (tap.getOrNull(2) == "long") 900L else 80L)
                    }
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
