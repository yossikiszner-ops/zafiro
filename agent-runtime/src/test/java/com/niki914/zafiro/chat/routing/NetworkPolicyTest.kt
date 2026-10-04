package com.niki914.zafiro.chat.routing

import org.junit.Assert.*
import org.junit.Test

class NetworkPolicyTest {
    @Test fun managedConnectionsRequireExactOrigin() {
        NetworkPolicy.enabled.value = true
        NetworkPolicy.configure(listOf("https://provider.test/v1/chat", "https://mcp.test:8443/server"))
        assertTrue(NetworkPolicy.permits("https://provider.test/v1/models"))
        assertTrue(NetworkPolicy.permits("https://mcp.test:8443/call"))
        assertFalse(NetworkPolicy.permits("https://provider.test.attacker.test/chat"))
        assertFalse(NetworkPolicy.permits("http://provider.test/chat"))
        assertFalse(NetworkPolicy.permits("https://mcp.test/call"))
        assertFalse(NetworkPolicy.permits("file:///private"))
    }
    @Test fun invalidConfiguredUrlsNeverExpandScope() {
        NetworkPolicy.enabled.value = true
        NetworkPolicy.configure(listOf("not a URL", "file:///private"))
        assertFalse(NetworkPolicy.permits("https://unknown.test"))
        assertTrue(NetworkPolicy.destinations.value.isEmpty())
    }
}
