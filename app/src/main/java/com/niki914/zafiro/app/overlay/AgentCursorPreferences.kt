package com.niki914.zafiro.app.overlay

import android.content.Context
import com.niki914.zafiro.chat.agentic.accessibility.AccessibilityController
import kotlinx.coroutines.flow.MutableStateFlow

internal enum class AgentCursorStyle { Optical, Arrow, Halo, Crosshair, Diamond, Aperture, Comet, Orbit, Brackets, Dot }

internal data class AgentCursorAppearance(
    val style: AgentCursorStyle = AgentCursorStyle.Optical,
    val enabled: Boolean = true,
    val intensity: Float = .7f,
    val speed: Float = 1f,
    val tapFeedback: Boolean = true,
)

internal object AgentCursorPreferences {
    val appearance = MutableStateFlow(AgentCursorAppearance())
    fun load(context: Context) {
        val prefs = context.getSharedPreferences("zafiro_cursor", Context.MODE_PRIVATE)
        appearance.value = AgentCursorAppearance(
            style = AgentCursorStyle.entries.firstOrNull { it.name == prefs.getString("style", "Optical") } ?: AgentCursorStyle.Optical,
            enabled = prefs.getBoolean("enabled", true),
            intensity = prefs.getFloat("intensity", .7f).takeIf { it.isFinite() }?.coerceIn(.1f, 1f) ?: .7f,
            speed = prefs.getFloat("speed", 1f).takeIf { it.isFinite() }?.coerceIn(.5f, 2f) ?: 1f,
            tapFeedback = prefs.getBoolean("feedback", true),
        )
    }
    fun save(context: Context, value: AgentCursorAppearance) {
        appearance.value = value
        context.getSharedPreferences("zafiro_cursor", Context.MODE_PRIVATE).edit()
            .putString("style", value.style.name).putBoolean("enabled", value.enabled).putFloat("intensity", value.intensity)
            .putFloat("speed", value.speed).putBoolean("feedback", value.tapFeedback).apply()
        (AccessibilityController.pointerOverlay as? PointerOverlay)?.refreshAppearance()
    }
}
