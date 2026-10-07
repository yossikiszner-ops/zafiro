package com.niki914.zafiro.app.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.niki914.zafiro.app.R
import com.niki914.zafiro.api.Agent
import com.niki914.zafiro.api.TurnStart
import com.niki914.zafiro.api.model.AgentState
import com.niki914.zafiro.api.model.Draft
import com.niki914.zafiro.api.model.TurnBlock
import com.niki914.zafiro.api.model.TurnOutcome
import com.niki914.zafiro.api.model.isRunning
import com.niki914.zafiro.service.requireService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** One voice owner for the composer, overlay and foreground microphone service. */
internal object VoiceController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var session: VoiceSession
        private set
    private var ownsResponse = false
    fun initialize(context: Context) {
        if (::session.isInitialized) return
        val provider = GeminiVoiceProvider()
        val agent = requireService<Agent>()
        session = VoiceSession(context.applicationContext, scope, provider, provider) { text ->
            scope.launch {
                try {
                    ownsResponse = false
                    if (agent.status.value.isRunning) {
                        agent.stop(); withTimeout(10000) { agent.status.first { !it.isRunning } }
                    }
                    val draft = agent.draft.value
                    agent.updateDraft { Draft(text = text) }
                    ownsResponse = true
                    val result = agent.stream()
                    agent.updateDraft { draft }
                    if (result != TurnStart.Started) { ownsResponse = false; session.agentFailed() }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { ownsResponse = false; session.agentFailed() }
            }
        }
        configure(context)
        scope.launch {
            var running = false
            var lastTool = ""
            var narratedAt = 0L
            agent.status.collect { state ->
                if (state.isRunning) running = true
                if (ownsResponse && state is AgentState.ToolRunning && state.toolName != lastTool && android.os.SystemClock.elapsedRealtime() - narratedAt > 5000) {
                    lastTool = state.toolName; narratedAt = android.os.SystemClock.elapsedRealtime()
                    session.response(context.getString(R.string.voice_action_checking), announcement = true)
                }
                if (state is AgentState.Idle && running) {
                    running = false; lastTool = ""
                    if (ownsResponse) {
                        ownsResponse = false
                        if (state.lastOutcome == TurnOutcome.Completed) {
                            val text = agent.conversation.value.turns.lastOrNull()?.blocks
                                ?.filterIsInstance<TurnBlock.Text>()?.joinToString("\n") { it.text }.orEmpty()
                            session.response(text)
                        } else session.agentFailed()
                    }
                }
            }
        }
    }
    fun configure(context: Context) {
        val prefs = context.getSharedPreferences("zafiro_voice", Context.MODE_PRIVATE)
        session.voice = prefs.getString("voice", "Charon") ?: "Charon"
        session.style = prefs.getString("style", "Calm, precise, serious. Natural pace.") ?: ""
        session.model = prefs.getString("model", "") ?: ""
        session.speed = prefs.getFloat("speed", 1f)
        session.autoSpeak = prefs.getBoolean("auto_speak", true)
    }
    fun start(context: Context, wake: Boolean = false) {
        initialize(context)
        if (requireService<com.niki914.zafiro.business.permission.PermissionManager>()
                .status(com.niki914.zafiro.business.permission.Permission.MICROPHONE) != com.niki914.zafiro.business.permission.PermissionState.GRANTED) {
            session.microphoneFailed(); return
        }
        try {
            ContextCompat.startForegroundService(context, Intent(context, VoiceForegroundService::class.java)
                .setAction(if (wake) VoiceForegroundService.WAKE else VoiceForegroundService.LISTEN))
        } catch (_: Exception) { session.stop(); session.microphoneFailed() }
    }
    fun stop(context: Context) {
        if (ownsResponse) requireService<Agent>().stop()
        ownsResponse = false
        session.stop()
        context.stopService(Intent(context, VoiceForegroundService::class.java))
    }
}
