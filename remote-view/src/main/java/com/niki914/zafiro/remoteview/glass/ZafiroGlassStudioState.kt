package com.niki914.zafiro.remoteview.glass

import com.niki914.zafiro.api.model.AgentState
import com.niki914.zafiro.api.model.TurnOutcome

/**
 * Persistable, renderer-independent Glass configuration.
 * The app settings UI can edit this model without knowing how the overlay is rendered.
 */
data class ZafiroGlassStudioState(
    val appearance: ZafiroGlassAppearance = ZafiroGlassPresets.Signature,
    val presetName: String = "Zafiro Signature",
    val personality: PresencePersonality = PresencePersonality.Alive,
    val visibility: PresenceVisibility = PresenceVisibility.TaskOnly,
    val reaction: PresenceReaction = PresenceReaction.Balanced,
    val depth: PresenceDepth = PresenceDepth.DeepOptical,
    val microActionRichness: MicroActionRichness = MicroActionRichness.Rich,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
)

enum class PresencePersonality { Off, Professional, Alive, Expressive }
enum class PresenceVisibility { Rare, TaskOnly, VoiceAndTask, Always }
enum class PresenceReaction { None, Micro, Balanced, Rich }
enum class PresenceDepth { Surface, Embedded, DeepOptical, BehindGlass }
enum class MicroActionRichness { Off, Minimal, Balanced, Rich, Extreme }

/**
 * A stable bridge from Agent/tool lifecycle events to visual language.
 * Keep this mapping human-facing: implementation names never need to leak into the Glass.
 */
object ZafiroGlassPhaseMapper {
    fun fromAgentState(state: AgentState): ZafiroGlassPhase = when (state) {
        is AgentState.Idle -> when (state.lastOutcome) {
            TurnOutcome.Completed -> ZafiroGlassPhase.Success
            TurnOutcome.Failed -> ZafiroGlassPhase.Error
            TurnOutcome.Interrupted -> ZafiroGlassPhase.Interrupted
            null -> ZafiroGlassPhase.Dormant
        }
        is AgentState.Generating -> if (state.text == null) ZafiroGlassPhase.Understanding else ZafiroGlassPhase.Writing
        is AgentState.Thinking -> ZafiroGlassPhase.Thinking
        is AgentState.ToolRunning -> fromActivity(state.toolName)
        is AgentState.WaitingApproval -> ZafiroGlassPhase.Permission
        AgentState.Stopping -> ZafiroGlassPhase.Interrupted
    }

    fun fromActivity(activity: String?): ZafiroGlassPhase {
        val value = activity.orEmpty().lowercase()
        return when {
            "interrupt" in value || "cancel" in value -> ZafiroGlassPhase.Interrupted
            value.isBlank() -> ZafiroGlassPhase.Dormant
            "listen" in value || "record" in value -> ZafiroGlassPhase.Listening
            "search" in value || "browse" in value || "web" in value -> ZafiroGlassPhase.Searching
            "read" in value || "inspect" in value -> ZafiroGlassPhase.Reading
            "think" in value || "reason" in value || "plan" in value -> ZafiroGlassPhase.Thinking
            "write" in value || "compose" in value || "draft" in value -> ZafiroGlassPhase.Writing
            "send" in value || "upload" in value || "transfer" in value -> ZafiroGlassPhase.Sending
            "speak" in value || "tts" in value -> ZafiroGlassPhase.Speaking
            "permission" in value || "approval" in value -> ZafiroGlassPhase.Permission
            "confirm" in value -> ZafiroGlassPhase.Confirm
            "success" in value || "complete" in value || "done" in value -> ZafiroGlassPhase.Success
            "error" in value || "fail" in value -> ZafiroGlassPhase.Error
            "recover" in value || "retry" in value -> ZafiroGlassPhase.Recover
            "tool" in value || "shell" in value || "python" in value || "mcp" in value -> ZafiroGlassPhase.Tool
            else -> ZafiroGlassPhase.Understanding
        }
    }
}
