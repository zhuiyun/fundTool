package com.yunplayer.stockdashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemBarsTest {
    @Test
    fun systemBarColorsMatchResolvedTheme() {
        assertEquals(0xFF0D1014.toInt(), SystemBars.scrimColor(darkTheme = true))
        assertEquals(0xFFF4F7FA.toInt(), SystemBars.scrimColor(darkTheme = false))
    }
}
