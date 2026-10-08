package com.niki914.zafiro.app.voice

import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.niki914.zafiro.api.Agent
import com.niki914.zafiro.api.model.AgentState
import com.niki914.zafiro.api.model.TurnBlock
import com.niki914.zafiro.api.model.TurnOutcome
import com.niki914.zafiro.api.model.isRunning
import com.niki914.zafiro.app.R
import com.niki914.zafiro.remoteview.glass.ZafiroGlassPhase
import com.niki914.zafiro.service.requireService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeVoiceControls(onInputChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier, settingsOnly: Boolean = false, onSettingsDismiss: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { GeminiVoiceProvider() }
    val prefs = remember { context.getSharedPreferences("zafiro_voice", Context.MODE_PRIVATE) }
    val session = remember { VoiceController.initialize(context.applicationContext); VoiceController.session }
    val status by session.status.collectAsState()
    val owner = LocalLifecycleOwner.current
    val permissions = remember { requireService<com.niki914.zafiro.business.permission.PermissionManager>() }
    var showSettings by remember { mutableStateOf(settingsOnly) }
    if (status.problem != null) {
        AlertDialog(onDismissRequest = { VoiceController.stop(context) }, title = { Text(stringResource(R.string.voice_settings)) }, text = { Text(stringResource(when(status.problem) { VoiceProblem.WakeUnavailable -> R.string.voice_wake_unavailable; VoiceProblem.Microphone -> R.string.voice_microphone_error; VoiceProblem.Configuration -> R.string.voice_configuration; VoiceProblem.Quota -> R.string.voice_quota; else -> R.string.voice_error })) }, confirmButton = { TextButton(onClick = { VoiceController.stop(context) }) { Text(stringResource(R.string.voice_stop_audio)) } })
    }
    var showGlass by remember { mutableStateOf(false) }
    if (!settingsOnly) Row(modifier, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        Box(Modifier.combinedClickable(onClick = {
            if (status.active) {
                if (status.phase == ZafiroGlassPhase.Speaking) VoiceController.interrupt()
                else VoiceController.stop(context)
            } else scope.launch {
                permissions.request(com.niki914.zafiro.business.permission.Permission.MICROPHONE)
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) VoiceController.start(context)
            }
        }, onLongClickLabel = stringResource(R.string.voice_settings), onLongClick = { showSettings = true }).size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(if (status.active && status.phase != ZafiroGlassPhase.Speaking) Icons.Default.Stop else Icons.Default.Mic, contentDescription = stringResource(when {
                status.problem != null -> when (status.problem) {
                    VoiceProblem.Configuration -> R.string.voice_configuration
                    VoiceProblem.Quota -> R.string.voice_quota
                    VoiceProblem.Microphone -> R.string.voice_microphone_error
                    VoiceProblem.WakeUnavailable -> R.string.voice_wake_unavailable
                    VoiceProblem.Model -> R.string.voice_model_error
                    else -> R.string.voice_error
                }
                !status.active -> R.string.voice_start
                status.phase == ZafiroGlassPhase.Speaking -> R.string.voice_speaking
                status.phase == ZafiroGlassPhase.Understanding -> R.string.voice_transcribing
                status.phase == ZafiroGlassPhase.Thinking -> R.string.voice_working
                else -> R.string.voice_listening
            }))

        }

    }
    if (showGlass) GlassAppearanceSettings { showGlass = false }
    val destinations by com.niki914.zafiro.chat.routing.NetworkPolicy.destinations.collectAsState()
    val route by com.niki914.zafiro.chat.routing.RequestRouting.latest.collectAsState()
    if (showSettings) {
        var voice by remember { mutableStateOf(session.voice) }
        var style by remember { mutableStateOf(session.style) }
        var model by remember { mutableStateOf(session.model) }
        var speed by remember { mutableFloatStateOf(session.speed) }
        var auto by remember { mutableStateOf(session.autoSpeak) }
        var activeConfiguration by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { activeConfiguration = com.niki914.zafiro.repo.XRepo.llmConfigs.active()?.name.orEmpty() }
        val speechSample = stringResource(R.string.voice_test_sample)
        var models by remember { mutableStateOf<List<String>>(emptyList()) }
        var menu by remember { mutableStateOf(false) }
        var discovered by remember { mutableStateOf(false) }
        var budget by remember { mutableStateOf(com.niki914.zafiro.chat.routing.RequestRouting.budget.value) }
        var budgetMenu by remember { mutableStateOf(false) }
        var networkLock by remember { mutableStateOf(com.niki914.zafiro.chat.routing.NetworkPolicy.enabled.value) }
        AlertDialog(onDismissRequest = { showSettings = false; onSettingsDismiss() }, title = { Text(stringResource(R.string.voice_settings)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showSettings = false; showGlass = true }) { Text(stringResource(R.string.glass_studio)) }
                Text(stringResource(R.string.voice_privacy))
                TextButton(onClick = {
                    scope.launch {
                        permissions.request(com.niki914.zafiro.business.permission.Permission.MICROPHONE)
                        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) VoiceController.start(context, wake = true)
                    }
                }) { Text(stringResource(R.string.voice_wake_enable)) }
                Text(stringResource(R.string.voice_wake_scope))
                TextButton(onClick = { VoiceController.stop(context) }) { Text(stringResource(R.string.voice_background_stop)) }
                Text(stringResource(R.string.voice_active_configuration, activeConfiguration))
                Row { Text(stringResource(R.string.network_lock)); Switch(networkLock, { networkLock = it }) }
                Text(stringResource(R.string.network_scope))
                destinations.forEach { destination -> Text(destination) }
                Box {
                    TextButton(onClick = { budgetMenu = true }) { Text(stringResource(R.string.request_budget) + ": " + stringResource(when (budget) {
                        com.niki914.zafiro.chat.routing.RequestBudget.Economy -> R.string.request_economy
                        com.niki914.zafiro.chat.routing.RequestBudget.Balanced -> R.string.request_balanced
                        com.niki914.zafiro.chat.routing.RequestBudget.Quality -> R.string.request_quality
                    })) }
                    DropdownMenu(budgetMenu, onDismissRequest = { budgetMenu = false }) {
                        com.niki914.zafiro.chat.routing.RequestBudget.entries.forEach { policy ->
                            DropdownMenuItem(text = { Text(stringResource(when (policy) {
                                com.niki914.zafiro.chat.routing.RequestBudget.Economy -> R.string.request_economy
                                com.niki914.zafiro.chat.routing.RequestBudget.Balanced -> R.string.request_balanced
                                com.niki914.zafiro.chat.routing.RequestBudget.Quality -> R.string.request_quality
                            })) }, onClick = { budget = policy; budgetMenu = false })
                        }
                    }
                }
                if (route.model.isNotBlank()) Text(stringResource(R.string.request_observation, route.model, route.inputTokens, route.outputTokens, route.tools, route.totalTools, route.latencyMs))
                OutlinedTextField(voice, { voice = it }, label = { Text(stringResource(R.string.voice_name)) }, singleLine = true)
                OutlinedTextField(style, { style = it }, label = { Text(stringResource(R.string.voice_style)) })
                OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.voice_model)) }, singleLine = true)
                Box {
                    TextButton(onClick = {
                        scope.launch {
                            models = try { provider.availableModels().filter { "tts" in it } } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (_: Exception) { emptyList() }
                            discovered = true; menu = models.isNotEmpty()
                        }
                    }) { Text(stringResource(R.string.voice_discover)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        models.forEach { name -> DropdownMenuItem(text = { Text(name) }, onClick = { model = name; menu = false }) }
                    }
                }
                if (discovered && models.isEmpty()) Text(stringResource(R.string.voice_model_error))
                Text(stringResource(R.string.voice_speed, speed))
                Slider(speed, { speed = it }, valueRange = .75f..1.5f)
                Row { Text(stringResource(R.string.voice_auto)); Switch(auto, { auto = it }) }
                TextButton(onClick = {
                    session.voice = voice.trim().ifBlank { "Charon" }; session.style = style.trim().take(300)
                    session.model = model.trim().removePrefix("models/"); session.speed = speed
                    session.response(speechSample, preview = true)
                }) { Text(stringResource(R.string.voice_test_audio)) }
                TextButton(onClick = { session.interruptSpeech() }) { Text(stringResource(R.string.voice_stop_audio)) }
            }
        }, confirmButton = {
            TextButton(onClick = {
                com.niki914.zafiro.chat.routing.NetworkPolicy.enabled.value = networkLock
                prefs.edit().putBoolean("network_lock", networkLock).apply()
                com.niki914.zafiro.chat.routing.RequestRouting.budget.value = budget
                prefs.edit().putString("request_budget", budget.name).apply()
                session.voice = voice.trim().ifBlank { "Charon" }; session.style = style.trim().take(300)
                session.model = model.trim().removePrefix("models/"); session.speed = speed; session.autoSpeak = auto
                prefs.edit().putString("voice", session.voice).putString("style", session.style).putString("model", session.model)
                    .putFloat("speed", speed).putBoolean("auto_speak", auto).apply()
                showSettings = false; onSettingsDismiss()
            }) { Text(stringResource(R.string.voice_save)) }
        })
    }
}
