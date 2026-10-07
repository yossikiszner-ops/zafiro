package com.niki914.zafiro.chat.agentic.accessibility

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.niki914.xposed.api.util.ContextProvider

/** Coalesces accessibility events; no screenshots, polling, cloud calls or action authority. */
object ScreenBrain {
    private val current = MutableStateFlow(ScreenState.Empty)
    val state: StateFlow<ScreenState> = current.asStateFlow()
    val graph = ScreenGraph()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var rootProvider: (() -> AccessibilityNodeInfo?)? = null
    private var pending = false
    private var revision = 0L
    private val versions = LinkedHashMap<String, Pair<Long, Long>>()
    private val update = Runnable {
        pending = false
        val root = runCatching { rootProvider?.invoke() }.getOrNull()
        if (root == null) current.value = ScreenState.Empty
        else runCatching { snapshot(root) }.onSuccess { current.value = it }.onFailure { current.value = ScreenState.Empty }
    }

    fun connect(provider: () -> AccessibilityNodeInfo?) {
        rootProvider = provider
        onUiEvent()
    }
    @Synchronized fun disconnect() {
        handler.removeCallbacks(update)
        pending = false
        rootProvider = null
        current.value = ScreenState.Empty
        graph.clear()
        versions.clear()
    }
    fun onUiEvent() {
        if (rootProvider == null || pending) return
        pending = true
        handler.postDelayed(update, 80) // A fixed window, not starvation-prone trailing debounce.
    }
    @Synchronized fun key(screen: ScreenState): ScreenGraph.Key? {
        val pkg = screen.packageName ?: return null
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = versions[pkg]
        val version = if (cached != null && now - cached.second < 60_000) cached.first else {
            @Suppress("DEPRECATION")
            val info = runCatching { ContextProvider.awaitIfAvailable()?.packageManager?.getPackageInfo(pkg, 0) }.getOrNull()
            val value = if (android.os.Build.VERSION.SDK_INT >= 28) info?.longVersionCode else info?.versionCode?.toLong()
            (value ?: return null).also { versions[pkg] = it to now }
        }
        while (versions.size > 64) versions.remove(versions.keys.first())
        val structure = screen.elements.mapNotNull { node -> node.resourceId?.let { "$it:${node.role}:${node.editable}" } }.distinct().sorted().joinToString("|")
        return ScreenGraph.Key(pkg, version, structure.hashCode().toString())
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
            return ScreenState(root.packageName?.toString(), root.windowId, ++revision, elements).also { screen ->
                key(screen)?.let { graph.remember(it, screen) }
            }
        } finally { @Suppress("DEPRECATION") root.recycle() }
    }
}
