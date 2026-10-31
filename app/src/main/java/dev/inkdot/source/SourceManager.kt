package dev.inkdot.source

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import dev.inkdot.data.Chapter
import dev.inkdot.data.MangaItem
import dev.inkdot.data.getText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Installed sources, extension repos, and a small in-memory chapter cache. */
class SourceManager(private val context: Context, private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val dir = File(context.filesDir, "sources").apply { mkdirs() }
    private val sp = context.getSharedPreferences("repos", Context.MODE_PRIVATE)
    private val chapterCache = ConcurrentHashMap<String, List<Chapter>>()

    val local = LocalSource(context)

    private val installedState = MutableStateFlow(loadInstalled())
    val installed: StateFlow<List<DeclarativeSource>> = installedState

    private val repoState = MutableStateFlow(sp.getStringSet("repos", emptySet()).orEmpty().sorted())
    val repos: StateFlow<List<String>> = repoState

    fun get(id: String): Source? = if (id == local.id) local else installedState.value.find { it.id == id }

    suspend fun chapters(source: Source, manga: MangaItem, refresh: Boolean = false): List<Chapter> {
        if (!refresh) chapterCache[manga.id]?.let { return it }
        return source.chapters(manga).also { chapterCache[manga.id] = it }
    }

    /**
     * Accepts `user/repo`, `github.com/user/repo[/tree/branch/path]`,
     * a github.com blob link, or any direct URL to an index.json.
     */
    fun normalizeRepo(input: String): String {
        val s = input.trim().trimEnd('/').removePrefix("https://").removePrefix("http://")
        Regex("""^github\.com/([^/]+)/([^/]+)/blob/([^/]+)/(.+\.json)$""").find(s)?.let { m ->
            val (user, repo, branch, path) = m.destructured
            return "https://raw.githubusercontent.com/$user/$repo/$branch/$path"
        }
        if (s.endsWith(".json")) return "https://$s"
        Regex("""^(?:github\.com/)?([\w.-]+)/([\w.-]+)(?:/tree/([^/]+)(/.*)?)?$""").find(s)?.let { m ->
            val (user, repo, branch, path) = m.destructured
            val b = branch.ifEmpty { "main" }
            return "https://raw.githubusercontent.com/$user/${repo.removeSuffix(".git")}/$b${path.trimEnd('/')}/index.json"
        }
        return "https://$s"
    }

    fun addRepo(url: String) = saveRepos((repoState.value + url).distinct().sorted())
    fun removeRepo(url: String) = saveRepos(repoState.value - url)

    private fun saveRepos(list: List<String>) {
        repoState.value = list
        sp.edit { putStringSet("repos", list.toSet()) }
    }

    suspend fun fetchRepo(url: String): RepoIndex = json.decodeFromString(http.getText(url))

    suspend fun install(repoUrl: String, entry: RepoEntry): SourceDef {
        val fileUrl = repoUrl.toHttpUrl().resolve(entry.file)?.toString() ?: error("Bad file path ${entry.file}")
        return save(http.getText(fileUrl))
    }

    suspend fun installFromUri(uri: Uri): SourceDef = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() } ?: error("Couldn't read file")
        save(text)
    }

    private suspend fun save(text: String): SourceDef = withContext(Dispatchers.IO) {
        val def = json.decodeFromString<SourceDef>(text)
        require(def.id.isNotBlank() && def.id != LocalSource.ID) { "Invalid source id" }
        File(dir, "${def.id.safeName()}.json").writeText(text)
        installedState.value = loadInstalled()
        def
    }

    fun uninstall(id: String) {
        File(dir, "${id.safeName()}.json").delete()
        installedState.value = loadInstalled()
    }

    private fun loadInstalled(): List<DeclarativeSource> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { DeclarativeSource(json.decodeFromString<SourceDef>(f.readText()), http) }.getOrNull() }
            .sortedBy { it.name.lowercase() }

    private fun String.safeName() = replace(Regex("""[^\w.-]"""), "_")
}
