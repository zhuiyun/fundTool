package com.yunplayer.stockdashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class WebsiteLinkTest {
    @Test
    fun websiteUsesRequiredHttpAddress() {
        assertEquals("http://web.345569.xyz/", WEBSITE_URL)
    }
}
