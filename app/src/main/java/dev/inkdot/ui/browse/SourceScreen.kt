package dev.inkdot.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.inkdot.InkApp
import dev.inkdot.data.MangaItem
import dev.inkdot.ui.BackButton
import dev.inkdot.ui.CenteredMessage
import dev.inkdot.ui.LoadingDots
import dev.inkdot.ui.MangaCard
import dev.inkdot.ui.MissingSource
import dev.inkdot.ui.Pill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
fun SourceScreen(sourceId: String, onBack: () -> Unit, onOpen: (MangaItem) -> Unit) {
    val source = remember(sourceId) { InkApp.instance.sources.get(sourceId) }
    if (source == null) { MissingSource(onBack); return }

    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val items = remember { mutableStateListOf<MangaItem>() }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var end by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    fun load(reset: Boolean) {
        if (reset) { job?.cancel(); items.clear(); page = 1; end = false } else if (loading || end) return
        loading = true
        error = null
        job = scope.launch {
            try {
                val q = submitted
                val result = if (q.isBlank()) source.popular(page) else source.search(q, page)
                val known = items.mapTo(HashSet()) { it.key }
                val fresh = result.filter { it.key !in known }
                items.addAll(fresh)
                if (fresh.isEmpty()) end = true
                page++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(submitted) { load(reset = true) }

    val grid = rememberLazyGridState()
    LaunchedEffect(grid) {
        snapshotFlow {
            val info = grid.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }.distinctUntilChanged().filter { it }.collect { if (items.isNotEmpty()) load(reset = false) }
    }

    val insets = WindowInsets.statusBars.asPaddingValues()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scheme = MaterialTheme.colorScheme
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        state = grid,
        modifier = Modifier.fillMaxSize().background(scheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = insets.calculateTopPadding() + 8.dp, bottom = bottom + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack)
                Spacer(Modifier.width(12.dp))
                if (source.supportsSearch) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).height(52.dp).clip(CircleShape),
                        placeholder = { Text("Search ${source.name}") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; submitted = "" }) { Icon(Icons.Filled.Close, "Clear") }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitted = query.trim(); focus.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = scheme.surfaceContainerHigh,
                            unfocusedContainerColor = scheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                } else {
                    Text(source.name, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        items(items, key = { it.key }) { manga ->
            MangaCard(manga.title, manga.cover, source.headers, onClick = { onOpen(manga) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                when {
                    error != null -> CenteredMessage("Couldn't load", error) { Pill("Retry", { load(reset = items.isEmpty()) }) }
                    loading -> LoadingDots()
                    end && items.isEmpty() -> CenteredMessage("Nothing found")
                }
            }
        }
    }
}
