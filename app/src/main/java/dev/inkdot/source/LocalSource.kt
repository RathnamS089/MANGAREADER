package dev.inkdot.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.inkdot.data.Chapter
import dev.inkdot.data.MangaDetails
import dev.inkdot.data.MangaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Reads manga from storage: a CBZ/ZIP file, or a folder holding images,
 * chapter sub-folders and/or CBZ files. Keys are prefixed with their kind.
 */
class LocalSource(private val context: Context) : Source {
    override val id = ID
    override val name = "Local files"
    override val lang = "—"
    override val supportsSearch = false
    override val browsable = false

    fun open(uri: Uri, isFolder: Boolean): MangaItem {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val doc = if (isFolder) DocumentFile.fromTreeUri(context, uri) else DocumentFile.fromSingleUri(context, uri)
        val title = doc?.name?.let { if (isFolder) it else it.substringBeforeLast('.') } ?: "Untitled"
        return MangaItem(id, (if (isFolder) "tree:" else "file:") + uri, title)
    }

    override suspend fun popular(page: Int) = emptyList<MangaItem>()
    override suspend fun search(query: String, page: Int) = emptyList<MangaItem>()
    override suspend fun details(manga: MangaItem) =
        MangaDetails(description = if (manga.key.startsWith("tree:")) "Folder on this device" else "Archive on this device")

    override suspend fun chapters(manga: MangaItem): List<Chapter> = withContext(Dispatchers.IO) {
        val (kind, uriString) = manga.key.split(":", limit = 2)
        if (kind == "file") return@withContext listOf(Chapter("zip:$uriString", manga.title))

        val root = DocumentFile.fromTreeUri(context, Uri.parse(uriString)) ?: return@withContext emptyList()
        val children = root.listFiles()
        val nested = children
            .filter { it.isDirectory || (it.isFile && it.name.isArchive()) }
            .sortedWith(compareBy(NaturalOrder) { it.name.orEmpty() })
            .map { doc ->
                val label = doc.name.orEmpty().let { if (doc.isDirectory) it else it.substringBeforeLast('.') }
                Chapter((if (doc.isDirectory) "dir:" else "zip:") + doc.uri, label)
            }
        val loose = if (children.any { it.isFile && it.name.isImage() }) listOf(Chapter("dir:$uriString", manga.title)) else emptyList()
        (loose + nested).reversed()
    }

    override suspend fun pages(manga: MangaItem, chapter: Chapter): List<Any> = withContext(Dispatchers.IO) {
        val (kind, uriString) = chapter.key.split(":", limit = 2)
        val uri = Uri.parse(uriString)
        if (kind == "dir") {
            DocumentFile.fromTreeUri(context, uri)?.listFiles().orEmpty()
                .filter { it.isFile && it.name.isImage() }
                .sortedWith(compareBy(NaturalOrder) { it.name.orEmpty() })
                .map { it.uri }
        } else {
            extract(uri)
        }
    }

    /** Unpacks a CBZ into the cache once; later opens reuse it. */
    private fun extract(uri: Uri): List<File> {
        val out = File(context.cacheDir, "local/" + Integer.toHexString(uri.toString().hashCode()))
        val done = File(out, ".done")
        if (!done.exists()) {
            out.deleteRecursively()
            out.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.isImage()) {
                            val safe = entry.name.replace('/', '_').replace('\\', '_')
                            File(out, safe).outputStream().use { zip.copyTo(it) }
                        }
                        entry = zip.nextEntry
                    }
                }
            } ?: error("Couldn't open archive")
            done.createNewFile()
        }
        return out.listFiles().orEmpty()
            .filter { it.name.isImage() }
            .sortedWith(compareBy(NaturalOrder) { it.name })
    }

    private fun String?.isImage() =
        this?.substringAfterLast('.', "")?.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif")

    private fun String?.isArchive() =
        this?.substringAfterLast('.', "")?.lowercase() in setOf("cbz", "zip")

    companion object {
        const val ID = "local"
    }
}
