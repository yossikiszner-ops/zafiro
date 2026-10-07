package com.niki914.zafiro.app.voice

import com.niki914.zafiro.remoteview.glass.ZafiroGlassAppearance
import com.niki914.zafiro.remoteview.glass.ZafiroPresenceStyle

/** All renderer parameters must survive process recreation and preset selection. */
internal object GlassAppearanceCodec {
    fun decode(values: Map<String, *>): ZafiroGlassAppearance {
        val defaults = ZafiroGlassAppearance()
        fun number(key: String, fallback: Float): Float =
            (values[key] as? Number)?.toFloat()?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: fallback
        return ZafiroGlassAppearance(
            darkness = number("darkness", defaults.darkness),
            transparency = number("transparency", defaults.transparency),
            refraction = number("refraction", defaults.refraction),
            distortion = number("distortion", defaults.distortion),
            opticalThickness = number("thickness", defaults.opticalThickness),
            reflection = number("reflection", defaults.reflection),
            edgeBrightness = number("edge", defaults.edgeBrightness),
            liquidAmount = number("liquid", defaults.liquidAmount),
            viscosity = number("viscosity", defaults.viscosity),
            surfaceTension = number("tension", defaults.surfaceTension),
            curvature = number("curvature", defaults.curvature),
            motion = number("motion", defaults.motion),
            presence = ZafiroPresenceStyle.entries.firstOrNull { it.name == values["presence"] } ?: defaults.presence,
            presenceIntensity = number("intensity", defaults.presenceIntensity),
        )
    }

    fun encode(a: ZafiroGlassAppearance): Map<String, Any> = mapOf(
        "darkness" to a.darkness, "transparency" to a.transparency,
        "refraction" to a.refraction, "distortion" to a.distortion,
        "thickness" to a.opticalThickness, "reflection" to a.reflection,
        "edge" to a.edgeBrightness, "liquid" to a.liquidAmount,
        "viscosity" to a.viscosity, "tension" to a.surfaceTension,
        "curvature" to a.curvature, "motion" to a.motion,
        "presence" to a.presence.name, "intensity" to a.presenceIntensity,
    )
}
