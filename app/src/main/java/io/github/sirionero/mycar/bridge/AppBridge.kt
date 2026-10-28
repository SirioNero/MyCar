package io.github.sirionero.mycar.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.SystemClock
import io.github.sirionero.mycar.MyCarLog
import java.io.IOException

/**
 * The way into a tailnet through an app inside it: the Plancia app for Home Assistant, the Xonos
 * app for Music Assistant. To connect to the car wirelessly with Tailscale on, myCar has to be left
 * out of it (inside a VPN, Android keeps myCar off the car's Wi-Fi), and a Tailscale address is
 * then out of its reach - not of the apps', inside Tailscale. So myCar binds to the app's bridge
 * (its MyCarBridge service) and asks it for a connection to the server: the app opens it and
 * hands myCar the other end of a local socket. The app answers only myCar, by its signature, and
 * opens only its own server's addresses.
 *
 * Bound on the first connection asked for and for as long as there are connections, then a while
 * longer: the binding keeps the app as awake as myCar, which in the car is in the foreground.
 */
class AppBridge(val packageName: String, private val label: String, private val server: String) {

    private val log = MyCarLog.tagged("BRIDGE")
    private val main = Handler(Looper.getMainLooper())
    private val lock = Object()

    private var binder: IBinder? = null
    private var connection: ServiceConnection? = null
    private var context: Context? = null
    private var open = 0

    @Volatile private var checkedAt = 0L
    @Volatile private var there = false

    private fun intent() = Intent(ACTION).setPackage(packageName)

    /** Whether the app on the phone has the bridge (an up-to-date Plancia or Xonos). Checked again every half minute. */
    fun available(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (checkedAt == 0L || now - checkedAt > CHECK_EVERY_MS) {
            there = runCatching { context.packageManager.queryIntentServices(intent(), 0).isNotEmpty() }.getOrDefault(false)
            checkedAt = now
        }
        return there
    }

    /**
     * A connection to [host]:[port] through the app: the other end of its local socket. Waits for the
     * app at most [timeoutMs], and the app gives the server 10 seconds; throws [BridgeException] with
     * the reason, in myCar's words. Never on the main thread: the binding arrives there.
     */
    fun open(context: Context, host: String, port: Int, timeoutMs: Int): ParcelFileDescriptor {
        check(Looper.myLooper() != Looper.getMainLooper()) { "the bridge is opened off the main thread" }
        try {
            return transact(context, host, port, timeoutMs)
        } catch (e: IOException) {
            // Nothing opened: the app isn't kept bound for this one.
            synchronized(lock) { if (open == 0) unbindAfterAWhile() }
            throw e
        }
    }

    private fun transact(context: Context, host: String, port: Int, timeoutMs: Int): ParcelFileDescriptor {
        val remote = awaitBinder(context, if (timeoutMs > 0) timeoutMs.toLong() else BIND_WAIT_MS)
            ?: throw BridgeException(said("it didn't answer. Open the $label app once, then try again."))
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(host)
            data.writeInt(port)
            if (!remote.transact(OPEN, data, reply, 0)) {
                throw BridgeException(said("it doesn't know this myCar's bridge. Update the $label app."))
            }
            val code = reply.readInt()
            if (code == OK) {
                val socket = ParcelFileDescriptor.CREATOR.createFromParcel(reply)
                synchronized(lock) {
                    open++
                    main.removeCallbacks(unbindLater)
                }
                return socket
            }
            val detail = reply.readString().orEmpty()
            log.w("the $label app turned down $host:$port ($code): $detail")
            throw BridgeException(
                said(
                    when (code) {
                        NOT_MYCAR -> "it doesn't accept this myCar. Update the $label app."
                        NOT_OURS -> "it's set up with another address. Use the same address in myCar and in the $label app."
                        NO_ANSWER -> "$server doesn't answer it either. Check that Tailscale is on, and $server is running."
                        else -> "no connection ($detail)."
                    },
                ),
            )
        } catch (e: RemoteException) {
            throw BridgeException(said("it closed. Try again."), e)
        } catch (e: RuntimeException) {
            // An answer that isn't the bridge's: never a crash in the middle of a connection.
            throw BridgeException(said("its answer wasn't understood. Update the $label app."), e)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    /** A connection [open] gave is closed: once there are none, the app is let go after a while. */
    fun closed() {
        synchronized(lock) {
            open = (open - 1).coerceAtLeast(0)
            if (open == 0) unbindAfterAWhile()
        }
    }

    private fun unbindAfterAWhile() {
        main.removeCallbacks(unbindLater)
        main.postDelayed(unbindLater, IDLE_MS)
    }

    private val unbindLater = Runnable {
        synchronized(lock) {
            if (open == 0) unbindLocked()
        }
    }

    /** What's said about the bridge, in the words the car and the phone show (see [BridgeException]). */
    private fun said(what: String) = "$THROUGH$label app: $what"

    private fun awaitBinder(context: Context, timeoutMs: Long): IBinder? {
        val until = SystemClock.elapsedRealtime() + timeoutMs
        synchronized(lock) {
            if (connection == null) bindLocked(context.applicationContext)
            while (true) {
                // A binder of an app that has just closed is waited past: Android binds again once it's back.
                binder?.takeIf { it.isBinderAlive }?.let { return it }
                val left = until - SystemClock.elapsedRealtime()
                if (left <= 0 || connection == null) return null
                lock.wait(left)
            }
        }
    }

    private fun bindLocked(app: Context) {
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                synchronized(lock) {
                    binder = service
                    lock.notifyAll()
                }
                log.i("bound to the $label app's bridge")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                // Android binds again by itself when the app comes back.
                synchronized(lock) { binder = null }
                log.w("the $label app's bridge went away")
            }

            override fun onBindingDied(name: ComponentName) {
                synchronized(lock) { unbindLocked() }
            }

            override fun onNullBinding(name: ComponentName) {
                synchronized(lock) { unbindLocked() }
            }
        }
        // As important as myCar is: in the car, in the foreground, and with its network.
        val flags = Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT or Context.BIND_INCLUDE_CAPABILITIES
        val bound = runCatching { app.bindService(intent(), conn, flags) }.getOrDefault(false)
        if (bound) {
            connection = conn
            context = app
        } else {
            log.w("couldn't bind to the $label app's bridge")
            runCatching { app.unbindService(conn) }
        }
    }

    private fun unbindLocked() {
        val conn = connection ?: return
        runCatching { context?.unbindService(conn) }
        connection = null
        binder = null
        lock.notifyAll()
    }

    companion object {
        /** What the Plancia and Xonos apps' MyCarBridge services answer to. */
        const val ACTION = "io.github.sirionero.mycar.BRIDGE"

        /** The bridge's interface, the same in the apps. */
        const val DESCRIPTOR = "io.github.sirionero.mycar.Bridge"

        /** Open a connection: host (String), port (Int); answered [OK] and the socket, or a refusal's code and why. */
        const val OPEN = IBinder.FIRST_CALL_TRANSACTION

        private const val OK = 1
        private const val NOT_MYCAR = 2
        private const val NOT_OURS = 3
        private const val NO_ANSWER = 4

        /** How a bridge's message starts: Tailnet.why says it as it is, not "Tailscale leaves myCar out". */
        const val THROUGH = "Through the "

        private const val BIND_WAIT_MS = 10_000L
        private const val IDLE_MS = 5 * 60_000L
        private const val CHECK_EVERY_MS = 30_000L
    }
}

/** A connection through an app's bridge that couldn't be made, said in myCar's words. */
class BridgeException(message: String, cause: Throwable? = null) : IOException(message, cause)
