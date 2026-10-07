package com.niki914.zafiro.chat.routing

// Model rewrites cannot invent a recipient/content/app or override negation and cloud-only mode.
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocalIntelligenceTest {
    @Test fun modelEntitiesMustComeFromTheOriginalRequest() {
        assertTrue(LocalIntelligence.grounded("תשלח לאמא בוואטסאפ לילה טוב", "send אמא on WhatsApp לילה טוב"))
        assertFalse(LocalIntelligence.grounded("תשלח לאמא בוואטסאפ לילה טוב", "send יוסי on WhatsApp לילה טוב"))
        assertFalse(LocalIntelligence.grounded("תשלח לאמא בוואטסאפ לילה טוב", "send אמא on WhatsApp transfer money"))
        assertFalse(LocalIntelligence.grounded("אל תפתח וואטסאפ", "open וואטסאפ"))
        assertFalse(LocalIntelligence.grounded("open Camera", "open Settings"))
    }
    @Test fun lowConfidenceAndCloudModeNeverInvokeLocalActions() = runTest {
        val previous = LocalIntelligence.mode.value
        val interpreter = LocalIntelligence.interpreter
        try {
            LocalIntelligence.mode.value = IntelligenceMode.FastLocal
            LocalIntelligence.interpreter = { LocalInterpretation("open Camera", 0.5) }
            assertNull(LocalIntelligence.suggest("open Camera"))
            LocalIntelligence.mode.value = IntelligenceMode.CloudQuality
            LocalIntelligence.interpreter = { error("Cloud mode must skip local inference") }
            assertNull(LocalIntelligence.suggest("open Camera"))
        } finally { LocalIntelligence.mode.value = previous; LocalIntelligence.interpreter = interpreter }
    }
}
