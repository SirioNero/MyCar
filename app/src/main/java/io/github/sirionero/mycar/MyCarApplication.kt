package io.github.sirionero.mycar

import android.app.Application

/**
 * myCar's words ([Words]) from the start of the process, and on Android 12 the language chosen in
 * Settings, which myCar applies itself, so services and notifications have it too (Android 13
 * does it for us).
 */
class MyCarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Words.init(this)
        AppLanguage.applyToProcess(this)
    }
}
