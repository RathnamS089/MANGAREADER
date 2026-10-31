package dev.inkdot.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inkdot.InkApp
import dev.inkdot.data.Entry
import dev.inkdot.data.MangaItem
import dev.inkdot.ui.CenteredMessage
import dev.inkdot.ui.Cover
import dev.inkdot.ui.Label
import dev.inkdot.ui.MangaCard
import dev.inkdot.ui.NavBarClearance
import dev.inkdot.ui.RoundIconButton
import dev.inkdot.ui.ScreenHeader
import dev.inkdot.ui.bouncyClick
import dev.inkdot.ui.dotGrid
import dev.inkdot.ui.theme.InkRed

@Composable
fun LibraryScreen(onOpen: (MangaItem) -> Unit, onResume: (MangaItem, String) -> Unit) {
    val app = InkApp.instance
    val entries by app.library.entries.collectAsStateWithLifecycle()
    val installed by app.sources.installed.collectAsStateWithLifecycle()
    val library = remember(entries) {
        entries.values.filter { it.inLibrary }.sortedByDescending { maxOf(it.lastReadAt, it.addedAt) }
    }
    val recent = remember(entries) {
        entries.values.filter { it.lastChapterKey != null && it.lastReadAt > 0 }.maxByOrNull { it.lastReadAt }
    }
    fun headersFor(sourceId: String) = installed.find { it.id == sourceId }?.headers.orEmpty()

    var menu by remember { mutableStateOf(false) }
    fun openLocal(item: MangaItem) {
        app.library.setInLibrary(item, true)
        onOpen(item)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { openLocal(app.sources.local.open(it, isFolder = true)) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { openLocal(app.sources.local.open(it, isFolder = false)) }
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 8.dp, bottom = NavBarClearance),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenHeader("Library", "${library.size} titles") {
                Box {
                    RoundIconButton(Icons.Filled.Add, "Open local manga", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Open folder") }, onClick = { menu = false; pickFolder.launch(null) })
                        DropdownMenuItem(text = { Text("Open CBZ / ZIP") }, onClick = {
                            menu = false
                            pickFile.launch(arrayOf("application/zip", "application/x-cbz", "application/vnd.comicbook+zip", "application/octet-stream"))
                        })
                    }
                }
            }
        }
        if (recent != null) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "continue") {
                ContinueCard(recent, headersFor(recent.sourceId)) { onResume(recent.toItem(), recent.lastChapterKey!!) }
            }
        }
        if (library.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CenteredMessage("Your shelf is empty", "Browse a source from Extensions, or tap + to open a CBZ or folder.")
            }
        }
        items(library, key = { it.id }) { entry ->
            MangaCard(
                title = entry.title,
                cover = entry.cover,
                headers = headersFor(entry.sourceId),
                onClick = { onOpen(entry.toItem()) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ContinueCard(entry: Entry, headers: Map<String, String>, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .bouncyClick(pressedScale = 0.97f, onClick = onClick)
            .clip(MaterialTheme.shapes.large)
            .background(scheme.surfaceContainer)
            .dotGrid(scheme.onSurface.copy(alpha = 0.05f), 10.dp, 1.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(entry.cover, entry.title, headers, Modifier.size(width = 64.dp, height = 90.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(InkRed))
                Spacer(Modifier.width(8.dp))
                Label("Continue reading")
            }
            Spacer(Modifier.height(6.dp))
            Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(entry.lastChapterName?.ifBlank { null }, "page ${entry.lastPage + 1}").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(48.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Resume", tint = scheme.onPrimary)
        }
    }
}
