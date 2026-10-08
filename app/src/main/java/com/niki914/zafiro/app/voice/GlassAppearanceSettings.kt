package com.niki914.zafiro.app.voice

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.niki914.zafiro.app.R
import com.niki914.zafiro.remoteview.glass.*
import kotlinx.coroutines.flow.MutableStateFlow

internal object GlassPreferences {
    val appearance = MutableStateFlow(ZafiroGlassPresets.Signature)
    val reducedMotion = MutableStateFlow(false)
    fun load(context: Context) {
        val p = context.getSharedPreferences("zafiro_glass", Context.MODE_PRIVATE)
        appearance.value = GlassAppearanceCodec.decode(p.all)

        reducedMotion.value = p.getBoolean("reduced_motion", false)
    }
    fun save(context: Context, a: ZafiroGlassAppearance, reduced: Boolean) {
        appearance.value = a; reducedMotion.value = reduced
        val editor = context.getSharedPreferences("zafiro_glass", Context.MODE_PRIVATE).edit()
        GlassAppearanceCodec.encode(a).forEach { (key, value) ->
            when (value) {
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
            }
        }
        editor.putBoolean("reduced_motion", reduced).apply()
    }
}

@Composable
internal fun GlassAppearanceSettings(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var a by remember { mutableStateOf(GlassPreferences.appearance.value) }
    var reduced by remember { mutableStateOf(GlassPreferences.reducedMotion.value) }
    var presenceMenu by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.glass_studio)) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ZafiroGlass(ZafiroGlassPhase.Thinking, appearance = a, reducedMotion = reduced)
            Box {
                TextButton(onClick = { presetMenu = true }) { Text(stringResource(R.string.glass_presets)) }
                DropdownMenu(presetMenu, { presetMenu = false }) {
                    listOf(R.string.glass_preset_signature to ZafiroGlassPresets.Signature,
                        R.string.glass_preset_pure to ZafiroGlassPresets.PureGlass,
                        R.string.glass_preset_obsidian to ZafiroGlassPresets.Obsidian,
                        R.string.glass_preset_cinematic to ZafiroGlassPresets.Cinematic,
                        R.string.glass_preset_invisible to ZafiroGlassPresets.Invisible).forEach { (name, preset) ->
                        DropdownMenuItem(text = { Text(stringResource(name)) }, onClick = { a = preset; presetMenu = false })
                    }
                }
            }
            GlassSlider(R.string.glass_darkness, a.darkness) { a = a.copy(darkness = it) }
            GlassSlider(R.string.glass_transparency, a.transparency) { a = a.copy(transparency = it) }
            GlassSlider(R.string.glass_thickness, a.opticalThickness) { a = a.copy(opticalThickness = it) }
            GlassSlider(R.string.glass_reflection, a.reflection) { a = a.copy(reflection = it) }
            GlassSlider(R.string.glass_edge, a.edgeBrightness) { a = a.copy(edgeBrightness = it) }
            GlassSlider(R.string.glass_curvature, a.curvature) { a = a.copy(curvature = it) }
            Box {
                TextButton(onClick = { presenceMenu = true }) { Text(stringResource(R.string.glass_presence) + ": " + stringResource(presenceLabel(a.presence))) }
                DropdownMenu(presenceMenu, { presenceMenu = false }) {
                    ZafiroPresenceStyle.entries.forEach { style -> DropdownMenuItem(text = { Text(stringResource(presenceLabel(style))) }, onClick = { a = a.copy(presence = style); presenceMenu = false }) }
                }
            }
            GlassSlider(R.string.glass_intensity, a.presenceIntensity) { a = a.copy(presenceIntensity = it) }
            Row { Text(stringResource(R.string.glass_reduced_motion)); Switch(reduced, { reduced = it }) }
        }
    }, confirmButton = { TextButton(onClick = { GlassPreferences.save(context, a, reduced); onDismiss() }) { Text(stringResource(R.string.voice_save)) } })
}
@Composable
private fun GlassSlider(label: Int, value: Float, onChange: (Float) -> Unit) {
    Text(stringResource(label)); Slider(value, onChange, valueRange = 0f..1f)
}

private fun presenceLabel(style: ZafiroPresenceStyle): Int = when (style) {
    ZafiroPresenceStyle.OpticalEyes -> R.string.glass_presence_eyes
    ZafiroPresenceStyle.PrismCore -> R.string.glass_presence_prism
    ZafiroPresenceStyle.PulseCore -> R.string.glass_presence_pulse
    ZafiroPresenceStyle.Constellation -> R.string.glass_presence_constellation
    ZafiroPresenceStyle.LiquidGlyph -> R.string.glass_presence_glyph
    ZafiroPresenceStyle.Aperture -> R.string.glass_presence_aperture
    ZafiroPresenceStyle.LightSlit -> R.string.glass_presence_slit
    ZafiroPresenceStyle.WaveformSoul -> R.string.glass_presence_waveform
    ZafiroPresenceStyle.FireflyField -> R.string.glass_presence_fireflies
    ZafiroPresenceStyle.OrbitalCore -> R.string.glass_presence_orbital
    ZafiroPresenceStyle.InkDrop -> R.string.glass_presence_ink
    ZafiroPresenceStyle.Invisible -> R.string.glass_presence_invisible
}
