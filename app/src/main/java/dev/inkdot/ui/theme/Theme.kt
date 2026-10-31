@file:OptIn(ExperimentalTextApi::class)

package dev.inkdot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.inkdot.R

val InkRed = Color(0xFFD71921)

/** Dot-matrix display face (Doto, OFL) with fully rounded dots. */
val DotFont = FontFamily(
    Font(
        R.font.doto, FontWeight.Black,
        variationSettings = FontVariation.Settings(FontVariation.weight(900), FontVariation.Setting("ROND", 100f)),
    ),
    Font(
        R.font.doto, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.Setting("ROND", 100f)),
    ),
)

private val base = Typography()
private val InkType = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = DotFont, fontWeight = FontWeight.Black),
    displayMedium = base.displayMedium.copy(fontFamily = DotFont, fontWeight = FontWeight.Black),
    displaySmall = base.displaySmall.copy(fontFamily = DotFont, fontWeight = FontWeight.Black, letterSpacing = 1.sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Medium),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall,
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelMedium = base.labelMedium.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.2.sp),
    labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Monospace, letterSpacing = 1.6.sp),
)

private val InkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun InkTheme(dark: Boolean, amoled: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        val bg = if (amoled) Color.Black else Color(0xFF0C0C0C)
        darkColorScheme(
            primary = Color(0xFFF2F2F2), onPrimary = Color.Black,
            primaryContainer = Color(0xFF262626), onPrimaryContainer = Color(0xFFF2F2F2),
            secondary = Color(0xFFBDBDBD), onSecondary = Color.Black,
            tertiary = InkRed, onTertiary = Color.White,
            background = bg, onBackground = Color(0xFFF2F2F2),
            surface = bg, onSurface = Color(0xFFF2F2F2),
            surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color(0xFF8E8E8E),
            surfaceContainerLowest = bg, surfaceContainerLow = Color(0xFF0F0F0F),
            surfaceContainer = Color(0xFF141414), surfaceContainerHigh = Color(0xFF1B1B1B),
            surfaceContainerHighest = Color(0xFF242424),
            outline = Color(0xFF3A3A3A), outlineVariant = Color(0xFF232323),
            error = InkRed, onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF111111), onPrimary = Color.White,
            primaryContainer = Color(0xFFE6E6E3), onPrimaryContainer = Color(0xFF111111),
            secondary = Color(0xFF444444), onSecondary = Color.White,
            tertiary = InkRed, onTertiary = Color.White,
            background = Color(0xFFF3F3F1), onBackground = Color(0xFF111111),
            surface = Color(0xFFF3F3F1), onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFE8E8E5), onSurfaceVariant = Color(0xFF6B6B6B),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F8F6),
            surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFEAEAE7),
            surfaceContainerHighest = Color(0xFFE0E0DD),
            outline = Color(0xFFCFCFCB), outlineVariant = Color(0xFFE2E2DF),
            error = InkRed, onError = Color.White,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = InkType, shapes = InkShapes, content = content)
}
