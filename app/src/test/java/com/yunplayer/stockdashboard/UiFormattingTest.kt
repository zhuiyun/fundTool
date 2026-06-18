package com.yunplayer.stockdashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class UiFormattingTest {
    @Test
    fun signedPercentUsesTwoDecimalsAndExplicitPositiveSign() {
        assertEquals("+1.20%", formatSignedPercent(1.2))
        assertEquals("-0.35%", formatSignedPercent(-0.35))
        assertEquals("0.00%", formatSignedPercent(0.0))
    }

    @Test
    fun percentTextCanBeParsedForTrendColor() {
        assertEquals(0.49, parsePercentText("0.49%"), 0.0001)
        assertEquals(-1.2, parsePercentText("-1.20%"), 0.0001)
        assertEquals(0.0, parsePercentText("--"), 0.0001)
    }
}
