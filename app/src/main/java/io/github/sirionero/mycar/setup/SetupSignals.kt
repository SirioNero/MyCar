package io.github.sirionero.mycar.setup

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.sirionero.mycar.AppSettings
import io.github.sirionero.mycar.CertProvider
import io.github.sirionero.mycar.LocalNetwork
import io.github.sirionero.mycar.host.AndroidAuto
import io.github.sirionero.mycar.notify.MyCarNotificationListener

/** What [SetupSteps] needs to know, read from the phone: permissions, roles, settings, files. */
object SetupSignals {

    /** The drive's permissions, by the name Android gives their group in its dialogs. */
    fun driveGroups(): List<Pair<String, List<String>>> = listOf(
        "Calls" to listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CALL_PHONE,
        ),
        "Contacts" to listOf(Manifest.permission.READ_CONTACTS),
        // Both together: from Android 12 a request for the precise location alone is dropped.
        "Location" to listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        "Microphone" to listOf(Manifest.permission.RECORD_AUDIO),
        // From Android 17 the home network goes with Bluetooth (see LocalNetwork).
        "Nearby devices" to listOfNotNull(
            Manifest.permission.BLUETOOTH_CONNECT,
            LocalNetwork.PERMISSION.takeIf { LocalNetwork.asked },
        ),
    )

    /**
     * What the drive's one "Allow" asks for: the permissions of the groups still off. Not the
     * precise location once the approximate one is allowed, which Android would ask about again.
     */
    fun missingDrivePermissions(context: Context): Array<String> =
        driveGroups().filterNot { (name, permissions) -> groupOn(context, name, permissions) }
            .flatMap { (_, permissions) -> permissions.filterNot { granted(context, it) } }
            .distinct().toTypedArray()

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** A group is on with all of its permissions; the location already with the approximate one. */
    private fun groupOn(context: Context, name: String, permissions: List<String>): Boolean =
        if (name == "Location") permissions.any { granted(context, it) } else permissions.all { granted(context, it) }

    fun read(context: Context): SetupSteps.Signals {
        val missing = driveGroups().filterNot { (name, permissions) -> groupOn(context, name, permissions) }
        // Seen blocked when it was asked for, still refused, and Android still not explaining: it
        // won't show the dialog again.
        val seenBlocked = FirstSetup.blocked(context)
        val blocked = (context as? Activity)?.let { activity ->
            missing.filter { (_, permissions) ->
                permissions.any { it in seenBlocked && !granted(context, it) && !activity.shouldShowRequestPermissionRationale(it) }
            }
        }.orEmpty()
        val androidAutoPackage = AndroidAuto.installedPackage(context)
        return SetupSteps.Signals(
            hasCertificate = CertProvider.hasAnyCert(context),
            myCarOn = AppSettings.myCarEnabled(context),
            androidAuto = when {
                androidAutoPackage == null -> SetupSteps.AndroidAuto.NONE
                AndroidAuto.isActive(context, androidAutoPackage) -> SetupSteps.AndroidAuto.ON
                else -> SetupSteps.AndroidAuto.OFF
            },
            keepAndroidAuto = FirstSetup.keepAndroidAuto(context),
            driveMissing = missing.map { it.first },
            driveBlocked = blocked.map { it.first },
            messages = hasNotificationAccess(context),
            calendar = granted(context, Manifest.permission.READ_CALENDAR),
            // Android 12 has no such permission: its notifications need nothing.
            notifications = Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS),
            screening = hasCallScreeningRole(context),
            skipped = FirstSetup.skipped(context),
        )
    }

    fun hasNotificationAccess(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        val component = ComponentName(context, MyCarNotificationListener::class.java)
        return runCatching { manager.isNotificationListenerAccessGranted(component) }.getOrDefault(false)
    }

    fun hasCallScreeningRole(context: Context): Boolean {
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return runCatching { roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }.getOrDefault(false)
    }
}
