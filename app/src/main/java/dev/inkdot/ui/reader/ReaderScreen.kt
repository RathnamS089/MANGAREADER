package dev.inkdot.ui.reader

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Dimension
import coil3.size.Precision
import dev.inkdot.InkApp
import dev.inkdot.data.Chapter
import dev.inkdot.data.MangaItem
import dev.inkdot.data.ReaderMode
import dev.inkdot.ui.BackButton
import dev.inkdot.ui.Label
import dev.inkdot.ui.LoadingDots
import dev.inkdot.ui.MissingSource
import dev.inkdot.ui.Pill
import dev.inkdot.ui.RoundIconButton
import dev.inkdot.ui.bouncyClick
import dev.inkdot.ui.theme.InkRed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import coil3.size.Size as CoilSize

private val PanelColor = Color(0xEB141414)

@Composable
fun ReaderScreen(manga: MangaItem, initialChapter: String, onBack: () -> Unit) {
    val app = InkApp.instance
    val source = remember(manga.sourceId) { app.sources.get(manga.sourceId) }
    if (source == null) { MissingSource(onBack); return }

    var chapterKey by rememberSaveable { mutableStateOf(initialChapter) }
    var chapters by remember { mutableStateOf(emptyList<Chapter>()) }
    LaunchedEffect(Unit) { chapters = runCatching { app.sources.chapters(source, manga) }.getOrDefault(emptyList()) }

    val index = chapters.indexOfFirst { it.key == chapterKey }
    val chapter = chapters.getOrNull(index) ?: Chapter(chapterKey, "")
    val next = if (index > 0) chapters[index - 1] else null
    val prev = if (index >= 0) chapters.getOrNull(index + 1) else null

    val mode by app.prefs.readerMode.flow.collectAsStateWithLifecycle()
    val glass by app.prefs.glassFlip.flow.collectAsStateWithLifecycle()
    var uiVisible by remember { mutableStateOf(false) }

    var pages by remember(chapterKey) { mutableStateOf<List<Any>?>(null) }
    var error by remember(chapterKey) { mutableStateOf<String?>(null) }
    var retry by remember(chapterKey) { mutableIntStateOf(0) }
    LaunchedEffect(chapterKey, retry) {
        error = null
        try {
            val loaded = source.pages(manga, Chapter(chapterKey, ""))
            if (loaded.isEmpty()) error = "No pages in this chapter" else pages = loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load pages"
        }
    }

    val startPage = remember(chapterKey) {
        app.library.get(manga.id)?.takeIf { it.lastChapterKey == chapterKey }?.lastPage ?: 0
    }
    var currentPage by remember(chapterKey) { mutableIntStateOf(startPage) }
    LaunchedEffect(chapterKey, currentPage, pages, chapter.name) {
        val p = pages ?: return@LaunchedEffect
        delay(250)
        app.library.saveProgress(manga, chapterKey, chapter.name, currentPage.coerceAtMost(p.lastIndex), currentPage >= p.lastIndex)
    }

    Immersive(uiVisible)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val p = pages
        if (p == null) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { uiVisible = !uiVisible } },
                contentAlignment = Alignment.Center,
            ) {
                val e = error
                if (e != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(e, color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Pill("Retry", { retry++ })
                    }
                } else {
                    LoadingDots(color = Color.White)
                }
            }
            TopBar(true, manga.title, chapter.name, onBack)
        } else key(chapterKey, mode) {
            val scope = rememberCoroutineScope()
            val start = remember { currentPage.coerceIn(0, p.lastIndex) }
            val end: @Composable () -> Unit = {
                ChapterEnd(chapter.name, next, onNext = { next?.let { chapterKey = it.key } }, onBack = onBack)
            }
            val seek: (Int) -> Unit
            if (mode == ReaderMode.VERTICAL) {
                val list = rememberLazyListState(start)
                LaunchedEffect(list) {
                    snapshotFlow { list.firstVisibleItemIndex }.collect { currentPage = it.coerceAtMost(p.lastIndex) }
                }
                WebtoonReader(list, p, source.headers, onTap = { uiVisible = !uiVisible }, end = end)
                seek = { scope.launch { list.scrollToItem(it) } }
            } else {
                val pager = rememberPagerState(start) { p.size + 1 }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { currentPage = it.coerceAtMost(p.lastIndex) }
                }
                PagedReader(pager, p, source.headers, rtl = mode == ReaderMode.RTL, glass = glass, onTapCenter = { uiVisible = !uiVisible }, end = end)
                seek = { scope.launch { pager.scrollToPage(it) } }
            }
            TopBar(uiVisible, manga.title, chapter.name, onBack)
            BottomPanel(
                visible = uiVisible,
                page = currentPage,
                total = p.size,
                mode = mode,
                glass = glass,
                hasPrev = prev != null,
                hasNext = next != null,
                onSeek = seek,
                onMode = { app.prefs.readerMode.value = it },
                onGlass = { app.prefs.glassFlip.value = it },
                onPrev = { prev?.let { chapterKey = it.key } },
                onNext = { next?.let { chapterKey = it.key } },
            )
        }
    }
}

