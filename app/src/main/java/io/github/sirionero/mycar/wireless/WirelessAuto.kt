package io.github.sirionero.mycar.wireless

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Handler
import android.os.Looper
import io.github.sirionero.mycar.MyCarActivity
import io.github.sirionero.mycar.MyCarLog

/**
 * Wireless Android Auto that starts by itself: once a car has worked without the cable, the next
 * time it comes up over Bluetooth - the engine on, the phone in a pocket - myCar connects to it
 * with no tap.
 *
 * Android lets an app wake up and open its screen from the background only for a device it
 * manages as a companion. So the first time a car's picture comes up over Wi-Fi, myCar asks
 * Android to make that car its companion, and Android shows its own one-time "Allow". From then
 * on it tells [CarPresenceService] whenever the car connects, and myCar starts the wireless
 * start-up as a tap on the car would. Only cars that worked are asked about: one that refused
 * the certificate would have opened myCar on every drive for nothing. After Gearslip's (0.3.02).
 */
object WirelessAuto {

    /** Action on the activity's intent when the car came up on its own. */
    const val ACTION_CAR_NEARBY = "io.github.sirionero.mycar.CAR_NEARBY"
    const val EXTRA_ADDRESS = "address"

    private const val PREFS = "mycar_wireless_auto"
    private const val KEY_ON = "on"
    /** Cars whose "Allow" the driver has answered, yes or no: not asked again. */
    private const val ANSWERED = "answered:"
    /** A car whose Bluetooth comes and goes opens myCar once, not at every flicker. */
    private const val SETTLE_MS = 15_000L
    /** A dialog that never showed (myCar wasn't on screen) gets no answer: asked again after this. */
    private const val UNANSWERED_MS = 10 * 60_000L

    private val log = MyCarLog.tagged("AAW")

    /** Set by the activity: shows Android's companion dialog, which only an activity can do. */
    @Volatile var showApproval: ((IntentSender, String) -> Unit)? = null

    /** The car whose dialog is up, so a second call in the same drive doesn't ask twice. */
    @Volatile private var asking: String? = null
    @Volatile private var askedAt = 0L
    @Volatile private var lastAppeared = 0L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether myCar connects by itself to the cars it manages. On unless the driver turned it off. */
    fun isOn(context: Context): Boolean = prefs(context).getBoolean(KEY_ON, true)

    /**
     * Off stops Android's watching for the cars; on starts it again, and the cars whose dialog was
     * turned down are asked about once more at their next wireless drive.
     */
    fun setOn(context: Context, on: Boolean) {
        val prefs = prefs(context)
        val edit = prefs.edit().putBoolean(KEY_ON, on)
        if (on) prefs.all.keys.filter { it.startsWith(ANSWERED) }.forEach { edit.remove(it) }
        edit.apply()
        log.i("auto-connect ${if (on) "on" else "off"}")
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        companions(manager).forEach { if (on) watch(context, it) else unwatch(context, it) }
    }

    /**
     * The car's picture came up over Wi-Fi: it works without the cable. Asks Android once per car
     * for the "Allow"; a car already allowed only has its presence watched again, in case Android forgot.
     */
    @SuppressLint("MissingPermission")
    fun reached(context: Context, device: BluetoothDevice) {
        if (!isOn(context)) return
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        val address = device.address
        if (companions(manager).any { it.equals(address, ignoreCase = true) }) {
            watch(context, address)
            return
        }
        if (prefs(context).getBoolean(ANSWERED + address, false)) return
        val now = System.currentTimeMillis()
        if (asking == address && now - askedAt < UNANSWERED_MS) return
        asking = address
        askedAt = now
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        log.i("auto-connect: asking Android to let myCar connect to ${WirelessLink.name(device)} by itself")
        @Suppress("DEPRECATION")
        manager.associate(request, object : CompanionDeviceManager.Callback() {
            // Android 13 and later call this one; before, onDeviceFound.
            override fun onAssociationPending(intentSender: IntentSender) = pending(intentSender)

            @Deprecated("Deprecated in Java")
            override fun onDeviceFound(chooser: IntentSender) = pending(chooser)

            private fun pending(chooser: IntentSender) {
                val show = showApproval
                if (show == null) {
                    // Asked again at the next wireless drive: only an answer counts.
                    log.w("auto-connect: no screen to ask on")
                    asking = null
                } else {
                    show(chooser, address)
                }
            }

            override fun onFailure(error: CharSequence?) {
                log.w("auto-connect: Android wouldn't ask ($error)")
                asking = null
            }
        }, Handler(Looper.getMainLooper()))
    }

    /** The driver allowed it in Android's dialog. */
    fun approved(context: Context, address: String) {
        log.i("auto-connect: allowed - myCar connects to this car by itself from now on")
        answered(context, address)
        watch(context, address)
    }

    /** The driver said no, or closed the dialog: this car isn't asked about again until the switch goes off and on. */
    fun declined(context: Context, address: String) {
        log.w("auto-connect: not allowed in Android's dialog")
        answered(context, address)
    }

    private fun answered(context: Context, address: String) {
        asking = null
        prefs(context).edit().putBoolean(ANSWERED + address, true).apply()
    }

    @SuppressLint("MissingPermission")
    private fun watch(context: Context, address: String) {
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        @Suppress("DEPRECATION")
        runCatching { manager.startObservingDevicePresence(address) }
            .onFailure { log.w("auto-connect: couldn't watch for the car (${it.message})") }
    }

    @SuppressLint("MissingPermission")
    private fun unwatch(context: Context, address: String) {
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        @Suppress("DEPRECATION")
        runCatching { manager.stopObservingDevicePresence(address) }
    }

    @Suppress("DEPRECATION")
    private fun companions(manager: CompanionDeviceManager): List<String> =
        runCatching { manager.associations }.getOrDefault(emptyList())

    /**
     * Whether [address] is a car myCar may connect to by itself now: the switch on, and the car one
     * the driver allowed in Android's dialog. MyCarActivity takes [ACTION_CAR_NEARBY] from anyone
     * (it's the launcher's activity): without this, any app could make it join a device of its own.
     */
    fun mayConnect(context: Context, address: String): Boolean {
        if (!isOn(context)) return false
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return companions(manager).any { it.equals(address, ignoreCase = true) }
    }

    /** From [CarPresenceService]: the car is here, so bring myCar up to connect to it. */
    fun carAppeared(context: Context, address: String) {
        if (!isOn(context)) {
            log.i("auto-connect: a car myCar knows connected over Bluetooth, but auto-connect is off")
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastAppeared < SETTLE_MS) return
        lastAppeared = now
        log.i("auto-connect: a car myCar knows connected over Bluetooth")
        runCatching {
            context.startActivity(
                Intent(context, MyCarActivity::class.java)
                    .setAction(ACTION_CAR_NEARBY)
                    // Bluetooth takes an address in capitals only.
                    .putExtra(EXTRA_ADDRESS, address.uppercase(java.util.Locale.ROOT))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { log.w("auto-connect: couldn't open myCar (${it.message})") }
    }
}
