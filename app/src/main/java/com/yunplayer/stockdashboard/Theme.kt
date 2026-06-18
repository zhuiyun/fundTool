package com.yunplayer.stockdashboard

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private data class StockColors(
    val elevatedSurface: Color,
    val cardBorder: Color,
    val trackColor: Color,
    val trendUp: Color,
    val trendDown: Color,
    val trendFlat: Color,
    val trendUpStart: Color,
    val trendUpEnd: Color,
    val trendDownStart: Color,
    val trendDownEnd: Color,
    val upContainer: Color,
    val upBorder: Color,
    val downContainer: Color,
    val downBorder: Color
)

private val DarkStockColors = StockColors(
    elevatedSurface = Color(0xFF161A21),
    cardBorder = Color(0x12FFFFFF),
    trackColor = Color(0x14FFFFFF),
    trendUp = Color(0xFFEE6E70),
    trendDown = Color(0xFF34C77E),
    trendFlat = Color(0xFFC7CDD8),
    trendUpStart = Color(0xFFE0565A),
    trendUpEnd = Color(0xFFF07E80),
    trendDownStart = Color(0xFF1BA866),
    trendDownEnd = Color(0xFF3FCE82),
    upContainer = Color(0xFF221619),
    upBorder = Color(0x33FF6B6B),
    downContainer = Color(0xFF15211C),
    downBorder = Color(0x332BD67B)
)

private val LightStockColors = StockColors(
    elevatedSurface = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE6EBF1),
    trackColor = Color(0xFFE6EBF1),
    trendUp = Color(0xFFD64649),
    trendDown = Color(0xFF138A4C),
    trendFlat = Color(0xFF344054),
    trendUpStart = Color(0xFFD8474C),
    trendUpEnd = Color(0xFFE9696C),
    trendDownStart = Color(0xFF138A4C),
    trendDownEnd = Color(0xFF2BB06B),
    upContainer = Color(0xFFFFF3F3),
    upBorder = Color(0x33E5484D),
    downContainer = Color(0xFFEFFBF4),
    downBorder = Color(0x3315A05A)
)

private val LocalStockColors = staticCompositionLocalOf { DarkStockColors }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF14B8A6),
    onPrimary = Color(0xFF06241F),
    background = Color(0xFF0D1014),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF161A21),
    onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFF161A21),
    onSurfaceVariant = Color(0xFF8A93A3),
    outline = Color(0xFF232833),
    error = Color(0xFFFF6B6B)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0EA5E9),
    onPrimary = Color.White,
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF1A2230),
    surface = Color.White,
    onSurface = Color(0xFF1A2230),
    surfaceVariant = Color.White,
    onSurfaceVariant = Color(0xFF6B7585),
    outline = Color(0xFFE6EBF1),
    error = Color(0xFFE5484D)
)

val HeroGradientColors = listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6))
val HeroGoldColor = Color(0xFFFFDE8A)
val OnHero = Color.White
val OnHeroMuted = Color(0xCCFFFFFF)
val HeroGlass = Color(0x29FFFFFF)

val AppBackground: Color
    @Composable get() = MaterialTheme.colorScheme.background
val AppSurface: Color
    @Composable get() = MaterialTheme.colorScheme.surface
val ElevatedSurface: Color
    @Composable get() = LocalStockColors.current.elevatedSurface
val CardBorder: Color
    @Composable get() = LocalStockColors.current.cardBorder
val TrackColor: Color
    @Composable get() = LocalStockColors.current.trackColor
val BorderColor: Color
    @Composable get() = MaterialTheme.colorScheme.outline
val TextPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TrendUp: Color
    @Composable get() = LocalStockColors.current.trendUp
val TrendDown: Color
    @Composable get() = LocalStockColors.current.trendDown
val TrendFlat: Color
    @Composable get() = LocalStockColors.current.trendFlat

@Composable
fun heroBrush(): Brush = Brush.linearGradient(
    colors = HeroGradientColors,
    start = Offset.Zero,
    end = Offset.Infinite
)

@Composable
fun trendColor(value: Double): Color = when {
    value > 0.0 -> TrendUp
    value < 0.0 -> TrendDown
    else -> TrendFlat
}

@Composable
fun trendGradientColors(value: Double): List<Color> {
    val colors = LocalStockColors.current
    return when {
        value > 0.0 -> listOf(colors.trendUpStart, colors.trendUpEnd)
        value < 0.0 -> listOf(colors.trendDownStart, colors.trendDownEnd)
        else -> listOf(colors.trendFlat, colors.trendFlat)
    }
}

@Composable
fun trendContainerColor(value: Double): Color {
    val colors = LocalStockColors.current
    return when {
        value > 0.0 -> colors.upContainer
        value < 0.0 -> colors.downContainer
        else -> colors.elevatedSurface
    }
}

@Composable
fun trendBorderColor(value: Double): Color {
    val colors = LocalStockColors.current
    return when {
        value > 0.0 -> colors.upBorder
        value < 0.0 -> colors.downBorder
        else -> colors.cardBorder
    }
}

@Composable
fun StockDashboardTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalStockColors provides if (darkTheme) DarkStockColors else LightStockColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            content = content
        )
    }
}
