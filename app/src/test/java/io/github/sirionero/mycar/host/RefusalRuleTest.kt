package io.github.sirionero.mycar.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which refusals of myCar last, and how the car says them. */
class RefusalRuleTest {

    private val handshake = RefusalRule.HANDSHAKE

    @Test
    fun theOldLibrarysUnknownHostIsTheHostCheck() {
        // ABRP, as a tester saw it in the car: "It failed at onHandshakeCompleted: Unknown host ...".
        assertTrue(RefusalRule.hostCheckSaidNo(handshake, "java.lang.IllegalArgumentException: Unknown host 'io.github.sirionero.mycar', uid:10339"))
        assertTrue(RefusalRule.hostCheckSaidNo(handshake, "java.lang.SecurityException: Unknown host 'io.github.sirionero.mycar.dev', uid:10412"))
    }

    @Test
    fun theNewLibrarysWordsAreTheHostCheckToo() {
        assertTrue(RefusalRule.hostCheckSaidNo(handshake, "java.lang.SecurityException: Unrecognized host with uid 10339"))
        assertTrue(RefusalRule.hostCheckSaidNo(handshake, "java.lang.SecurityException: The host is not allowed to call this service"))
        // Wrapped in another exception, and with spaces round it: the message is what counts.
        assertTrue(RefusalRule.hostCheckSaidNo(handshake, "  java.lang.RuntimeException: java.lang.IllegalArgumentException: Unknown host 'io.github.sirionero.mycar', uid:10339 "))
    }

    @Test
    fun otherAnswersAtTheHandshakeAreNot() {
        assertFalse(RefusalRule.hostCheckSaidNo(handshake, "java.lang.IllegalArgumentException: Host API level (8) is less than the app's min API level (9)"))
        assertFalse(RefusalRule.hostCheckSaidNo(handshake, "java.lang.IllegalStateException: Service is null"))
        assertFalse(RefusalRule.hostCheckSaidNo(handshake, "java.lang.SecurityException: Handshake already completed by another host"))
        assertFalse(RefusalRule.hostCheckSaidNo(handshake, "java.lang.SecurityException: Claimed host package name io.github.sirionero.mycar does not match calling UID 10339"))
        assertFalse(RefusalRule.hostCheckSaidNo(handshake, "java.lang.NullPointerException"))
        assertFalse(RefusalRule.hostCheckSaidNo(handshake, "java.lang.IllegalStateException: Couldn't reach host 'example.org'"))
        assertFalse(RefusalRule.hostCheckSaidNo(handshake, ""))
        assertFalse(RefusalRule.hostCheckSaidNo(handshake, null))
    }

    @Test
    fun theSameWordsAtAnotherStepAreNot() {
        val line = "java.lang.IllegalArgumentException: Unknown host 'io.github.sirionero.mycar', uid:10339"
        assertFalse(RefusalRule.hostCheckSaidNo("getAppInfo", line))
        assertFalse(RefusalRule.hostCheckSaidNo("onAppCreate", line))
        assertFalse(RefusalRule.hostCheckSaidNo("getTemplate", "java.lang.SecurityException: Unrecognized host with uid 10339"))
    }

    @Test
    fun anAppsHostCheckSayingNoLasts() {
        assertEquals(RefusalRule.Kind.HOST_CHECK, RefusalRule.kind(rejected = true, accepted = false, modified = false, hostCheck = true))
    }

    @Test
    fun anyOtherFailureOfAnAppAsItCameIsSaidAsItIs() {
        // No answer in 10 s, no binder, a crash, a state error: not remembered, the car's words as before.
        assertNull(RefusalRule.kind(rejected = true, accepted = false, modified = false, hostCheck = false))
        assertNull(RefusalRule.kind(rejected = false, accepted = false, modified = false, hostCheck = false))
    }

    @Test
    fun aModifiedCopyThatSaidNoKeepsItsOwnWords() {
        // Waze patched with Morphe: whatever said no, as in 0.7.4, and its words even for the host check.
        assertEquals(RefusalRule.Kind.MODIFIED_COPY, RefusalRule.kind(rejected = true, accepted = false, modified = true, hostCheck = false))
        assertEquals(RefusalRule.Kind.MODIFIED_COPY, RefusalRule.kind(rejected = true, accepted = false, modified = true, hostCheck = true))
    }

    @Test
    fun anAppThatTookMyCarAsItsHostIsNeverRemembered() {
        // Vela Maps, the Plancia app: once the handshake went through, what fails after isn't a refusal.
        assertNull(RefusalRule.kind(rejected = true, accepted = true, modified = false, hostCheck = true))
        assertNull(RefusalRule.kind(rejected = true, accepted = true, modified = true, hostCheck = false))
    }

    @Test
    fun aFailureThatIsntARefusalIsNeverRemembered() {
        // The app's service gone (FAILED): not a refusal, whatever else is true.
        assertNull(RefusalRule.kind(rejected = false, accepted = false, modified = true, hostCheck = true))
    }

    @Test
    fun aRememberedCopySaysWhatItRefusedAs() {
        assertEquals(RefusalRule.Kind.MODIFIED_COPY, RefusalRule.remembered(modified = true))
        assertEquals(RefusalRule.Kind.HOST_CHECK, RefusalRule.remembered(modified = false))
    }
}
