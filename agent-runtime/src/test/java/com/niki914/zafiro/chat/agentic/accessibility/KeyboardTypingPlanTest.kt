package com.niki914.zafiro.chat.agentic.accessibility

// Protects physical typing from accidentally pressing Send/Enter or substituting the wrong Unicode key.
import org.junit.Assert.*
import org.junit.Test

class KeyboardTypingPlanTest {
    @Test fun multilineAndOversizedInputCannotBecomeKeyboardTaps() {
        assertNull(KeyboardTypingPlan.characters("Good night\n"))
        assertNull(KeyboardTypingPlan.characters("a".repeat(161)))
    }
    @Test fun mixedLanguageAndEmojiStayAsWholeCharacters() {
        assertEquals(listOf("ל", "a", "🙂"), KeyboardTypingPlan.characters("לa🙂"))
        assertTrue(KeyboardTypingPlan.matches("ל", null, "ל"))
        assertFalse(KeyboardTypingPlan.matches("A", "a", null))
    }
    @Test fun spaceDoesNotMatchSendOrAnArbitraryLabel() {
        assertTrue(KeyboardTypingPlan.matches(" ", null, "רווח"))
        assertFalse(KeyboardTypingPlan.matches(" ", null, "Send"))
    }
}
