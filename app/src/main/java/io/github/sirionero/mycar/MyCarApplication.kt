package io.github.sirionero.mycar

import android.app.Application

/**
 * Only for the language chosen in Settings on Android 12, where myCar applies it itself: from the
 * start of the process, so services and notifications have it too (Android 13 does it for us).
 */
class MyCarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguage.applyToProcess(this)
    }
}
