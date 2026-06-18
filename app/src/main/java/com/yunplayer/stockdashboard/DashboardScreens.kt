package com.yunplayer.stockdashboard

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun DashboardRoute(
    state: DashboardUiState,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    showGold: Boolean,
    onShowGoldChange: (Boolean) -> Unit,
    floatRunning: Boolean,
    showFloat: Boolean,
    onFloatToggle: () -> Unit,
    showNotification: Boolean,
    onNotificationToggle: () -> Unit,
    overlayNasdaq: Boolean,
    onOverlayNasdaqChange: (Boolean) -> Unit,
    overlayGold: Boolean,
    onOverlayGoldChange: (Boolean) -> Unit,
    overlayFunds: Boolean,
    onOverlayFundsChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onFundSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onRetryDetail: () -> Unit,
    onToggleExpanded: () -> Unit
) {
    when {
        state.detailLoading -> LoadingScreen(title = "正在加载持仓", onBack = onBack)
        state.detailError != null -> ErrorScreen(
            title = "持仓加载失败",
            message = state.detailError,
            onRetry = onRetryDetail,
            onBack = onBack
        )
        state.detail != null -> DetailScreen(
            detail = state.detail,
            expanded = state.expanded,
            onBack = onBack,
            onToggleExpanded = onToggleExpanded
        )
        state.loading -> LoadingScreen(title = "正在获取最新估算")
        state.dashboard == null && state.error != null -> ErrorScreen(
            title = "暂时无法加载",
            message = state.error,
            onRetry = onRefresh
        )
        state.dashboard != null -> HomeScreen(
            dashboard = state.dashboard,
            refreshError = state.error,
            refreshing = state.refreshing,
            goldQuote = state.goldQuote,
            goldLoading = state.goldLoading,
            goldError = state.goldError,
            themeMode = themeMode,
            onThemeModeSelected = onThemeModeSelected,
            showGold = showGold,
            onShowGoldChange = onShowGoldChange,
            showFloat = showFloat,
            onFloatToggle = onFloatToggle,
            showNotification = showNotification,
            onNotificationToggle = onNotificationToggle,
            overlayNasdaq = overlayNasdaq,
            onOverlayNasdaqChange = onOverlayNasdaqChange,
            overlayGold = overlayGold,
            onOverlayGoldChange = onOverlayGoldChange,
            overlayFunds = overlayFunds,
            onOverlayFundsChange = onOverlayFundsChange,
            onRefresh = onRefresh,
            onFundSelected = onFundSelected
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    dashboard: Dashboard,
    refreshError: String?,
    refreshing: Boolean,
    goldQuote: GoldQuote?,
    goldLoading: Boolean,
    goldError: String?,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    showGold: Boolean,
    onShowGoldChange: (Boolean) -> Unit,
    showFloat: Boolean,
    onFloatToggle: () -> Unit,
    showNotification: Boolean,
    onNotificationToggle: () -> Unit,
    overlayNasdaq: Boolean,
    onOverlayNasdaqChange: (Boolean) -> Unit,
    overlayGold: Boolean,
    onOverlayGoldChange: (Boolean) -> Unit,
    overlayFunds: Boolean,
    onOverlayFundsChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onFundSelected: (Int) -> Unit
) {
    val maxImpact = dashboard.funds
        .maxOfOrNull { abs(it.estimatedImpact) }
        ?.takeIf { it > 0.0 } ?: 1.0
    var fundFilter by remember { mutableStateOf(FundFilter.All) }
    val visibleFunds = when (fundFilter) {
        FundFilter.All -> dashboard.funds
        FundFilter.Up -> dashboard.funds.filter { it.estimatedImpact > 0.0 }
        FundFilter.Down -> dashboard.funds.filter { it.estimatedImpact < 0.0 }
    }
    Scaffold(
        containerColor = AppBackground,
        topBar = {
            HomeHeader(
                timestamp = dashboard.timestamp,
                goldQuote = goldQuote,
                goldLoading = goldLoading,
                goldError = goldError,
                showGold = showGold,
                themeMode = themeMode,
                onThemeModeSelected = onThemeModeSelected,
                onShowGoldChange = onShowGoldChange,
                showFloat = showFloat,
                onFloatToggle = onFloatToggle,
                showNotification = showNotification,
                onNotificationToggle = onNotificationToggle,
                overlayNasdaq = overlayNasdaq,
                onOverlayNasdaqChange = onOverlayNasdaqChange,
                overlayGold = overlayGold,
                onOverlayGoldChange = onOverlayGoldChange,
                overlayFunds = overlayFunds,
                onOverlayFundsChange = onOverlayFundsChange,
                onRefresh = onRefresh
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 0.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    SummaryPanel(dashboard.indexes)
                }
                if (dashboard.description.isNotBlank() || refreshError != null) {
                    item {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .animateContentSize()
                        ) {
                            if (dashboard.description.isNotBlank()) {
                                Text(
                                    dashboard.description,
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                            if (refreshError != null) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    refreshError,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                item {
                    SectionHeader("基金列表", trailing = "${visibleFunds.size} 只")
                }
                item {
                    FundFilterBar(selected = fundFilter, onSelect = { fundFilter = it })
                }
                if (visibleFunds.isEmpty()) {
                    item { EmptyFundsHint() }
                } else {
                    itemsIndexed(visibleFunds, key = { _, fund -> fund.id }) { index, fund ->
                        FundRow(
                            fund = fund,
                            rank = index + 1,
                            maxImpact = maxImpact,
                            modifier = Modifier.animateItem(),
                            onClick = { onFundSelected(fund.id) }
                        )
                    }
                }
                if (!dashboard.hiddenOvernight) {
                    item {
                        WebsiteSection()
                    }
                }
                item {
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    timestamp: String,
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
    overlayNasdaq: Boolean,
    onOverlayNasdaqChange: (Boolean) -> Unit,
    overlayGold: Boolean,
    onOverlayGoldChange: (Boolean) -> Unit,
    overlayFunds: Boolean,
    onOverlayFundsChange: (Boolean) -> Unit,
    onRefresh: () -> Unit
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
                    "基金估值",
                    color = OnHero,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(timestamp, color = OnHeroMuted, fontSize = 12.sp, maxLines = 1)
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
                overlayNasdaq = overlayNasdaq,
                onOverlayNasdaqChange = onOverlayNasdaqChange,
                overlayGold = overlayGold,
                onOverlayGoldChange = onOverlayGoldChange,
                overlayFunds = overlayFunds,
                onOverlayFundsChange = onOverlayFundsChange,
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
private fun SectionHeader(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(PaddingValues(start = 2.dp, top = 2.dp, end = 2.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.verticalGradient(HeroGradientColors))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(trailing, fontSize = 12.sp, color = TextSecondary)
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
    overlayNasdaq: Boolean,
    onOverlayNasdaqChange: (Boolean) -> Unit,
    overlayGold: Boolean,
    onOverlayGoldChange: (Boolean) -> Unit,
    overlayFunds: Boolean,
    onOverlayFundsChange: (Boolean) -> Unit,
    tint: Color = TextPrimary
) {
    var expanded by remember { mutableStateOf(false) }
    val anyOverlayActive = showFloat || showNotification
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
                onClick = {
                    expanded = false
                    onFloatToggle()
                }
            )
            DropdownMenuItem(
                text = { Text("常驻通知") },
                trailingIcon = { Switch(checked = showNotification, onCheckedChange = null) },
                onClick = { onNotificationToggle() }
            )
            if (anyOverlayActive) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            "叠加显示内容",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                DropdownMenuItem(
                    text = { Text("纳斯达克") },
                    trailingIcon = { Switch(checked = overlayNasdaq, onCheckedChange = null) },
                    onClick = { onOverlayNasdaqChange(!overlayNasdaq) }
                )
                DropdownMenuItem(
                    text = { Text("现货黄金") },
                    trailingIcon = { Switch(checked = overlayGold, onCheckedChange = null) },
                    onClick = { onOverlayGoldChange(!overlayGold) }
                )
                DropdownMenuItem(
                    text = { Text("基金涨跌") },
                    trailingIcon = { Switch(checked = overlayFunds, onCheckedChange = null) },
                    onClick = { onOverlayFundsChange(!overlayFunds) }
                )
            }
        }
    }
}

@Composable
private fun SummaryPanel(indexes: List<IndexImpact>) {
    val shown = marketSummaryItems(indexes)
    val maxAbs = shown
        .maxOfOrNull { abs(parsePercentText(it.changePercent)) }
        ?.takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        shown.forEach { index ->
            SummaryCard(
                index = index,
                maxAbs = maxAbs,
                modifier = Modifier.width(132.dp)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    index: IndexImpact,
    maxAbs: Double,
    modifier: Modifier = Modifier
) {
    val value = parsePercentText(index.changePercent)
    CardSurface(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            Text(
                index.name,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                index.changePercent,
                color = trendColor(value),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(9.dp))
            MagnitudeBar(
                fraction = (abs(value) / maxAbs).toFloat(),
                brush = Brush.horizontalGradient(trendGradientColors(value)),
                modifier = Modifier.fillMaxWidth(),
                height = 3.dp
            )
        }
    }
}

@Composable
private fun FundRow(
    fund: FundImpact,
    rank: Int,
    maxImpact: Double,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val value = fund.estimatedImpact
    CardSurface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(trendGradientColors(value)))
            )
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RankBadge(rank = rank)
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            fund.name,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (fund.time.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(fund.time, color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    TrendPill(
                        text = formatSignedPercent(value),
                        value = value,
                        fontSize = 14
                    )
                }
                Spacer(Modifier.height(10.dp))
                MagnitudeBar(
                    fraction = (abs(value) / maxImpact).toFloat(),
                    brush = Brush.horizontalGradient(trendGradientColors(value)),
                    modifier = Modifier.fillMaxWidth(),
                    height = 3.dp
                )
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(HeroGradientColors)),
        contentAlignment = Alignment.Center
    ) {
        Text("$rank", color = OnHero, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private enum class FundFilter(val label: String) {
    All("全部"),
    Up("上涨"),
    Down("下跌")
}

@Composable
private fun FundFilterBar(selected: FundFilter, onSelect: (FundFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(ElevatedSurface)
            .border(BorderStroke(0.5.dp, CardBorder), RoundedCornerShape(50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        FundFilter.entries.forEach { filter ->
            val active = filter == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .then(if (active) Modifier.background(heroBrush()) else Modifier)
                    .clickable { onSelect(filter) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    filter.label,
                    color = if (active) OnHero else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EmptyFundsHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("暂无符合条件的基金", color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun DetailScreen(
    detail: FundDetail,
    expanded: Boolean,
    onBack: () -> Unit,
    onToggleExpanded: () -> Unit
) {
    val visibleHoldings = if (expanded) detail.holdings else detail.holdings.take(10)
    val maxWeight = detail.holdings
        .maxOfOrNull { it.weight }
        ?.takeIf { it > 0.0 } ?: 1.0
    Scaffold(
        containerColor = AppBackground,
        topBar = { GradientTopBar(title = detail.name, onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CardSurface(shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("估算影响", color = TextSecondary, fontSize = 13.sp)
                            if (detail.time.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(detail.time, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                        TrendPill(
                            text = formatSignedPercent(detail.estimatedImpact),
                            value = detail.estimatedImpact,
                            fontSize = 22,
                            horizontalPadding = 14.dp,
                            verticalPadding = 8.dp
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(2.dp))
                HoldingsHeader()
            }
            itemsIndexed(
                items = visibleHoldings,
                key = { index, holding -> holdingItemKey(index, holding) }
            ) { _, holding ->
                HoldingRow(
                    holding = holding,
                    maxWeight = maxWeight,
                    modifier = Modifier.animateItem()
                )
            }
            if (detail.holdings.size > 10) {
                item {
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(300),
                        label = "chevron"
                    )
                    Button(
                        onClick = onToggleExpanded,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElevatedSurface,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.rotate(rotation)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (expanded) "收起" else "查看全部 ${detail.holdings.size} 项")
                    }
                }
            }
            item {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

internal fun holdingItemKey(index: Int, holding: Holding): String {
    return "$index:${holding.name}"
}

@Composable
private fun HoldingsHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text("持仓", modifier = Modifier.weight(1f), color = TextSecondary, fontSize = 12.sp)
        Text(
            "占比",
            modifier = Modifier.width(64.dp),
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.End
        )
        Text(
            "涨跌",
            modifier = Modifier.width(80.dp),
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun HoldingRow(holding: Holding, maxWeight: Double, modifier: Modifier = Modifier) {
    CardSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    holding.name,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatPercent(holding.weight),
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.End,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Box(modifier = Modifier.width(80.dp), contentAlignment = Alignment.CenterEnd) {
                    TrendPill(
                        text = formatSignedPercent(holding.change),
                        value = holding.change,
                        fontSize = 13
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            MagnitudeBar(
                fraction = (holding.weight / maxWeight).toFloat(),
                brush = Brush.horizontalGradient(HeroGradientColors),
                modifier = Modifier.fillMaxWidth(),
                height = 3.dp
            )
        }
    }
}

@Composable
private fun WebsiteSection() {
    val context = LocalContext.current
    Button(
        onClick = {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL))
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ElevatedSurface,
            contentColor = TextPrimary
        )
    ) {
        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("访问网页版")
    }
}

internal const val WEBSITE_URL = "http://web.345569.xyz/"

@Composable
private fun GradientTopBar(title: String, onBack: (() -> Unit)? = null) {
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
private fun LoadingScreen(title: String, onBack: (() -> Unit)? = null) {
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
private fun ErrorScreen(
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
            Button(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
                Text(retryText)
            }
        }
    }
}

@Composable
private fun CardSurface(
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
private fun TrendPill(
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
private fun MagnitudeBar(
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
