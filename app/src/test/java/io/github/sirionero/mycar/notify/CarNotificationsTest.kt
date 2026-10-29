package io.github.sirionero.mycar.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The dot on the dock's clock, and what "read my messages" counts as new (see CarNotifications). */
class CarNotificationsTest {

    private fun message(key: String, text: String = "Hi") = CarNotification(
        key = key,
        id = CarNotifications.nextId(),
        packageName = "chat.app",
        appLabel = "Chat",
        title = key,
        text = text,
        postedAt = 0L,
        icon = null,
        reply = null,
    )

    @Before
    fun empty() = CarNotifications.clear()

    @Test
    fun `the dot counts chats, and each counts its new messages`() {
        CarNotifications.post(message("anna"), quiet = false)
        CarNotifications.post(message("anna", text = "Are you coming?"), quiet = false)
        CarNotifications.post(message("luca"), quiet = false)
        assertEquals(2, CarNotifications.unread.value)
        assertEquals(2, CarNotifications.unreadCount("anna"))
        assertEquals(1, CarNotifications.unreadCount("luca"))
    }

    @Test
    fun `a chat read aloud or answered leaves the dot`() {
        CarNotifications.post(message("anna"), quiet = false)
        CarNotifications.post(message("luca"), quiet = false)
        CarNotifications.seen("anna")
        assertFalse(CarNotifications.isUnread("anna"))
        assertEquals(1, CarNotifications.unread.value)
    }

    @Test
    fun `a chat read on the phone is not new in the car`() {
        CarNotifications.post(message("anna"), quiet = false)
        CarNotifications.gone("anna")
        assertEquals(0, CarNotifications.unread.value)
        assertEquals(0, CarNotifications.unreadCount("anna"))
    }

    @Test
    fun `quiet posts and the same post again don't light it`() {
        CarNotifications.post(message("anna"), quiet = true)
        assertEquals(0, CarNotifications.unread.value)
        CarNotifications.post(message("luca"), quiet = false)
        CarNotifications.post(message("luca"), quiet = false)
        assertEquals(1, CarNotifications.unreadCount("luca"))
        assertTrue(CarNotifications.isUnread("luca"))
    }

    @Test
    fun `the dashboard clears it all`() {
        CarNotifications.post(message("anna"), quiet = false)
        CarNotifications.post(message("luca"), quiet = false)
        CarNotifications.markRead()
        assertEquals(0, CarNotifications.unread.value)
        assertFalse(CarNotifications.isUnread("anna"))
    }
}
