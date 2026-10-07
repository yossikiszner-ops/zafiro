package com.niki914.zafiro.app.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class VoiceModelFallbackTest {
    @Test fun unavailableModelFallsBackAndIsNotRepeated() = runTest {
        val route = VoiceModelFallback { 1000L }
        val calls = mutableListOf<String>()
        suspend fun invoke(model: String): String {
            calls += model
            if (model == "blocked") throw VoiceFailure(VoiceProblem.Quota, 429, 86400000)
            return "audio"
        }
        assertEquals("audio", route.execute(listOf("blocked", "available"), ::invoke))
        assertEquals("audio", route.execute(listOf("blocked", "available"), ::invoke))
        assertEquals(listOf("blocked", "available", "available"), calls)
    }
    @Test fun refusalsAreBoundedAndAuthenticationDoesNotSwitchModels() = runTest {
        var calls = 0
        try { VoiceModelFallback().execute((1..10).map(Int::toString)) { calls++; throw VoiceFailure(VoiceProblem.Quota, 429) }; fail() }
        catch (_: VoiceFailure) { assertEquals(3, calls) }
        calls = 0
        try { VoiceModelFallback().execute(listOf("one", "two")) { calls++; throw VoiceFailure(VoiceProblem.Configuration, 403) }; fail() }
        catch (_: VoiceFailure) { assertEquals(1, calls) }
    }
    @Test fun cancellationNeverDispatchesFallback() = runTest {
        var calls = 0
        try { VoiceModelFallback().execute(listOf("one", "two")) { calls++; throw CancellationException() }; fail() }
        catch (_: CancellationException) { assertEquals(1, calls) }
    }
}
