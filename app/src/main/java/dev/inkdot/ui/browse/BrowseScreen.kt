package dev.inkdot.ui.browse

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inkdot.InkApp
import dev.inkdot.ui.CenteredMessage
import dev.inkdot.ui.Label
import dev.inkdot.ui.NavBarClearance
import dev.inkdot.ui.Pill
import dev.inkdot.ui.ScreenHeader
import dev.inkdot.ui.bouncyClick
import dev.inkdot.ui.dotGrid

@Composable
fun BrowseScreen(onSource: (String) -> Unit, onGetExtensions: () -> Unit) {
    val installed by InkApp.instance.sources.installed.collectAsStateWithLifecycle()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 8.dp, bottom = NavBarClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("Browse", "${installed.size} sources") }
        if (installed.isEmpty()) {
            item {
                CenteredMessage("No sources yet", "Add a GitHub extension repo to start browsing.") {
                    Pill("Get extensions", onGetExtensions)
                }
            }
        }
        items(installed, key = { it.id }) { source ->
            val scheme = MaterialTheme.colorScheme
            Row(
                Modifier
                    .fillMaxWidth()
                    .animateItem()
                    .bouncyClick(pressedScale = 0.97f) { onSource(source.id) }
                    .clip(MaterialTheme.shapes.large)
                    .background(scheme.surfaceContainer)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(scheme.surfaceContainerHighest)
                        .dotGrid(scheme.onSurface.copy(alpha = 0.15f), 6.dp, 0.8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(source.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium)
                    Label("${source.lang} · ${source.def.type} · v${source.def.version}")
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
            }
        }
    }
}
