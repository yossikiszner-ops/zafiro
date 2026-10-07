package com.niki914.zafiro.chat.routing

import com.niki914.zafiro.chat.agentic.accessibility.ScreenState
import com.niki914.zafiro.chat.agentic.accessibility.SemanticTarget

/** Planning data only. Execution remains in the existing OKIA turn and Accessibility authority. */
internal data class LocalTaskPlan(val packageName: String, val steps: List<Step>) {
    enum class Action { Launch, Tap, SetText, Approval }
    enum class Phase { Launch, Search, RecipientQuery, Conversation, Compose, Approval, Send }
    enum class Recovery { StopBeforeMutation, StopPreservingDraft, NeverRepeatSubmission }
    data class Step(val phase: Phase, val action: Action, val target: SemanticTarget? = null,
        val text: String? = null, val expected: Expected, val recovery: Recovery,
        val timeoutMs: Long = 3_000, val preconditions: List<SemanticTarget> = emptyList())
    sealed interface Expected {
        data object Foreground : Expected
        data class Target(val target: SemanticTarget) : Expected
        data class Conversation(val recipient: String, val composerText: String) : Expected
        data object Approval : Expected
    }
    fun canExecute(step: Step, screen: ScreenState): Boolean = screen.packageName == packageName &&
        (step.target == null || screen.resolve(step.target) != null) && step.preconditions.all { screen.resolve(it) != null }
    fun matches(step: Step, screen: ScreenState): Boolean {
        if (screen.packageName != packageName) return false
        return when (val expected = step.expected) {
            Expected.Foreground -> true
            Expected.Approval -> false // Approval comes only from AgentControl.
            is Expected.Target -> screen.resolve(expected.target) != null
            is Expected.Conversation -> screen.resolve(SemanticTarget(
                resourceId = packageName + ":id/entry", editable = true))?.text == expected.composerText &&
                screen.elements.singleOrNull { it.resourceId == packageName + ":id/conversation_contact_name" }
                    ?.text?.trim()?.equals(expected.recipient, ignoreCase = true) == true
        }
    }
    companion object {
        fun message(message: LocalMessagePlan): LocalTaskPlan {
            val pkg = "com.whatsapp"
            val search = SemanticTarget(labels = setOf("Search", "חיפוש"), clickable = true)
            val searchField = SemanticTarget(editable = true)
            val recipient = SemanticTarget(labels = setOf(message.recipient))
            val composer = SemanticTarget(resourceId = pkg + ":id/entry", editable = true, exactText = "")
            val title = SemanticTarget(resourceId = pkg + ":id/conversation_contact_name", labels = setOf(message.recipient))
            return LocalTaskPlan(pkg, listOf(
                Step(Phase.Launch, Action.Launch, expected = Expected.Foreground, recovery = Recovery.StopBeforeMutation),
                Step(Phase.Search, Action.Tap, search, expected = Expected.Target(searchField), recovery = Recovery.StopBeforeMutation),
                Step(Phase.RecipientQuery, Action.SetText, searchField, message.recipient,
                    Expected.Target(recipient), Recovery.StopBeforeMutation),
                Step(Phase.Conversation, Action.Tap, recipient, expected = Expected.Conversation(message.recipient, ""),
                    recovery = Recovery.StopPreservingDraft),
                Step(Phase.Compose, Action.SetText, composer, message.content,
                    Expected.Conversation(message.recipient, message.content), Recovery.StopPreservingDraft, preconditions = listOf(title)),
                Step(Phase.Approval, Action.Approval, expected = Expected.Approval, recovery = Recovery.StopPreservingDraft),
                Step(Phase.Send, Action.Tap, SemanticTarget(resourceId = pkg + ":id/send", clickable = true),
                    expected = Expected.Conversation(message.recipient, ""), recovery = Recovery.NeverRepeatSubmission,
                    preconditions = listOf(title, composer.copy(exactText = message.content))),
            ))
        }
    }
}
