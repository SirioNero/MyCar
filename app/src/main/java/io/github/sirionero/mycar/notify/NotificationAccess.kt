package io.github.sirionero.mycar.notify

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Android's notification access for myCar, which messages, notifications and the media controls
 * of other apps need. Opens straight on myCar's own switch, else the list of apps where the
 * driver looks for myCar; from the phone's own context, since one tied to the car's display can't
 * host Android's settings. False if neither page opened.
 */
object NotificationAccess {

    fun open(context: Context): Boolean {
        val app = context.applicationContext
        val listener = ComponentName(app, MyCarNotificationListener::class.java).flattenToString()
        val mine = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { app.startActivity(mine) }
            .recoverCatching { app.startActivity(list) }
            .isSuccess
    }
}
