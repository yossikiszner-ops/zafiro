package com.niki914.zafiro.app.voice

import android.content.Context
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

@Composable
internal fun HomeVoiceControls(onInputChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val provider = remember { GeminiVoiceProvider() }
    val prefs = remember { context.getSharedPreferences("zafiro_voice", Context.MODE_PRIVATE) }
    val input by rememberUpdatedState(onInputChange); val send by rememberUpdatedState(onSend)
    val agent = remember { requireService<Agent>() }
    var ownsResponse by remember { mutableStateOf(false) }
    var sendFailed by remember { mutableStateOf(false) }
    val session = remember {
        VoiceSession(context.applicationContext, scope, provider, provider) { transcript ->
            scope.launch {
                try {
                    if (agent.status.value.isRunning) { agent.stop(); withTimeout(10000) { agent.status.first { !it.isRunning } } }
                    ownsResponse = true
                    input(transcript); send()
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { ownsResponse = false; sendFailed = true }
            }
        }.apply {
            voice = prefs.getString("voice", "Charon") ?: "Charon"
            style = prefs.getString("style", "Calm, precise, serious. Natural pace.") ?: ""
            model = prefs.getString("model", "") ?: ""
            speed = prefs.getFloat("speed", 1f)
            autoSpeak = prefs.getBoolean("auto_speak", true)
        }
    }
    LaunchedEffect(sendFailed) {
        if (sendFailed) { session.agentFailed(); sendFailed = false }
    }
    val status by session.status.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { ownsResponse = false; session.stop() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); session.stop() }
    }
    LaunchedEffect(agent, session) {
        var running = false
        agent.status.collect { state ->
            if (state.isRunning) running = true
            if (state is AgentState.Idle && running) {
                running = false
                if (ownsResponse) {
                    ownsResponse = false
                    if (state.lastOutcome == TurnOutcome.Completed) {
                        val text = agent.conversation.value.turns.lastOrNull()?.blocks?.filterIsInstance<TurnBlock.Text>()?.joinToString("\n") { it.text }.orEmpty()
                        session.response(text)
                    } else session.agentFailed()
                }
            }
        }
    }
    val permissions = remember { requireService<com.niki914.zafiro.business.permission.PermissionManager>() }
    var showSettings by remember { mutableStateOf(false) }
    var showGlass by remember { mutableStateOf(false) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = {
            if (status.active) {
                if (ownsResponse && agent.status.value.isRunning) agent.stop()
                ownsResponse = false; session.stop()
            }
            else scope.launch {
                permissions.request(com.niki914.zafiro.business.permission.Permission.MICROPHONE)
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) session.start()
            }
        }) {
            Icon(if (status.active) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(when {
                status.problem != null -> when (status.problem) {
                    VoiceProblem.Configuration -> R.string.voice_configuration
                    VoiceProblem.Quota -> R.string.voice_quota
                    VoiceProblem.Microphone -> R.string.voice_microphone_error
                    VoiceProblem.Model -> R.string.voice_model_error
                    else -> R.string.voice_error
                }
                !status.active -> R.string.voice_start
                status.phase == ZafiroGlassPhase.Speaking -> R.string.voice_speaking
                status.phase == ZafiroGlassPhase.Understanding -> R.string.voice_transcribing
                status.phase == ZafiroGlassPhase.Thinking -> R.string.voice_working
                else -> R.string.voice_listening
            }), maxLines = 2)
        }
        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, stringResource(R.string.voice_settings)) }
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
        var models by remember { mutableStateOf<List<String>>(emptyList()) }
        var menu by remember { mutableStateOf(false) }
        var discovered by remember { mutableStateOf(false) }
        var budget by remember { mutableStateOf(com.niki914.zafiro.chat.routing.RequestRouting.budget.value) }
        var budgetMenu by remember { mutableStateOf(false) }
        var networkLock by remember { mutableStateOf(com.niki914.zafiro.chat.routing.NetworkPolicy.enabled.value) }
        AlertDialog(onDismissRequest = { showSettings = false }, title = { Text(stringResource(R.string.voice_settings)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showSettings = false; showGlass = true }) { Text(stringResource(R.string.glass_studio)) }
                Text(stringResource(R.string.voice_privacy))
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
                showSettings = false
            }) { Text(stringResource(R.string.voice_save)) }
        })
    }
}
