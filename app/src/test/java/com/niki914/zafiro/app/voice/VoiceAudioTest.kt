package com.niki914.zafiro.app.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceAudioTest {
    @Test fun bargeInCancelsGenerationAndPrefetchAndStopsAudioSynchronously() {
        val parent = kotlinx.coroutines.Job()
        val prefetch = kotlinx.coroutines.Job(parent)
        val interruption = SpeechCancellation().apply { job = parent }
        var stopped = false
        interruption.interrupt { stopped = true }
        assertTrue(stopped)
        assertTrue(parent.isCancelled)
        assertTrue(prefetch.isCancelled)
        assertNull(interruption.job)
    }
    @Test fun silenceDoesNotProduceUtterance() {
        val vad = LocalVoiceDetector()
        repeat(2000) { assertEquals(LocalVoiceDetector.Event.None, vad.accept(ShortArray(320), 320)) }
        assertFalse(vad.speaking)
    }
    @Test fun speechStartRequiresSustainedSignalAndEndRequiresQuiet() {
        val vad = LocalVoiceDetector()
        val speech = ShortArray(320) { 2500 }
        repeat(2) { assertEquals(LocalVoiceDetector.Event.None, vad.accept(speech, 320)) }
        assertEquals(LocalVoiceDetector.Event.Start, vad.accept(speech, 320))
        repeat(24) { assertEquals(LocalVoiceDetector.Event.None, vad.accept(ShortArray(320), 320)) }
        assertEquals(LocalVoiceDetector.Event.End, vad.accept(ShortArray(320), 320))
        assertFalse(vad.speaking)
    }
    @Test fun isolatedNoiseDoesNotOpenUtterance() {
        val vad = LocalVoiceDetector()
        repeat(30) { vad.accept(ShortArray(320) { 3000 }, 320); vad.accept(ShortArray(320), 320) }
        assertFalse(vad.speaking)
    }
    @Test fun wavRoundTripsPcmAndRate() {
        val pcm = byteArrayOf(1, 0, -1, 127, 0, -128)
        val clip = VoiceAudio.decode(VoiceAudio.wav(pcm, 16000), "audio/wav")
        assertArrayEquals(pcm, clip.pcm); assertEquals(16000, clip.rate)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidAudioIsRejected() {
        VoiceAudio.decode(byteArrayOf(1, 2), "audio/mpeg")
    }
    @Test fun chunkingPreservesTextAndBoundsNormalSentences() {
        val text = "שלום עולם. This is a clear sentence in English. " + "Another sentence. ".repeat(40)
        val chunks = VoiceAudio.chunks(text)
        assertEquals(text.trim().replace(Regex("\\s+"), " "), chunks.joinToString(" "))
        assertTrue(chunks.all { it.length <= 350 })
    }
}
