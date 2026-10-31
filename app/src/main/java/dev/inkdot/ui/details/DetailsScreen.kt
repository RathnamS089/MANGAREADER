package dev.inkdot.ui.details

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.inkdot.InkApp
import dev.inkdot.data.Chapter
import dev.inkdot.data.MangaDetails
import dev.inkdot.data.MangaItem
import dev.inkdot.ui.BackButton
import dev.inkdot.ui.CenteredMessage
import dev.inkdot.ui.Cover
import dev.inkdot.ui.Label
import dev.inkdot.ui.LoadingDots
import dev.inkdot.ui.MissingSource
import dev.inkdot.ui.Pill
import dev.inkdot.ui.RoundIconButton
import dev.inkdot.ui.rememberImageRequest
import dev.inkdot.ui.theme.InkRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun DetailsScreen(manga: MangaItem, onBack: () -> Unit, onRead: (MangaItem, String) -> Unit) {
    val app = InkApp.instance
    val source = remember(manga.sourceId) { app.sources.get(manga.sourceId) }
    if (source == null) { MissingSource(onBack); return }

    val entries by app.library.entries.collectAsStateWithLifecycle()
    val entry = entries[manga.id]
    var details by remember { mutableStateOf<MangaDetails?>(null) }
    var chapters by remember { mutableStateOf<List<Chapter>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        error = null
        try {
            coroutineScope {
                val d = async { runCatching { source.details(manga) }.getOrNull() }
                chapters = app.sources.chapters(source, manga, refresh = reload > 0)
                details = d.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load chapters"
        }
    }

    val cover = details?.cover ?: manga.cover
    val withCover = manga.copy(cover = cover)
    val list = chapters.orEmpty()
    val resume = entry?.lastChapterKey?.let { key -> list.find { it.key == key } }
    val first = list.lastOrNull()
    val scheme = MaterialTheme.colorScheme
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(scheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 24.dp)) {
            item { Hero(withCover, details?.author, source.name, source.headers) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val target = resume ?: first
                    Pill(
                        text = when {
                            resume != null -> "Continue"
                            else -> "Start reading"
                        },
                        onClick = { target?.let { onRead(withCover, it.key) } },
                        icon = Icons.Filled.PlayArrow,
                        enabled = target != null,
                        modifier = Modifier.weight(1f),
                    )
                    val inLibrary = entry?.inLibrary == true
                    Pill(
                        text = if (inLibrary) "In library" else "Add",
                        onClick = { app.library.setInLibrary(withCover, !inLibrary) },
                        icon = if (inLibrary) Icons.Filled.Check else Icons.Filled.Add,
                        filled = false,
                    )
                }
            }
            details?.description?.let { desc ->
                item { Description(desc) }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("Chapters · ${chapters?.size ?: "…"}", Modifier.weight(1f))
                    RoundIconButton(Icons.Filled.Refresh, "Refresh", { reload++ }, background = Color.Transparent)
                }
            }
            when {
                error != null -> item { CenteredMessage("Couldn't load", error) { Pill("Retry", { reload++ }) } }
                chapters == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { LoadingDots() } }
                list.isEmpty() -> item { CenteredMessage("No chapters") }
                else -> items(list, key = { it.key }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        read = entry?.read?.contains(chapter.key) == true,
                        current = entry?.lastChapterKey == chapter.key,
                        onClick = { onRead(withCover, chapter.key) },
                    )
                }
            }
        }
        BackButton(onBack, Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp))
    }
}

@Composable
private fun Hero(manga: MangaItem, author: String?, sourceName: String, headers: Map<String, String>) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(360.dp)) {
        if (manga.cover != null) {
            AsyncImage(
                model = rememberImageRequest(manga.cover, headers),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f },
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scheme.background.copy(alpha = 0.3f), scheme.background))))
        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            Cover(manga.cover, manga.title, headers, Modifier.size(width = 118.dp, height = 170.dp))
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                Label(sourceName)
                Spacer(Modifier.height(6.dp))
                Text(manga.title, style = MaterialTheme.typography.headlineSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (author != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(author, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Description(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (expanded) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clickable { expanded = !expanded }
            .animateContentSize(spring(dampingRatio = 0.9f, stiffness = 500f)),
    )
}

@Composable
private fun ChapterRow(chapter: Chapter, read: Boolean, current: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .graphicsLayer { alpha = if (read && !current) 0.45f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(chapter.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            chapter.date?.let { Label(it.take(10)) }
        }
        if (current) Box(Modifier.size(8.dp).clip(CircleShape).background(InkRed))
    }
}
