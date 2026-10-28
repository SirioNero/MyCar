package io.github.sirionero.mycar.wireless

import android.companion.CompanionDeviceService

/** Android calls this when a car allowed in [WirelessAuto] connects over Bluetooth. */
class CarPresenceService : CompanionDeviceService() {

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        WirelessAuto.carAppeared(applicationContext, address)
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {}
}
