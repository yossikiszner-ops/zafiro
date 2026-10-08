package com.niki914.zafiro.app.voice

// Protects against ordinary background conversation being submitted as a command.
import org.junit.Assert.*
import org.junit.Test

class WakeCommandTest {
    @Test fun wakeWordMustLeadTheUtterance() {
        assertNull(WakeCommand.parse("I was talking about Jarvis today"))
        assertNull(WakeCommand.parse("Jarvison open WhatsApp"))
    }
    @Test fun wakeWordIsRemovedBeforeCommandSubmission() {
        assertEquals("open WhatsApp", WakeCommand.parse("Hey Jarvis, open WhatsApp"))
        assertEquals("", WakeCommand.parse("Jarvis"))
    }
}
