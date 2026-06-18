package com.yunplayer.stockdashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HoldingItemKeyTest {
    @Test
    fun duplicateHoldingNamesStillReceiveUniqueStableKeys() {
        val holding = Holding("同名股票", 5.0, 1.0)

        assertNotEquals(holdingItemKey(0, holding), holdingItemKey(1, holding))
        assertEquals(holdingItemKey(0, holding), holdingItemKey(0, holding))
    }
}
