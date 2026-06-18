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

    private val mutableShowFloat = MutableStateFlow(preferenceStore.loadShowFloat())
    val showFloat: StateFlow<Boolean> = mutableShowFloat.asStateFlow()

    private val mutableShowNotification = MutableStateFlow(preferenceStore.loadShowNotification())
    val showNotification: StateFlow<Boolean> = mutableShowNotification.asStateFlow()

    private val mutableOverlayNasdaq = MutableStateFlow(preferenceStore.loadOverlayNasdaq())
    val overlayNasdaq: StateFlow<Boolean> = mutableOverlayNasdaq.asStateFlow()

    private val mutableOverlayGold = MutableStateFlow(preferenceStore.loadOverlayGold())
    val overlayGold: StateFlow<Boolean> = mutableOverlayGold.asStateFlow()

    private val mutableOverlayFunds = MutableStateFlow(preferenceStore.loadOverlayFunds())
    val overlayFunds: StateFlow<Boolean> = mutableOverlayFunds.asStateFlow()

    private val mutableShowLiveUpdate = MutableStateFlow(preferenceStore.loadShowLiveUpdate())
    val showLiveUpdate: StateFlow<Boolean> = mutableShowLiveUpdate.asStateFlow()

    fun select(mode: ThemeMode) {
        mutableMode.value = mode
        preferenceStore.save(mode)
    }

    fun setShowGold(show: Boolean) {
        mutableShowGold.value = show
        preferenceStore.saveShowGold(show)
    }

    fun setShowFloat(show: Boolean) {
        mutableShowFloat.value = show
        preferenceStore.saveShowFloat(show)
    }

    fun setShowNotification(show: Boolean) {
        mutableShowNotification.value = show
        preferenceStore.saveShowNotification(show)
    }

    fun setOverlayNasdaq(show: Boolean) {
        mutableOverlayNasdaq.value = show
        preferenceStore.saveOverlayNasdaq(show)
    }

    fun setOverlayGold(show: Boolean) {
        mutableOverlayGold.value = show
        preferenceStore.saveOverlayGold(show)
    }

    fun setOverlayFunds(show: Boolean) {
        mutableOverlayFunds.value = show
        preferenceStore.saveOverlayFunds(show)
    }

    fun setShowLiveUpdate(show: Boolean) {
        mutableShowLiveUpdate.value = show
        preferenceStore.saveShowLiveUpdate(show)
    }

    fun syncFromPrefs() {
        mutableShowFloat.value = preferenceStore.loadShowFloat()
        mutableShowNotification.value = preferenceStore.loadShowNotification()
        mutableShowLiveUpdate.value = preferenceStore.loadShowLiveUpdate()
    }
}
