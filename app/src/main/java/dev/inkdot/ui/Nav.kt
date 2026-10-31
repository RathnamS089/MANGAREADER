package dev.inkdot.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.inkdot.data.MangaItem
import dev.inkdot.ui.browse.BrowseScreen
import dev.inkdot.ui.browse.SourceScreen
import dev.inkdot.ui.details.DetailsScreen
import dev.inkdot.ui.extensions.ExtensionsScreen
import dev.inkdot.ui.library.LibraryScreen
import dev.inkdot.ui.reader.ReaderScreen
import dev.inkdot.ui.settings.SettingsScreen

/** Bottom padding for scrolling content so it clears the floating nav bar. */
val NavBarClearance = 112.dp

private object Routes {
    const val LIBRARY = "library"
    const val BROWSE = "browse"
    const val EXTENSIONS = "extensions"
    const val SETTINGS = "settings"
    const val SOURCE = "source/{sid}"
    const val MANGA = "manga/{sid}/{key}?title={title}&cover={cover}"
    const val READER = "reader/{sid}/{key}/{chapter}?title={title}&cover={cover}"

    private fun e(s: String?) = Uri.encode(s.orEmpty())
    fun source(id: String) = "source/${e(id)}"
    fun manga(m: MangaItem) = "manga/${e(m.sourceId)}/${e(m.key)}?title=${e(m.title)}&cover=${e(m.cover)}"
    fun reader(m: MangaItem, chapterKey: String) =
        "reader/${e(m.sourceId)}/${e(m.key)}/${e(chapterKey)}?title=${e(m.title)}&cover=${e(m.cover)}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIBRARY, "Library", Icons.Outlined.Home),
    Tab(Routes.BROWSE, "Browse", Icons.Outlined.Search),
    Tab(Routes.EXTENSIONS, "Extensions", Icons.Outlined.Build),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun InkRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }
    val emphasized = tween<Float>(320, easing = FastOutSlowInEasing)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            navController = nav,
            startDestination = Routes.LIBRARY,
            enterTransition = { fadeIn(tween(260, delayMillis = 40)) + scaleIn(emphasized, initialScale = 0.94f) },
            exitTransition = { fadeOut(tween(160)) + scaleOut(emphasized, targetScale = 1.03f) },
            popEnterTransition = { fadeIn(tween(260, delayMillis = 40)) + scaleIn(emphasized, initialScale = 1.03f) },
            popExitTransition = { fadeOut(tween(160)) + scaleOut(emphasized, targetScale = 0.94f) },
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpen = { nav.navigate(Routes.manga(it)) },
                    onResume = { manga, chapter -> nav.navigate(Routes.reader(manga, chapter)) },
                )
            }
            composable(Routes.BROWSE) {
                BrowseScreen(
                    onSource = { nav.navigate(Routes.source(it)) },
                    onGetExtensions = { nav.switchTab(Routes.EXTENSIONS) },
                )
            }
            composable(Routes.EXTENSIONS) { ExtensionsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.SOURCE) { back ->
                SourceScreen(
                    sourceId = back.arguments?.getString("sid").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate(Routes.manga(it)) },
                )
            }
            composable(
                Routes.MANGA,
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("cover") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { back ->
                val a = back.arguments
                val manga = MangaItem(
                    a?.getString("sid").orEmpty(), a?.getString("key").orEmpty(),
                    a?.getString("title").orEmpty(), a?.getString("cover")?.ifBlank { null },
                )
                DetailsScreen(
                    manga = manga,
                    onBack = { nav.popBackStack() },
                    onRead = { m, chapter -> nav.navigate(Routes.reader(m, chapter)) },
                )
            }
            composable(
                Routes.READER,
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                    navArgument("cover") { type = NavType.StringType; defaultValue = "" },
                ),
                enterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(180)) },
            ) { back ->
                val a = back.arguments
                ReaderScreen(
                    manga = MangaItem(
                        a?.getString("sid").orEmpty(), a?.getString("key").orEmpty(),
                        a?.getString("title").orEmpty(), a?.getString("cover")?.ifBlank { null },
                    ),
                    initialChapter = a?.getString("chapter").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }

        AnimatedVisibility(
            visible = showBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
        ) {
            FloatingNavBar(current = route, onSelect = { nav.switchTab(it) })
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun FloatingNavBar(current: String?, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .shadow(24.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = tab.route == current
            val bg by animateColorAsState(if (selected) scheme.primary else Color.Transparent, tween(240), label = "tab-bg")
            val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, tween(240), label = "tab-fg")
            Row(
                Modifier
                    .bouncyClick { onSelect(tab.route) }
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon, tab.label, tint = fg, modifier = Modifier.size(22.dp))
                AnimatedVisibility(
                    visible = selected,
                    enter = expandHorizontally(spring(dampingRatio = 0.75f, stiffness = 600f)) + fadeIn(),
                    exit = shrinkHorizontally(spring(dampingRatio = 1f, stiffness = 800f)) + fadeOut(tween(100)),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                    }
                }
            }
        }
    }
}
