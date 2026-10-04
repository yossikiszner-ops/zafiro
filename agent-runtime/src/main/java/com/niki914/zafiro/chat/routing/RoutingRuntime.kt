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
    private val engine = OkHttpEngine()
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
            credential = snapshot.apiKey; discoveryAt = now; cached = emptyList()
            try {
                val response = engine.unary(HttpRequest("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", "GET", mapOf("x-goog-api-key" to snapshot.apiKey), null, HttpTimeouts(5000, 5000, 5000)))
                if (response.statusCode == 200) cached = json.parseToJsonElement(response.body!!.decodeToString()).jsonObject["models"]?.jsonArray
                    ?.mapNotNull { it.jsonObject.takeIf { m -> m["supportedGenerationMethods"]?.jsonArray?.any { v -> v.jsonPrimitive.content == "generateContent" } == true }?.get("name")?.jsonPrimitive?.content?.removePrefix("models/") }.orEmpty()
            } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* Discovery unavailable: preserve the configured model. */ }
        }
        val history = request.history
        val budget = RequestRouting.budget.value
        val text = RequestPlanner.userText(history)
        val images = history.filterIsInstance<Message.User>().lastOrNull()?.content?.any { it is ContentBlock.Image } == true
        val (chosen, reason) = RequestPlanner.selectModel(snapshot.model, if (google) cached else emptyList(), text, images, budget, cooldown.filterValues { it > now }.keys)
        val tools = RequestPlanner.tools(snapshot.tools, history)
        val compact = RequestPlanner.compact(history, if (budget == RequestBudget.Economy) 3 else 5)
        val projected = snapshot.copy(model = chosen, tools = tools,
            maxTokens = minOf(snapshot.maxTokens, when (budget) { RequestBudget.Economy -> 4096; RequestBudget.Balanced -> 16384; RequestBudget.Quality -> snapshot.maxTokens }))
        request.write(projected, compact, "zafiro_request_router")
        RequestRouting.latest.value = RouteObservation(chosen, reason, tools = tools.size, totalTools = snapshot.tools.size, retainedMessages = compact.size, totalMessages = history.size)
    }
    override suspend fun stream(request: HttpRequest): StreamResponse {
        val start = System.currentTimeMillis()
        val model = runCatching { json.parseToJsonElement(request.body.orEmpty()).jsonObject["model"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
        RequestRouting.latest.value = RequestRouting.latest.value.copy(inputTokens = RequestPlanner.estimateTokens(request.body.orEmpty()))
        val response = try { engine.stream(request) } catch (cancel: CancellationException) { throw cancel }
            catch (e: Exception) { if (model.isNotBlank()) cooldown[model] = System.currentTimeMillis() + 30_000; throw e }
        if (response is StreamResponse.Error && response.statusCode in setOf(404, 429, 500, 502, 503, 504)) {
            val seconds = response.headers.entries.firstOrNull { it.key.equals("Retry-After", true) }?.value?.toLongOrNull()?.coerceIn(30, 3600) ?: 60
            if (model.isNotBlank()) cooldown[model] = System.currentTimeMillis() + seconds * 1000
        }
        if (response !is StreamResponse.Ok) return response
        var output = 0
        return response.copy(lines = response.lines.onEach { line ->
            line.data?.let { data ->
                runCatching {
                    val root = json.parseToJsonElement(data).jsonObject
                    root["choices"]?.jsonArray?.forEach { c -> output += c.jsonObject["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.length ?: 0 }
                }
            }
        }.onCompletion { RequestRouting.latest.value = RequestRouting.latest.value.copy(outputTokens = (output + 2) / 3, latencyMs = System.currentTimeMillis() - start) })
    }
    override suspend fun unary(request: HttpRequest): HttpResponse = engine.unary(request)
    override fun close() = Unit
}
