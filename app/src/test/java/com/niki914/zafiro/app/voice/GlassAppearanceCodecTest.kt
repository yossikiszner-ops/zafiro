package com.niki914.zafiro.app.voice

import com.niki914.zafiro.remoteview.glass.*
import org.junit.Assert.*
import org.junit.Test

class GlassAppearanceCodecTest {
    @Test fun everyPresetSurvivesRestart() {
        listOf(ZafiroGlassPresets.Signature, ZafiroGlassPresets.PureGlass,
            ZafiroGlassPresets.Obsidian, ZafiroGlassPresets.Cinematic,
            ZafiroGlassPresets.Invisible).forEach {
            assertEquals(it, GlassAppearanceCodec.decode(GlassAppearanceCodec.encode(it)))
        }
    }
    @Test fun olderSettingsKeepTheirValuesAndDefaultMissingParameters() {
        assertEquals(ZafiroGlassAppearance(darkness = .3f, presence = ZafiroPresenceStyle.Invisible),
            GlassAppearanceCodec.decode(mapOf("darkness" to .3f, "presence" to "Invisible")))
    }
    @Test fun corruptSettingsCannotFeedNonFiniteValuesIntoRendering() {
        val a = GlassAppearanceCodec.decode(mapOf("darkness" to Float.NaN,
            "motion" to Float.POSITIVE_INFINITY, "curvature" to -1f,
            "transparency" to 7f, "presence" to "Unknown", "reflection" to "bad"))
        assertEquals(.82f, a.darkness, 0f)
        assertEquals(.62f, a.motion, 0f)
        assertEquals(0f, a.curvature, 0f)
        assertEquals(1f, a.transparency, 0f)
        assertEquals(ZafiroPresenceStyle.PrismCore, a.presence)
        assertEquals(.58f, a.reflection, 0f)
    }
}
