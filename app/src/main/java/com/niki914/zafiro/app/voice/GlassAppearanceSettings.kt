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
        appearance.value = ZafiroGlassAppearance(
            darkness = p.getFloat("darkness", .82f).coerceIn(0f, 1f),
            transparency = p.getFloat("transparency", .18f).coerceIn(0f, 1f),
            opticalThickness = p.getFloat("thickness", .72f).coerceIn(0f, 1f),
            reflection = p.getFloat("reflection", .58f).coerceIn(0f, 1f),
            edgeBrightness = p.getFloat("edge", .46f).coerceIn(0f, 1f),
            curvature = p.getFloat("curvature", .88f).coerceIn(0f, 1f),
            presence = runCatching { ZafiroPresenceStyle.valueOf(p.getString("presence", "PrismCore") ?: "PrismCore") }.getOrDefault(ZafiroPresenceStyle.PrismCore),
            presenceIntensity = p.getFloat("intensity", .72f).coerceIn(0f, 1f),
        )
        reducedMotion.value = p.getBoolean("reduced_motion", false)
    }
    fun save(context: Context, a: ZafiroGlassAppearance, reduced: Boolean) {
        appearance.value = a; reducedMotion.value = reduced
        context.getSharedPreferences("zafiro_glass", Context.MODE_PRIVATE).edit()
            .putFloat("darkness", a.darkness).putFloat("transparency", a.transparency)
            .putFloat("thickness", a.opticalThickness).putFloat("reflection", a.reflection)
            .putFloat("edge", a.edgeBrightness).putFloat("curvature", a.curvature)
            .putString("presence", a.presence.name).putFloat("intensity", a.presenceIntensity)
            .putBoolean("reduced_motion", reduced).apply()
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
                    listOf("Zafiro Signature" to ZafiroGlassPresets.Signature, "Pure Glass" to ZafiroGlassPresets.PureGlass,
                        "Obsidian" to ZafiroGlassPresets.Obsidian, "Cinematic" to ZafiroGlassPresets.Cinematic,
                        "Invisible Presence" to ZafiroGlassPresets.Invisible).forEach { (name, preset) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { a = preset; presetMenu = false })
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
                TextButton(onClick = { presenceMenu = true }) { Text(stringResource(R.string.glass_presence) + ": " + a.presence.name) }
                DropdownMenu(presenceMenu, { presenceMenu = false }) {
                    ZafiroPresenceStyle.entries.forEach { style -> DropdownMenuItem(text = { Text(style.name) }, onClick = { a = a.copy(presence = style); presenceMenu = false }) }
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
