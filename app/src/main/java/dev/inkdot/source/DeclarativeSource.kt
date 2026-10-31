package dev.inkdot.source

import dev.inkdot.data.Chapter
import dev.inkdot.data.MangaDetails
import dev.inkdot.data.MangaItem
import dev.inkdot.data.getText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/** Runs a [SourceDef]: fetch a URL, pick a list of items, read fields off each one. */
class DeclarativeSource(val def: SourceDef, private val http: OkHttpClient) : Source {
    override val id get() = def.id
    override val name get() = def.name
    override val lang get() = def.lang
    override val headers get() = def.headers
    override val supportsSearch get() = def.search != null

    private val isJson = def.type.equals("json", ignoreCase = true)
    private val urlVar = Regex("""\{(\w+)}""")

    override suspend fun popular(page: Int) = list(def.popular, vars(page = page))

    override suspend fun search(query: String, page: Int): List<MangaItem> =
        def.search?.let { list(it, vars(page = page, query = query)) } ?: emptyList()

    override suspend fun details(manga: MangaItem): MangaDetails {
        val spec = def.details ?: return MangaDetails(cover = manga.cover)
        val doc = load(url(spec.url, vars(key = manga.key)))
        return MangaDetails(
            description = field(doc, doc, spec.description),
            author = field(doc, doc, spec.author),
            cover = field(doc, doc, spec.cover) ?: manga.cover,
        )
    }

    override suspend fun chapters(manga: MangaItem): List<Chapter> {
        val spec = def.chapters
        val doc = load(url(spec.url, vars(key = manga.key)))
        val chapters = withContext(Dispatchers.Default) {
            items(doc, spec.list).mapNotNull { item ->
                val key = field(doc, item, spec.key) ?: return@mapNotNull null
                Chapter(key, field(doc, item, spec.name) ?: "Chapter", field(doc, item, spec.date))
            }.distinctBy { it.key }
        }
        return if (spec.reverse) chapters.reversed() else chapters
    }

    override suspend fun pages(manga: MangaItem, chapter: Chapter): List<Any> {
        val spec = def.pages
        val doc = load(url(spec.url, vars(key = chapter.key, mangaKey = manga.key)))
        return withContext(Dispatchers.Default) {
            items(doc, spec.list).mapNotNull { field(doc, it, spec.image) }
        }
    }

    private suspend fun list(spec: ListSpec, v: Map<String, String>): List<MangaItem> {
        val doc = load(url(spec.url, v))
        return withContext(Dispatchers.Default) {
            items(doc, spec.list).mapNotNull { item ->
                val key = field(doc, item, spec.key) ?: return@mapNotNull null
                MangaItem(id, key, field(doc, item, spec.title) ?: "Untitled", field(doc, item, spec.cover))
            }.distinctBy { it.key }
        }
    }

    private fun vars(page: Int = 1, query: String = "", key: String = "", mangaKey: String = "") = mapOf(
        "base" to def.baseUrl.trimEnd('/'),
        "page" to "$page",
        "offset" to "${(page - 1) * def.pageSize}",
        "query" to URLEncoder.encode(query, "UTF-8"),
        "key" to key,
        "mangaKey" to mangaKey,
    )

    private fun url(template: String, v: Map<String, String>) =
        urlVar.replace(template) { v[it.groupValues[1]] ?: it.value }

    private suspend fun load(url: String): Any {
        val body = http.getText(url, def.headers)
        return withContext(Dispatchers.Default) {
            if (isJson) Json.parseToJsonElement(body) else Jsoup.parse(body, url)
        }
    }

    private fun items(doc: Any, selector: String): List<Any> = when (doc) {
        is Document -> doc.select(selector).toList()
        is JsonElement -> (JsonPath.resolve(doc, selector, doc) as? JsonArray)?.toList().orEmpty()
        else -> emptyList()
    }

    private fun field(doc: Any, item: Any, spec: String?): String? = when (item) {
        is Element -> HtmlSelector.get(item, spec)
        is JsonElement -> JsonPath.template(spec, item, doc as JsonElement)
        else -> null
    }
}
