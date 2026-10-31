package dev.inkdot.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Entry(
    val sourceId: String,
    val key: String,
    val title: String,
    val cover: String? = null,
    val inLibrary: Boolean = false,
    val addedAt: Long = 0,
    val lastReadAt: Long = 0,
    val lastChapterKey: String? = null,
    val lastChapterName: String? = null,
    val lastPage: Int = 0,
    val read: Set<String> = emptySet(),
) {
    val id: String get() = "$sourceId|$key"
    fun toItem() = MangaItem(sourceId, key, title, cover)
}

/** Library + reading progress, kept as one small JSON file. */
class Library(context: Context) {
    private val file = File(context.filesDir, "library.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val state = MutableStateFlow(load())
    val entries: StateFlow<Map<String, Entry>> = state

    fun get(id: String): Entry? = state.value[id]

    fun setInLibrary(manga: MangaItem, value: Boolean) = mutate(manga) {
        it.copy(
            inLibrary = value,
            addedAt = if (value) System.currentTimeMillis() else it.addedAt,
            title = manga.title,
            cover = manga.cover ?: it.cover,
        )
    }

    fun saveProgress(manga: MangaItem, chapterKey: String, chapterName: String, page: Int, finished: Boolean) =
        mutate(manga) {
            val sameChapter = it.lastChapterKey == chapterKey
            it.copy(
                lastReadAt = System.currentTimeMillis(),
                lastChapterKey = chapterKey,
                lastChapterName = chapterName.ifBlank { if (sameChapter) it.lastChapterName.orEmpty() else "" },
                lastPage = page,
                read = if (finished) it.read + chapterKey else it.read,
            )
        }

    private fun mutate(manga: MangaItem, change: (Entry) -> Entry) {
        state.update { map ->
            val current = map[manga.id] ?: Entry(manga.sourceId, manga.key, manga.title, manga.cover)
            map + (manga.id to change(current))
        }
        persist()
    }

    private fun persist() = scope.launch {
        writeLock.withLock {
            val tmp = File(file.parentFile, "library.json.tmp")
            tmp.writeText(json.encodeToString(state.value.values.toList()))
            tmp.renameTo(file)
        }
    }

    private fun load(): Map<String, Entry> = runCatching {
        json.decodeFromString<List<Entry>>(file.readText()).associateBy { it.id }
    }.getOrDefault(emptyMap())
}
