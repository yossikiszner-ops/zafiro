package com.niki914.zafiro.chat.routing

// Protects preplanned execution: exact recipient/composer checks and approval before submission.
import com.niki914.zafiro.chat.agentic.accessibility.ScreenElement
import com.niki914.zafiro.chat.agentic.accessibility.ScreenState
import org.junit.Assert.*
import org.junit.Test

class LocalTaskPlanTest {
    private val plan = LocalTaskPlan.message(LocalMessagePlan("אמא", "לילה טוב"))
    private fun conversation(recipient: String, draft: String, pkg: String = "com.whatsapp") = ScreenState(pkg, 1, 1,
        listOf(ScreenElement("0", pkg + ":id/conversation_contact_name", "TextView", recipient, "", false, false, true, false, emptyList()),
            ScreenElement("1", pkg + ":id/entry", "EditText", draft, "", true, true, true, true, emptyList())))
    @Test fun completePlanHasOneSubmissionAfterExplicitApproval() {
        assertEquals(listOf(LocalTaskPlan.Phase.Launch, LocalTaskPlan.Phase.Search,
            LocalTaskPlan.Phase.RecipientQuery, LocalTaskPlan.Phase.Conversation, LocalTaskPlan.Phase.Compose,
            LocalTaskPlan.Phase.Approval, LocalTaskPlan.Phase.Send), plan.steps.map { it.phase })
        assertEquals(LocalTaskPlan.Recovery.NeverRepeatSubmission, plan.steps.last().recovery)
        assertEquals(1, plan.steps.count { it.phase == LocalTaskPlan.Phase.Send })
        assertFalse(plan.matches(plan.steps[5], conversation("אמא", "לילה טוב")))
    }
    @Test fun wrongConversationOrExistingDraftCannotAdvanceToComposition() {
        val open = plan.steps[3]
        assertTrue(plan.matches(open, conversation("אמא", "")))
        assertFalse(plan.matches(open, conversation("יוסי", "")))
        assertFalse(plan.matches(open, conversation("אמא", "existing draft")))
        assertFalse(plan.matches(open, conversation("אמא", "", "com.fake.whatsapp")))
        val duplicate = conversation("אמא", "").let { it.copy(elements = it.elements + it.elements.first()) }
        assertFalse(plan.matches(open, duplicate))
    }
    @Test fun userEditsOrConversationChangesBlockMutationAtExecutionTime() {
        val compose = plan.steps[4]
        assertTrue(plan.canExecute(compose, conversation("אמא", "")))
        assertFalse(plan.canExecute(compose, conversation("אמא", "user draft")))
        assertFalse(plan.canExecute(compose, conversation("יוסי", "")))
        val send = plan.steps.last()
        fun withSend(screen: ScreenState) = screen.copy(elements = screen.elements +
            ScreenElement("2", "com.whatsapp:id/send", "Button", "", "", true, false, true, false, emptyList()))
        assertTrue(plan.canExecute(send, withSend(conversation("אמא", "לילה טוב"))))
        assertFalse(plan.canExecute(send, withSend(conversation("אמא", "changed by user"))))
        assertFalse(plan.canExecute(send, withSend(conversation("יוסי", "לילה טוב"))))
    }
    @Test fun compositionAndSubmissionVerifyExactTextWithoutDeliveryClaims() {
        assertTrue(plan.matches(plan.steps[4], conversation("אמא", "לילה טוב")))
        assertFalse(plan.matches(plan.steps[4], conversation("אמא", "לילה")))
        assertFalse(plan.matches(plan.steps.last(), conversation("אמא", "לילה טוב")))
        assertTrue(plan.matches(plan.steps.last(), conversation("אמא", "")))
    }
}
