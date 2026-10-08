package com.niki914.zafiro.chat.routing

// Protects local-only requests from silently reaching a cloud provider, while allowing explicit opt-in.
import org.junit.Assert.*
import org.junit.Test

class LocalCloudPolicyTest {
    @Test fun localOnlyBlocksCloudBeforeAnyProviderCall() {
        val mode = LocalIntelligence.mode.value
        val fallback = LocalIntelligence.allowCloudFallback.value
        val diagnostics = LocalIntelligence.diagnostics.value
        try {
            LocalIntelligence.mode.value = IntelligenceMode.FastLocal
            LocalIntelligence.allowCloudFallback.value = false
            var blocked = false
            try { LocalIntelligence.requireCloudPermission() } catch (_: IllegalStateException) { blocked = true }
            assertTrue(blocked)
            assertEquals("LOCAL_ONLY_BLOCKED", LocalIntelligence.diagnostics.value.route)
        } finally {
            LocalIntelligence.mode.value = mode
            LocalIntelligence.allowCloudFallback.value = fallback
            LocalIntelligence.diagnostics.value = diagnostics
        }
    }

    @Test fun explicitFallbackAllowsCloudWhenNoLocalPlanExists() {
        val mode = LocalIntelligence.mode.value
        val fallback = LocalIntelligence.allowCloudFallback.value
        try {
            LocalIntelligence.mode.value = IntelligenceMode.FastLocal
            LocalIntelligence.allowCloudFallback.value = true
            LocalIntelligence.requireCloudPermission()
        } finally {
            LocalIntelligence.mode.value = mode
            LocalIntelligence.allowCloudFallback.value = fallback
        }
    }
}
