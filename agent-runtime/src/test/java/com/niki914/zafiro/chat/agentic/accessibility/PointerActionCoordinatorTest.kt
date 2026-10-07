package com.niki914.zafiro.chat.agentic.accessibility

import com.niki914.zafiro.animation.PointerCurveMath.MovementMode
import com.niki914.zafiro.chat.agentic.buildin.BuiltinToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PointerActionCoordinatorTest {
    private class Pointer(val events: MutableList<String>, val cancel: Boolean = false) : IPointerOverlay {
        override fun show(x: Float, y: Float) {}
        override suspend fun animateTo(x: Float, y: Float, mode: MovementMode) {
            events += "focus"
            if (cancel) throw CancellationException("user interrupted")
        }
        override suspend fun showSwipe(sx: Float, sy: Float, ex: Float, ey: Float, duration: Long) {}
        override fun actionAccepted() { events += "feedback" }
        override fun hide() {}
        override fun dispose() {}
    }
    @Test fun acceptedActionPrecedesFeedback() = runTest {
        val events = mutableListOf<String>()
        PointerActionCoordinator.execute(Pointer(events), 20f, 30f) {
            events += "android action"; BuiltinToolResult.success("accepted")
        }
        assertEquals(listOf("focus", "android action", "feedback"), events)
    }
    @Test fun rejectionNeverShowsPositiveFeedback() = runTest {
        val events = mutableListOf<String>()
        val result = PointerActionCoordinator.execute(Pointer(events), 20f, 30f) {
            events += "rejected"; BuiltinToolResult.failure("DENIED", "denied")
        }
        assertFalse(result.ok)
        assertEquals(listOf("focus", "rejected"), events)
    }
    @Test fun cancellationBeforeDispatchPreventsAndroidAction() = runTest {
        val events = mutableListOf<String>()
        try {
            PointerActionCoordinator.execute(Pointer(events, cancel = true), 20f, 30f) {
                events += "android action"; BuiltinToolResult.success("accepted")
            }
            fail("expected cancellation")
        } catch (_: CancellationException) {}
        assertEquals(listOf("focus"), events)
    }
    @Test fun missingVisualizationDoesNotDisableAndroidAuthority() = runTest {
        assertTrue(PointerActionCoordinator.execute(null, 20f, 30f) { BuiltinToolResult.success("accepted") }.ok)
    }
}
