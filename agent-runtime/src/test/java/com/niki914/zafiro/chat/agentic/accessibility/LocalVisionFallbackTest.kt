package com.niki914.zafiro.chat.agentic.accessibility

// Vision is not requested for a uniquely resolved semantic target; empty/missing/failed states escalate perception.
import org.junit.Assert.*
import org.junit.Test

class LocalVisionFallbackTest {
    @Test fun validSemanticScreenAvoidsVision() {
        val target = SemanticTarget(resourceId = "com.test:id/search")
        val screen = ScreenState("com.test", 1, 1, listOf(ScreenElement("0", target.resourceId,
            "Button", "Search", "", true, false, true, false, listOf(0,0,48,48))))
        assertNull(LocalVisionFallback.needed(screen, target))
        assertEquals(VisionReason.MissingTarget, LocalVisionFallback.needed(screen, target.copy(resourceId = "com.test:id/other")))
        assertEquals(VisionReason.VerificationFailed, LocalVisionFallback.needed(screen, target, true))
        assertEquals(VisionReason.EmptyTree, LocalVisionFallback.needed(ScreenState.Empty, target))
    }
}
