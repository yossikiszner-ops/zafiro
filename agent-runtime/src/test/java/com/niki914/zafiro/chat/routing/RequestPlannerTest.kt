package com.niki914.zafiro.chat.routing

import com.niki914.okia.message.*
import com.niki914.okia.tooling.*
import org.junit.Assert.*
import org.junit.Test

class RequestPlannerTest {
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
