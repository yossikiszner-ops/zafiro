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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.File

data class LocalBenchmark(val loadMs: Long, val warmMs: Long, val firstOutputMs: Long,
    val nativeHeapMiB: Long, val correct: Int, val total: Int,
    val processPssMiB: Long, val decodeTokensPerSecond: Double)
enum class LocalRuntimeStatus { NotSelected, Loading, Ready, LowMemory, IntegrityFailed, ValidationFailed, RuntimeFailed, Idle }

/** One verified model at a time. Native work runs on IO; cold loading survives a routing timeout. */
@OptIn(ExperimentalApi::class)
object LocalCommandRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var application: Context? = null
    private var selected: ModelArtifact? = null
    private var reliability = 0.0
    private var inferenceBudgetMs = 8_000L
    private var idle: Job? = null
    private val currentStatus = MutableStateFlow(LocalRuntimeStatus.NotSelected)
    val status = currentStatus.asStateFlow()
    private val activeModel = MutableStateFlow<String?>(null)
    val selectedModel = activeModel.asStateFlow()
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

    fun install(context: Context) {
        application = context.applicationContext
        val prefs = context.getSharedPreferences("local-ai", 0)
        selected = ModelArtifact.candidates.firstOrNull { it.id == prefs.getString("validated-model", null) }
        reliability = prefs.getFloat("validated-reliability", 0f).toDouble()
        inferenceBudgetMs = prefs.getLong("inference-budget-ms", 8_000).coerceIn(2_000, 15_000)
        activeModel.value = selected?.id
        if (selected != null) currentStatus.value = LocalRuntimeStatus.Idle
        LocalIntelligence.interpreter = { interpret(it) }
    }

    fun unload() { scope.launch { mutex.withLock { closeLocked() } } }
    suspend fun unloadAndWait() = withContext(Dispatchers.IO) { mutex.withLock { closeLocked() } }
    suspend fun forget(artifact: ModelArtifact) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (selected?.id != artifact.id) return@withLock
            closeLocked()
            selected = null
            reliability = 0.0
            activeModel.value = null
            currentStatus.value = LocalRuntimeStatus.NotSelected
            application?.getSharedPreferences("local-ai", 0)?.edit()
                ?.remove("validated-model")?.remove("validated-reliability")?.remove("inference-budget-ms")?.apply()
        }
    }

    private fun closeLocked() {
        idle?.cancel()
        idle = null
        engine?.let { runCatching { it.close() } }
        engine = null
        if (currentStatus.value == LocalRuntimeStatus.Ready) currentStatus.value = LocalRuntimeStatus.Idle
    }
    private fun scheduleUnload() {
        idle?.cancel()
        idle = scope.launch { delay(60_000); mutex.withLock { closeLocked() } }
    }
    private fun checkMemory(context: Context, artifact: ModelArtifact) {
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        val minimumMiB = if (artifact.bytes > 512L * 1024 * 1024) 3072L else 1536L
        if (memory.lowMemory || memory.availMem < minimumMiB * 1024 * 1024) {
            currentStatus.value = LocalRuntimeStatus.LowMemory
            error("LOW_MEMORY")
        }
    }
    private fun openEngine(context: Context, artifact: ModelArtifact): Engine {
        checkMemory(context, artifact)
        val file = File(context.noBackupFilesDir, "local-models/${artifact.id}.litertlm")
        if (!artifact.verify(file)) {
            currentStatus.value = LocalRuntimeStatus.IntegrityFailed
            error("INTEGRITY")
        }
        currentStatus.value = LocalRuntimeStatus.Loading
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        ExperimentalFlags.enableBenchmark = true
        val candidate = Engine(EngineConfig(file.path, Backend.CPU(threadCount = 2), maxNumTokens = 1024,
            cacheDir = File(context.cacheDir, "local-ai").apply { mkdirs() }.path))
        try {
            candidate.initialize()
            return candidate
        } catch (failure: Throwable) {
            runCatching { candidate.close() }
            currentStatus.value = LocalRuntimeStatus.RuntimeFailed
            throw failure
        }
    }

    suspend fun benchmark(context: Context, artifact: ModelArtifact): LocalBenchmark = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
            val start = SystemClock.elapsedRealtime()
            val candidate = openEngine(context, artifact)
            engine = candidate
            try {
                val loadMs = SystemClock.elapsedRealtime() - start
                var correct = 0
                var totalMs = 0L
                var firstMs = 0L
                var peakPss = 0L
                var decodeSpeed = 0.0
                var longestMs = 0L
                for ((input, expected) in fixtures) {
                    currentCoroutineContext().ensureActive()
                    val result = withTimeoutOrNull(8_000) { infer(candidate, input) }
                    if (result?.command?.equals(expected, true) == true) correct++
                    totalMs += result?.elapsedMs ?: 8_000
                    longestMs = maxOf(longestMs, result?.elapsedMs ?: 8_000)
                    firstMs += result?.firstMs ?: 8_000
                    peakPss = maxOf(peakPss, Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss.toLong())
                    decodeSpeed += result?.decodeSpeed ?: 0.0
                }
                val measured = correct.toDouble() / fixtures.size
                val result = LocalBenchmark(loadMs, totalMs / fixtures.size, firstMs / fixtures.size,
                    Debug.getNativeHeapAllocatedSize() / (1024 * 1024), correct, fixtures.size,
                    peakPss / 1024, decodeSpeed / fixtures.size)
                if (measured < 0.98) {
                    closeLocked()
                    currentStatus.value = LocalRuntimeStatus.ValidationFailed
                } else {
                    selected = artifact
                    reliability = measured
                    inferenceBudgetMs = (longestMs * 2).coerceIn(2_000, 15_000)
                    activeModel.value = artifact.id
                    context.getSharedPreferences("local-ai", 0).edit()
                        .putString("validated-model", artifact.id)
                        .putFloat("validated-reliability", measured.toFloat())
                        .putLong("inference-budget-ms", inferenceBudgetMs).apply()
                    currentStatus.value = LocalRuntimeStatus.Ready
                    scheduleUnload()
                }
                result
            } catch (failure: Throwable) {
                closeLocked()
                currentStatus.value = LocalRuntimeStatus.RuntimeFailed
                throw failure
            }
        }
    }

    private suspend fun interpret(input: String): LocalInterpretation? = withContext(Dispatchers.IO) {
        // Independent load job: a short cloud-routing deadline does not keep restarting native loading.
        val prepared = scope.async {
            mutex.withLock {
                if (engine != null) return@withLock true
                val context = application ?: return@withLock false
                val artifact = selected ?: return@withLock false
                if (reliability < 0.98) return@withLock false
                try {
                    engine = openEngine(context, artifact)
                    currentStatus.value = LocalRuntimeStatus.Ready
                    scheduleUnload()
                    true
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { false }
                catch (_: LinkageError) { currentStatus.value = LocalRuntimeStatus.RuntimeFailed; false }
            }
        }
        if (!prepared.await()) return@withContext null
        mutex.withLock {
            val active = engine ?: return@withLock null
            if (reliability < 0.98) return@withLock null
            try {
                val output = withTimeoutOrNull(inferenceBudgetMs) { infer(active, input) }?.command
                    ?: return@withLock null
                if (output == "GEMINI") null else LocalInterpretation(output, reliability)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { currentStatus.value = LocalRuntimeStatus.RuntimeFailed; null }
            catch (_: LinkageError) { currentStatus.value = LocalRuntimeStatus.RuntimeFailed; null }
            finally { scheduleUnload() }
        }
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
