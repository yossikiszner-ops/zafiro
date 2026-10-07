package com.niki914.zafiro.chat.agentic.accessibility

import com.niki914.zafiro.animation.PointerCurveMath.MovementMode

interface IPointerOverlay {
    /** Fade in at [x],[y] with default idle heading. Non-blocking. */
    fun show(x: Float, y: Float)

    /** Fly from current position to [x],[y] using [mode]. Suspends until animation completes. */
    suspend fun animateTo(x: Float, y: Float, mode: MovementMode = MovementMode.FLY)

    /** Short movement for actual keyboard keys; preserves cancellation and action ordering. */
    suspend fun animateTypingTo(x: Float, y: Float) { animateTo(x, y) }

    /** Trace a swipe after dispatch; caller positions at the start first. Suspends until both phases complete. */
    suspend fun showSwipe(sx: Float, sy: Float, ex: Float, ey: Float, duration: Long)

    /** Feedback means Android accepted an action, not that its external effect is verified. */
    fun actionAccepted() {}

    /** Fade out and remove from window. Non-blocking. */
    fun hide()

    /** Cancel all animations and remove the view from the window immediately. */
    fun dispose()
}
