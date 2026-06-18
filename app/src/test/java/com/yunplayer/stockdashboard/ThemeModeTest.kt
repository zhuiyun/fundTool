package com.yunplayer.stockdashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun modesResolveAgainstSystemSetting() {
        assertTrue(ThemeMode.FollowSystem.resolveDark(systemDark = true))
        assertFalse(ThemeMode.FollowSystem.resolveDark(systemDark = false))
        assertFalse(ThemeMode.Light.resolveDark(systemDark = true))
        assertTrue(ThemeMode.Dark.resolveDark(systemDark = false))
    }

    @Test
    fun storedValuesRestoreOrFallBackToFollowSystem() {
        assertEquals(ThemeMode.Light, ThemeMode.fromStoredValue("light"))
        assertEquals(ThemeMode.Dark, ThemeMode.fromStoredValue("dark"))
        assertEquals(ThemeMode.FollowSystem, ThemeMode.fromStoredValue("unknown"))
        assertEquals(ThemeMode.FollowSystem, ThemeMode.fromStoredValue(null))
    }
}
