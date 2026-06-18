package com.yunplayer.stockdashboard

import android.content.Context

interface ThemePreferenceStore {
    fun load(): ThemeMode
    fun save(mode: ThemeMode)
    fun loadShowGold(): Boolean = true
    fun saveShowGold(show: Boolean) {}
}

class SharedPreferencesThemePreferenceStore(context: Context) : ThemePreferenceStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): ThemeMode {
        return ThemeMode.fromStoredValue(preferences.getString(KEY_MODE, null))
    }

    override fun save(mode: ThemeMode) {
        preferences.edit().putString(KEY_MODE, mode.storedValue).apply()
    }

    override fun loadShowGold(): Boolean {
        return preferences.getBoolean(KEY_SHOW_GOLD, true)
    }

    override fun saveShowGold(show: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_GOLD, show).apply()
    }

    private companion object {
        const val FILE_NAME = "theme_preferences"
        const val KEY_MODE = "theme_mode"
        const val KEY_SHOW_GOLD = "show_gold"
    }
}
