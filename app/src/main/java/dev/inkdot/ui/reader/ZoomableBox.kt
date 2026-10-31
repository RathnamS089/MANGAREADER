package dev.inkdot.ui.reader

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch

/**
 * Pinch / double-tap zoom that only claims drags while zoomed (or pinching),
 * so swiping at 1x — or past the edge of a zoomed page — still turns the page.
 */
@Composable
fun ZoomableBox(
    onTap: (xFraction: Float) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    fun clamp(o: Offset, s: Float): Offset {
        val mx = (s - 1f) * bounds.width / 2f
        val my = (s - 1f) * bounds.height / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { bounds = it }
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { p ->
                        val target = if (scale > 1.05f) 1f else 2.5f
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val fromScale = scale
                        val fromOffset = offset
                        val toOffset = if (target == 1f) Offset.Zero else clamp((center - p) * (target - 1f), target)
                        scope.launch {
                            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)) { v, _ ->
                                scale = fromScale + (target - fromScale) * v
                                offset = lerp(fromOffset, toOffset, v)
                            }
                        }
                    },
                    onTap = { p -> onTap(p.x / size.width) },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } > 1
                        if (pinching || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            val factor = newScale / scale
                            val center = Offset(size.width / 2f, size.height / 2f)
                            // Keep the content under the fingers fixed while scaling around the centre.
                            val anchor = if (centroid == Offset.Unspecified) center else centroid
                            val newOffset = clamp((anchor - center) * (1f - factor) + offset * factor + pan, newScale)
                            val moved = newOffset != offset
                            scale = newScale
                            offset = newOffset
                            if (pinching || moved) event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.03f) { scale = 1f; offset = Offset.Zero }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
