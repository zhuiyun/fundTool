package com.yunplayer.stockdashboard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeViewModel(
    private val preferenceStore: ThemePreferenceStore
) : ViewModel() {
    private val mutableMode = MutableStateFlow(preferenceStore.load())
    val mode: StateFlow<ThemeMode> = mutableMode.asStateFlow()

    private val mutableShowGold = MutableStateFlow(preferenceStore.loadShowGold())
    val showGold: StateFlow<Boolean> = mutableShowGold.asStateFlow()

    fun select(mode: ThemeMode) {
        mutableMode.value = mode
        preferenceStore.save(mode)
    }

    fun setShowGold(show: Boolean) {
        mutableShowGold.value = show
        preferenceStore.saveShowGold(show)
    }
}
