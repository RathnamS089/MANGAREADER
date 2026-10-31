package dev.inkdot.source

import kotlinx.serialization.Serializable

/**
 * A source extension: a small JSON file hosted in a GitHub repo.
 * See extensions-repo/README.md for the format.
 */
@Serializable
data class SourceDef(
    val id: String,
    val name: String,
    val lang: String = "en",
    val version: Int = 1,
    /** "html" (CSS selectors, parsed with jsoup) or "json" (dot paths into an API response). */
    val type: String = "html",
    val baseUrl: String = "",
    val pageSize: Int = 20,
    val headers: Map<String, String> = emptyMap(),
    val popular: ListSpec,
    val search: ListSpec? = null,
    val details: DetailsSpec? = null,
    val chapters: ChapterSpec,
    val pages: PageSpec,
)

@Serializable
data class ListSpec(
    val url: String,
    val list: String,
    val key: String,
    val title: String,
    val cover: String? = null,
)

@Serializable
data class DetailsSpec(
    val url: String = "{key}",
    val description: String? = null,
    val author: String? = null,
    val cover: String? = null,
)

@Serializable
data class ChapterSpec(
    val url: String = "{key}",
    val list: String,
    val key: String,
    val name: String,
    val date: String? = null,
    /** Set when the site lists chapters oldest first. */
    val reverse: Boolean = false,
)

@Serializable
data class PageSpec(
    val url: String = "{key}",
    val list: String,
    val image: String,
)

@Serializable
data class RepoIndex(
    val name: String = "Repository",
    val sources: List<RepoEntry> = emptyList(),
)

@Serializable
data class RepoEntry(
    val id: String,
    val name: String,
    val lang: String = "en",
    val version: Int = 1,
    val file: String,
    val description: String? = null,
)
