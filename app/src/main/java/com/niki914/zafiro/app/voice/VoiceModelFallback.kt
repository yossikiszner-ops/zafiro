package com.niki914.zafiro.app.voice

internal class VoiceModelFallback(private val now: () -> Long = System::currentTimeMillis) {
    val blocked = java.util.concurrent.ConcurrentHashMap<String, Long>()
    suspend fun <T> execute(candidates: List<String>, request: suspend (String) -> T): T {
        var last: VoiceFailure? = null
        for (candidate in candidates.distinct().filter { (blocked[it] ?: 0) <= now() }.take(3)) {
            try { return request(candidate) }
            catch (failure: VoiceFailure) {
                if (failure.status !in setOf(404, 429, 500, 502, 503, 504)) throw failure
                blocked[candidate] = now() + failure.retryMillis
                last = failure
            }
        }
        throw last ?: VoiceFailure(if (candidates.isEmpty()) VoiceProblem.Model else VoiceProblem.Quota)
    }
}
