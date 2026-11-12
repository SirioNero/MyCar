package io.github.sirionero.mycar.car

/**
 * What myCar keeps of each car about the phone's Bluetooth address (CarBluetooth, PhoneAddressCheck),
 * by a short hash of the car's address, never the address: the cars seen taking the phone's music over
 * Bluetooth without being told it, over Wi-Fi and over the cable apart (over Wi-Fi a car may know the
 * phone from the wireless start, over the cable not), and the cars no longer told it. Each list is one
 * setting, comma-separated, read and written through [read] and [write]. No Android: PhoneAddressCarsTest.
 */
internal class PhoneAddressCars(
    private val read: (key: String) -> String,
    private val write: (key: String, value: String) -> Unit,
) {
    /** At a drive over this link where it wasn't told the phone's address, [car] took the phone's music over Bluetooth. */
    fun tookMusicWithout(car: String, wireless: Boolean): Boolean = car in list(musicKey(wireless))

    /** Remembers [tookMusicWithout]; true the first time. */
    fun rememberMusicWithout(car: String, wireless: Boolean): Boolean = add(musicKey(wireless), car)

    /** [car] isn't told the phone's address any more. */
    fun notTold(car: String): Boolean = car in list(NOT_TOLD)

    /** Told it, [car] stopped taking the phone's music over Bluetooth: it isn't told again. True the first time. */
    fun stopTelling(car: String): Boolean = add(NOT_TOLD, car)

    /** Some car isn't told the phone's address any more. */
    fun keptFromSome(): Boolean = list(NOT_TOLD).isNotEmpty()

    /**
     * The address saved again, or taken out: told again to every car, those it was kept from too. What
     * made myCar keep it from them is forgotten, over both links, or their next drive would keep it from
     * them again: only a new drive without the address can.
     */
    fun addressSaved() {
        val kept = list(NOT_TOLD).toSet()
        if (kept.isEmpty()) return
        for (key in listOf(MUSIC_WIRELESS, MUSIC_CABLE)) {
            val cars = list(key)
            if (cars.any { it in kept }) write(key, (cars - kept).joinToString(","))
        }
        write(NOT_TOLD, "")
    }

    private fun musicKey(wireless: Boolean) = if (wireless) MUSIC_WIRELESS else MUSIC_CABLE

    private fun list(key: String): List<String> = read(key).split(",").filter { it.isNotEmpty() }

    private fun add(key: String, car: String): Boolean {
        val cars = list(key)
        if (car in cars) return false
        write(key, (cars + car).joinToString(","))
        return true
    }

    companion object {
        /** The settings: cars that took the music without the address over Wi-Fi, over the cable, and those not told it. */
        const val MUSIC_WIRELESS = "bluetooth_music_without_address_wireless"
        const val MUSIC_CABLE = "bluetooth_music_without_address_cable"
        const val NOT_TOLD = "phone_address_not_told"
    }
}
