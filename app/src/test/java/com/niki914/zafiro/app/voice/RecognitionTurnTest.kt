package com.niki914.zafiro.app.voice

// Protects voice commands against duplicate finals and callbacks after speech playback starts.
import org.junit.Assert.*
import org.junit.Test

class RecognitionTurnTest {
    @Test fun partialResultsDoNotConsumeTheFinalResult() {
        val turn = RecognitionTurn { true }
        val token = turn.begin()
        assertTrue(turn.accepts(token))
        assertTrue(turn.finish(token))
        assertFalse(turn.finish(token))
    }
    @Test fun cancelledInputCannotReplaceTheNewInput() {
        val turn = RecognitionTurn { true }
        val old = turn.begin()
        turn.cancel()
        val current = turn.begin()
        assertFalse(turn.finish(old))
        assertTrue(turn.finish(current))
    }
    @Test fun speechAndItsEchoTailRejectLateRecognitionResults() {
        val gate = VoiceInputGate()
        var now = 1000L
        val turn = RecognitionTurn { gate.canCapture(now) }
        val token = turn.begin()
        val lease = gate.beginPlayback()
        assertFalse(turn.accepts(token))
        gate.endPlayback(lease, now)
        now = 1649
        assertFalse(turn.finish(token))
        now = 1650
        assertTrue(turn.finish(turn.begin()))
    }
}
