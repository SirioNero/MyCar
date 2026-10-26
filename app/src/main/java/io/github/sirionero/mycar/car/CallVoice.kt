package io.github.sirionero.mycar.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether a call's voice is on the phone itself, its earpiece or speaker, rather than in the car.
 * A call reaches the car only over Bluetooth, as with Android Auto: the USB link carries the car
 * screen and media, and Android gives no app a way to put a phone call on it, or to move one to
 * the speaker. So the call screen can only say where the voice is, and this tells it.
 *
 * Under way ([active]), it's where Android sends the call's audio right now; while ringing, whether
 * the phone has a hands-free Bluetooth connection for the call to go to. It follows both as they
 * change, for as long as the caller stays in the composition.
 */
@Composable
internal fun rememberCallOnPhone(active: Boolean): Boolean {
    val context = LocalContext.current.applicationContext
    var onPhone by remember(active) { mutableStateOf(callOnPhone(context, active)) }
    DisposableEffect(active) {
        val recheck = { onPhone = callOnPhone(context, active) }
        val audio = context.getSystemService(AudioManager::class.java)
        val listener = AudioManager.OnCommunicationDeviceChangedListener { recheck() }
        audio?.addOnCommunicationDeviceChangedListener(context.mainExecutor, listener)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = recheck()
        }
        val registered = runCatching {
            context.registerReceiver(
                receiver,
                IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED).apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                },
                Context.RECEIVER_EXPORTED,
            )
        }.isSuccess
        recheck()
        onDispose {
            audio?.removeOnCommunicationDeviceChangedListener(listener)
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return onPhone
}

@SuppressLint("MissingPermission")
private fun callOnPhone(context: Context, active: Boolean): Boolean {
    if (active) {
        val device = context.getSystemService(AudioManager::class.java)?.communicationDevice
        if (device != null) {
            return device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE || device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
    }
    // Without the Bluetooth permission there's no telling, and a wrong warning is worse than none.
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return false
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return true
    if (!adapter.isEnabled) return true
    return adapter.getProfileConnectionState(BluetoothProfile.HEADSET) != BluetoothProfile.STATE_CONNECTED
}
