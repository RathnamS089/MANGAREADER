package dev.inkdot.source

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.nodes.Element

/**
 * HTML field selectors: `css@attr`, `css` (text), or `@attr` on the item itself.
 * `|` separates fallbacks. URL attributes are made absolute.
 */
object HtmlSelector {
    fun get(element: Element, spec: String?): String? {
        spec ?: return null
        for (alt in spec.split('|')) {
            val s = alt.trim()
            val at = s.lastIndexOf('@')
            val css = if (at >= 0) s.substring(0, at).trim() else s
            val attr = if (at >= 0) s.substring(at + 1).trim() else "text"
            val target = if (css.isEmpty()) element else element.selectFirst(css) ?: continue
            val value = when (attr) {
                "text" -> target.text()
                "own" -> target.ownText()
                "html" -> target.html()
                else -> target.absUrl(attr).ifBlank { target.attr(attr) }
            }.trim()
            if (value.isNotBlank()) return value
        }
        return null
    }
}

/**
 * Minimal JSON paths: `a.b[0].c`, `[type=cover_art]` filters, `*` (first value),
 * `$` (current item) and `$root` (whole response). Field specs are templates such
 * as `Ch. {attributes.chapter}`; a spec without braces is a single path.
 */
object JsonPath {
    private val token = Regex("""([^.\[\]]+)|\[([^\]]*)]""")
    private val placeholder = Regex("""\{([^}]*)}""")
    private val spaces = Regex("""\s+""")

    fun resolve(start: JsonElement?, path: String, root: JsonElement): JsonElement? {
        var p = path.trim()
        var cur = start
        when {
            p == "$" -> return start
            p.startsWith("\$root") -> { cur = root; p = p.removePrefix("\$root").removePrefix(".") }
            p.startsWith("$.") -> p = p.substring(2)
        }
        for (m in token.findAll(p)) {
            val node = cur ?: return null
            val name = m.groups[1]?.value
            val bracket = m.groups[2]?.value
            cur = when {
                name == "*" -> when (node) {
                    is JsonObject -> node.values.firstOrNull()
                    is JsonArray -> node.firstOrNull()
                    else -> null
                }
                name != null -> (node as? JsonObject)?.get(name)
                bracket?.toIntOrNull() != null -> (node as? JsonArray)?.getOrNull(bracket.toInt())
                bracket != null && '=' in bracket -> {
                    val (k, v) = bracket.split('=', limit = 2)
                    (node as? JsonArray)?.firstOrNull { text((it as? JsonObject)?.get(k)) == v }
                }
                else -> null
            }
        }
        return cur
    }

    fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    fun template(spec: String?, item: JsonElement, root: JsonElement): String? {
        spec ?: return null
        val t = if ('{' in spec) spec else "{$spec}"
        val out = placeholder.replace(t) { m ->
            m.groupValues[1].split('|').firstNotNullOfOrNull { path ->
                text(resolve(item, path, root))?.takeIf { it.isNotBlank() }
            }.orEmpty()
        }
        return out.replace(spaces, " ").trim().ifBlank { null }
    }
}

/** Sorts "Chapter 2" before "Chapter 10". */
val NaturalOrder = Comparator<String> { a, b ->
    val chunk = Regex("""\d+|\D+""")
    val ca = chunk.findAll(a.lowercase()).map { it.value }.toList()
    val cb = chunk.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ca.size, cb.size)) {
        val x = ca[i]; val y = cb[i]
        val nx = x.toBigIntegerOrNull(); val ny = y.toBigIntegerOrNull()
        val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y)
        if (c != 0) return@Comparator c
    }
    ca.size - cb.size
}
