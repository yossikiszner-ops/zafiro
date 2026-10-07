package com.niki914.zafiro.chat.agentic.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.niki914.zafiro.chat.agentic.buildin.BuiltinToolResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Never guesses coordinates or presses Enter. Null permits normal input only before any key was tapped. */
internal object PhysicalKeyboardTyper {
    suspend fun type(service: IAccessibility, field: AccessibilityNodeInfo, text: String,
                     pointer: IPointerOverlay?): BuiltinToolResult? {
        val characters = KeyboardTypingPlan.characters(text) ?: return null
        if (field.isPassword || !field.isEditable || field.text?.isNotEmpty() == true) return null
        if (!service.performAction(field, AccessibilityNodeInfo.ACTION_FOCUS, null)) return null
        service.performAction(field, AccessibilityNodeInfo.ACTION_CLICK, null)
        val ready = withTimeoutOrNull(500) {
            while (service.keyboardRoots.isEmpty()) delay(30)
            true
        } == true
        if (!ready || characters.any { findKey(service.keyboardRoots, it) == null }) return null
        var expected = ""
        for (character in characters) {
            currentCoroutineContext().ensureActive()
            if (!field.refresh() || !field.isFocused || field.text?.toString().orEmpty() != expected)
                return BuiltinToolResult.failure("TYPING_INTERRUPTED", "Input changed or focus moved; keyboard typing stopped")
            val key = findKey(service.keyboardRoots, character)
                ?: return BuiltinToolResult.failure("KEY_UNAVAILABLE", "Keyboard changed; typing stopped without guessing")
            val bounds = Rect().also(key::getBoundsInScreen)
            val result = PointerActionCoordinator.execute(pointer, bounds.centerX().toFloat(), bounds.centerY().toFloat(), typing = true) {
                if (service.performAction(key, AccessibilityNodeInfo.ACTION_CLICK, null)) BuiltinToolResult.success("Keyboard key accepted")
                else BuiltinToolResult.failure("KEY_REJECTED", "Keyboard key rejected")
            }
            if (!result.ok) return result
            expected += character
            val verified = withTimeoutOrNull(350) {
                while (field.refresh() && field.isFocused) {
                    if (field.text?.toString().orEmpty() == expected) return@withTimeoutOrNull true
                    delay(20)
                }
                false
            } == true
            if (!verified) return BuiltinToolResult.failure("TYPING_NOT_VERIFIED", "Keyboard input differs from requested text; stopped without retrying")
        }
        return BuiltinToolResult.success("Keyboard typing verified")
    }
    private fun findKey(roots: List<AccessibilityNodeInfo>, character: String): AccessibilityNodeInfo? {
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        roots.forEach(pending::addLast)
        val matches = mutableListOf<AccessibilityNodeInfo>()
        var examined = 0
        while (pending.isNotEmpty() && examined++ < 500) {
            val node = pending.removeFirst()
            if (node.isVisibleToUser && node.isEnabled && node.isClickable &&
                KeyboardTypingPlan.matches(character, node.text?.toString(), node.contentDescription?.toString())) matches += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        return matches.singleOrNull() // Ambiguity is not an excuse for blind tapping.
    }
}

internal object KeyboardTypingPlan {
    fun characters(text: String): List<String>? {
        if (text.isEmpty() || text.length > 160 || text.any { it.isISOControl() }) return null
        return text.codePoints().toArray().map { String(Character.toChars(it)) }
    }
    fun matches(character: String, text: String?, description: String?): Boolean {
        val labels = listOfNotNull(text, description)
        return if (character == " ") labels.any { it == " " || it.lowercase() in setOf("space", "spacebar", "רווח", "מקש רווח") }
        else labels.any { it == character } // Preserve case and Unicode, never approximate a key.
    }
}
