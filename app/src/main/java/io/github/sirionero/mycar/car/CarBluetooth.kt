package io.github.sirionero.mycar.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarToast
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.Protobuf
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.ServiceDiscovery
import io.github.sirionero.mycar.Wire
import io.github.sirionero.mycar.Words
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest

/**
 * Pairs the phone with the car over Bluetooth the first time they meet, so calls (and music, when
 * the driver chose Bluetooth audio) reach the car's speakers. Android Auto does this on its own;
 * myCar asks first, on the car screen.
 *
 * The car names its Bluetooth address in its service list. When the driver taps Pair, myCar
 * opens the car's Bluetooth channel and sends what Android Auto sends:
 *
 *   -> CHANNEL_OPEN_REQUEST                 <- CHANNEL_OPEN_RESPONSE
 *   -> PairingRequest { phone_address, method }
 *   <- PairingResponse { status, already_paired }
 *
 * then starts pairing from the phone. Two things Android Auto has that myCar can't: the
 * phone's own Bluetooth address (apps get a placeholder), and confirming the pairing code without
 * asking. So a car may turn the request down, and the phone always shows its pairing dialog. The
 * channel stays closed unless the driver taps Pair: every new channel is a risk to cars that work.
 *
 * The one exception is a driver who wrote the phone's address in Settings > Connection. Some cars
 * can't tell that the phone connecting is the one they're paired with without it: a 2021 Bolt hung
 * up on Gearslip's cable until it was sent (Gearslip issue #18). With an address written, a car already
 * paired gets its channel opened and the same request at every connection, as Android Auto does,
 * and no pairing is started. Without one, nothing changes. A car that, told the address, stops
 * taking the phone's music over Bluetooth isn't told it again (PhoneAddressCheck).
 */
object CarBluetooth {

    enum class Prompt { NONE, ASK, PAIRING }

    private val _prompt = MutableStateFlow(Prompt.NONE)
    val prompt: StateFlow<Prompt> = _prompt

    private val log = MyCarLog.tagged("bluetooth")
    private val main = Handler(Looper.getMainLooper())
    private var appContext: Context? = null

    /** This session's car: its channel, and how to open and write to it. */
    private class Car(
        val service: ServiceDiscovery.BluetoothService,
        val open: () -> Unit,
        val send: (messageId: Int, body: ByteArray) -> Unit,
    )

    @Volatile private var car: Car? = null
    @Volatile private var bondStarted = false
    /** From the Pair tap until it succeeds or fails, whether or not the card still shows. */
    @Volatile private var pairing = false
    /** Already paired, with the phone's address written: the channel is open only to say which phone this is. */
    @Volatile private var announcing = false
    /** This drive's car was already paired with the phone when it came. */
    @Volatile private var paired = false
    private var receiver: BroadcastReceiver? = null

    /** The car's Bluetooth address, when it named one in its service list. */
    val carAddress: String? get() = car?.service?.carAddress

    /** This drive's car, already paired with the phone when it came: its Bluetooth address. Null otherwise. */
    val pairedCarAddress: String? get() = car?.takeIf { paired }?.service?.carAddress

    /** myCar told this drive's car the phone's address: the PairingRequest went with it. */
    @Volatile var toldAddress = false
        private set

    /** The car's Bluetooth channel once the driver has asked to pair, or while telling the car which phone this is; -1 otherwise. */
    val channelId: Int get() = car?.takeIf { pairing || announcing }?.service?.serviceId ?: -1

    fun init(context: Context) {
        appContext = context.applicationContext
        PhoneAddressCheck.init(context)
    }

    /** Called once the car lists its services. Asks on the car screen only if there's pairing to do. */
    fun onCarConnected(
        service: ServiceDiscovery.BluetoothService,
        open: () -> Unit,
        send: (messageId: Int, body: ByteArray) -> Unit,
    ) {
        car = Car(service, open, send)
        bondStarted = false
        announcing = false
        paired = false
        toldAddress = false
        log.i("the car offers Bluetooth pairing, methods=${service.pairingMethods}")
        val device = device() ?: return
        paired = device.bondState == BluetoothDevice.BOND_BONDED
        when {
            paired && writtenAddress() != null && notTold(service.carAddress) ->
                log.i("already paired with this car - not telling it the phone's address: told it, the car stopped taking the phone's music over Bluetooth")
            paired && writtenAddress() != null -> {
                log.i("already paired with this car - telling it which phone this is, with the address written in Settings")
                announcing = true
                open()
            }
            paired -> log.i("already paired with this car")
            declined(service.carAddress) -> log.i("not paired, and the driver said not now for this car")
            else -> _prompt.value = Prompt.ASK
        }
    }

