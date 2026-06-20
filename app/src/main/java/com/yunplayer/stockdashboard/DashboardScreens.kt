package com.yunplayer.stockdashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DashboardRoute(
    state: DashboardUiState,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    showGold: Boolean,
    onShowGoldChange: (Boolean) -> Unit,
    showFloat: Boolean,
    onFloatToggle: () -> Unit,
    showNotification: Boolean,
    onNotificationToggle: () -> Unit,
    overlayGold: Boolean,
    onOverlayGoldChange: (Boolean) -> Unit,
    showLiveUpdate: Boolean,
    onShowLiveUpdateChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onTabSelected: (StockTab) -> Unit,
) {
    when {
        state.loading -> LoadingScreen("获取热门美股")
        state.error != null && state.gainers.isEmpty() -> ErrorScreen(
            title = "加载失败",
            message = state.error,
            onRetry = onRefresh
        )
        else -> HotStocksScreen(
            state = state,
            themeMode = themeMode,
            onThemeModeSelected = onThemeModeSelected,
            showGold = showGold,
            onShowGoldChange = onShowGoldChange,
            showFloat = showFloat,
            onFloatToggle = onFloatToggle,
            showNotification = showNotification,
            onNotificationToggle = onNotificationToggle,
            overlayGold = overlayGold,
            onOverlayGoldChange = onOverlayGoldChange,
            showLiveUpdate = showLiveUpdate,
            onShowLiveUpdateChange = onShowLiveUpdateChange,
            onRefresh = onRefresh,
            onTabSelected = onTabSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HotStocksScreen(
    state: DashboardUiState,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    showGold: Boolean,
    onShowGoldChange: (Boolean) -> Unit,
    showFloat: Boolean,
    onFloatToggle: () -> Unit,
    showNotification: Boolean,
    onNotificationToggle: () -> Unit,
    overlayGold: Boolean,
    onOverlayGoldChange: (Boolean) -> Unit,
    showLiveUpdate: Boolean,
    onShowLiveUpdateChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onTabSelected: (StockTab) -> Unit,
) {
    val stocks = when (state.activeTab) {
        StockTab.Gainers -> state.gainers
        StockTab.Losers -> state.losers
        StockTab.Actives -> state.actives
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            HotStocksHeader(
                goldQuote = state.goldQuote,
                goldLoading = state.goldLoading,
                goldError = state.goldError,
                showGold = showGold,
                themeMode = themeMode,
                onThemeModeSelected = onThemeModeSelected,
                onShowGoldChange = onShowGoldChange,
                showFloat = showFloat,
                onFloatToggle = onFloatToggle,
                showNotification = showNotification,
                onNotificationToggle = onNotificationToggle,
                overlayGold = overlayGold,
                onOverlayGoldChange = onOverlayGoldChange,
                showLiveUpdate = showLiveUpdate,
                onShowLiveUpdateChange = onShowLiveUpdateChange,
                onRefresh = onRefresh,
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    StockTabBar(
                        selected = state.activeTab,
                        onSelect = onTabSelected,
                    )
                }
                if (state.error != null) {
                    item {
                        Text(
                            state.error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .animateContentSize()
                                .padding(horizontal = 2.dp, vertical = 4.dp)
                        )
                    }
                }
                items(stocks, key = { it.symbol }) { stock ->
                    StockRow(stock = stock, modifier = Modifier.animateItem())
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

@Composable
private fun HotStocksHeader(
    goldQuote: GoldQuote?,
    goldLoading: Boolean,
    goldError: String?,
    showGold: Boolean,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onShowGoldChange: (Boolean) -> Unit,
    showFloat: Boolean,
    onFloatToggle: () -> Unit,
    showNotification: Boolean,
    onNotificationToggle: () -> Unit,
    overlayGold: Boolean,
    onOverlayGoldChange: (Boolean) -> Unit,
    showLiveUpdate: Boolean,
    onShowLiveUpdateChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(heroBrush())
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "热门美股",
                    color = OnHero,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            SettingsMenu(
                selectedMode = themeMode,
                onModeSelected = onThemeModeSelected,
                showGold = showGold,
                onShowGoldChange = onShowGoldChange,
                showFloat = showFloat,
                onFloatToggle = onFloatToggle,
                showNotification = showNotification,
                onNotificationToggle = onNotificationToggle,
                overlayGold = overlayGold,
                onOverlayGoldChange = onOverlayGoldChange,
                showLiveUpdate = showLiveUpdate,
                onShowLiveUpdateChange = onShowLiveUpdateChange,
                tint = OnHero
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = OnHero)
            }
        }
        if (showGold) {
            Spacer(Modifier.height(12.dp))
            HeroGoldStrip(quote = goldQuote, loading = goldLoading, error = goldError)
        }
    }
}

@Composable
private fun HeroGoldStrip(quote: GoldQuote?, loading: Boolean, error: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HeroGlass)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val status = when {
                quote != null && error != null -> "金价更新失败 · ${formatClockTime(quote.updatedAtMillis)}"
                quote != null -> "实时金价 · ${formatClockTime(quote.updatedAtMillis)}"
                loading -> "实时金价 · 获取中"
                else -> error ?: "实时金价"
            }
            Text(status, color = OnHeroMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                if (quote != null) formatGoldPrice(quote.price) else "--",
                color = HeroGoldColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        if (quote != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x3DFFFFFF))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    "${formatSignedNumber(quote.changeAmount)} · ${formatSignedPercent(quote.changePercent)}",
                    color = OnHero,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        } else if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = OnHero
            )
        }
    }
}

