package com.yunplayer.stockdashboard

import android.content.Context

interface ThemePreferenceStore {
    fun load(): ThemeMode
    fun save(mode: ThemeMode)
    fun loadShowGold(): Boolean = true
    fun saveShowGold(show: Boolean) {}
    fun loadShowFloat(): Boolean = false
    fun saveShowFloat(show: Boolean) {}
    fun loadShowNotification(): Boolean = false
    fun saveShowNotification(show: Boolean) {}
    fun loadOverlayNasdaq(): Boolean = true
    fun saveOverlayNasdaq(show: Boolean) {}
    fun loadOverlayGold(): Boolean = true
    fun saveOverlayGold(show: Boolean) {}
    fun loadOverlayFunds(): Boolean = true
    fun saveOverlayFunds(show: Boolean) {}
}

class SharedPreferencesThemePreferenceStore(context: Context) : ThemePreferenceStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): ThemeMode {
        return ThemeMode.fromStoredValue(preferences.getString(KEY_MODE, null))
    }

    override fun save(mode: ThemeMode) {
        preferences.edit().putString(KEY_MODE, mode.storedValue).apply()
    }

    override fun loadShowGold(): Boolean = preferences.getBoolean(KEY_SHOW_GOLD, true)
    override fun saveShowGold(show: Boolean) { preferences.edit().putBoolean(KEY_SHOW_GOLD, show).apply() }

    override fun loadShowFloat(): Boolean = preferences.getBoolean(KEY_SHOW_FLOAT, false)
    override fun saveShowFloat(show: Boolean) { preferences.edit().putBoolean(KEY_SHOW_FLOAT, show).apply() }

    override fun loadShowNotification(): Boolean = preferences.getBoolean(KEY_SHOW_NOTIFICATION, false)
    override fun saveShowNotification(show: Boolean) { preferences.edit().putBoolean(KEY_SHOW_NOTIFICATION, show).apply() }

    override fun loadOverlayNasdaq(): Boolean = preferences.getBoolean(KEY_OVERLAY_NASDAQ, true)
    override fun saveOverlayNasdaq(show: Boolean) { preferences.edit().putBoolean(KEY_OVERLAY_NASDAQ, show).apply() }

    override fun loadOverlayGold(): Boolean = preferences.getBoolean(KEY_OVERLAY_GOLD, true)
    override fun saveOverlayGold(show: Boolean) { preferences.edit().putBoolean(KEY_OVERLAY_GOLD, show).apply() }

    override fun loadOverlayFunds(): Boolean = preferences.getBoolean(KEY_OVERLAY_FUNDS, true)
    override fun saveOverlayFunds(show: Boolean) { preferences.edit().putBoolean(KEY_OVERLAY_FUNDS, show).apply() }

    private companion object {
        const val FILE_NAME = "theme_preferences"
        const val KEY_MODE = "theme_mode"
        const val KEY_SHOW_GOLD = "show_gold"
        const val KEY_SHOW_FLOAT = "show_float"
        const val KEY_SHOW_NOTIFICATION = "show_notification"
        const val KEY_OVERLAY_NASDAQ = "overlay_nasdaq"
        const val KEY_OVERLAY_GOLD = "overlay_gold"
        const val KEY_OVERLAY_FUNDS = "overlay_funds"
    }
}
