package com.yunplayer.stockdashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

inline fun <VM : ViewModel> viewModelFactory(crossinline builder: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
    }

class MainActivity : ComponentActivity() {
    private val dashboardViewModel by viewModels<StockDashboardViewModel> {
        viewModelFactory {
            StockDashboardViewModel(
                dataSource = StockRepository(),
                goldDataSource = GoldRepository()
            )
        }
    }

    private val themeViewModel by viewModels<ThemeViewModel> {
        viewModelFactory {
            ThemeViewModel(SharedPreferencesThemePreferenceStore(applicationContext))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StockDashboardApp(
                dashboardViewModel = dashboardViewModel,
                themeViewModel = themeViewModel
            )
        }
    }
}
