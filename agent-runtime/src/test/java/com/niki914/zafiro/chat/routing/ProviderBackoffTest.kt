package com.niki914.zafiro.chat.routing

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderBackoffTest {
    @Test fun zeroQuotaDoesNotBecomeAShortRetryLoop() {
        assertEquals(86400000L, ProviderBackoff.delayMillis(429, "Quota exceeded, limit: 0", null))
        assertEquals(34538000L, ProviderBackoff.delayMillis(429, "limit: 0 Please retry in 9h35m38s", null))
    }
    @Test fun respectsLongestProviderDelayAndBoundsBadValues() {
        assertEquals(120000L, ProviderBackoff.delayMillis(429, "{\"retryDelay\":\"120s\"}", "10"))
        assertEquals(60000L, ProviderBackoff.delayMillis(503, "", "NaN"))
        assertEquals(86400000L, ProviderBackoff.delayMillis(429, "", "999999999"))
    }
}