@Composable
private fun StockTabBar(selected: StockTab, onSelect: (StockTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(ElevatedSurface)
            .border(BorderStroke(0.5.dp, CardBorder), RoundedCornerShape(50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        StockTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .then(if (active) Modifier.background(heroBrush()) else Modifier)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tab.label,
                    color = if (active) OnHero else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun StockRow(stock: HotStock, modifier: Modifier = Modifier) {
    val change = stock.changePercent
    CardSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(trendGradientColors(change)))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stock.symbol,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        Text(
                            stock.name,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatPrice(stock.price),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        TrendPill(
                            text = formatSignedPercent(change),
                            value = change,
                            fontSize = 12
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Vol ${formatVolume(stock.volume)}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                    )
                    if (stock.preMarketChangePercent != null) {
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "盘前 ${formatSignedPercent(stock.preMarketChangePercent)}",
                                color = trendColor(stock.preMarketChangePercent),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenu(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    showGold: Boolean,
    onShowGoldChange: (Boolean) -> Unit,
    showFloat: Boolean,
    onFloatToggle: () -> Unit,
    showNotification: Boolean,
    onNotificationToggle: () -> Unit,
    overlayGold: Boolean,
    onOverlayGoldChange: (Boolean) -> Unit,
    showLiveUpdate: Boolean,
    onShowLiveUpdateChange: (Boolean) -> Unit,
    tint: Color = TextPrimary
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.Palette, contentDescription = "设置", tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (mode) {
                                ThemeMode.FollowSystem -> "跟随系统"
                                ThemeMode.Light -> "浅色"
                                ThemeMode.Dark -> "深色"
                            }
                        )
                    },
                    leadingIcon = {
                        if (mode == selectedMode) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onModeSelected(mode)
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("显示金价") },
                trailingIcon = { Switch(checked = showGold, onCheckedChange = null) },
                onClick = { onShowGoldChange(!showGold) }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("悬浮窗") },
                trailingIcon = { Switch(checked = showFloat, onCheckedChange = null) },
                onClick = { expanded = false; onFloatToggle() }
            )
            DropdownMenuItem(
                text = { Text("常驻通知") },
                trailingIcon = { Switch(checked = showNotification, onCheckedChange = null) },
                onClick = { onNotificationToggle() }
            )
            DropdownMenuItem(
                text = { Text("流体云") },
                trailingIcon = { Switch(checked = showLiveUpdate, onCheckedChange = null) },
                onClick = { onShowLiveUpdateChange(!showLiveUpdate) }
            )
            if (showFloat || showNotification || showLiveUpdate) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            "叠加显示",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                DropdownMenuItem(
                    text = { Text("现货黄金") },
                    trailingIcon = { Switch(checked = overlayGold, onCheckedChange = null) },
                    onClick = { onOverlayGoldChange(!overlayGold) }
                )
            }
        }
    }
}

@Composable
internal fun GradientTopBar(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(heroBrush())
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = OnHero)
            }
            Spacer(Modifier.width(2.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            color = OnHero,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun LoadingScreen(title: String, onBack: (() -> Unit)? = null) {
    Scaffold(
        containerColor = AppBackground,
        topBar = { GradientTopBar(title = title, onBack = onBack) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
internal fun ErrorScreen(
    title: String,
    message: String,
    onRetry: () -> Unit,
    retryText: String = "重试",
    onBack: (() -> Unit)? = null
) {
    Scaffold(
        containerColor = AppBackground,
        topBar = { GradientTopBar(title = title, onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(message, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) { Text(retryText) }
        }
    }
}

@Composable
internal fun CardSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = ElevatedSurface,
        border = BorderStroke(0.5.dp, CardBorder),
        shadowElevation = 1.dp,
        content = content
    )
}

@Composable
internal fun TrendPill(
    text: String,
    value: Double,
    fontSize: Int,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 3.dp
) {
    val color = trendColor(value)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
internal fun MagnitudeBar(
    fraction: Float,
    brush: Brush,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "magnitude"
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(TrackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(brush)
        )
    }
}
