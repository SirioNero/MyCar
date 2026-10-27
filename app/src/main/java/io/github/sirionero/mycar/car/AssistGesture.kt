package io.github.sirionero.mycar.car

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import io.github.sirionero.mycar.MyCarLog

/**
 * Wakes the phone's default assistant the way the swipe up from a bottom corner of the screen
 * does.
 *
 * That swipe has SystemUI start the assistant, and the assistant shows itself as it chooses for
 * the swipe: listening, for Google, Gemini or Claude, where an ASSIST intent opens their typed chat.
 * Android has no public call for it. SystemUI's own way in is the search service's launchAssist,
 * which checks no permission and has kept its place in the service's interface since Android 12:
 * the sixth call, after five getters. Its binder is looked up by name with ServiceManager, which
 * Android leaves open to apps. Should any of that change, [start] returns false, and myCar falls
 * back to the intents.
 */
internal object AssistGesture {
    private const val SEARCH_DESCRIPTOR = "android.app.ISearchManager"

    /** launchAssist(int userHandle, in Bundle args), Android 12 to 16. */
    private const val TRANSACTION_LAUNCH_ASSIST = IBinder.FIRST_CALL_TRANSACTION + 5

    /** What SystemUI passes on to the assistant: how it was invoked, here the gesture. */
    private const val INVOCATION_TYPE_KEY = "invocation_type"
    private const val INVOCATION_TYPE_GESTURE = 1

    /** Each Android user's UIDs come in a block this size: the UID divided by it is the user. */
    private const val PER_USER_RANGE = 100_000

    /** Starts the default assistant as the corner swipe does. False if Android wouldn't take it. */
    fun start(): Boolean = runCatching {
        val binder = (
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, Context.SEARCH_SERVICE) as? IBinder
            )?.takeIf { it.interfaceDescriptor == SEARCH_DESCRIPTOR } ?: return false
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SEARCH_DESCRIPTOR)
            data.writeInt(Process.myUid() / PER_USER_RANGE)
            data.writeTypedObject(gestureExtras(), 0)
            binder.transact(TRANSACTION_LAUNCH_ASSIST, data, reply, 0).also { if (it) reply.readException() }
        } finally {
            reply.recycle()
            data.recycle()
        }
    }.onFailure { MyCarLog.w("assistant: no corner-swipe start: $it") }.getOrDefault(false)

    /** The extras an assistant opened by the swipe finds: for an ASSIST intent to say the same. */
    fun gestureExtras(): Bundle = Bundle().apply { putInt(INVOCATION_TYPE_KEY, INVOCATION_TYPE_GESTURE) }
}
