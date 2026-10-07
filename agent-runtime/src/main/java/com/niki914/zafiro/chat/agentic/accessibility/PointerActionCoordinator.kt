package com.niki914.zafiro.chat.agentic.accessibility

import com.niki914.zafiro.chat.agentic.buildin.BuiltinToolResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Visualization follows authority; rejection and cancellation never produce positive feedback. */
internal object PointerActionCoordinator {
    suspend fun execute(pointer: IPointerOverlay?, x: Float, y: Float, typing: Boolean = false,
        action: suspend () -> BuiltinToolResult): BuiltinToolResult {
        if (typing) pointer?.animateTypingTo(x, y) else pointer?.animateTo(x, y)
        currentCoroutineContext().ensureActive()
        val result = action()
        currentCoroutineContext().ensureActive()
        if (result.ok) pointer?.actionAccepted()
        return result
    }
}
