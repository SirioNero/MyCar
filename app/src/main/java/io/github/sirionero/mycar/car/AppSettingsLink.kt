package io.github.sirionero.mycar.car

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.sirionero.mycar.MyCarLog

/**
 * myCar's page in the phone's settings, from a button on the car screen. The car UI's context
 * isn't an activity: started from it without a new task, Android threw, and myCar - and the
 * drive - went down. On the phone, where the driver grants what's missing.
 */
internal fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.applicationContext.startActivity(intent) }
        .onFailure { MyCarLog.w("couldn't open myCar's settings on the phone: ${it.message}") }
}
