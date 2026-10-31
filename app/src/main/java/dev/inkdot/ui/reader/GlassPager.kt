package dev.inkdot.ui.reader

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.inkdot.ui.theme.InkRed
import kotlin.math.PI
import kotlin.math.sin

/**
 * Horizontal pager with a "liquid glass" page turn: the page being left sinks back
 * (parallax, scale, dim, rounded corners) while the incoming page slides over it
 * with a refracted rim, a sweeping specular sheen and a slight liquid squash.
 *
 * Everything is driven from graphicsLayer / draw lambdas, so a page turn only
 * touches the layer and draw phases (no recomposition per frame), which keeps it
 * at full frame rate on a Snapdragon 835 and needs no Android 12+ APIs.
 */
@Composable
fun GlassPager(
    state: PagerState,
    rtl: Boolean,
    glass: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        HorizontalPager(
            state = state,
            modifier = modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { it },
            flingBehavior = PagerDefaults.flingBehavior(
                state = state,
                snapAnimationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
            ),
        ) { page ->
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().then(if (glass) Modifier.glassTurn(state, page, rtl) else Modifier)) {
                    content(page)
                }
            }
        }
    }
}

/** -1..0 = incoming from the end side, 0..1 = being left behind. */
private fun PagerState.offsetFor(page: Int) =
    ((currentPage - page) + currentPageOffsetFraction).coerceIn(-1f, 1f)

private fun Modifier.glassTurn(state: PagerState, page: Int, rtl: Boolean): Modifier = this
    .graphicsLayer {
        val o = state.offsetFor(page)
        val dir = if (rtl) -1f else 1f
        val corner = 30.dp.toPx()
        cameraDistance = 14f * density
        if (o > 0f) {
            // Sinking page: move at ~28% of the finger and recede into the stack.
            translationX = o * size.width * 0.72f * dir
            val s = 1f - 0.09f * o
            scaleX = s
            scaleY = s
            shape = RoundedCornerShape(corner * o)
            clip = true
        } else if (o < 0f) {
            // Incoming glass sheet: slight lean and squash that peaks mid-turn.
            val f = -o
            val wobble = sin(PI.toFloat() * f)
            transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
            rotationY = -dir * 5f * f
            scaleY = 1f - 0.025f * wobble
            shape = RoundedCornerShape(corner * f)
            clip = true
            shadowElevation = 20.dp.toPx() * wobble
        }
    }
    .drawWithContent {
        drawContent()
        val o = state.offsetFor(page)
        if (o > 0f) {
            drawRect(Color.Black, alpha = 0.6f * o)
        } else if (o < 0f) {
            val f = -o
            val w = size.width
            val h = size.height
            val inward = if (rtl) -1f else 1f
            val lead = if (rtl) w else 0f

            // Refracted rim along the leading edge.
            val rim = 44.dp.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White.copy(alpha = 0.30f * f),
                    1f to Color.Transparent,
                    startX = lead,
                    endX = lead + inward * rim,
                ),
                topLeft = Offset(if (rtl) w - rim else 0f, 0f),
                size = Size(rim, h),
            )
            // Crisp glass edge with a faint red dispersion line just inside it.
            val line = 1.5.dp.toPx()
            drawRect(Color.White.copy(alpha = 0.75f * f), Offset(if (rtl) w - line else 0f, 0f), Size(line, h))
            drawRect(InkRed.copy(alpha = 0.35f * f), Offset(if (rtl) w - 2 * line else line, 0f), Size(line, h))

            // Specular sheen sweeping across as the sheet settles.
            val a = 0.16f * sin(PI.toFloat() * f)
            if (a > 0.004f) {
                val cx = lead + inward * w * (1f - f) * 1.25f
                val band = w * 0.32f
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = a),
                        1f to Color.Transparent,
                        start = Offset(cx - band, 0f),
                        end = Offset(cx + band, h * 0.45f),
                    ),
                )
            }
        }
    }
