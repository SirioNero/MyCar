package io.github.sirionero.mycar.xonos.data

/**
 * Il testo di un brano. Con i tempi (formato LRC) ogni riga sa quando si canta; senza, è solo
 * testo da leggere.
 */
data class Lyrics(val lines: List<Line>, val synced: Boolean) {

    /** Una riga del testo; [timeMs] è quando comincia (0 per i testi senza tempi). */
    data class Line(val timeMs: Long, val text: String)

    /** La riga che si sta cantando a [positionMs]: -1 prima della prima, e sempre per i testi senza tempi. */
    fun indexAt(positionMs: Long): Int {
        if (!synced) return -1
        // L'ultima riga cominciata: le righe sono in ordine di tempo.
        var low = 0
        var high = lines.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    companion object {
        // [mm:ss], [mm:ss.xx] o [mm:ss:xx]: il tempo di una riga.
        private val TIME = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

        // [ar:...], [ti:...], [offset:...]: le informazioni in testa al file.
        private val TAG = Regex("""^\[([a-zA-Z#][a-zA-Z0-9_]*):?([^\]]*)]\s*$""")

        // <mm:ss.xx>: i tempi di ogni parola (LRC "esteso"), che qui non servono.
        private val WORD_TIME = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>\s?""")

        /** Il testo dai due campi di Music Assistant: quello con i tempi se c'è, altrimenti quello semplice. */
        fun of(plain: String?, lrc: String?): Lyrics? = parse(lrc) ?: parse(plain)

        /**
         * Legge un testo LRC; se meno di metà delle righe ha un tempo lo tratta come testo semplice
         * (un "[2:30]" in mezzo a un testo normale non lo rende sincronizzato).
         */
        fun parse(text: String?): Lyrics? {
            if (text.isNullOrBlank()) return null
            val rows = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
            var offsetMs = 0L
            val timed = mutableListOf<Line>()
            var content = 0
            var lastTime = 0L
            for (raw in rows) {
                val row = raw.trim()
                val tag = TAG.matchEntire(row)
                if (tag != null) {
                    if (tag.groupValues[1].equals("offset", ignoreCase = true)) {
                        offsetMs = tag.groupValues[2].trim().toLongOrNull() ?: offsetMs
                    }
                    continue
                }
                if (row.isEmpty()) continue
                content++
                var rest = row
                val times = mutableListOf<Long>()
                while (true) {
                    val match = TIME.matchAt(rest, 0) ?: break
                    times += timeOf(match)
                    rest = rest.substring(match.range.last + 1).trimStart()
                }
                val line = rest.replace(WORD_TIME, "").trim()
                if (times.isEmpty()) {
                    // Una riga senza tempo resta dopo quella che la precede.
                    timed += Line(lastTime, line)
                } else {
                    times.forEach { timed += Line(it, line) }
                    lastTime = times.last()
                }
            }
            if (content == 0) return null
            val withTime = rows.count { TIME.matchAt(it.trim(), 0) != null }
            if (withTime * 2 < content) {
                val lines = rows.map { it.trim() }.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() }
                return Lyrics(lines.map { Line(0, it) }, synced = false)
            }
            // L'offset positivo anticipa il testo, come nei lettori LRC.
            val lines = timed.sortedBy { it.timeMs }.map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0)) }
            return Lyrics(lines, synced = true)
        }

        private fun timeOf(match: MatchResult): Long {
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3]
            val ms = when (fraction.length) {
                0 -> 0L
                1 -> fraction.toLong() * 100
                2 -> fraction.toLong() * 10
                else -> fraction.take(3).toLong()
            }
            return (minutes * 60 + seconds) * 1000 + ms
        }
    }
}
