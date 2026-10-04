package com.niki914.zafiro.app.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Local energy VAD, 20 ms frames, bounded utterances. No network or Android dependency. */
internal class LocalVoiceDetector(private val threshold: Float = .018f) {
    private var voiced = 0
    private var quiet = 0
    var speaking = false
        private set
    fun accept(samples: ShortArray, count: Int): Event {
        if (count <= 0) return Event.None
        val rms = sqrt((0 until count).sumOf { val v = samples[it] / 32768.0; v * v } / count).toFloat()
        if (rms > threshold) { voiced++; quiet = 0 } else { quiet++; if (!speaking) voiced = 0 }
        if (!speaking && voiced >= 3) { speaking = true; return Event.Start }
        if (speaking && quiet >= 25) { reset(); return Event.End }
        return Event.None
    }
    fun reset() { speaking = false; voiced = 0; quiet = 0 }
    enum class Event { None, Start, End }
}

internal object VoiceAudio {
    fun wav(pcm: ByteArray, rate: Int = 16000): ByteArray {
        require(pcm.size % 2 == 0)
        return ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(pcm.size); put(pcm)
        }.array()
    }
    fun pcm(samples: ShortArray, count: Int) = ByteBuffer.allocate(count * 2)
        .order(ByteOrder.LITTLE_ENDIAN).apply { repeat(count) { putShort(samples[it]) } }.array()

    data class Clip(val pcm: ByteArray, val rate: Int)
    fun decode(bytes: ByteArray, mime: String): Clip {
        if (bytes.size >= 12 && String(bytes, 0, 4) == "RIFF") {
            require(String(bytes, 8, 4) == "WAVE")
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var rate = 24000; var data: ByteArray? = null; var offset = 12; var valid = false
            while (offset + 8 <= bytes.size) {
                val id = String(bytes, offset, 4); val size = b.getInt(offset + 4)
                require(size >= 0 && size <= bytes.size - offset - 8)
                if (id == "fmt ") {
                    require(size >= 16 && b.getShort(offset + 8).toInt() == 1 && b.getShort(offset + 10).toInt() == 1 && b.getShort(offset + 22).toInt() == 16)
                    rate = b.getInt(offset + 12); valid = true
                }
                if (id == "data") data = bytes.copyOfRange(offset + 8, offset + 8 + size)
                offset += 8 + size + size % 2
            }
            require(valid && data != null && rate in 8000..96000)
            return Clip(data!!, rate)
        }
        require(mime.startsWith("audio/L16", true) || mime.startsWith("audio/pcm", true)) { "Unsupported audio format" }
        val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toInt() ?: 24000
        require(bytes.size % 2 == 0 && rate in 8000..96000)
        return Clip(bytes, rate)
    }
    /** Sentence-sized exact-text chunks. No conversation history or reasoning is spoken. */
    fun chunks(text: String): List<String> {
        val result = mutableListOf<String>(); val pending = StringBuilder()
        for (word in text.trim().split(Regex("\\s+"))) {
            if (pending.isNotEmpty() && pending.length + word.length > 350) { result += pending.toString(); pending.clear() }
            if (pending.isNotEmpty()) pending.append(' ')
            pending.append(word)
            if (word.lastOrNull() in listOf('.', '!', '?', '։', '׃') && pending.length >= 35) { result += pending.toString(); pending.clear() }
        }
        if (pending.isNotBlank()) result += pending.toString()
        return result
    }
}

/** Cancellation owns generation and all prefetched chunks; stop is synchronous at speech onset. */
internal class SpeechCancellation {
    @Volatile var job: kotlinx.coroutines.Job? = null
    fun interrupt(stopAudio: () -> Unit) {
        job?.cancel(); job = null
        stopAudio()
    }
}