@Composable
private fun Immersive(showBars: Boolean) {
    val view = LocalView.current
    val window = (LocalContext.current as Activity).window
    val controller = remember { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            view.keepScreenOn = false
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(showBars) {
        if (showBars) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {}
    }
}

private fun pageRequest(context: PlatformContext, data: Any, headers: Map<String, String>, size: CoilSize) =
    ImageRequest.Builder(context)
        .data(data)
        .size(size)
        .precision(Precision.INEXACT)
        .apply {
            if (headers.isNotEmpty()) httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (k, v) -> set(k, v) } }.build())
        }
        .crossfade(false)
        .build()

@Composable
private fun rememberScreenPx(): Pair<Int, Int> {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(config.screenWidthDp, config.screenHeightDp, density) {
        with(density) { config.screenWidthDp.dp.roundToPx() to config.screenHeightDp.dp.roundToPx() }
    }
}

private fun ImageLoader.prefetch(context: PlatformContext, pages: List<Any>, from: Int, headers: Map<String, String>, size: CoilSize) {
    for (i in from + 1..from + 3) pages.getOrNull(i)?.let { enqueue(pageRequest(context, it, headers, size)) }
}

@Composable
private fun PagedReader(
    state: PagerState,
    pages: List<Any>,
    headers: Map<String, String>,
    rtl: Boolean,
    glass: Boolean,
    onTapCenter: () -> Unit,
    end: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalPlatformContext.current
    val (w, h) = rememberScreenPx()
    val size = remember(w, h) { CoilSize(w, h) }
    LaunchedEffect(state.settledPage) {
        SingletonImageLoader.get(context).prefetch(context, pages, state.settledPage, headers, size)
    }
    GlassPager(state, rtl, glass) { page ->
        if (page >= pages.size) {
            end()
        } else {
            ZoomableBox(onTap = { x ->
                val forward = if (rtl) x < 0.3f else x > 0.7f
                val backward = if (rtl) x > 0.7f else x < 0.3f
                when {
                    forward -> scope.launch { state.animateScrollToPage(page + 1, animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f)) }
                    backward && page > 0 -> scope.launch { state.animateScrollToPage(page - 1, animationSpec = spring(dampingRatio = 0.9f, stiffness = 300f)) }
                    else -> onTapCenter()
                }
            }) {
                PageImage(pages[page], headers, size, ContentScale.Fit, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun WebtoonReader(
    state: LazyListState,
    pages: List<Any>,
    headers: Map<String, String>,
    onTap: () -> Unit,
    end: @Composable () -> Unit,
) {
    val context = LocalPlatformContext.current
    val (w, _) = rememberScreenPx()
    val size = remember(w) { CoilSize(Dimension(w), Dimension.Undefined) }
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect {
            SingletonImageLoader.get(context).prefetch(context, pages, it, headers, size)
        }
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onTap() } },
    ) {
        items(pages.size, key = { it }) { i ->
            PageImage(pages[i], headers, size, ContentScale.FillWidth, Modifier.fillMaxWidth(), placeholderHeight = true)
        }
        item(key = "end") { Box(Modifier.fillParentMaxHeight(0.7f)) { end() } }
    }
}

@Composable
private fun PageImage(
    data: Any,
    headers: Map<String, String>,
    size: CoilSize,
    scale: ContentScale,
    modifier: Modifier,
    placeholderHeight: Boolean = false,
) {
    val context = LocalPlatformContext.current
    var attempt by remember(data) { mutableIntStateOf(0) }
    val request = remember(data, attempt) { pageRequest(context, data, headers, size) }
    var state by remember(data) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    val loaded = state is AsyncImagePainter.State.Success
    Box(modifier.then(if (placeholderHeight && !loaded) Modifier.heightIn(min = 520.dp) else Modifier), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = scale,
            onState = { state = it },
            modifier = if (placeholderHeight) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
        )
        when (state) {
            is AsyncImagePainter.State.Error -> Pill("Reload page", { attempt++ })
            is AsyncImagePainter.State.Success -> Unit
            else -> LoadingDots(color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun ChapterEnd(name: String, next: Chapter?, onNext: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Label("End of chapter", color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            Text(name, style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            if (next != null) {
                Label("Up next", color = InkRed)
                Spacer(Modifier.height(6.dp))
                Text(next.name, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Pill("Next chapter", onNext, icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            } else {
                Text("You're all caught up", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(16.dp))
                Pill("Back to details", onBack, filled = false)
            }
        }
    }
}

@Composable
private fun BoxScope.TopBar(visible: Boolean, title: String, chapter: String, onBack: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (chapter.isNotBlank()) Label(chapter, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun BoxScope.BottomPanel(
    visible: Boolean,
    page: Int,
    total: Int,
    mode: ReaderMode,
    glass: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onSeek: (Int) -> Unit,
    onMode: (ReaderMode) -> Unit,
    onGlass: (Boolean) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(PanelColor)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous chapter", { if (hasPrev) onPrev() },
                    tint = Color.White.copy(alpha = if (hasPrev) 1f else 0.3f), background = Color.White.copy(alpha = 0.08f))
                var dragging by remember { mutableStateOf<Float?>(null) }
                Slider(
                    value = dragging ?: page.toFloat(),
                    onValueChange = { dragging = it; onSeek(it.roundToInt()) },
                    onValueChangeFinished = { dragging = null },
                    valueRange = 0f..(total - 1).coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = InkRed,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                    ),
                )
                RoundIconButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next chapter", { if (hasNext) onNext() },
                    tint = Color.White.copy(alpha = if (hasNext) 1f else 0.3f), background = Color.White.copy(alpha = 0.08f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Label("${(page + 1).coerceAtMost(total)} / $total", color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                Chip("LTR", mode == ReaderMode.LTR) { onMode(ReaderMode.LTR) }
                Chip("RTL", mode == ReaderMode.RTL) { onMode(ReaderMode.RTL) }
                Chip("WEB", mode == ReaderMode.VERTICAL) { onMode(ReaderMode.VERTICAL) }
                Spacer(Modifier.width(6.dp))
                Chip("GLASS", glass, accent = true) { onGlass(!glass) }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, accent: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        selected && accent -> InkRed
        selected -> Color.White
        else -> Color.White.copy(alpha = 0.08f)
    }
    val fg = if (selected && !accent) Color.Black else Color.White
    Box(
        Modifier
            .padding(start = 6.dp)
            .bouncyClick(onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}
