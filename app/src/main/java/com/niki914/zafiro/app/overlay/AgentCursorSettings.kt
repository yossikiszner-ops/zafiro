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
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cursor_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(stringResource(R.string.cursor_show), Modifier.weight(1f))
                    Switch(config.enabled, { config = config.copy(enabled = it) })
                }
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
