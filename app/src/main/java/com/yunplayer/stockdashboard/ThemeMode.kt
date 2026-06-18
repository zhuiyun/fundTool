package com.yunplayer.stockdashboard

enum class ThemeMode(val storedValue: String) {
    FollowSystem("system"),
    Light("light"),
    Dark("dark");

    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        FollowSystem -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        fun fromStoredValue(value: String?): ThemeMode {
            return entries.firstOrNull { it.storedValue == value } ?: FollowSystem
        }
    }
}
