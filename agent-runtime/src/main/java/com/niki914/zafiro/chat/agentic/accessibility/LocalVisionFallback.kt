package com.niki914.zafiro.chat.agentic.accessibility

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

enum class VisionReason { EmptyTree, MissingTarget, UnexpectedScreen, VerificationFailed }
data class VisualTarget(val label: String, val bounds: List<Int>, val confidence: Double, val advisoryOnly: Boolean = false)
fun interface LocalUiDetector {
    val budgetMs: Long get() = 1_000L
    suspend fun detect(image: Bitmap): List<VisualTarget>
}

/** Optional perception seam. No detector is claimed installed; never grants permission or executes taps. */
object LocalVisionFallback {
    @Volatile var detector: LocalUiDetector? = null
    private val mutex = Mutex()
    fun needed(state: ScreenState, target: SemanticTarget?, verificationFailed: Boolean = false): VisionReason? = when {
        state.packageName == null || state.elements.isEmpty() -> VisionReason.EmptyTree
        verificationFailed -> VisionReason.VerificationFailed
        target != null && state.resolve(target) == null -> VisionReason.MissingTarget
        else -> null
    }
    suspend fun inspect(reason: VisionReason?, expectedPackage: String): List<VisualTarget>? {
        val active = detector ?: return null // No screenshot if no verified, installed runtime exists.
        reason ?: return null
        if (!mutex.tryLock()) return null
        try {
            if (AccessibilityController.foregroundPackage() != expectedPackage) return null
            val eventTime = AccessibilityController.lastUiEventTime
            val image = AccessibilityController.captureScreenImage().getOrNull() ?: return null
            return try {
                withTimeoutOrNull(active.budgetMs.coerceIn(1_000, 35_000)) { active.detect(image) }?.filter {
                    (it.advisoryOnly || it.confidence >= 0.95) && it.bounds.size == 4 && it.bounds[0] >= 0 && it.bounds[1] >= 0 &&
                        it.bounds[2] <= image.width && it.bounds[3] <= image.height &&
                        it.bounds[2] > it.bounds[0] && it.bounds[3] > it.bounds[1]
                }?.takeIf { AccessibilityController.foregroundPackage() == expectedPackage &&
                    AccessibilityController.lastUiEventTime == eventTime }
            } finally { image.recycle() }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return null }
        finally { mutex.unlock() }
    }
}
