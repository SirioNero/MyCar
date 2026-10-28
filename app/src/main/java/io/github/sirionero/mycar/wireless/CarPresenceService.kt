package io.github.sirionero.mycar.wireless

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import androidx.annotation.RequiresApi

/** Android calls this when a car allowed in [WirelessAuto] connects over Bluetooth. */
class CarPresenceService : CompanionDeviceService() {

    // Android 13 and later call this one; before, the one with the address.
    @RequiresApi(33)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        val address = associationInfo.deviceMacAddress?.toString() ?: return
        WirelessAuto.carAppeared(applicationContext, address)
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        WirelessAuto.carAppeared(applicationContext, address)
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {}
}
