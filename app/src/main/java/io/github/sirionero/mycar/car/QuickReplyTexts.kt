package io.github.sirionero.mycar.car

import androidx.car.app.model.DateTimeWithZone
import io.github.sirionero.mycar.host.remainingTime
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * The ready-made replies to a message, in myCar's language (the process's default locale: Android
 * 13 sets it to the language chosen for myCar, MyCarApplication does on Android 12): they go to
 * people, who read them the way the driver writes. The buttons show exactly what's sent. English
 * for a language not here.
 */
internal object QuickReplyTexts {

    /**
     * [around] and [inAbout] take the arrival time and the time left; [hour] and [minute] write the
     * time left, with [gap] between number and unit (none in Chinese).
     */
    data class Texts(
        val around: String, val inAbout: String, val later: String, val onMyWay: String,
        val hour: String = "h", val minute: String = "min", val gap: String = " ",
    )

    private val ENGLISH = Texts(
        "Driving, I'll be there around %s.", "Driving, I'll be there in about %s.", "Driving, I'll reply soon.", "On my way.",
    )

    private val BY_LANGUAGE = mapOf(
        "it" to Texts(
            "Sto guidando, arrivo verso le %s.", "Sto guidando, arrivo tra circa %s.",
            "Sto guidando, ti rispondo appena posso.", "Sto arrivando.",
        ),
        "es" to Texts(
            "Estoy conduciendo, llego sobre las %s.", "Estoy conduciendo, llego en unos %s.",
            "Estoy conduciendo, te respondo pronto.", "Voy de camino.",
        ),
        "fr" to Texts(
            "Je conduis, j'arrive vers %s.", "Je conduis, j'arrive dans environ %s.",
            "Je conduis, je te réponds bientôt.", "J'arrive.",
        ),
        "de" to Texts(
            "Ich fahre gerade, bin gegen %s da.", "Ich fahre gerade, bin in etwa %s da.",
            "Ich fahre gerade, melde mich bald.", "Bin unterwegs.",
        ),
        "pt" to Texts(
            "Estou ao volante, chego por volta das %s.", "Estou ao volante, chego em cerca de %s.",
            "Estou ao volante, respondo em breve.", "Estou a caminho.",
        ),
        // The time left ends with the minutes' own full stop ("5 λεπ."): none after it.
        "el" to Texts(
            "Οδηγώ, θα είμαι εκεί γύρω στις %s.", "Οδηγώ, θα είμαι εκεί σε περίπου %s",
            "Οδηγώ, θα σου απαντήσω σύντομα.", "Έρχομαι.",
            hour = "ώρ.", minute = "λεπ.",
        ),
        "ru" to Texts(
            "Я за рулём, буду около %s.", "Я за рулём, буду примерно через %s.",
            "Я за рулём, отвечу чуть позже.", "Уже еду.",
            hour = "ч", minute = "мин",
        ),
        "zh" to Texts(
            "正在开车，大约%s到。", "正在开车，大约%s后到。",
            "正在开车，稍后回复。", "我在路上了。",
            hour = "小时", minute = "分钟", gap = "",
        ),
    )

    fun forPhone(locale: Locale = Locale.getDefault()): Texts = BY_LANGUAGE[locale.language] ?: ENGLISH

    /**
     * The arrival time as the phone writes times ("18:40" in Italy, "6:40 PM" in the US), at the
     * destination's clock - a route can cross a time zone.
     */
    fun arrival(at: DateTimeWithZone?, locale: Locale = Locale.getDefault()): String {
        val time = at ?: return ""
        return runCatching {
            DateFormat.getTimeInstance(DateFormat.SHORT, locale)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(time.timeSinceEpochMillis + time.zoneOffsetSeconds * 1000L))
        }.getOrDefault("")
    }

    /** "25 min", "1 h 5 min": the time left, without the English "hr" in another language. */
    fun left(seconds: Long, locale: Locale = Locale.getDefault()): String {
        val texts = BY_LANGUAGE[locale.language] ?: return remainingTime(seconds)
        if (seconds <= 0) return ""
        val hours = TimeUnit.SECONDS.toHours(seconds)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val gap = texts.gap
        return if (hours > 0) "$hours$gap${texts.hour}$gap$minutes$gap${texts.minute}" else "$minutes$gap${texts.minute}"
    }
}
