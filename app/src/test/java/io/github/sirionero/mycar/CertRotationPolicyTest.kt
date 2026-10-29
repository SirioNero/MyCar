package io.github.sirionero.mycar

import io.github.sirionero.mycar.CertRotationPolicy.Notice
import io.github.sirionero.mycar.CertRotationPolicy.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sequences a car can put myCar through, certificate by certificate. */
class CertRotationPolicyTest {

    private val both = listOf("aasdk", "revived")
    private val hard = CertVerdict.Refused(hard = true, why = "auth -2")
    private val hangUp = CertVerdict.Refused(hard = false, why = "dropped during TLS")

    private fun decide(state: State, verdict: CertVerdict, presented: String?, present: List<String> = both, own: Boolean = false) =
        CertRotationPolicy.decide(state, verdict, presented, own, present)

    @Test
    fun `turned down, the spare goes in use`() {
        val out = decide(State(), hard, "aasdk")
        assertEquals(Notice.SWITCHED, out.notice)
        assertEquals("revived", out.switchTo)
        assertEquals(setOf("aasdk"), out.state.refused)
    }

    @Test
    fun `a hang-up on a certificate the car took before changes nothing`() {
        val out = decide(State(accepted = "aasdk"), hangUp, "aasdk")
        assertEquals(Notice.NONE, out.notice)
        assertNull(out.switchTo)
    }

    @Test
    fun `a hang-up on a certificate never taken counts as turned down`() {
        val out = decide(State(), hangUp, "aasdk")
        assertEquals("revived", out.switchTo)
    }

    @Test
    fun `the car's clock changes nothing`() {
        val state = State(accepted = "aasdk")
        val out = decide(state, CertVerdict.Clock, "aasdk")
        assertEquals(Notice.CLOCK, out.notice)
        assertNull(out.switchTo)
        assertEquals(state, out.state)
    }

    @Test
    fun `every one turned down goes back to the first, then stops switching`() {
        val first = decide(State(), hard, "aasdk")
        val second = decide(first.state, hard, "revived")
        assertEquals(Notice.EXHAUSTED, second.notice)
        assertEquals("aasdk", second.switchTo)
        assertTrue(second.state.exhausted)
        // No endless round: the next refusal keeps the first and says so again.
        val third = decide(second.state, hard, "aasdk")
        assertEquals(Notice.EXHAUSTED, third.notice)
        assertNull(third.switchTo)
    }

    @Test
    fun `with no spare on the phone, it says to get one`() {
        val out = decide(State(), hard, "aasdk", present = listOf("aasdk"))
        assertEquals(Notice.NO_SPARE, out.notice)
        assertNull(out.switchTo)
    }

    @Test
    fun `a catalog certificate not downloaded yet comes before starting over`() {
        val catalog = both + "carservice"
        val first = CertRotationPolicy.decide(State(), hard, "aasdk", own = false, present = both, catalog = catalog)
        assertEquals("revived", first.switchTo)
        val second = CertRotationPolicy.decide(first.state, hard, "revived", own = false, present = both, catalog = catalog)
        assertEquals(Notice.NO_SPARE, second.notice)
        assertNull(second.switchTo)
        // Downloaded, it's the next; turned down too, every one has been.
        val third = CertRotationPolicy.decide(second.state, hard, "carservice", own = false, present = catalog, catalog = catalog)
        assertEquals(Notice.EXHAUSTED, third.notice)
        assertEquals("aasdk", third.switchTo)
    }

    @Test
    fun `turned down every one before a new one joined the catalog, it says to get it`() {
        val catalog = both + "carservice"
        val exhausted = State(refused = both.toSet(), exhausted = true)
        assertEquals(Notice.NO_SPARE, CertRotationPolicy.shown(Notice.EXHAUSTED, both, catalog))
        assertEquals(Notice.EXHAUSTED, CertRotationPolicy.shown(Notice.EXHAUSTED, catalog, catalog))
        assertEquals(Notice.SWITCHED, CertRotationPolicy.shown(Notice.SWITCHED, both, catalog))
        val out = CertRotationPolicy.decide(exhausted, hard, "aasdk", own = false, present = both, catalog = catalog)
        assertEquals(Notice.NO_SPARE, out.notice)
    }

    @Test
    fun `the driver's own certificate is never swapped`() {
        val out = decide(State(), hard, null, own = true)
        assertEquals(Notice.OWN_REFUSED, out.notice)
        assertNull(out.switchTo)
    }

    @Test
    fun `no certificate at all says to get one`() {
        val out = decide(State(), hard, null)
        assertEquals(Notice.NO_CERT, out.notice)
        assertNull(out.switchTo)
    }

    @Test
    fun `accepted, the round starts over`() {
        val out = decide(State(refused = setOf("aasdk"), exhausted = true), CertVerdict.Accepted, "revived")
        assertEquals(State(accepted = "revived"), out.state)
        assertEquals(Notice.NONE, out.notice)
    }

    @Test
    fun `starting over lets every one be tried again`() {
        val state = CertRotationPolicy.startOver(State(accepted = "aasdk", refused = setOf("aasdk", "revived"), exhausted = true))
        assertEquals(State(accepted = "aasdk"), state)
        assertEquals("revived", decide(state, hard, "aasdk").switchTo)
    }
}
