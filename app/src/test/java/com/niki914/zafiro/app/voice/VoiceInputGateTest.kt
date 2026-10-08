package com.niki914.zafiro.app.voice

// Protects against the reported speaker -> transcription -> response feedback loop.
import org.junit.Assert.*
import org.junit.Test

class VoiceInputGateTest {
    @Test fun speakerAndEchoTailCannotBecomeANewRequest() {
        val gate = VoiceInputGate()
        val lease = gate.beginPlayback()
        assertFalse(gate.canCapture(1000))
        gate.endPlayback(lease, 1000)
        assertFalse(gate.canCapture(1649))
        assertTrue(gate.canCapture(1650))
    }
    @Test fun nextSentenceClosesInputAgain() {
        val gate = VoiceInputGate()
        val first = gate.beginPlayback()
        gate.endPlayback(first, 1000)
        gate.beginPlayback()
        gate.endPlayback(first, 2000) // Completion of an interrupted sentence cannot reopen capture.
        assertFalse(gate.canCapture(2000))
    }
}
