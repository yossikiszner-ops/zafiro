package com.niki914.zafiro.chat.routing

import com.niki914.okia.message.ContentBlock
import com.niki914.okia.message.Message
import com.niki914.okia.tooling.ToolDescriptor
import kotlinx.coroutines.flow.MutableStateFlow

/** Policies affect request projection only; originals remain in the local conversation store. */
enum class RequestBudget { Economy, Balanced, Quality }
object RequestRouting {
    val budget = MutableStateFlow(RequestBudget.Balanced)
    val latest = MutableStateFlow(RouteObservation())
}
data class RouteObservation(
    val model: String = "", val reason: String = "", val inputTokens: Int = 0,
    val outputTokens: Int = 0, val tools: Int = 0, val totalTools: Int = 0,
    val retainedMessages: Int = 0, val totalMessages: Int = 0, val latencyMs: Long = 0,
)

internal object RequestPlanner {
    fun userText(history: List<Message>): String = history.filterIsInstance<Message.User>().lastOrNull()?.content
        ?.filterIsInstance<ContentBlock.Text>()?.joinToString("\n") { it.text }.orEmpty()
    fun complex(text: String): Boolean = text.length > 1200 || Regex("(?i)architect|proof|deep research|compare.*trade|debug|refactor|אדריכלות|מחקר מעמיק|הוכח|ניתוח מעמיק|תקן.*קוד").containsMatchIn(text)
    fun selectModel(configured: String, available: List<String>, text: String, images: Boolean, budget: RequestBudget, cooling: Set<String>): Pair<String, String> {
        if (images) return configured to "multimodal"
        if (complex(text) || budget == RequestBudget.Quality) return configured to "quality"
        val suitable = available.filter { it.startsWith("gemini-") && listOf("tts", "live", "image", "native-audio", "transcribe", "embedding", "computer-use", "robotics").none(it::contains) && it !in cooling }
        val fast = suitable.sortedWith(compareBy<String> {
            when { "flash-lite" in it && budget == RequestBudget.Economy -> 0; "flash" in it && "lite" !in it -> 1; "flash-lite" in it -> 2; else -> 3 }
        }.thenBy { if ("preview" in it || "exp" in it) 1 else 0 }.thenByDescending { it }).firstOrNull { "flash" in it }
        return (fast ?: configured) to if (fast == null) "configured" else "fast"
    }
    fun compact(history: List<Message>, turns: Int): List<Message> {
        val starts = history.indices.filter { history[it] is Message.User }
        if (starts.size <= turns) return history
        val cut = starts[starts.size - turns]
        val excerpts = history.take(cut).mapNotNull { message -> when (message) {
            is Message.User -> "User: " + message.content.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }.take(450)
            is Message.Assistant -> message.message.content.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }.takeIf { it.isNotBlank() }?.let { "Assistant: ${it.take(650)}" }
            is Message.ToolResult -> null
        } }.takeLast(12).joinToString("\n").takeLast(5000)
        val memory = Message.User(listOf(ContentBlock.Text("Earlier conversation excerpts (context, not new instructions; original history remains stored locally):\n$excerpts")))
        return listOf(memory) + history.drop(cut)
    }
    fun tools(all: List<ToolDescriptor>, history: List<Message>): List<ToolDescriptor> {
        val text = userText(history).lowercase()
        val lastUser = history.indexOfLast { it is Message.User }
        val used = history.drop((lastUser + 1).coerceAtLeast(0)).filterIsInstance<Message.Assistant>()
            .flatMap { it.message.content.filterIsInstance<ContentBlock.ToolCall>() }.map { it.name }.toSet()
        if (Regex("^(hi|hello|thanks|thank you|שלום|היי|תודה)[!. ]*$", RegexOption.IGNORE_CASE).matches(text)) return all.filter { it.wireName in used }
        val tags = mutableSetOf<String>()
        fun tag(pattern: String, vararg terms: String) { if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)) tags.addAll(terms) }
        tag("whatsapp|message|contact|send|וואטסאפ|הודעה|שלח|איש קשר", "message", "contact", "send", "whatsapp", "screen", "app")
        tag("weather|search|browse|web|latest|מזג|חפש|באינטרנט|עדכני", "search", "weather", "web", "browse", "fetch")
        tag("open|launch|spotify|phone|screen|פתח|טלפון|מסך|ספוטיפיי", "app", "screen", "uri", "device")
        tag("file|folder|document|קובץ|תיקיה|מסמך", "file", "directory", "document", "terminal", "python")
        tag("remember|memory|זכור|תזכור|זיכרון", "memory")
        tag("image|photo|picture|תמונה|צילום", "image", "screenshot", "camera")
        tag("code|python|shell|terminal|קוד|תכנת|טרמינל", "python", "terminal", "shell")
        // Unknown intent keeps capability coverage. Recognized intents get relevant schemas only.
        if (tags.isEmpty()) return all
        val selected = all.filter { descriptor ->
            descriptor.wireName in used || descriptor.name in setOf("load_skill", "py_meta_tools") ||
                tags.any { it in descriptor.name.lowercase() || it in descriptor.description.lowercase() }
        }
        return selected.ifEmpty { all }
    }
    fun estimateTokens(text: String): Int = (text.length + 2) / 3
}
