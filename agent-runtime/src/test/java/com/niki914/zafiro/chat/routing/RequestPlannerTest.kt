package com.niki914.zafiro.chat.routing

import com.niki914.okia.message.*
import com.niki914.okia.tooling.*
import org.junit.Assert.*
import org.junit.Test

class RequestPlannerTest {
    // Protects the reported Omni quota/no-text failure: video models must not be selected for chat.
    @Test fun videoModelCannotWinFastRoutingOrRemainConfiguredForText() {
        val ids = listOf("gemini-omni-1.1-flash", "gemini-3.8-flash", "gemini-3.8-flash-tts")
        assertEquals("gemini-3.8-flash", RequestPlanner.selectModel("gemini-3.8-flash", ids, "hello", false, RequestBudget.Balanced, emptySet()).first)
        assertEquals("gemini-3.8-flash", RequestPlanner.selectModel("gemini-omni-1.1-flash", ids, "deep research", false, RequestBudget.Quality, emptySet()).first)
        assertEquals("unsupported_model", RequestPlanner.selectModel("gemini-omni-1.1-flash", listOf(ids.first()), "hello", false, RequestBudget.Balanced, emptySet()).second)
    }

    // Protects normal chat latency without reducing reasoning for complex tasks or Quality mode.
    @Test fun ordinaryFlashChatAvoidsHighReasoningButComplexTasksKeepIt() {
        assertEquals(ThinkingLevel.LOW, RequestPlanner.thinkingLevel("gemini-3.8-flash", "שלום", false, RequestBudget.Balanced, ThinkingLevel.HIGH))
        assertEquals(ThinkingLevel.HIGH, RequestPlanner.thinkingLevel("gemini-3.8-flash", "deep research", false, RequestBudget.Balanced, ThinkingLevel.HIGH))
        assertEquals(ThinkingLevel.HIGH, RequestPlanner.thinkingLevel("gemini-3.8-flash", "hello", false, RequestBudget.Quality, ThinkingLevel.HIGH))
    }
    @Test fun flash25AlsoUsesLowReasoningForSimpleActions() {
        assertEquals(ThinkingLevel.LOW, RequestPlanner.thinkingLevel("gemini-2.5-flash", "open WhatsApp", false, RequestBudget.Balanced, ThinkingLevel.HIGH))
    }
    @Test fun fastRoutePreservesDisabledAndProviderDefaultReasoning() {
        assertEquals(ThinkingLevel.OFF, RequestPlanner.thinkingLevel("gemini-3.8-flash", "hello", false, RequestBudget.Economy, ThinkingLevel.OFF))
        assertNull(RequestPlanner.thinkingLevel("gemini-3.8-flash", "hello", false, RequestBudget.Balanced, null))
        assertEquals(ThinkingLevel.HIGH, RequestPlanner.thinkingLevel("another-provider", "hello", false, RequestBudget.Balanced, ThinkingLevel.HIGH))
    }

    private fun user(s: String) = Message.User(listOf(ContentBlock.Text(s)))
    private fun tool(s: String) = ToolDescriptor(s, s, kind = ToolKind.Local)
    @Test fun routingUsesOnlyAvailableSuitableModels() {
        val ids = listOf("gemini-test-flash", "gemini-test-flash-lite", "gemini-test-flash-tts")
        assertEquals("gemini-test-flash-lite", RequestPlanner.selectModel("configured", ids, "hello", false, RequestBudget.Economy, emptySet()).first)
        assertEquals("gemini-test-flash", RequestPlanner.selectModel("configured", ids, "hello", false, RequestBudget.Balanced, emptySet()).first)
        assertEquals("configured", RequestPlanner.selectModel("configured", ids, "deep research", false, RequestBudget.Balanced, emptySet()).first)
        assertEquals("configured", RequestPlanner.selectModel("configured", ids, "hello", true, RequestBudget.Economy, emptySet()).first)
    }
    @Test fun cooledModelIsNotReusedForFastRoute() {
        assertEquals("configured", RequestPlanner.selectModel("configured", listOf("gemini-test-flash"), "hello", false, RequestBudget.Economy, setOf("gemini-test-flash")).first)
    }
    @Test fun qualityFallsBackOnlyToAvailableNonCoolingModel() {
        val route = RequestPlanner.selectModel("gemini-test-pro", listOf("gemini-test-pro", "gemini-next-pro", "gemini-test-flash-tts"), "deep research", false, RequestBudget.Quality, setOf("gemini-test-pro"))
        assertEquals("gemini-next-pro", route.first)
        assertEquals("fallback_after_failure", route.second)
    }
    @Test fun memoryProjectionPreservesRecentToolExchange() {
        val call = Message.Assistant(AssistantMessage(listOf(ContentBlock.ToolCall("call", "launch_app", "{}"))))
        val result = Message.ToolResult("call", "launch_app", ToolCallOutcome.Success("done"))
        val history = (1..9).map { user("old $it") } + user("open app") + call + result
        val compact = RequestPlanner.compact(history, 3)
        assertEquals(listOf(call, result), compact.takeLast(2))
        assertTrue(compact.size < history.size)
        assertTrue(history.size == 12)
    }
    @Test fun hebrewMessagingGetsMessagingToolsAndPreservesCapabilities() {
        val all = listOf(tool("send_message"), tool("contact_lookup"), tool("weather"), tool("load_skill"))
        val selected = RequestPlanner.tools(all, listOf(user("שלח הודעה ליוסי")))
        assertTrue(selected.any { it.name == "send_message" }); assertFalse(selected.any { it.name == "weather" })
        assertTrue(selected.any { it.name == "load_skill" })
    }
    @Test fun greetingUsesNoToolSchemas() { assertTrue(RequestPlanner.tools(listOf(tool("send_message")), listOf(user("שלום"))).isEmpty()) }
}
