package io.github.sirionero.mycar.car

import androidx.car.app.model.DateTimeWithZone
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** The ready-made replies go out in the phone's language (see QuickReplyTexts). */
class QuickReplyTextsTest {

    @Test
    fun `an Italian phone sends Italian`() {
        val texts = QuickReplyTexts.forPhone(Locale.ITALY)
        assertEquals("Sto arrivando.", texts.onMyWay)
        assertEquals("Sto guidando, arrivo tra circa 1 h 5 min.", texts.inAbout.format(QuickReplyTexts.left(3_900, Locale.ITALY)))
    }

    @Test
    fun `Greek, Russian and Chinese write the time left their own way`() {
        assertEquals("Οδηγώ, θα είμαι εκεί σε περίπου 1 ώρ. 5 λεπ.", QuickReplyTexts.forPhone(Locale.forLanguageTag("el")).inAbout.format(QuickReplyTexts.left(3_900, Locale.forLanguageTag("el"))))
        assertEquals("Я за рулём, буду примерно через 25 мин.", QuickReplyTexts.forPhone(Locale.forLanguageTag("ru")).inAbout.format(QuickReplyTexts.left(1_500, Locale.forLanguageTag("ru"))))
        assertEquals("正在开车，大约1小时5分钟后到。", QuickReplyTexts.forPhone(Locale.SIMPLIFIED_CHINESE).inAbout.format(QuickReplyTexts.left(3_900, Locale.SIMPLIFIED_CHINESE)))
    }

    @Test
    fun `a language not there sends English`() {
        assertEquals("On my way.", QuickReplyTexts.forPhone(Locale.JAPAN).onMyWay)
        assertEquals("1 hr 5 min", QuickReplyTexts.left(3_900, Locale.US))
        assertEquals("25 min", QuickReplyTexts.left(1_500, Locale.US))
    }

    @Test
    fun `the arrival time is written the phone's way, at the destination's clock`() {
        // 17:40 UTC, at a destination one hour ahead.
        val at = DateTimeWithZone.create(1_700_000_000_000L - 1_700_000_000_000L % 86_400_000L + 63_600_000L, 3_600, "CET")
        assertEquals("18:40", QuickReplyTexts.arrival(at, Locale.ITALY))
    }
}
