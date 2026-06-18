package com.yunplayer.stockdashboard

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun StockDashboardApp(
    dashboardViewModel: StockDashboardViewModel,
    themeViewModel: ThemeViewModel
) {
    val state = dashboardViewModel.uiState.collectAsStateWithLifecycle().value
    val themeMode = themeViewModel.mode.collectAsStateWithLifecycle().value
    val showGold = themeViewModel.showGold.collectAsStateWithLifecycle().value
    val darkTheme = themeMode.resolveDark(isSystemInDarkTheme())
    val activity = LocalContext.current as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrimColor = SystemBars.scrimColor(darkTheme)

    DisposableEffect(lifecycleOwner, dashboardViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> dashboardViewModel.startGoldPolling()
                Lifecycle.Event.ON_STOP -> dashboardViewModel.stopGoldPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            dashboardViewModel.stopGoldPolling()
        }
    }

    SideEffect {
        val navStyle = if (darkTheme) {
            SystemBarStyle.dark(scrimColor)
        } else {
            SystemBarStyle.light(scrimColor, scrimColor)
        }
        activity.enableEdgeToEdge(
            // 顶部渐变 Hero 始终为深色调，状态栏图标固定用浅色（白）
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = navStyle
        )
    }

    val showingDetail = state.detailLoading || state.detailError != null || state.detail != null
    BackHandler(enabled = showingDetail) {
        dashboardViewModel.closeDetail()
    }

    StockDashboardTheme(darkTheme = darkTheme) {
        DashboardRoute(
            state = state,
            themeMode = themeMode,
            onThemeModeSelected = themeViewModel::select,
            showGold = showGold,
            onShowGoldChange = themeViewModel::setShowGold,
            onRefresh = dashboardViewModel::refreshAll,
            onFundSelected = dashboardViewModel::selectFund,
            onBack = dashboardViewModel::closeDetail,
            onRetryDetail = dashboardViewModel::retryDetail,
            onToggleExpanded = dashboardViewModel::toggleExpanded
        )
    }
}
