package com.niki914.zafiro.chat.routing

import com.niki914.okia.hooks.Hooks
import com.niki914.okia.hooks.SerializationHolder
import com.niki914.okia.message.ContentBlock
import com.niki914.okia.message.Message
import com.niki914.okia.transport.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.*
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/** Shared by the existing runtime's serialization hook and cancellable transport. */
internal object RoutingRuntime : Hooks, HttpEngine {
    private val engine = OkHttpEngine(base = okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build())
    private var cached = emptyList<String>()
    private var credential = ""
    private var discoveryAt = 0L
    private val cooldown = ConcurrentHashMap<String, Long>()
    private val json = Json { ignoreUnknownKeys = true }
    fun updateProxy(url: String) = engine.updateProxy(url)

    override suspend fun beforeSerialization(request: SerializationHolder) {
        val snapshot = request.snapshot
        val now = System.currentTimeMillis()
        val google = runCatching { URI(snapshot.endpoint).host == "generativelanguage.googleapis.com" }.getOrDefault(false)
        if (google && (credential != snapshot.apiKey || now - discoveryAt > 900_000)) {
            if (credential != snapshot.apiKey) cooldown.clear()
            credential = snapshot.apiKey; discoveryAt = now; cached = emptyList()
            try {
                val response = kotlinx.coroutines.withTimeoutOrNull(2_000) { engine.unary(HttpRequest("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", "GET", mapOf("x-goog-api-key" to snapshot.apiKey), null, HttpTimeouts(2000, 2000, 2000))) }
                if (response?.statusCode == 200) cached = json.parseToJsonElement(response.body!!.decodeToString()).jsonObject["models"]?.jsonArray
                    ?.mapNotNull { it.jsonObject.takeIf { m -> m["supportedGenerationMethods"]?.jsonArray?.any { v -> v.jsonPrimitive.content == "generateContent" } == true }?.get("name")?.jsonPrimitive?.content?.removePrefix("models/") }.orEmpty()
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* Discovery unavailable: preserve the configured model. */ }
        }
        val history = request.history
        val budget = RequestRouting.budget.value
        val text = RequestPlanner.userText(history)
        val images = history.filterIsInstance<Message.User>().lastOrNull()?.content?.any { it is ContentBlock.Image } == true
        val (chosen, reason) = RequestPlanner.selectModel(snapshot.model, if (google) cached else emptyList(), text, images, budget, cooldown.filterValues { it > now }.keys)
        if (google && reason == "unsupported_model") throw NoConversationModelAvailableException()
        val tools = RequestPlanner.tools(snapshot.tools, history)
        val compact = RequestPlanner.compact(history, if (budget == RequestBudget.Economy) 3 else 5)
        val projected = snapshot.copy(model = chosen, tools = tools,
            thinkingLevel = if (google) RequestPlanner.thinkingLevel(chosen, text, images, budget, snapshot.thinkingLevel) else snapshot.thinkingLevel,
            maxTokens = minOf(snapshot.maxTokens, when (budget) { RequestBudget.Economy -> 4096; RequestBudget.Balanced -> 16384; RequestBudget.Quality -> snapshot.maxTokens }))
        request.write(projected, compact, "zafiro_request_router")
        RequestRouting.latest.value = RouteObservation(chosen, reason, tools = tools.size, totalTools = snapshot.tools.size, retainedMessages = compact.size, totalMessages = history.size)
    }
    override suspend fun stream(request: HttpRequest): StreamResponse {
        check(NetworkPolicy.permits(request.url)) { "Network destination is not configured or enabled" }
        val start = System.currentTimeMillis()
        val model = runCatching { json.parseToJsonElement(request.body.orEmpty()).jsonObject["model"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
        RequestRouting.latest.value = RequestRouting.latest.value.copy(inputTokens = RequestPlanner.estimateTokens(request.body.orEmpty()))
        var response = try { if ((cooldown[model] ?: 0) > System.currentTimeMillis()) StreamResponse.Error(429, emptyMap(), "Model API quota temporarily unavailable") else engine.stream(request) } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { if (model.isNotBlank()) cooldown[model] = System.currentTimeMillis() + 30_000; throw e }
        val google = runCatching { URI(request.url).host == "generativelanguage.googleapis.com" }.getOrDefault(false)
        var currentModel = model
        val tried = mutableSetOf(model)
        for (attempt in 0..2) {
            val failure = response as? StreamResponse.Error ?: break
            if (failure.statusCode !in setOf(404, 429, 500, 502, 503, 504)) break
            val delay = ProviderBackoff.delayMillis(failure.statusCode, failure.body,
                failure.headers.entries.firstOrNull { it.key.equals("Retry-After", true) }?.value)
            if (currentModel.isNotBlank()) cooldown[currentModel] = maxOf(cooldown[currentModel] ?: 0, System.currentTimeMillis() + delay)
            if (!google || attempt == 2 || model.isBlank()) break
            val alternate = cached.firstOrNull { candidate ->
                candidate !in tried && GeminiModelCapabilities.isConversationModel(candidate) && "flash" in candidate &&
                    (cooldown[candidate] ?: 0) <= System.currentTimeMillis()
            } ?: break
            val body = json.parseToJsonElement(request.body!!).jsonObject
            val rewritten = JsonObject(body + ("model" to JsonPrimitive(alternate)))
            currentModel = alternate; tried += alternate
            RequestRouting.latest.value = RequestRouting.latest.value.copy(model = alternate, reason = "fallback_after_failure")
            response = engine.stream(request.copy(body = rewritten.toString()))
        }
        val success = response as? StreamResponse.Ok ?: return response
        var output = 0
        var measuredInput: Int? = null
        var measuredOutput: Int? = null
        return success.copy(lines = success.lines.onEach { line ->
            line.data?.let { data ->
                runCatching {
                    val root = json.parseToJsonElement(data).jsonObject
                    root["choices"]?.jsonArray?.forEach { c -> output += c.jsonObject["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.length ?: 0 }
                    when (val delta = root["delta"]) {
                        is JsonObject -> output += delta["text"]?.jsonPrimitive?.contentOrNull?.length ?: 0
                        is JsonPrimitive -> if (root["type"]?.jsonPrimitive?.contentOrNull == "response.output_text.delta") output += delta.contentOrNull?.length ?: 0
                        else -> Unit
                    }
                    val usage = root["usage"]?.jsonObject ?: root["message"]?.jsonObject?.get("usage")?.jsonObject ?: root["response"]?.jsonObject?.get("usage")?.jsonObject
                    usage?.let { u ->
                        measuredInput = (u["input_tokens"] ?: u["prompt_tokens"])?.jsonPrimitive?.intOrNull ?: measuredInput
                        measuredOutput = (u["output_tokens"] ?: u["completion_tokens"])?.jsonPrimitive?.intOrNull ?: measuredOutput
                    }
                }
            }
        }.onCompletion { RequestRouting.latest.value = RequestRouting.latest.value.copy(inputTokens = measuredInput ?: RequestRouting.latest.value.inputTokens, outputTokens = measuredOutput ?: (output + 2) / 3, latencyMs = System.currentTimeMillis() - start) })
    }
    override suspend fun beforeToolCall(call: com.niki914.okia.hooks.ToolCallHolder) = NetworkPolicy.approveScript(call)
    override suspend fun unary(request: HttpRequest): HttpResponse {
        check(NetworkPolicy.permits(request.url)) { "Network destination is not configured or enabled" }
        return engine.unary(request)
    }
    override fun close() = Unit
}
