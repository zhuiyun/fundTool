package com.yunplayer.stockdashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
    val showFloat = themeViewModel.showFloat.collectAsStateWithLifecycle().value
    val showNotification = themeViewModel.showNotification.collectAsStateWithLifecycle().value
    val floatRunning = DashboardService.floatRunning.collectAsStateWithLifecycle().value
    val overlayNasdaq = themeViewModel.overlayNasdaq.collectAsStateWithLifecycle().value
    val overlayGold = themeViewModel.overlayGold.collectAsStateWithLifecycle().value
    val overlayFunds = themeViewModel.overlayFunds.collectAsStateWithLifecycle().value
    val darkTheme = themeMode.resolveDark(isSystemInDarkTheme())
    val activity = LocalContext.current as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrimColor = SystemBars.scrimColor(darkTheme)

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            themeViewModel.setShowNotification(true)
        } else {
            Toast.makeText(activity, "请开启通知权限后再试", Toast.LENGTH_SHORT).show()
        }
    }

    // Gold polling lifecycle
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

    // Sync float/notification state from SharedPrefs on resume (service may have changed them)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                themeViewModel.syncFromPrefs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Start/stop DashboardService when prefs change
    LaunchedEffect(showFloat, showNotification) {
        if (showFloat || showNotification) {
            DashboardService.update(activity)
        } else {
            DashboardService.stop(activity)
        }
    }

    // Re-configure service when overlay content prefs change
    LaunchedEffect(overlayNasdaq, overlayGold, overlayFunds) {
        if (showFloat || showNotification) DashboardService.update(activity)
        FundWidget.requestUpdate(activity)
    }

    // When user closes float from X button, sync pref in ViewModel
    LaunchedEffect(Unit) {
        DashboardService.floatClosed.collect {
            themeViewModel.setShowFloat(false)
        }
    }

    SideEffect {
        val navStyle = if (darkTheme) {
            SystemBarStyle.dark(scrimColor)
        } else {
            SystemBarStyle.light(scrimColor, scrimColor)
        }
        activity.enableEdgeToEdge(
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
            floatRunning = floatRunning,
            showFloat = showFloat,
            onFloatToggle = {
                if (!showFloat) {
                    if (Settings.canDrawOverlays(activity)) {
                        themeViewModel.setShowFloat(true)
                    } else {
                        Toast.makeText(activity, "请开启悬浮窗权限后再试", Toast.LENGTH_SHORT).show()
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${activity.packageName}")
                            )
                        )
                    }
                } else {
                    themeViewModel.setShowFloat(false)
                }
            },
            showNotification = showNotification,
            onNotificationToggle = {
                if (showNotification) {
                    themeViewModel.setShowNotification(false)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    themeViewModel.setShowNotification(true)
                }
            },
            overlayNasdaq = overlayNasdaq,
            onOverlayNasdaqChange = themeViewModel::setOverlayNasdaq,
            overlayGold = overlayGold,
            onOverlayGoldChange = themeViewModel::setOverlayGold,
            overlayFunds = overlayFunds,
            onOverlayFundsChange = themeViewModel::setOverlayFunds,
            onRefresh = dashboardViewModel::refreshAll,
            onFundSelected = dashboardViewModel::selectFund,
            onBack = dashboardViewModel::closeDetail,
            onRetryDetail = dashboardViewModel::retryDetail,
            onToggleExpanded = dashboardViewModel::toggleExpanded
        )
    }
}
