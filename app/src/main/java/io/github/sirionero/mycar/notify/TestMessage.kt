package io.github.sirionero.mycar.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import io.github.sirionero.mycar.MyCarLog
import io.github.sirionero.mycar.R
import io.github.sirionero.mycar.Words

/**
 * Debug builds only: posts a chat message from myCar to itself, shaped like Signal's or
 * WhatsApp's (MessagingStyle, a Reply action, a Mark as read action), so popups and voice reply
 * can be tried without asking someone to text you. The listener lets these through only in a
 * debug build; everything else myCar posts is still ignored.
 */
object TestMessage {

    const val CHANNEL = "mycar_test_message"
    private const val NOTIFICATION_ID = 77
    private const val KEY_REPLY = "reply"
    private const val ACTION_REPLY = "io.github.sirionero.mycar.TEST_REPLY"
    private const val ACTION_READ = "io.github.sirionero.mycar.TEST_READ"

    private val SAMPLES = listOf(
        "Alex" to R.string.message_test_late,
        "Sam" to R.string.message_test_tonight,
        "Jordan" to R.string.message_test_milk,
        "Riley" to R.string.message_test_parked,
    )
    private var next = 0
    private val me: Person get() = Person.Builder().setName(Words.get(R.string.message_test_you)).build()

    fun post(context: Context) {
        val (name, words) = SAMPLES[next++ % SAMPLES.size]
        val text = Words.get(words)
        val sender = Person.Builder().setName(name).build()
        val style = NotificationCompat.MessagingStyle(me)
            .addMessage(text, System.currentTimeMillis(), sender)
        show(context, style)
        MyCarLog.i("test message: posted \"$text\" from $name")
    }

    private fun show(context: Context, style: NotificationCompat.MessagingStyle) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // High importance, like a real messaging app's chats: lower ones never pop up in the car.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, Words.get(R.string.message_test_channel), NotificationManager.IMPORTANCE_HIGH),
        )
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, Words.get(R.string.message_reply),
            broadcast(context, ACTION_REPLY, mutable = true),
        )
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel(Words.get(R.string.message_reply)).build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
        val readAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view, Words.get(R.string.message_test_mark_as_read),
            broadcast(context, ACTION_READ, mutable = false),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(replyAction)
            .addAction(readAction)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onFailure { MyCarLog.w("test message: could not post (${it.message})") }
    }

    private fun broadcast(context: Context, action: String, mutable: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, TestMessageReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )

    internal fun onAction(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
                MyCarLog.i("test message: reply received: \"$text\"")
                // Show the reply in the conversation, the way a chat app confirms it was sent.
                val active = context.getSystemService(NotificationManager::class.java)
                    .activeNotifications.firstOrNull { it.id == NOTIFICATION_ID }?.notification
                val style = active?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
                    ?: NotificationCompat.MessagingStyle(me)
                style.addMessage(text.orEmpty(), System.currentTimeMillis(), null as Person?)
                show(context, style)
            }
            ACTION_READ -> {
                MyCarLog.i("test message: marked as read")
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }
        }
    }
}

class TestMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = TestMessage.onAction(context, intent)
}
