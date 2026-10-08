package com.niki914.zafiro.chat.routing

// Real spoken Hebrew templates preserve recipient/content; ambiguous multi-recipient input must escalate.
import org.junit.Assert.*
import org.junit.Test

class LocalMessagePlanTest {
    @Test fun spokenHebrewComplaintRoutesToOneLocalPlan() {
        assertEquals(LocalMessagePlan("אמא", "לילה טוב"),
            LocalMessagePlan.parse("שלח הודעת ואטסאפ לאמא שלי עם המילים לילה טוב"))
        assertEquals(LocalMessagePlan("יוסי", "אני בדרך"),
            LocalMessagePlan.parse("תשלח ליוסי בwhatsapp אני בדרך"))
        assertEquals(LocalMessagePlan("Mom", "good night"),
            LocalMessagePlan.parse("Send Mom on WhatsApp good night"))
    }
    @Test fun unsupportedOrAmbiguousRequestsDoNotPerformPartialPlan() {
        assertNull(LocalMessagePlan.parse("שלח לאמא ולאבא בוואטסאפ לילה טוב"))
        assertNull(LocalMessagePlan.parse("delete my messages"))
        assertNull(LocalMessagePlan.parse("שלח לאמא בוואטסאפ"))
        assertNull(LocalMessagePlan.parse("שלח לאמא ולאבא לילה טוב"))
    }
    @Test fun familyShortcutDoesNotConsumeTheExplicitApplicationAsMessageText() {
        assertEquals(LocalMessagePlan("אמא", "לילה טוב"), LocalMessagePlan.parse("שלח לאמא לילה טוב"))
        assertEquals(LocalMessagePlan("אמא", "לילה טוב"), LocalMessagePlan.parse("תשלח לאמא בוואטסאפ לילה טוב"))
    }
}
