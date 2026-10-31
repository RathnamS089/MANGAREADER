package dev.inkdot.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import dev.inkdot.InkApp
import dev.inkdot.data.ReaderMode
import dev.inkdot.data.ThemeMode
import dev.inkdot.ui.Label
import dev.inkdot.ui.NavBarClearance
import dev.inkdot.ui.ScreenHeader
import dev.inkdot.ui.Segmented
import dev.inkdot.ui.theme.InkRed
import java.io.File

@Composable
fun SettingsScreen() {
    val prefs = InkApp.instance.prefs
    val context = LocalContext.current
    val theme by prefs.theme.flow.collectAsStateWithLifecycle()
    val amoled by prefs.amoled.flow.collectAsStateWithLifecycle()
    val mode by prefs.readerMode.flow.collectAsStateWithLifecycle()
    val glass by prefs.glassFlip.flow.collectAsStateWithLifecycle()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 8.dp, bottom = NavBarClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Settings", "No ads · no tracking") }
        item {
            Group("Appearance") {
                Segmented(
                    listOf(ThemeMode.SYSTEM to "System", ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light"),
                    theme, { prefs.theme.value = it }, Modifier.fillMaxWidth(),
                )
                Toggle("Pure black", "True-black backgrounds for OLED screens", amoled) { prefs.amoled.value = it }
            }
        }
        item {
            Group("Reader") {
                Segmented(
                    listOf(ReaderMode.LTR to "Left → right", ReaderMode.RTL to "Manga ←", ReaderMode.VERTICAL to "Webtoon"),
                    mode, { prefs.readerMode.value = it }, Modifier.fillMaxWidth(),
                )
                Toggle("Glass page turn", "Liquid-glass sheen and depth while flipping. Turn off for a plain slide.", glass) { prefs.glassFlip.value = it }
            }
        }
        item {
            Group("Storage") {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable {
                        SingletonImageLoader.get(context).diskCache?.clear()
                        SingletonImageLoader.get(context).memoryCache?.clear()
                        File(context.cacheDir, "local").deleteRecursively()
                        Toast.makeText(context, "Image cache cleared", Toast.LENGTH_SHORT).show()
                    }.padding(vertical = 10.dp),
                ) {
                    Column {
                        Text("Clear image cache", style = MaterialTheme.typography.bodyLarge)
                        Text("Frees downloaded pages and unpacked CBZ files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("INKDOT", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Label("v0.1.0 · open source")
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label(title)
        content()
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = InkRed,
                checkedBorderColor = InkRed,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
