package io.github.sirionero.mycar

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting

/**
 * myCar's words in the language myCar is in ([AppLanguage]), for code with no screen of its own:
 * toasts, notices, notifications, states shown later by a screen. On a screen, stringResource does
 * the same.
 */
object Words {
    private var app: Context? = null

    /** Tests put the English of res/values here (TestWords): there are no resources on the JVM. */
    @VisibleForTesting
    internal var lookup: ((id: Int, count: Int?, args: Array<out Any>) -> String)? = null

    /** From MyCarApplication, before anything else of myCar runs. */
    fun init(context: Context) {
        app = context.applicationContext
    }

    val resources: Resources
        get() = AppLanguage.resources(checkNotNull(app) { "Words before MyCarApplication.onCreate" })

    fun get(@StringRes id: Int, vararg args: Any): String {
        lookup?.let { return it(id, null, args) }
        return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
    }

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        lookup?.let { return it(id, count, args) }
        return if (args.isEmpty()) resources.getQuantityString(id, count) else resources.getQuantityString(id, count, *args)
    }
}
