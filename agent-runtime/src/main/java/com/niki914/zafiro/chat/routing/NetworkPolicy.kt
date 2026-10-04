package com.niki914.zafiro.chat.routing

import com.niki914.okia.hooks.ToolCallHolder
import com.niki914.okia.message.ToolCallOutcome
import com.niki914.okia.tooling.ToolKind
import com.niki914.zafiro.api.AgentControl
import com.niki914.zafiro.api.model.ApprovalDecision
import com.niki914.zafiro.api.model.ApprovalRequest
import com.niki914.zafiro.service.requireService
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URI

/** Applies to managed AI/MCP transport. Arbitrary local scripts require separate consent. */
object NetworkPolicy {
    val enabled = MutableStateFlow(true)
    val destinations = MutableStateFlow<List<String>>(emptyList())
    private var allowed = emptySet<String>()
    fun configure(urls: List<String>) {
        allowed = urls.mapNotNull(::origin).toSet()
        destinations.value = allowed.sorted()
    }
    internal fun permits(url: String): Boolean = !enabled.value || origin(url)?.let { it in allowed } == true
    private fun origin(url: String): String? = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return null
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "https" || it == "http" } ?: return null
        "$scheme://$host" + if (uri.port != -1 && !(scheme == "https" && uri.port == 443) && !(scheme == "http" && uri.port == 80)) ":${uri.port}" else ""
    }.getOrNull()
    internal suspend fun approveScript(call: ToolCallHolder) {
        if (!enabled.value || call.descriptor.kind !is ToolKind.Local) return
        val safe = setOf("open_uri", "notify", "view_image", "screenshot", "memory", "load_skill", "launch_app", "find_installed_apps", "screen_operation_shell", "screen_operation_accessibility", "py_meta_tools")
        if (call.descriptor.name in safe) return
        val decision = runCatching { requireService<AgentControl>() }.getOrNull()?.decideApproval(
            ApprovalRequest.ToolExecution(call.descriptor.name, call.argumentsJson, "Local script: may access files and network")
        ) ?: ApprovalDecision.Deny
        if (decision != ApprovalDecision.Allow) call.writeOutcome(ToolCallOutcome.Intercepted("Local script permission denied", isError = true), "privacy_lock")
    }
}
