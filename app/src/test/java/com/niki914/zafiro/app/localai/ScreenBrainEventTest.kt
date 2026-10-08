package com.niki914.zafiro.app.localai

// Event storms must not poll the screen; disconnect must discard pending reads and private screen state.
import android.app.Application
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.niki914.zafiro.chat.agentic.accessibility.ScreenBrain
import com.niki914.zafiro.chat.agentic.accessibility.ScreenState
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ScreenBrainEventTest {
    private var reads = 0
    @Before fun prepare() { ScreenBrain.disconnect(); ScreenBrain.activeTurn = true }
    @After fun clear() { ScreenBrain.disconnect(); ScreenBrain.activeTurn = false }
    @Suppress("DEPRECATION")
    private fun root(password: Boolean = false): AccessibilityNodeInfo {
        reads++
        return AccessibilityNodeInfo.obtain().apply {
            packageName = "com.test"
            className = "android.widget.EditText"
            viewIdResourceName = "com.test:id/field"
            text = "private field"
            isVisibleToUser = true
            isEnabled = true
            isEditable = true
            isPassword = password
        }
    }
    @Test fun eventBurstProducesOneFreshSemanticSnapshot() {
        ScreenBrain.connect { root() }
        repeat(100) { ScreenBrain.onUiEvent() }
        assertEquals(0, reads)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        assertEquals(1, reads)
        assertEquals("com.test", ScreenBrain.state.value.packageName)
        assertEquals("private field", ScreenBrain.state.value.elements.single().text)
        ScreenBrain.onUiEvent()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        assertEquals(2, reads)
    }
    @Test fun passiveAwarenessIsCoalescedAndDisconnectCancelsPendingRead() {
        ScreenBrain.activeTurn = false
        ScreenBrain.connect { root() }
        repeat(50) { ScreenBrain.onUiEvent() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(0, reads)
        ScreenBrain.disconnect()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(0, reads)
        assertEquals(ScreenState.Empty, ScreenBrain.state.value)
    }
    @Test fun passwordTextIsRedactedFromEphemeralState() {
        ScreenBrain.connect { root(password = true) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(80))
        assertEquals("", ScreenBrain.state.value.elements.single().text)
    }
}