    fun onCarDisconnected() {
        car = null
        pairing = false
        announcing = false
        paired = false
        toldAddress = false
        main.post {
            stopWatching()
            _prompt.value = Prompt.NONE
        }
    }

    /** The driver tapped Pair. */
    fun pair() {
        val c = car ?: return
        val device = device() ?: return
        pairing = true
        _prompt.value = Prompt.PAIRING
        watchBonding(device)
        c.open()
        log.i("-> ChannelOpenRequest on channel ${c.service.serviceId}")
        // If the car never answers on its channel, pair from the phone anyway.
        main.postDelayed({ startBond("the car didn't answer the pairing request") }, CAR_REPLY_WAIT_MS)
        main.postDelayed({
            if (pairing) finish(Words.get(R.string.bluetooth_pairing_unfinished))
        }, GIVE_UP_MS)
    }

    /** The driver tapped Not now: this car isn't asked about again. */
    fun notNow() {
        car?.service?.carAddress?.let { address ->
            val context = appContext ?: return@let
            val list = AppSettings.getString(context, KEY_DECLINED, "").split(",").filter { it.isNotEmpty() }
            AppSettings.putString(context, KEY_DECLINED, (list + hash(address)).distinct().joinToString(","))
        }
        _prompt.value = Prompt.NONE
    }

    /** Hides the pairing card. The phone's own dialog carries on. */
    fun hide() {
        _prompt.value = Prompt.NONE
    }

