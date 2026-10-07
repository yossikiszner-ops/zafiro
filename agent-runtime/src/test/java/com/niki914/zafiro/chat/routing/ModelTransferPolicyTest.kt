package com.niki914.zafiro.chat.routing

// Reject shifted/truncated ranges before appending; retain storage headroom for atomic installation.
import org.junit.Assert.*
import org.junit.Test

class ModelTransferPolicyTest {
    @Test fun partialResumeMustMatchThePinnedArtifactRangeExactly() {
        assertEquals(true, ModelTransferPolicy.resume(206, "bytes 30-99/100", 30, 100))
        assertEquals(false, ModelTransferPolicy.resume(200, null, 30, 100))
        assertNull(ModelTransferPolicy.resume(206, "bytes 0-99/100", 30, 100))
        assertNull(ModelTransferPolicy.resume(206, "bytes 30-90/100", 30, 100))
        assertNull(ModelTransferPolicy.resume(206, "bytes 30-99/200", 30, 100))
        assertNull(ModelTransferPolicy.resume(416, null, 100, 100))
        assertNull(ModelTransferPolicy.resume(206, "bytes 9999999999999999999999-99/100", 30, 100))
    }
    @Test fun lowStorageAndOversizedPartialsCannotStartInstallation() {
        val headroom = 64L * 1024 * 1024
        assertFalse(ModelTransferPolicy.storageAvailable(100, 0, 100))
        assertTrue(ModelTransferPolicy.storageAvailable(headroom + 70, 30, 100))
        assertFalse(ModelTransferPolicy.storageAvailable(headroom + 69, 30, 100))
        assertFalse(ModelTransferPolicy.storageAvailable(Long.MAX_VALUE, 101, 100))
    }
}
