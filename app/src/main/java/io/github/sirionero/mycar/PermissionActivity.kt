package io.github.sirionero.mycar

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import io.github.sirionero.mycar.car.openAppSettings

/**
 * Asks for runtime permissions on the phone's own screen, for a car screen that can't show
 * Android's dialog itself. It has no look of its own: the dialog appears, the answer is given,
 * and it finishes. A permission Android no longer asks for - refused twice before - comes back
 * at once with no dialog: then myCar's page in the phone's settings opens, where it can still be
 * turned on. After Gearslip's (0.3.12).
 */
class PermissionActivity : Activity() {

    private var askedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wanted = intent.getStringArrayExtra(EXTRA_PERMISSIONS)
            ?.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            .orEmpty()
        if (wanted.isEmpty()) {
            finish()
            return
        }
        if (savedInstanceState != null) return
        askedAt = SystemClock.uptimeMillis()
        requestPermissions(wanted.toTypedArray(), REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        MyCarLog.i(
            "permissions: " + permissions.zip(grantResults.toList())
                .joinToString { (p, r) -> "${p.substringAfterLast('.')}=${r == PackageManager.PERMISSION_GRANTED}" },
        )
        val refused = grantResults.any { it != PackageManager.PERMISSION_GRANTED }
        if (refused && SystemClock.uptimeMillis() - askedAt < NO_DIALOG_MS) {
            MyCarLog.i("permissions: Android no longer asks - opening myCar's settings on the phone")
            openAppSettings(this)
        }
        finish()
    }

    companion object {
        private const val EXTRA_PERMISSIONS = "permissions"
        private const val REQUEST = 1
        /** An answer quicker than this came with no dialog: nobody can tap that fast. */
        private const val NO_DIALOG_MS = 400L

        /** Shows the permission dialog on the phone, whichever screen [context] belongs to. */
        fun ask(context: Context, permissions: Array<String>) {
            val intent = Intent(context, PermissionActivity::class.java)
                .putExtra(EXTRA_PERMISSIONS, permissions)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            runCatching { context.applicationContext.startActivity(intent, options.toBundle()) }
                .onFailure { MyCarLog.w("permissions: could not ask on the phone: ${it.message}") }
        }
    }
}
