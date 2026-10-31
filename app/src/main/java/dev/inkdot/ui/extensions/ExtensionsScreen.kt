package dev.inkdot.ui.extensions

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.inkdot.InkApp
import dev.inkdot.source.RepoEntry
import dev.inkdot.source.RepoIndex
import dev.inkdot.ui.Label
import dev.inkdot.ui.LoadingDots
import dev.inkdot.ui.NavBarClearance
import dev.inkdot.ui.Pill
import dev.inkdot.ui.RoundIconButton
import dev.inkdot.ui.ScreenHeader
import kotlinx.coroutines.launch

@Composable
fun ExtensionsScreen() {
    val app = InkApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repos by app.sources.repos.collectAsStateWithLifecycle()
    val installed by app.sources.installed.collectAsStateWithLifecycle()
    val indexes = remember { mutableStateMapOf<String, Result<RepoIndex>>() }
    var refresh by remember { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    LaunchedEffect(repos, refresh) {
        repos.forEach { url ->
            if (refresh > 0 || url !in indexes) launch { indexes[url] = runCatching { app.sources.fetchRepo(url) } }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { app.sources.installFromUri(uri) }
                .onSuccess { toast("Installed ${it.name}") }
                .onFailure { toast(it.message ?: "Invalid source file") }
        }
    }

    fun install(repo: String, entry: RepoEntry) {
        busy = entry.id
        scope.launch {
            runCatching { app.sources.install(repo, entry) }
                .onSuccess { toast("Installed ${it.name}") }
                .onFailure { toast(it.message ?: "Install failed") }
            busy = null
        }
    }

    fun addRepo() {
        if (input.isBlank()) return
        app.sources.addRepo(app.sources.normalizeRepo(input))
        input = ""
    }

    val scheme = MaterialTheme.colorScheme
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top + 8.dp, bottom = NavBarClearance),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ScreenHeader("Extensions", "${installed.size} installed") {
                RoundIconButton(Icons.Filled.Refresh, "Refresh repos", { refresh++ })
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(scheme.surfaceContainer).padding(16.dp),
            ) {
                Label("Add a GitHub repo")
                Spacer(Modifier.height(10.dp))
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    placeholder = { Text("user/repo or link to index.json") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addRepo() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = scheme.surfaceContainerHigh,
                        unfocusedContainerColor = scheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Add repo", { addRepo() }, icon = Icons.Filled.Add, enabled = input.isNotBlank())
                    Pill("Import .json", { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, filled = false)
                }
            }
        }

        item { Label("Installed", Modifier.padding(top = 14.dp)) }
        if (installed.isEmpty()) {
            item { Text("Nothing installed yet.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant) }
        }
        items(installed, key = { "installed-${it.id}" }) { src ->
            ExtensionRow(src.name, "${src.lang} · v${src.def.version}") {
                RoundIconButton(Icons.Filled.Delete, "Uninstall", { app.sources.uninstall(src.id); toast("Removed ${src.name}") })
            }
        }

        repos.forEach { url ->
            val result = indexes[url]
            item(key = "repo-$url") {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label(result?.getOrNull()?.name ?: "Repository")
                        Text(url, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RoundIconButton(Icons.Filled.Close, "Remove repo", { app.sources.removeRepo(url); indexes.remove(url) }, background = Color.Transparent)
                }
            }
            when {
                result == null -> item(key = "loading-$url") { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { LoadingDots() } }
                result.isFailure -> item(key = "error-$url") {
                    Text(
                        "Couldn't read index.json — ${result.exceptionOrNull()?.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.error,
                    )
                }
                else -> items(result.getOrThrow().sources, key = { "$url-${it.id}" }) { entry ->
                    val have = installed.find { it.id == entry.id }
                    ExtensionRow(entry.name, listOfNotNull(entry.lang, "v${entry.version}", entry.description).joinToString(" · ")) {
                        when {
                            busy == entry.id -> LoadingDots()
                            have == null -> Pill("Install", { install(url, entry) })
                            have.def.version < entry.version -> Pill("Update", { install(url, entry) })
                            else -> Label("Installed")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtensionRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}
