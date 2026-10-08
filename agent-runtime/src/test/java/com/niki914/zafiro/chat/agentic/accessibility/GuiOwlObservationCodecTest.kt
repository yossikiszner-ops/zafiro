package com.niki914.zafiro.chat.agentic.accessibility

import org.junit.Assert.*
import org.junit.Test

class GuiOwlObservationCodecTest {
    @Test fun normalizedCoordinatesUseOriginalScreenAndRemainAdvisory() {
        val target = GuiOwlObservationCodec.parse("""{"targets":[{"label":"חיפוש","bounds":[100,200,400,300],"confidence":1.0}]}""", 1080, 2340).single()
        assertEquals(listOf(108,468,432,702), target.bounds)
        assertEquals("חיפוש", target.label)
        assertEquals(0.0, target.confidence, 0.0)
        assertTrue(target.advisoryOnly)
    }
    @Test fun malformedPredictionsFailInsteadOfProducingPartialTargets() {
        listOf(
            """{"targets":[{"label":"button","bounds":[0,0,1001,100]}]}""",
            """{"targets":[{"label":"button","bounds":[900,0,100,100]}]}""",
            """{"targets":[{"label":"button","bounds":[0,0,0,100]}]}""",
            """{"targets":[{"label":"","bounds":[0,0,100,100]}]}""",
            """{"targets":[{"label":"button","bounds":[0,0,100]}]}""",
            """{"tap":[100,100]}""",
        ).forEach { output ->
            assertTrue(output, runCatching { GuiOwlObservationCodec.parse(output, 1080, 2340) }.isFailure)
        }
    }
}
