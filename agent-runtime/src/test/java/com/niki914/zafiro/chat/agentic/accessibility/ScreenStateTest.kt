package com.niki914.zafiro.chat.agentic.accessibility

// Regressions: reject duplicate targets; never retain private text or stale app-version paths.
import org.junit.Assert.*
import org.junit.Test

class ScreenStateTest {
    private fun node(id: String? = "com.test:id/search", text: String = "חיפוש") =
        ScreenElement("0/1", id, "Button", text, "", true, false, true, false, listOf(0, 0, 48, 48))
    @Test fun hebrewTargetRequiresUniqueEnabledSemanticMatch() {
        val target = SemanticTarget(labels = setOf("Search", "חיפוש"), clickable = true)
        assertEquals(node(), ScreenState("com.test", 1, 1, listOf(node())).resolve(target))
        assertNull(ScreenState("com.test", 1, 2, listOf(node(), node())).resolve(target))
        assertNull(ScreenState("com.test", 1, 3, listOf(node().copy(enabled = false))).resolve(target))
    }
    @Test fun graphStoresOnlyResourceStructureAndInvalidatesChangedVersion() {
        val graph = ScreenGraph(2)
        val old = ScreenGraph.Key("com.test", 1, "chats")
        graph.remember(old, ScreenState("com.test", 1, 1, listOf(node(text = "Private message"), node(null, "Mom"))))
        assertEquals(listOf(ScreenGraph.Control("com.test:id/search", "Button", false)), graph.recall(old))
        val newer = old.copy(appVersion = 2)
        graph.remember(newer, ScreenState("com.test", 1, 2, listOf(node())))
        assertNull(graph.recall(old))
        assertNotNull(graph.recall(newer))
        graph.clear()
        assertNull(graph.recall(newer))
    }
    @Test fun graphRejectsForeignPackageAndBoundsMemory() {
        val graph = ScreenGraph(1)
        val first = ScreenGraph.Key("com.test", 1, "first")
        graph.remember(first, ScreenState("other", 1, 1, listOf(node())))
        assertNull(graph.recall(first))
        graph.remember(first, ScreenState("com.test", 1, 1, listOf(node())))
        graph.remember(first.copy(screen = "second"), ScreenState("com.test", 1, 2, listOf(node())))
        assertNull(graph.recall(first))
    }
    // Navigation loops must terminate and updates must invalidate previously verified paths.
    @Test fun verifiedPathsAreBoundedAndVersionScoped() {
        val graph = ScreenGraph()
        val chats = ScreenGraph.Key("com.test", 1, "chats")
        val search = chats.copy(screen = "search")
        val conversation = chats.copy(screen = "conversation")
        listOf(chats, search, conversation).forEach { graph.remember(it, ScreenState("com.test", 1, 1, listOf(node()))) }
        assertNull(graph.path(chats, conversation))
        graph.verifiedTransition(chats, search)
        graph.verifiedTransition(search, chats)
        graph.verifiedTransition(search, conversation)
        assertEquals(listOf(chats, search, conversation), graph.path(chats, conversation))
        graph.remember(chats.copy(appVersion = 2), ScreenState("com.test", 1, 2, listOf(node())))
        assertNull(graph.path(chats, conversation))
    }
}
