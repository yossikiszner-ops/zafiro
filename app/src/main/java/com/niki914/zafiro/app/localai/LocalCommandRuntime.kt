package com.niki914.zafiro.app.localai

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import com.google.ai.edge.litertlm.*
import com.niki914.zafiro.chat.routing.LocalIntelligence
import com.niki914.zafiro.chat.routing.LocalInterpretation
import com.niki914.zafiro.chat.routing.ModelArtifact
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File

data class LocalBenchmark(val loadMs: Long, val warmMs: Long, val firstOutputMs: Long,
    val nativeHeapMiB: Long, val correct: Int, val total: Int,
    val processPssMiB: Long, val decodeTokensPerSecond: Double)

/** A single opt-in engine, no tools and no Android execution. Cold models never delay a user request. */
@OptIn(ExperimentalApi::class)
object LocalCommandRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var reliability = 0.0
    private var idle: Job? = null
    private const val instruction = "Return JSON only: {\"command\":string}. Rewrite ONE Android command as: open APP, go back, volume up, volume down, or send RECIPIENT on WhatsApp CONTENT. Preserve APP/RECIPIENT/CONTENT exactly as in the input, including Hebrew. For questions, negation, ambiguity or multiple unrelated tasks return {\"command\":\"GEMINI\"}. /no_think"
    private val format = ResponseFormat.json("""{"type":"object","properties":{"command":{"type":"string"}},"required":["command"],"additionalProperties":false}""")
    private val fixtures = listOf(
        "פתח וואטסאפ" to "open וואטסאפ", "תפתח ספוטיפיי" to "open ספוטיפיי",
        "תחזור אחורה" to "go back", "הגבר ווליום" to "volume up",
        "הנמך ווליום" to "volume down", "open Camera" to "open Camera",
        "go back" to "go back", "turn volume down" to "volume down",
        "תשלח לאמא בוואטסאפ לילה טוב" to "send אמא on WhatsApp לילה טוב",
        "Tell Yossi on WhatsApp I am coming" to "send Yossi on WhatsApp I am coming",
        "אל תפתח וואטסאפ" to "GEMINI", "Do not send a message" to "GEMINI",
        "Explain quantum mechanics" to "GEMINI", "מזג האוויר מחר" to "GEMINI",
        "פתח WhatsApp ואז תמחק שיחה" to "GEMINI",
    )
    fun install() { LocalIntelligence.interpreter = { interpret(it) } }
    fun unload() { scope.launch { mutex.withLock { closeLocked() } } }
    private fun closeLocked() { idle?.cancel(); idle = null; engine?.let { runCatching { it.close() } }; engine = null; reliability = 0.0 }
    private fun scheduleUnload() {
        idle?.cancel()
        idle = scope.launch { delay(60_000); mutex.withLock { closeLocked() } }
    }
    suspend fun benchmark(context: Context, artifact: ModelArtifact): LocalBenchmark = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
            val memory = ActivityManager.MemoryInfo().also {
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
            }
            check(!memory.lowMemory && memory.availMem > maxOf(3L * 1024 * 1024 * 1024, artifact.bytes * 5)) { "LOW_MEMORY" }
            val file = File(context.noBackupFilesDir, "local-models/${artifact.id}.litertlm")
            check(artifact.verify(file)) { "INTEGRITY" }
            val start = SystemClock.elapsedRealtime()
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
            ExperimentalFlags.enableBenchmark = true
            val candidate = Engine(EngineConfig(file.path, Backend.CPU(threadCount = 2), maxNumTokens = 1024,
                cacheDir = File(context.cacheDir, "local-ai").apply { mkdirs() }.path))
            try {
                candidate.initialize()
                engine = candidate
                val loadMs = SystemClock.elapsedRealtime() - start
                var correct = 0
                var totalMs = 0L
                var firstMs = 0L
                var peakPss = 0L
                var decodeSpeed = 0.0
                for ((input, expected) in fixtures) {
                    currentCoroutineContext().ensureActive()
                    val result = withTimeoutOrNull(8_000) { infer(candidate, input) }
                    if (result?.command?.equals(expected, true) == true) correct++
                    totalMs += result?.elapsedMs ?: 8_000
                    firstMs += result?.firstMs ?: 8_000
                    peakPss = maxOf(peakPss, Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss.toLong())
                    decodeSpeed += result?.decodeSpeed ?: 0.0
                }
                reliability = correct.toDouble() / fixtures.size
                val result = LocalBenchmark(loadMs, totalMs / fixtures.size, firstMs / fixtures.size,
                    Debug.getNativeHeapAllocatedSize() / (1024 * 1024), correct, fixtures.size, peakPss / 1024, decodeSpeed / fixtures.size)
                if (reliability < 0.98 || result.warmMs > 1500) closeLocked() else scheduleUnload()
                result
            } catch (error: Throwable) {
                if (candidate.isInitialized()) runCatching { candidate.close() }
                engine = null; reliability = 0.0
                throw error
            }
        }
    }
    private suspend fun interpret(input: String): LocalInterpretation? {
        if (!mutex.tryLock()) return null // Never queue behind a load/benchmark.
        return try {
            val active = engine ?: return null
            if (reliability < 0.98) return null
            val output = withTimeoutOrNull(1500) { infer(active, input) }?.command ?: return null
            scheduleUnload()
            if (output == "GEMINI") null else LocalInterpretation(output, reliability)
        } finally { mutex.unlock() }
    }
    private data class InferenceOutput(val command: String, val elapsedMs: Long, val firstMs: Long, val decodeSpeed: Double)
    private suspend fun infer(active: Engine, input: String): InferenceOutput {
        val conversation = active.createConversation(ConversationConfig(systemInstruction = Contents.of(instruction),
            samplerConfig = SamplerConfig(1, 1.0, 0.0), automaticToolCalling = false,
            maxOutputToken = 160, thinkingConfig = ThinkingConfig(false, 0), enableResponseFormat = true))
        val start = SystemClock.elapsedRealtime()
        var first = -1L
        val text = StringBuilder()
        try {
            conversation.sendMessageAsync(input, responseFormat = format).collect { message ->
                if (first < 0 && message.toString().isNotEmpty()) first = SystemClock.elapsedRealtime() - start
                text.append(message.toString())
                check(text.length < 2048)
            }
            val command = Json.parseToJsonElement(text.toString()).jsonObject["command"]?.jsonPrimitive?.content
                ?: "GEMINI"
            val speed = runCatching { conversation.getBenchmarkInfo().lastDecodeTokensPerSecond }.getOrDefault(0.0)
            return InferenceOutput(command, SystemClock.elapsedRealtime() - start, first.coerceAtLeast(0), speed)
        } finally { runCatching { conversation.cancelProcess() }; conversation.close() }
    }
}
