package com.niki914.zafiro.chat.agentic.accessibility

/** Ephemeral perception. Text stays in RAM and must never enter navigation memory. */
data class ScreenElement(
    val path: String,
    val resourceId: String?,
    val role: String,
    val text: String,
    val description: String,
    val clickable: Boolean,
    val editable: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val bounds: List<Int>,
)

data class ScreenState(
    val packageName: String?,
    val windowId: Int,
    val revision: Long,
    val elements: List<ScreenElement>,
) {
    /** Ambiguous targets are rejected, never picked by coordinate proximity. */
    fun resolve(target: SemanticTarget): ScreenElement? = elements.filter {
        it.enabled && (target.editable == null || it.editable == target.editable) &&
            (target.clickable == null || it.clickable == target.clickable) &&
            (target.resourceId == null || it.resourceId == target.resourceId) &&
            (target.labels.isEmpty() || target.labels.any { label ->
                label.equals(it.text.trim(), true) || label.equals(it.description.trim(), true)
            })
    }.singleOrNull()

    companion object { val Empty = ScreenState(null, -1, 0, emptyList()) }
}

data class SemanticTarget(
    val resourceId: String? = null,
    val labels: Set<String> = emptySet(),
    val editable: Boolean? = null,
    val clickable: Boolean? = null,
)

/** Bounded structural memory: no contact names, messages, screenshots or raw UI text. */
class ScreenGraph(private val capacity: Int = 64) {
    data class Key(val packageName: String, val appVersion: Long, val screen: String)
    data class Control(val resourceId: String, val role: String, val editable: Boolean)
    private val screens = LinkedHashMap<Key, List<Control>>()

    @Synchronized fun remember(key: Key, state: ScreenState) {
        if (state.packageName != key.packageName) return
        screens.keys.removeAll { it.packageName == key.packageName && it.appVersion != key.appVersion }
        screens.remove(key)
        screens[key] = state.elements.mapNotNull { node ->
            node.resourceId?.takeIf { it.startsWith("${key.packageName}:id/") }
                ?.let { Control(it, node.role, node.editable) }
        }.distinct().take(128)
        while (screens.size > capacity.coerceAtLeast(1)) screens.remove(screens.keys.first())
    }

    @Synchronized fun recall(key: Key): List<Control>? = screens[key]
    @Synchronized fun clear() = screens.clear()
}
