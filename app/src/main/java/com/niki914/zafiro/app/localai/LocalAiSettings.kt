package com.niki914.zafiro.app.localai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.niki914.uikit.infra.LiquidDialog
import com.niki914.zafiro.app.R
import com.niki914.zafiro.chat.routing.ModelArtifact
import com.niki914.zafiro.chat.routing.IntelligenceMode
import com.niki914.zafiro.chat.routing.LocalIntelligence

@Composable
fun LocalAiSettings(onDismiss: () -> Unit) {
    val model = rememberLocalAiModel()
    val runtimeStatus by LocalCommandRuntime.status.collectAsState()
    val selectedModel by LocalCommandRuntime.selectedModel.collectAsState()
    val discovering by model.discovering.collectAsState()
    val importStatus by model.importStatus.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.importModel(uri)
    }
    val state by model.state.collectAsState()
    val wifiOnly by model.wifiOnly.collectAsState()
    val mode by LocalIntelligence.mode.collectAsState()
    val diagnostics by LocalIntelligence.diagnostics.collectAsState()
    val allowMessageSending by LocalIntelligence.allowMessageSending.collectAsState()
    val cloudFallback by LocalIntelligence.allowCloudFallback.collectAsState()
    val trustedScripts by com.niki914.zafiro.chat.routing.NetworkPolicy.trustedScripts.collectAsState()
    LiquidDialog(visible = true, onDismissRequest = onDismiss, title = { Text(stringResource(R.string.local_ai_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.local_ai_optional))
                if (discovering) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.local_ai_discovery_scope))
                TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !discovering) {
                    Text(stringResource(R.string.local_ai_import))
                }
                importStatus?.let { Text(stringResource(it)) }
                Text(stringResource(when (runtimeStatus) {
                    LocalRuntimeStatus.NotSelected -> R.string.local_ai_not_selected
                    LocalRuntimeStatus.Loading -> R.string.local_ai_loading
                    LocalRuntimeStatus.Ready -> R.string.local_ai_ready
                    LocalRuntimeStatus.Idle -> R.string.local_ai_idle
                    LocalRuntimeStatus.LowMemory -> R.string.local_ai_low_memory
                    LocalRuntimeStatus.IntegrityFailed -> R.string.local_ai_integrity
                    LocalRuntimeStatus.ValidationFailed -> R.string.local_ai_accuracy_failed
                    LocalRuntimeStatus.RuntimeFailed -> R.string.local_ai_runtime_failed
                }))
                Row {
                    Text(stringResource(R.string.local_ai_wifi), Modifier.weight(1f))
                    Switch(wifiOnly, model::setWifiOnly)
                }
                IntelligenceMode.entries.forEach { option ->
                    Row {
                        RadioButton(selected = mode == option, onClick = { model.setMode(option) })
                        TextButton(onClick = { model.setMode(option) }) { Text(stringResource(when(option) {
                            IntelligenceMode.FastLocal -> R.string.local_ai_fast
                            IntelligenceMode.Balanced -> R.string.local_ai_balanced
                            IntelligenceMode.CloudQuality -> R.string.local_ai_cloud
                        })) }
                    }
                }
                if (mode == IntelligenceMode.FastLocal) {
                    Row {
                        Text(stringResource(R.string.local_ai_cloud_fallback), Modifier.weight(1f))
                        Switch(cloudFallback, model::setCloudFallback)
                    }
                    Text(stringResource(R.string.local_ai_cloud_fallback_scope))
                }
                Row {
                    Text(stringResource(R.string.local_ai_trusted_scripts), Modifier.weight(1f))
                    Switch(trustedScripts, model::setTrustedScripts)
                }
                Text(stringResource(R.string.local_ai_trusted_scripts_scope))
                Row {
                    Text(stringResource(R.string.local_ai_allow_messages), Modifier.weight(1f))
                    Switch(allowMessageSending, model::setMessageSending)
                }
                Text(stringResource(R.string.local_ai_allow_messages_scope))
                Text(stringResource(R.string.local_ai_diagnostics, diagnostics.route, diagnostics.routingMs))
                Text("GUI-Owl 1.5 2B · Android Brain", style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.local_ai_gui_owl_candidate))
                ModelArtifact.candidates.forEach { artifact ->
                    val download = state.getValue(artifact.id)
                    Text(if (selectedModel == artifact.id) stringResource(R.string.local_ai_selected_model, artifact.name) else artifact.name, style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.local_ai_candidate_info, artifact.bytes / (1024 * 1024)))
                    download.status?.let { Text(stringResource(it)) }
                    download.benchmark?.let { result -> Text(stringResource(R.string.local_ai_benchmark_result,
                        result.loadMs, result.warmMs, result.firstOutputMs, result.nativeHeapMiB, result.correct, result.total, result.processPssMiB, result.decodeTokensPerSecond)) }
                    if (download.downloading) LinearProgressIndicator(
                        progress = { (download.completed.toFloat() / artifact.bytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth())
                    Row {
                        TextButton(onClick = { if (download.downloading) model.pause(artifact) else model.download(artifact) }) {
                            Text(stringResource(if (download.downloading) R.string.local_ai_pause
                                else if (download.completed > 0 && !download.installed) R.string.local_ai_resume
                                else if (download.installed) R.string.local_ai_reinstall else R.string.local_ai_download))
                        }
                        if (download.installed || download.completed > 0) TextButton(onClick = { model.delete(artifact) }) {
                            Text(stringResource(R.string.local_ai_delete))
                        }
                    }
                    if (download.installed && !download.downloading) TextButton(onClick = { model.benchmark(artifact) }) {
                        Text(stringResource(R.string.local_ai_select_and_test))
                    }
                }
            }
        }, actions = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.local_ai_close)) } })
}
