package com.niki914.zafiro.chat.agentic.accessibility

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coalesces accessibility events; no screenshots, polling, cloud calls or action authority. */
object ScreenBrain {
    private val current = MutableStateFlow(ScreenState.Empty)
    val state: StateFlow<ScreenState> = current.asStateFlow()
    val graph = ScreenGraph()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var rootProvider: (() -> AccessibilityNodeInfo?)? = null
    private var pending = false
    private var revision = 0L
    private val update = Runnable {
        pending = false
        val root = runCatching { rootProvider?.invoke() }.getOrNull()
        if (root == null) current.value = ScreenState.Empty
        else runCatching { snapshot(root) }.onSuccess { current.value = it }
    }

    fun connect(provider: () -> AccessibilityNodeInfo?) {
        rootProvider = provider
        onUiEvent()
    }
    fun disconnect() {
        handler.removeCallbacks(update)
        pending = false
        rootProvider = null
        current.value = ScreenState.Empty
        graph.clear()
    }
    fun onUiEvent() {
        if (rootProvider == null || pending) return
        pending = true
        handler.postDelayed(update, 80) // A fixed window, not starvation-prone trailing debounce.
    }
    private fun snapshot(root: AccessibilityNodeInfo): ScreenState {
        val elements = ArrayList<ScreenElement>()
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, path: String, depth: Int) {
            if (depth > 32 || visited++ >= 512) return
            if (node.isVisibleToUser) {
                val bounds = Rect().also(node::getBoundsInScreen)
                elements += ScreenElement(path, node.viewIdResourceName, node.className?.toString().orEmpty(),
                    if (node.isPassword) "" else node.text?.toString().orEmpty().take(512),
                    if (node.isPassword) "" else node.contentDescription?.toString().orEmpty().take(256),
                    node.isClickable, node.isEditable, node.isEnabled, node.isFocused,
                    listOf(bounds.left, bounds.top, bounds.right, bounds.bottom))
            }
            for (i in 0 until node.childCount.coerceAtMost(128)) {
                node.getChild(i)?.let { child ->
                    try { visit(child, "$path/$i", depth + 1) }
                    finally { @Suppress("DEPRECATION") child.recycle() }
                }
            }
        }
        try {
            visit(root, "0", 0)
            return ScreenState(root.packageName?.toString(), root.windowId, ++revision, elements)
        } finally { @Suppress("DEPRECATION") root.recycle() }
    }
}
