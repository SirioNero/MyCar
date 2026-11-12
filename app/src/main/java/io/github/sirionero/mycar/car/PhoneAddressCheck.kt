package io.github.sirionero.mycar.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarToast
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.CarFocus
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words
import io.github.sirionero.mycar.audio.AudioRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * At each drive with a car already paired, whether telling it the phone's Bluetooth address takes its
 * Bluetooth music away ([PhoneAddressGuard]): a look at the car's Bluetooth connections a while after
 * the car showed myCar. When it does, CarBluetooth stops telling that car, and the driver hears why:
 * on the phone's Home until OK (PhoneAddressCard), and on the car's screen once it's stopped. Without
 * an address written nothing is told, and the looks only learn which cars take the phone's music
 * over Bluetooth.
 */
internal object PhoneAddressCheck {

    private val log = MyCarLog.tagged("bluetooth")
    private val main = Handler(Looper.getMainLooper())
    /** Tags this drive's looks, so the drive's end cancels them and nothing else. */
    private val looking = Any()
    private var appContext: Context? = null
    /** This drive's part of the rule; null between drives. */
    private var guard: PhoneAddressGuard? = null
    /** Counts drives: an answer from Bluetooth that comes after its drive ended is dropped. */
    private var drive = 0
    /** This drive reaches the car over Wi-Fi, not the cable: what a car does without the address is kept for each. */
    private var wifi = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** The car shows myCar, over Wi-Fi or not. Called again whenever the video restarts, so it arms once per drive. */
    fun onProjecting(wireless: Boolean) = main.post {
        if (guard != null) return@post
        guard = PhoneAddressGuard()
        wifi = wireless
        val at = drive
        // One of the two does something, as the car was told the address or not.
        main.postDelayed({ look(at, told = true) }, looking, PhoneAddressGuard.LOOK_TOLD_MS)
        main.postDelayed({ look(at, told = false) }, looking, PhoneAddressGuard.LOOK_UNTOLD_MS)
    }

    fun onCarDisconnected() = main.post {
        guard = null
        drive++
        main.removeCallbacksAndMessages(looking)
    }

    /** The look of a drive where the car was [told] the address, or not; nothing for the other kind. */
    private fun look(at: Int, told: Boolean) {
        if (at != drive) return
        val car = CarBluetooth.pairedCarAddress ?: return
        if (CarBluetooth.toldAddress != told) return
        val projected = CarFocus.projected.value
        // Not told, with the car on its own screen: the rule puts the look off, Bluetooth needn't be asked.
        if (!told && !projected) return decide(at, car, told, music = false, calls = false, projected = false)
        connected(BluetoothProfile.A2DP, car) { music ->
            connected(BluetoothProfile.HEADSET, car) { calls ->
                if (at == drive && music != null && calls != null) decide(at, car, told, music, calls, projected)
            }
        }
    }

    private fun decide(at: Int, car: String, told: Boolean, music: Boolean, calls: Boolean, projected: Boolean) {
        val rule = guard ?: return
        val musicWithout = CarBluetooth.tookMusicWithout(car, wifi)
        // The drive's route, as the car's next drive will have it too: through myCar, nothing to give back.
        val route = CarSettings.audioRoute.value
        val verdict = rule.look(
            told, musicWithout, music, calls, inCall(),
            projected = projected,
            bluetoothRoute = route != AudioRoute.CAR_LINK,
        )
        if (told && verdict != PhoneAddressGuard.Verdict.LATER) {
            log.i(
                "phone's address told - the car now: Bluetooth music ${if (music) "connected" else "not connected"}, " +
                    "calls ${if (calls) "connected" else "not connected"}; " +
                    (if (musicWithout) "it took the music without the address before" else "never seen taking the music without it") +
                    " over ${link()}; sound route ${route.id}",
            )
        }
        when (verdict) {
            PhoneAddressGuard.Verdict.LATER -> main.postDelayed({ look(at, told) }, looking, PhoneAddressGuard.AGAIN_MS)
            PhoneAddressGuard.Verdict.NOTHING -> Unit
            PhoneAddressGuard.Verdict.MUSIC_WITHOUT -> if (CarBluetooth.rememberMusicWithout(car, wifi)) {
                log.i("this car takes the phone's music over Bluetooth without the phone's address over ${link()} - remembered")
            }
            PhoneAddressGuard.Verdict.STOP_TELLING -> stopTelling(car)
        }
    }

    private fun link() = if (wifi) "Wi-Fi" else "the cable"

    private fun stopTelling(car: String) {
        val context = appContext ?: return
        // Taken out meanwhile in Settings: no car is told anything any more.
        if (CarBluetooth.writtenAddress(context) == null) return
        CarBluetooth.stopTelling(car)
        log.i("told the phone's address, this car stopped taking the phone's music over Bluetooth - not telling it again: from the next drive it can take it over Bluetooth again")
        AppSettings.putString(context, KEY_NOTICE, "true")
        noticeLoaded = true
        noticeFlow.value = true
        tellCarWhenStopped(drive)
    }

    /** The car's screen says it too, once the car is stopped: a glance at a red light, not one at speed. */
    private fun tellCarWhenStopped(at: Int) {
        if (at != drive) return
        if (CarDriving.videoAllowed) {
            CarToasts.show(this, Words.get(R.string.car_phone_address_not_told), CarToast.LENGTH_LONG)
        } else {
            main.postDelayed({ tellCarWhenStopped(at) }, looking, STOPPED_LOOK_MS)
        }
    }

    /**
     * Whether [car] is connected to the phone for [profile] (A2DP for music, HEADSET for calls),
     * given on the main thread; null when there's no telling. Bluetooth off: nothing is connected.
     */
    @SuppressLint("MissingPermission")
    private fun connected(profile: Int, car: String, answer: (Boolean?) -> Unit) {
        val context = appContext ?: return answer(null)
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return answer(null)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return answer(null)
        if (!adapter.isEnabled) return answer(false)
        val asked = runCatching {
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val devices = runCatching { proxy.connectedDevices }.getOrNull()
                    runCatching { adapter.closeProfileProxy(profile, proxy) }
                    val ok = devices?.any { it.address.equals(car, ignoreCase = true) }
                    main.post { answer(ok) }
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, profile)
        }.getOrDefault(false)
        if (!asked) answer(null)
    }

    /** A call on, ringing or in communication, as CarAudioRoute counts it. */
    private fun inCall(): Boolean = when (appContext?.getSystemService(AudioManager::class.java)?.mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
        else -> false
    }

    private val noticeFlow = MutableStateFlow(false)
    private val noticeView = noticeFlow.asStateFlow()
    @Volatile private var noticeLoaded = false

    /** myCar stopped telling a car the phone's address, and the driver hasn't tapped OK on the phone yet. */
    fun notice(context: Context): StateFlow<Boolean> {
        if (!noticeLoaded) {
            noticeLoaded = true
            noticeFlow.value = AppSettings.getString(context, KEY_NOTICE, "") == "true"
        }
        return noticeView
    }

    /** OK on the phone, or the address saved again: the notice goes. */
    fun noticeSeen(context: Context) {
        noticeLoaded = true
        noticeFlow.value = false
        AppSettings.putString(context, KEY_NOTICE, "")
    }

    private const val KEY_NOTICE = "phone_address_not_told_notice"

    /** While the car moves, how often it's looked at again for a stop. */
    private const val STOPPED_LOOK_MS = 5_000L
}
