package dev.inkdot.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.inkdot.ui.theme.InkRed
import kotlin.math.abs

/** Dot-grid texture, drawn as a single drawPoints call. */
fun Modifier.dotGrid(color: Color, spacing: Dp = 12.dp, radius: Dp = 1.dp) = drawWithCache {
    val step = spacing.toPx()
    val points = ArrayList<Offset>()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) { points += Offset(x, y); x += step }
        y += step
    }
    val stroke = radius.toPx() * 2
    onDrawBehind { drawPoints(points, PointMode.Points, color, strokeWidth = stroke, cap = StrokeCap.Round) }
}

/** Springy press feedback instead of a ripple. */
fun Modifier.bouncyClick(enabled: Boolean = true, pressedScale: Float = 0.94f, onClick: () -> Unit) = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun rememberImageRequest(data: Any?, headers: Map<String, String>, crossfade: Boolean = true): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(data, headers) {
        ImageRequest.Builder(context)
            .data(data)
            .apply {
                if (headers.isNotEmpty()) {
                    httpHeaders(NetworkHeaders.Builder().apply { headers.forEach { (k, v) -> set(k, v) } }.build())
                }
            }
            .crossfade(crossfade)
            .build()
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(InkRed))
                    Spacer(Modifier.width(8.dp))
                    Label(subtitle)
                }
            }
        }
        actions()
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, background: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Box(
        modifier.size(44.dp).bouncyClick(onClick = onClick).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) =
    RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, modifier)

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val fg = if (filled) scheme.onPrimary else scheme.onSurface
    Row(
        modifier
            .bouncyClick(enabled = enabled, onClick = onClick)
            .height(46.dp)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(scheme.primary) else Modifier.border(1.dp, scheme.outline, CircleShape))
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.clip(CircleShape).background(scheme.surfaceContainerHigh).padding(4.dp)) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val bg by animateColorAsState(if (isSelected) scheme.primary else Color.Transparent, tween(220), label = "seg-bg")
            val fg by animateColorAsState(if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant, tween(220), label = "seg-fg")
            Box(
                Modifier.weight(1f).height(40.dp).clip(CircleShape).background(bg).clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1) }
        }
    }
}

@Composable
fun LoadingDots(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase = transition.animateFloat(0f, 3f, infiniteRepeatable(tween(1050, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        val d = abs(phase.value - i - 0.5f).let { minOf(it, 3f - it) }
                        alpha = (1f - d).coerceIn(0.2f, 1f)
                        val s = 0.8f + 0.3f * (1f - d).coerceIn(0f, 1f)
                        scaleX = s; scaleY = s
                    }
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
fun CenteredMessage(title: String, body: String? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 120.dp, height = 72.dp).dotGrid(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), 10.dp, 1.5.dp),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.size(10.dp).clip(CircleShape).background(InkRed)) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

@Composable
fun Cover(data: String?, title: String, headers: Map<String, String>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.clip(MaterialTheme.shapes.medium).background(scheme.surfaceContainerHigh)) {
        Box(
            Modifier.fillMaxSize().dotGrid(scheme.onSurface.copy(alpha = 0.10f), 9.dp, 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(),
                style = MaterialTheme.typography.displaySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        if (data != null) {
            AsyncImage(
                model = rememberImageRequest(data, headers),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
fun MangaCard(title: String, cover: String?, headers: Map<String, String>, onClick: () -> Unit, modifier: Modifier = Modifier, unread: Boolean = false) {
    Column(modifier.bouncyClick(pressedScale = 0.96f, onClick = onClick)) {
        Box {
            Cover(cover, title, headers, Modifier.fillMaxWidth().aspectRatio(0.69f))
            if (unread) {
                Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(8.dp).clip(CircleShape).background(InkRed))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
fun MissingSource(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        CenteredMessage("Source not installed", "Install it again from Extensions.") { Pill("Go back", onBack) }
    }
}
