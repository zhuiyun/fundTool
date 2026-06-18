package com.yunplayer.stockdashboard

object SystemBars {
    fun scrimColor(darkTheme: Boolean): Int {
        return if (darkTheme) 0xFF0D1014.toInt() else 0xFFF4F7FA.toInt()
    }
}
