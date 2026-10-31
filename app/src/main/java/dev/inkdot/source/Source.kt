package dev.inkdot.source

import dev.inkdot.data.Chapter
import dev.inkdot.data.MangaDetails
import dev.inkdot.data.MangaItem

interface Source {
    val id: String
    val name: String
    val lang: String
    val headers: Map<String, String> get() = emptyMap()
    val supportsSearch: Boolean
    val browsable: Boolean get() = true

    suspend fun popular(page: Int): List<MangaItem>
    suspend fun search(query: String, page: Int): List<MangaItem>
    suspend fun details(manga: MangaItem): MangaDetails
    suspend fun chapters(manga: MangaItem): List<Chapter>

    /** Image models for Coil: a URL String, an android Uri or a File. */
    suspend fun pages(manga: MangaItem, chapter: Chapter): List<Any>
}
