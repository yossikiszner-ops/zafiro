package com.niki914.zafiro.chat.routing

import com.niki914.okia.LocalTurnAction
import com.niki914.okia.event.TurnEvent
import com.niki914.okia.message.*
import com.niki914.xposed.api.util.ContextProvider
import com.niki914.zafiro.R
import com.niki914.zafiro.api.AgentControl
import com.niki914.zafiro.api.model.ApprovalDecision
import com.niki914.zafiro.api.model.ApprovalRequest
import com.niki914.zafiro.chat.LocalTool
import com.niki914.zafiro.chat.agentic.accessibility.*
import com.niki914.zafiro.chat.agentic.buildin.BuiltinToolRequest
import com.niki914.zafiro.chat.agentic.buildin.BuiltinToolResult
import com.niki914.zafiro.service.requireService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/** One local turn and existing action authority; sends only after explicit approval and revalidation. */
internal object LocalMessageExecutor {
    fun action(plan: LocalMessagePlan?, tools: List<LocalTool>): LocalTurnAction? {
        plan ?: return null
        val builtins = tools.filterIsInstance<LocalTool.Builtin>()
        val launch = builtins.firstOrNull { it.name == "launch_app" } ?: return null
        if (builtins.none { it.name == "screen_operation_accessibility" }) return null
        return LocalTurnAction { emit ->
            val context = ContextProvider.await().applicationContext
            val pkg = "com.whatsapp"
            var step = 0
            suspend fun run(label: Int, operation: suspend () -> BuiltinToolResult) {
                val call = ContentBlock.ToolCall("local-message-${step++}", context.getString(label), "{}")
                val partial = AssistantMessage(listOf(call))
                emit(TurnEvent.ToolRunning(0, call, partial))
                val result = operation()
                if (!result.ok) {
                    emit(TurnEvent.ToolFailed(0, call, ToolCallOutcome.Failure(result.toJsonString()), partial))
                    throw PlanStopped()
                }
                emit(TurnEvent.ToolSucceeded(0, call, ToolCallOutcome.Success(result.toJsonString()), partial))
            }
            suspend fun awaitScreen(predicate: (ScreenState) -> Boolean): ScreenState =
                withTimeoutOrNull(3_000) { ScreenBrain.state.first { it.packageName == pkg && predicate(it) } }
                    ?: throw PlanStopped()
            fun composer() = SemanticTarget(resourceId = "$pkg:id/entry", editable = true)
            fun searchField() = SemanticTarget(editable = true)
            var sent = false
            var cancelled = false
            try {
                run(R.string.local_opening_app) {
                    launch.tool.invoke(BuiltinToolRequest("launch_app", "{\"app_name\":\"WhatsApp\"}"))
                }
                awaitScreen { true }
                run(R.string.local_finding_contact) {
                    AccessibilityController.executeSemanticTarget(
                        SemanticTarget(labels = setOf("Search", "חיפוש"), clickable = true), NodeAction.CLICK)
                }
                awaitScreen { it.resolve(searchField()) != null }
                run(R.string.local_finding_contact) {
                    AccessibilityController.executeSemanticTarget(searchField(), NodeAction.SET_TEXT, plan.recipient)
                }
                val recipient = SemanticTarget(labels = setOf(plan.recipient))
                awaitScreen { it.resolve(recipient) != null }
                run(R.string.local_opening_conversation) {
                    AccessibilityController.executeSemanticTarget(recipient, NodeAction.CLICK)
                }
                awaitScreen { it.resolve(composer()) != null && it.elements.any { n -> n.text == plan.recipient } }
                run(R.string.local_writing_message) {
                    AccessibilityController.executeSemanticTarget(composer(), NodeAction.SET_TEXT, plan.content)
                }
                awaitScreen { it.resolve(composer())?.text == plan.content }
                val decision = requireService<AgentControl>().decideApproval(ApprovalRequest.ToolExecution(
                    context.getString(R.string.local_send_message), "WhatsApp\n${plan.recipient}\n${plan.content}",
                    context.getString(R.string.local_send_confirmation)))
                if (decision != ApprovalDecision.Allow) { cancelled = true; throw PlanStopped() }
                // Permission UI may change the foreground window. Recheck the real conversation after it closes.
                awaitScreen { it.resolve(composer())?.text == plan.content && it.elements.any { n -> n.text == plan.recipient } }
                run(R.string.local_send_message) {
                    AccessibilityController.executeSemanticTarget(
                        SemanticTarget(resourceId = "$pkg:id/send", clickable = true), NodeAction.CLICK)
                }
                awaitScreen { it.resolve(composer())?.text?.isEmpty() == true }
                sent = true // Composer cleared; delivery/read status is deliberately not asserted.
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: PlanStopped) { /* Stop, never guess/retry a partially composed or submitted message. */ }
            AssistantMessage(listOf(ContentBlock.Text(context.getString(when {
                sent -> R.string.local_message_submitted
                cancelled -> R.string.local_message_cancelled
                else -> R.string.local_message_stopped
            }))), stopReason = StopReason.Stop)
        }
    }
    private class PlanStopped : Exception()
}
