package io.github.sirionero.mycar.setup

import io.github.sirionero.mycar.setup.SetupSteps.AndroidAuto
import io.github.sirionero.mycar.setup.SetupSteps.Id
import io.github.sirionero.mycar.setup.SetupSteps.Signals
import io.github.sirionero.mycar.setup.SetupSteps.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The checklist from first run to done, and back to it later with nothing undone. */
class SetupStepsTest {

    private val fresh = Signals(
        hasCertificate = false,
        myCarOn = true,
        androidAuto = AndroidAuto.ON,
        keepAndroidAuto = false,
        driveMissing = listOf("Calls", "Contacts", "Location", "Microphone"),
        driveBlocked = emptyList(),
        messages = false,
        calendar = false,
        notifications = false,
        screening = false,
    )

    private fun next(s: Signals) = SetupSteps.next(SetupSteps.compute(s))?.id

    private fun status(s: Signals, id: Id) = SetupSteps.compute(s).first { it.id == id }.status

    @Test
    fun `a new phone starts with the certificate`() {
        assertEquals(Id.CERTIFICATE, next(fresh))
    }

    @Test
    fun `myCar switched off comes before Android Auto`() {
        assertEquals(Id.MYCAR_ON, next(fresh.copy(hasCertificate = true, myCarOn = false)))
        assertEquals(Status.HIDDEN, status(fresh, Id.MYCAR_ON))
    }

    @Test
    fun `Android Auto off, kept or missing needs nothing`() {
        val ready = fresh.copy(hasCertificate = true)
        assertEquals(Id.ANDROID_AUTO, next(ready))
        assertEquals(Status.DONE, status(ready.copy(androidAuto = AndroidAuto.OFF), Id.ANDROID_AUTO))
        assertEquals(Status.DONE, status(ready.copy(keepAndroidAuto = true), Id.ANDROID_AUTO))
        assertEquals(Status.HIDDEN, status(ready.copy(androidAuto = AndroidAuto.NONE), Id.ANDROID_AUTO))
        assertEquals(Id.PERMISSIONS, next(ready.copy(androidAuto = AndroidAuto.NONE)))
    }

    @Test
    fun `ready to connect, then the permissions, then nothing`() {
        val connectable = fresh.copy(hasCertificate = true, keepAndroidAuto = true)
        assertEquals(emptyList<SetupSteps.Step>(), SetupSteps.leftToConnect(SetupSteps.compute(connectable)))
        assertEquals(Id.PERMISSIONS, next(connectable))
        assertNull(next(connectable.copy(driveMissing = emptyList())))
    }

    @Test
    fun `skipped or blocked permissions are not offered first again`() {
        val connectable = fresh.copy(hasCertificate = true, keepAndroidAuto = true)
        assertNull(next(connectable.copy(skipped = setOf(Id.PERMISSIONS))))
        assertEquals(Status.SKIPPED, status(connectable.copy(skipped = setOf(Id.PERMISSIONS)), Id.PERMISSIONS))
        val blocked = connectable.copy(driveBlocked = listOf("Location"))
        assertEquals(Status.BLOCKED, status(blocked, Id.PERMISSIONS))
        assertNull(next(blocked))
    }

    @Test
    fun `the optional ones never take the big button`() {
        val allButMore = fresh.copy(hasCertificate = true, keepAndroidAuto = true, driveMissing = emptyList())
        assertNull(next(allButMore))
        assertEquals(4, SetupSteps.moreLeft(SetupSteps.compute(allButMore)))
        val everything = allButMore.copy(messages = true, calendar = true, notifications = true, screening = true)
        assertEquals(0, SetupSteps.moreLeft(SetupSteps.compute(everything)))
    }

    @Test
    fun `granting something already skipped ticks it`() {
        val skipped = fresh.copy(hasCertificate = true, keepAndroidAuto = true, driveMissing = emptyList(), skipped = setOf(Id.PERMISSIONS))
        assertEquals(Status.DONE, status(skipped, Id.PERMISSIONS))
    }
}
