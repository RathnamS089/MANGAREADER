package dev.inkdot.source

import dev.inkdot.data.Http
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Live check of the sample extension against the public MangaDex API. */
class MangaDexSourceTest {
    @Test
    fun popularToPages() = runBlocking {
        val json = Json { ignoreUnknownKeys = true }
        val def = json.decodeFromString<SourceDef>(File("../extensions-repo/sources/mangadex.json").readText())
        val http = OkHttpClient.Builder().addInterceptor { it.proceed(it.request().newBuilder().header("User-Agent", Http.USER_AGENT).build()) }.build()
        val source = DeclarativeSource(def, http)

        val popular = source.popular(1)
        println("popular: ${popular.size} · ${popular.take(3).map { it.title to it.cover }}")
        assertTrue(popular.isNotEmpty())

        val search = source.search("frieren", 1)
        println("search: ${search.take(3).map { it.title }}")
        assertTrue(search.isNotEmpty())

        val manga = popular.first { source.chapters(it).isNotEmpty() }
        val details = source.details(manga)
        println("details: author=${details.author} cover=${details.cover} desc=${details.description?.take(60)}")

        val chapters = source.chapters(manga)
        println("chapters: ${chapters.size} · ${chapters.take(3).map { it.name }}")
        assertTrue(chapters.isNotEmpty())

        val pages = source.pages(manga, chapters.last())
        println("pages: ${pages.size} · ${pages.firstOrNull()}")
        assertTrue(pages.isNotEmpty())
    }
}
