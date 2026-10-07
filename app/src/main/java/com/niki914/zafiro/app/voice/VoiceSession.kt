package com.niki914.zafiro.app.voice

import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.niki914.zafiro.business.permission.Permission
import com.niki914.zafiro.business.permission.PermissionManager
import com.niki914.zafiro.business.permission.PermissionState
import com.niki914.zafiro.service.requireService
import com.niki914.zafiro.remoteview.glass.ZafiroGlassPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque

/** Foreground-only voice lifecycle. Audio stays in memory until a completed utterance. */
internal class VoiceSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val transcriber: VoiceTranscriber,
    private val synthesizer: VoiceSynthesizer,
    private val onTranscript: (String) -> Unit,
) {
    private val mutable = MutableStateFlow(VoiceStatus())
    val status = mutable.asStateFlow()
    private var microphone: AudioRecord? = null
    private var capture: Job? = null
    @Volatile private var epoch = 0
    private var processing: Job? = null
    private val speechCancellation = SpeechCancellation()
    private var playback: Job?
        get() = speechCancellation.job
        set(value) { speechCancellation.job = value }
    @Volatile private var track: AudioTrack? = null
    var voice = "Charon"
    var style = "Calm, precise, serious. Natural pace."
    var model = ""
    var speed = 1f
    var autoSpeak = true
    private val inputGate = VoiceInputGate()
    @Volatile private var awaitingAgent = false
    @Volatile private var accepting = true

    @android.annotation.SuppressLint("MissingPermission") // Central PermissionManager checks before capture.
    fun start() {
        if (capture?.isActive == true) return
        if (requireService<PermissionManager>().status(Permission.MICROPHONE) != PermissionState.GRANTED) {
            fail(VoiceProblem.Microphone); return
        }
        val generation = ++epoch
        accepting = true
        mutable.value = VoiceStatus(active = true, phase = ZafiroGlassPhase.Listening)
        capture = scope.launch(Dispatchers.IO) {
            var ownedRecorder: AudioRecord? = null
            var speechClassifier: com.konovalov.vad.webrtc.VadWebRTC? = null
            var aec: AcousticEchoCanceler? = null; var noise: NoiseSuppressor? = null
            try {
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minimum <= 0) throw VoiceFailure(VoiceProblem.Microphone)
                val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 3200))
                ownedRecorder = recorder
                microphone = recorder
                if (recorder.state != AudioRecord.STATE_INITIALIZED) throw VoiceFailure(VoiceProblem.Microphone)
                if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
                if (NoiseSuppressor.isAvailable()) noise = NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
                speechClassifier = try {
                    com.konovalov.vad.webrtc.VadWebRTC(
                        sampleRate = com.konovalov.vad.webrtc.config.SampleRate.SAMPLE_RATE_16K,
                        frameSize = com.konovalov.vad.webrtc.config.FrameSize.FRAME_SIZE_320,
                        mode = com.konovalov.vad.webrtc.config.Mode.VERY_AGGRESSIVE,
                    )
                } catch (_: LinkageError) { null } catch (_: Exception) { null } // Unsupported VAD configuration: local energy fallback.
                recorder.startRecording()
                val vad = LocalVoiceDetector(); val frame = ShortArray(320); val preRoll = ArrayDeque<ByteArray>()
                var utterance: ByteArrayOutputStream? = null; var silentFrames = 0
                while (isActive) {
                    val count = recorder.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                    if (count <= 0) { if (!isActive) break; throw VoiceFailure(VoiceProblem.Microphone) }
                    if (!accepting || !inputGate.canCapture(android.os.SystemClock.elapsedRealtime())) { vad.reset(); preRoll.clear(); utterance = null; continue }
                    val bytes = VoiceAudio.pcm(frame, count)
                    preRoll.addLast(bytes); while (preRoll.size > 10) preRoll.removeFirst()
                    when (vad.accept(frame, count, speechClassifier?.isSpeech(if (count == frame.size) frame else ShortArray(frame.size).also { frame.copyInto(it, endIndex = count) }))) {
                        LocalVoiceDetector.Event.Start -> {
                            silentFrames = 0
                            interruptSpeech()
                            utterance = ByteArrayOutputStream().apply { preRoll.forEach { write(it) } }
                            publish(ZafiroGlassPhase.Listening)
                        }
                        LocalVoiceDetector.Event.End -> {
                            utterance?.write(bytes)
                            utterance?.toByteArray()?.let(::transcribe)
                            utterance = null; preRoll.clear()
                        }
                        LocalVoiceDetector.Event.None -> utterance?.write(bytes)
                    }
                    if (utterance != null && utterance!!.size() >= 16000 * 2 * 30) {
                        transcribe(utterance!!.toByteArray()); utterance = null; vad.reset(); preRoll.clear()
                    }
                    if (utterance == null && accepting && playback?.isActive != true) {
                        silentFrames++
                        if (silentFrames >= 1500) break // 30 seconds idle: release microphone.
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { if (epoch == generation) fail((e as? VoiceFailure)?.problem ?: VoiceProblem.Microphone) }
            finally {
                speechClassifier?.close(); aec?.release(); noise?.release()
                ownedRecorder?.let { runCatching { it.stop() }; it.release(); if (microphone === it) microphone = null }
                withContext(NonCancellable + Dispatchers.Main) {
                    if (epoch == generation && mutable.value.problem == null) { mutable.value = VoiceStatus(); VoiceActivity.phase.value = null }
                }
            }
        }
    }
    private fun transcribe(pcm: ByteArray) {
        accepting = false
        awaitingAgent = true
        processing = scope.launch {
            publish(ZafiroGlassPhase.Understanding)
            try {
                val text = transcriber.transcribe(VoiceAudio.wav(pcm))
                if (text.isBlank()) { awaitingAgent = false; accepting = true; publish(ZafiroGlassPhase.Listening) }
                else {
                    mutable.value = mutable.value.copy(transcript = text, phase = ZafiroGlassPhase.Thinking)
                    VoiceActivity.phase.value = null // Actual AgentState owns activity while executing.
                    onTranscript(text)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { accepting = true; fail((e as? VoiceFailure)?.problem ?: VoiceProblem.Provider) }
        }
    }
    fun response(text: String, preview: Boolean = false, announcement: Boolean = false) {
        if (!mutable.value.active && !preview) return
        if (!preview && !announcement) awaitingAgent = false
        accepting = false
        if ((!autoSpeak && !preview) || text.isBlank()) { accepting = !awaitingAgent; if (accepting) publish(ZafiroGlassPhase.Listening); return }
        interruptSpeech()
        val lease = inputGate.beginPlayback()
        playback = scope.launch {
            try {
                val chunks = VoiceAudio.chunks(text)
                coroutineScope {
                    var next = async { synthesizer.synthesize(chunks.first(), voice, style, model) }
                    chunks.indices.forEach { i ->
                        val clip = next.await()
                        if (i + 1 < chunks.size) next = async { synthesizer.synthesize(chunks[i + 1], voice, style, model) }
                        publish(ZafiroGlassPhase.Speaking)
                        play(clip)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { fail((e as? VoiceFailure)?.problem ?: VoiceProblem.Provider) }
            finally {
                if (inputGate.endPlayback(lease, android.os.SystemClock.elapsedRealtime())) {
                    accepting = !awaitingAgent
                    if (accepting && mutable.value.problem == null && mutable.value.active) publish(ZafiroGlassPhase.Listening)
                }
            }
        }
    }
    private suspend fun play(clip: VoiceAudio.Clip) = withContext(Dispatchers.IO) {
        val audio = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(clip.rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(4096, AudioTrack.getMinBufferSize(clip.rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))).build()
        try {
            track = audio
            audio.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(.75f, 1.5f))
            audio.play(); var offset = 0
            while (offset < clip.pcm.size) {
                ensureActive()
                val written = audio.write(clip.pcm, offset, minOf(4096, clip.pcm.size - offset), AudioTrack.WRITE_NON_BLOCKING)
                if (written < 0) throw VoiceFailure(VoiceProblem.Provider)
                offset += written; if (written == 0) delay(10)
            }
            while (audio.playbackHeadPosition < clip.pcm.size / 2) { ensureActive(); delay(20) }
        } finally {
            if (track === audio) track = null
            runCatching { audio.stop() }; audio.release()
        }
    }
    fun interruptSpeech() {
        speechCancellation.interrupt(::stopTrack)
        if (mutable.value.phase == ZafiroGlassPhase.Speaking) publish(ZafiroGlassPhase.Interrupted)
    }
    private fun stopTrack() { track?.let { runCatching { it.pause(); it.flush() } } }
    fun listenNow() {
        interruptSpeech()
        accepting = true
        if (mutable.value.active) publish(ZafiroGlassPhase.Listening)
    }
    fun stop() {
        epoch++
        capture?.cancel(); capture = null; processing?.cancel(); processing = null
        interruptSpeech(); runCatching { microphone?.stop() }
        awaitingAgent = false; accepting = true; mutable.value = VoiceStatus(); VoiceActivity.phase.value = null
    }
    fun submitRecognized(text: String) {
        if (text.isBlank()) return
        awaitingAgent = true; accepting = false
        onTranscript(text)
    }
    fun reportFailure(problem: VoiceProblem) { fail(problem) }
    fun microphoneFailed() { fail(VoiceProblem.Microphone) }
    fun agentFailed() { awaitingAgent = false; accepting = true; fail(VoiceProblem.Provider) }
    private fun fail(problem: VoiceProblem) { mutable.value = mutable.value.copy(problem = problem, phase = ZafiroGlassPhase.Error); VoiceActivity.phase.value = ZafiroGlassPhase.Error }
    private fun publish(phase: ZafiroGlassPhase) { mutable.value = mutable.value.copy(phase = phase, problem = null); VoiceActivity.phase.value = phase }
}
internal data class VoiceStatus(val active: Boolean = false, val phase: ZafiroGlassPhase = ZafiroGlassPhase.Dormant, val transcript: String = "", val problem: VoiceProblem? = null)
internal object VoiceActivity { val phase = MutableStateFlow<ZafiroGlassPhase?>(null) }