    fun onMessage(messageId: Int, body: ByteArray) {
        val c = car ?: return
        when (messageId) {
            MSG_CHANNEL_OPEN_RESPONSE -> {
                val status = Protobuf.readInt32Field(body, 1) ?: 0
                log.i("<- ChannelOpenResponse: status=$status")
                if (status != 0) {
                    if (announcing) log.i("the car wouldn't open its Bluetooth channel")
                    else main.post { startBond("the car wouldn't open its Bluetooth channel") }
                    return
                }
                val method = c.service.pairingMethods.firstOrNull { it == METHOD_NUMERIC_COMPARISON }
                    ?: c.service.pairingMethods.firstOrNull() ?: METHOD_NUMERIC_COMPARISON
                c.send(
                    MSG_PAIRING_REQUEST,
                    Protobuf.stringField(1, phoneAddress()) + Protobuf.varintField(2, method.toLong()),
                )
                log.i("-> PairingRequest(method=$method)")
                // Only now does the car know which phone this is: PhoneAddressCheck looks at what it does with it.
                if (announcing) toldAddress = true
            }
            MSG_PAIRING_RESPONSE -> {
                val fields = Wire.fields(body)
                log.i("<- PairingResponse status=${Wire.varint(fields, 1)} already_paired=${Wire.varint(fields, 2) == 1L}")
                // Only telling an already paired car which phone this is: nothing to pair.
                if (!pairing) return
                // Some cars start pairing themselves once they answer; give them a moment first.
                main.postDelayed({ startBond("the car answered") }, CAR_BOND_WAIT_MS)
            }
            // Carries the pairing code. Android shows it to the driver, so it stays out of the log.
            MSG_AUTHENTICATION_DATA -> log.i("<- AuthenticationData (${body.size} bytes)")
            else -> log.i("<- unhandled message id=$messageId (${body.size} bytes)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBond(why: String) {
        if (bondStarted || !pairing) return
        val device = device() ?: return
        if (device.bondState != BluetoothDevice.BOND_NONE) return
        bondStarted = true
        val started = runCatching { device.createBond() }.getOrDefault(false)
        log.i("pairing from the phone ($why): ${if (started) "started" else "refused"}")
        if (!started) finish(Words.get(R.string.bluetooth_pairing_not_started))
    }

    private fun watchBonding(device: BluetoothDevice) {
        val context = appContext ?: return
        stopWatching()
        val r = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(context: Context, intent: Intent) {
                val changed = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (changed.address != device.address) return
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)
                log.i("bond state $previous -> $state")
                when {
                    state == BluetoothDevice.BOND_BONDING -> bondStarted = true
                    state == BluetoothDevice.BOND_BONDED -> finish(Words.get(R.string.bluetooth_paired))
                    previous == BluetoothDevice.BOND_BONDING -> finish(Words.get(R.string.bluetooth_pairing_unfinished))
                }
            }
        }
        context.registerReceiver(r, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), Context.RECEIVER_EXPORTED)
        receiver = r
    }

    private fun finish(message: String) {
        pairing = false
        stopWatching()
        main.removeCallbacksAndMessages(null)
        _prompt.value = Prompt.NONE
        CarToasts.show(this, message, CarToast.LENGTH_LONG)
    }

    private fun stopWatching() {
        val r = receiver ?: return
        receiver = null
        runCatching { appContext?.unregisterReceiver(r) }
    }

    /** The car as a Bluetooth device, or null (and logged) when myCar can't reach Bluetooth. */
    private fun device(): BluetoothDevice? {
        val context = appContext ?: return null
        val address = car?.service?.carAddress ?: return null
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            log.i("can't check pairing: the Bluetooth permission isn't granted")
            return null
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            log.i("can't check pairing: Bluetooth is off")
            return null
        }
        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
            log.w("the car sent an address that isn't one")
            return null
        }
        return adapter.getRemoteDevice(address)
    }

    /**
     * The address written in Settings, unless this car isn't told it any more; else what Android gives
     * apps (02:00:00:00:00:00, not the real one). Sent either way, as the field is required. Never
     * logged nor reported: it names the phone.
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    private fun phoneAddress(): String =
        writtenAddress()?.takeUnless { car?.service?.carAddress?.let { notTold(it) } == true }
            ?: runCatching { appContext?.getSystemService(BluetoothManager::class.java)?.adapter?.address }.getOrNull()
            ?: PhoneAddress.HIDDEN

    private fun writtenAddress(): String? = appContext?.let { writtenAddress(it) }

    /** The phone's own address as written in Settings > Connection, or null. */
    fun writtenAddress(context: Context): String? =
        PhoneAddress.tidy(AppSettings.getString(context, KEY_PHONE_ADDRESS, ""))?.takeIf { it.isNotEmpty() }

    /** Keeps [text] as the phone's address ([PhoneAddress.tidy]); false when it isn't one. Empty forgets it. */
    fun writeAddress(context: Context, text: String): Boolean {
        val address = PhoneAddress.tidy(text) ?: return false
        AppSettings.putString(context, KEY_PHONE_ADDRESS, address)
        // Saved again: told again to every car already paired, those it was kept from too. The
        // driver's way back, should such a car turn out to need it after all (PhoneAddressCars).
        cars(context).addressSaved()
        PhoneAddressCheck.noticeSeen(context)
        return true
    }

    /** At a drive over this link (Wi-Fi or cable) where myCar didn't tell it the phone's address, this car took the phone's music over Bluetooth. */
    internal fun tookMusicWithout(carAddress: String, wireless: Boolean): Boolean =
        appContext?.let { cars(it).tookMusicWithout(hash(carAddress), wireless) } == true

    /** Remembers [tookMusicWithout] for this car and link; true the first time. */
    internal fun rememberMusicWithout(carAddress: String, wireless: Boolean): Boolean =
        appContext?.let { cars(it).rememberMusicWithout(hash(carAddress), wireless) } == true

    /** Told the phone's address, this car stopped taking the phone's music over Bluetooth: it isn't told again. */
    internal fun stopTelling(carAddress: String) {
        appContext?.let { cars(it).stopTelling(hash(carAddress)) }
    }

    /** Some car isn't told the phone's address any more, for Settings > Connection. */
    fun keptFromSomeCar(context: Context): Boolean = cars(context).keptFromSome()

    private fun notTold(carAddress: String): Boolean = appContext?.let { cars(it).notTold(hash(carAddress)) } == true

    /** What's kept of each car about the phone's address, in myCar's settings. */
    private fun cars(context: Context) = PhoneAddressCars(
        read = { AppSettings.getString(context, it, "") },
        write = { key, value -> AppSettings.putString(context, key, value) },
    )

    private fun declined(address: String): Boolean {
        val context = appContext ?: return false
        return hash(address) in AppSettings.getString(context, KEY_DECLINED, "").split(",")
    }

    /** Settings keep a short hash, not the car's address. */
    private fun hash(address: String): String =
        MessageDigest.getInstance("SHA-256").digest(address.uppercase().toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }

    private const val KEY_DECLINED = "bluetooth_pair_declined"
    private const val KEY_PHONE_ADDRESS = "phone_bluetooth_address"
    private const val CAR_REPLY_WAIT_MS = 5_000L
    private const val CAR_BOND_WAIT_MS = 3_000L
    private const val GIVE_UP_MS = 90_000L
    private const val METHOD_NUMERIC_COMPARISON = 2
    private const val MSG_CHANNEL_OPEN_RESPONSE = 8
    private const val MSG_PAIRING_REQUEST = 32769
    private const val MSG_PAIRING_RESPONSE = 32770
    private const val MSG_AUTHENTICATION_DATA = 32771
}
