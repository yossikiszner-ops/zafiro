package com.niki914.zafiro.app.voice

/** Never submit speaker output to the agent, including its reverberation tail. */
internal class VoiceInputGate(private val echoTailMs: Long = 650) {
    @Volatile private var generation = 0
    @Volatile private var playing = false
    @Volatile private var resumeAt = 0L
    fun beginPlayback(): Int { playing = true; return ++generation }
    fun endPlayback(lease: Int, now: Long): Boolean {
        if (lease != generation) return false
        resumeAt = now + echoTailMs; playing = false; return true
    }
    fun canCapture(now: Long): Boolean = !playing && now >= resumeAt
}
