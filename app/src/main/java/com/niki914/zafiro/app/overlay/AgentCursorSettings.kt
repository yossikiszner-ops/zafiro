package com.niki914.zafiro.app.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.niki914.zafiro.app.R

@Composable
internal fun AgentCursorSettings(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(AgentCursorPreferences.appearance.value) }
    var styleMenu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cursor_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.cursor_show), Modifier.weight(1f))
                    Switch(config.enabled, { config = config.copy(enabled = it) })
                }
                Box {
                    TextButton(onClick = { styleMenu = true }) { Text(stringResource(cursorStyleLabel(config.style))) }
                    DropdownMenu(styleMenu, { styleMenu = false }) {
                        AgentCursorStyle.entries.forEach { style ->
                            DropdownMenuItem(text = { Text(stringResource(cursorStyleLabel(style))) }, onClick = { config = config.copy(style = style); styleMenu = false })
                        }
                    }
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { android.widget.ImageView(it) },
                    update = { it.setImageDrawable(OpticalFocusDrawable().apply { cursorStyle = config.style }) },
                    modifier = Modifier.size(64.dp),
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.cursor_physical_typing), Modifier.weight(1f))
                    Switch(config.physicalTyping, { config = config.copy(physicalTyping = it) })
                }
                Text(stringResource(R.string.cursor_physical_typing_hint))
                Text(stringResource(R.string.cursor_intensity))
                Slider(config.intensity, { config = config.copy(intensity = it) }, valueRange = .1f..1f, enabled = config.enabled)
                Text(stringResource(R.string.cursor_speed))
                Slider(config.speed, { config = config.copy(speed = it) }, valueRange = .5f..2f, enabled = config.enabled)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.cursor_feedback), Modifier.weight(1f))
                    Switch(config.tapFeedback, { config = config.copy(tapFeedback = it) }, enabled = config.enabled)
                }
            }
        },
        confirmButton = { TextButton(onClick = { AgentCursorPreferences.save(context, config); onDismiss() }) { Text(stringResource(R.string.voice_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

private fun cursorStyleLabel(style: AgentCursorStyle): Int = when (style) {
    AgentCursorStyle.Optical -> R.string.cursor_style_optical
    AgentCursorStyle.Arrow -> R.string.cursor_style_arrow
    AgentCursorStyle.Halo -> R.string.cursor_style_halo
    AgentCursorStyle.Crosshair -> R.string.cursor_style_crosshair
    AgentCursorStyle.Diamond -> R.string.cursor_style_diamond
    AgentCursorStyle.Aperture -> R.string.cursor_style_aperture
    AgentCursorStyle.Comet -> R.string.cursor_style_comet
    AgentCursorStyle.Orbit -> R.string.cursor_style_orbit
    AgentCursorStyle.Brackets -> R.string.cursor_style_brackets
    AgentCursorStyle.Dot -> R.string.cursor_style_dot
}
