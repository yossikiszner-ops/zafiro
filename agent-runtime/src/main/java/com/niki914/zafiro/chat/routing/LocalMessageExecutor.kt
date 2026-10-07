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
                val result = try { operation() }
                catch (_: PlanStopped) { BuiltinToolResult.failure("LOCAL_VERIFICATION_FAILED", "Expected screen did not appear") }
                if (!result.ok) {
                    emit(TurnEvent.ToolFailed(0, call, ToolCallOutcome.Failure(result.toJsonString()), partial))
                    throw PlanStopped()
                }
                emit(TurnEvent.ToolSucceeded(0, call, ToolCallOutcome.Success(result.toJsonString()), partial))
            }
            var previousScreen = ScreenBrain.state.value
            suspend fun awaitScreen(predicate: (ScreenState) -> Boolean): ScreenState =
                withTimeoutOrNull(3_000) { ScreenBrain.state.first { it.packageName == pkg && predicate(it) } }
                    ?.also { current ->
                        val before = ScreenBrain.key(previousScreen)
                        val after = ScreenBrain.key(current)
                        if (before != null && after != null) ScreenBrain.graph.verifiedTransition(before, after)
                        previousScreen = current
                    } ?: throw PlanStopped()
            val task = LocalTaskPlan.message(plan)
            var sent = false
            var cancelled = false
            try {
                for (instruction in task.steps) {
                    if (instruction.action == LocalTaskPlan.Action.Approval) {
                        val decision = requireService<AgentControl>().decideApproval(ApprovalRequest.ToolExecution(
                            context.getString(R.string.local_send_message), "WhatsApp\n" + plan.recipient + "\n" + plan.content,
                            context.getString(R.string.local_send_confirmation)))
                        if (decision != ApprovalDecision.Allow) { cancelled = true; throw PlanStopped() }
                        awaitScreen { task.matches(task.steps.first { step -> step.phase == LocalTaskPlan.Phase.Compose }, it) }
                        continue
                    }
                    val label = when (instruction.phase) {
                        LocalTaskPlan.Phase.Launch -> R.string.local_opening_app
                        LocalTaskPlan.Phase.Search, LocalTaskPlan.Phase.RecipientQuery -> R.string.local_finding_contact
                        LocalTaskPlan.Phase.Conversation -> R.string.local_opening_conversation
                        LocalTaskPlan.Phase.Compose -> R.string.local_writing_message
                        LocalTaskPlan.Phase.Send -> R.string.local_send_message
                        LocalTaskPlan.Phase.Approval -> error("Approval is handled separately")
                    }
                    run(label) {
                        val result = when (instruction.action) {
                            LocalTaskPlan.Action.Launch -> launch.tool.invoke(BuiltinToolRequest("launch_app", "{\"app_name\":\"WhatsApp\"}"))
                            LocalTaskPlan.Action.Tap -> AccessibilityController.executeSemanticTarget(
                                instruction.target!!, NodeAction.CLICK, expectedPackage = task.packageName)
                            LocalTaskPlan.Action.SetText -> AccessibilityController.executeSemanticTarget(
                                instruction.target!!, NodeAction.SET_TEXT, instruction.text, expectedPackage = task.packageName)
                            LocalTaskPlan.Action.Approval -> error("Approval is handled separately")
                        }
                        if (result.ok) awaitScreen { task.matches(instruction, it) }
                        result
                    }
                    if (instruction.phase == LocalTaskPlan.Phase.Send) sent = true
                }
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
