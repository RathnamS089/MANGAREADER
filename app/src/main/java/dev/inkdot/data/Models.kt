package dev.inkdot.data

/** A title as listed by a source. [key] is whatever the source needs to find it again (a URL or an API id). */
data class MangaItem(
    val sourceId: String,
    val key: String,
    val title: String,
    val cover: String? = null,
) {
    val id: String get() = "$sourceId|$key"
}

data class MangaDetails(
    val description: String? = null,
    val cover: String? = null,
    val author: String? = null,
)

/** Sources return chapters newest first. */
data class Chapter(
    val key: String,
    val name: String,
    val date: String? = null,
)
