package com.niki914.zafiro.chat.routing

// Sparse bilingual routing must skip command inference for obvious research/Skills/MCP domains.
import org.junit.Assert.*
import org.junit.Test

class TinyIntentRouterTest {
    @Test fun hebrewEnglishAndMixedDomainSamples() {
        assertEquals(TinyIntentRouter.Domain.Android, TinyIntentRouter.classify("תפתח whatsapp").domain)
        assertEquals(TinyIntentRouter.Domain.Mcp, TinyIntentRouter.classify("search connected mcp server").domain)
        assertEquals(TinyIntentRouter.Domain.Web, TinyIntentRouter.classify("מזג אוויר חדשות").domain)
        assertEquals(TinyIntentRouter.Domain.Skill, TinyIntentRouter.classify("install skill").domain)
    }
    @Test fun emptyInputHasNoConfidenceToAuthorizeAnyRoute() {
        val decision = TinyIntentRouter.classify("")
        assertEquals(0.0, decision.similarity, 0.0)
        assertEquals(0.0, decision.margin, 0.0)
    }
}
