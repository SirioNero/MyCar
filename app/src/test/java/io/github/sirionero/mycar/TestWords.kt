package io.github.sirionero.mycar

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * The English of res/values for [Words] in JVM tests, which have no Android resources: code that
 * says something through Words says it as on an English phone. Call install() before such code.
 */
object TestWords {
    fun install() {
        if (Words.lookup != null) return
        val strings = HashMap<String, String>()
        val plurals = HashMap<String, Map<String, String>>()
        val folder = listOf(File("src/main/res/values"), File("app/src/main/res/values")).first { it.isDirectory }
        folder.listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") }!!.forEach { file ->
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
            val nodes = root.childNodes
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as? Element ?: continue
                when (e.tagName) {
                    "string" -> strings[e.getAttribute("name")] = android(e.textContent)
                    "plurals" -> {
                        val items = e.getElementsByTagName("item")
                        plurals[e.getAttribute("name")] = (0 until items.length).associate { j ->
                            val item = items.item(j) as Element
                            item.getAttribute("quantity") to android(item.textContent)
                        }
                    }
                }
            }
        }
        // Resource ids are unique across kinds, so one map holds both.
        val names = HashMap<Int, String>()
        for (kind in listOf(R.string::class.java, R.plurals::class.java)) {
            for (field in kind.fields) names[field.getInt(null)] = field.name
        }
        Words.lookup = { id, count, args ->
            val name = names.getValue(id)
            val text = if (count == null) {
                strings.getValue(name)
            } else {
                plurals.getValue(name).let { forms -> forms[if (count == 1) "one" else "other"] ?: forms.getValue("other") }
            }
            if (args.isEmpty()) text else String.format(Locale.ENGLISH, text, *args)
        }
    }

    /** As aapt reads a string: spaces run together unless the whole text is quoted, then the escapes. */
    private fun android(raw: String): String {
        val quoted = raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')
        val text = if (quoted) raw.substring(1, raw.length - 1) else raw.trim().replace(Regex("\\s+"), " ")
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\\' || i + 1 >= text.length) {
                out.append(c)
                i++
                continue
            }
            when (val next = text[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'u' -> {
                    out.append(text.substring(i + 2, i + 6).toInt(16).toChar())
                    i += 4
                }
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }
}
