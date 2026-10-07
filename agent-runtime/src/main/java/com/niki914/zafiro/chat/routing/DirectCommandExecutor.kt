package com.niki914.zafiro.chat.routing

import android.content.Context
import android.media.AudioManager
import com.niki914.okia.LocalTurnAction
import com.niki914.okia.event.TurnEvent
import com.niki914.okia.message.*
import com.niki914.xposed.api.util.ContextProvider
import com.niki914.zafiro.R
import com.niki914.zafiro.chat.LocalTool
import com.niki914.zafiro.chat.agentic.accessibility.AccessibilityController
import com.niki914.zafiro.chat.agentic.buildin.BuiltinToolRequest
import com.niki914.zafiro.chat.agentic.buildin.BuiltinToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/** Reuses installed-app resolution and Android authority; no API or synthetic clicks. */
internal object DirectCommandExecutor {
    fun action(command: DirectCommand?, tools: List<LocalTool>): LocalTurnAction? {
        command ?: return null
        val name = if (command is DirectCommand.Open) "launch_app" else "screen_operation_shell"
        val tool = tools.filterIsInstance<LocalTool.Builtin>().firstOrNull { it.name == name } ?: return null // Honor disabled capabilities.
        return LocalTurnAction { emit ->
            val context = ContextProvider.await().applicationContext
            val call = ContentBlock.ToolCall(java.util.UUID.randomUUID().toString(), name,
                buildJsonObject {
                    when (command) {
                        is DirectCommand.Open -> put("app_name", alias(command.app))
                        DirectCommand.Back -> { put("operation", "key"); put("code", 4) }
                        DirectCommand.Home -> { put("operation", "key"); put("code", 3) }
                        is DirectCommand.OpenSettings -> { put("operation", "settings"); put("screen", command.screen.name) }
                        is DirectCommand.Volume -> { put("operation", "volume"); put("direction", command.direction) }
                    }
                }.toString())
            val partial = AssistantMessage(listOf(call))
            emit(TurnEvent.ToolRunning(0, call, partial))
            val result: BuiltinToolResult = try {
                when (command) {
                    is DirectCommand.Open -> tool.tool.invoke(BuiltinToolRequest(name, call.argumentsJson))
                    DirectCommand.Back -> AccessibilityController.executeKeyEvent(4)
                    DirectCommand.Home -> AccessibilityController.executeKeyEvent(3)
                    is DirectCommand.OpenSettings -> openSettings(context, command.screen)
                    is DirectCommand.Volume -> adjustVolume(context, command.direction)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { BuiltinToolResult.failure("ACTION_FAILED", "Android action failed") }
            var verified = false
            if (result.ok) {
                if (command is DirectCommand.Open || command is DirectCommand.OpenSettings) {
                    val pkg = result.data["package_name"]?.jsonPrimitive?.contentOrNull
                    verified = pkg != null && withTimeoutOrNull(1_200) {
                        while (AccessibilityController.foregroundPackage() != pkg) {
                            if (AccessibilityController.foregroundPackage() == null) return@withTimeoutOrNull false
                            delay(50)
                        }
                        true
                    } == true
                } else if (command is DirectCommand.Volume) verified = true
                else AccessibilityController.captureScreen() // Observe after navigation; never claim a specific screen.
                emit(TurnEvent.ToolSucceeded(0, call, ToolCallOutcome.Success(result.toJsonString()), partial))
            } else emit(TurnEvent.ToolFailed(0, call, ToolCallOutcome.Failure(result.toJsonString()), partial))
            val message = context.getString(when {
                !result.ok && result.code == "AMBIGUOUS_APP_MATCH" -> R.string.direct_app_ambiguous
                !result.ok && result.code == "APP_NOT_FOUND" -> R.string.direct_app_missing
                !result.ok -> R.string.direct_action_failed
                command is DirectCommand.Open && verified -> R.string.direct_app_opened
                command is DirectCommand.Open -> R.string.direct_app_requested
                command is DirectCommand.OpenSettings && verified -> R.string.direct_app_opened
                command is DirectCommand.OpenSettings -> R.string.direct_app_requested
                command is DirectCommand.Home -> R.string.direct_home_requested
                command is DirectCommand.Volume -> R.string.direct_volume_updated
                else -> R.string.direct_back_performed
            })
            AssistantMessage(listOf(ContentBlock.Text(message)), stopReason = StopReason.Stop)
        }
    }
    private fun openSettings(context: Context, screen: DirectCommand.SettingsScreen): BuiltinToolResult {
        val action = when (screen) {
            DirectCommand.SettingsScreen.Bluetooth -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            DirectCommand.SettingsScreen.BatterySaver -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
        }
        val intent = android.content.Intent(action).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = context.packageManager.resolveActivity(intent, 0)
            ?: return BuiltinToolResult.failure("SCREEN_UNAVAILABLE", "Settings screen unavailable")
        context.startActivity(intent)
        return BuiltinToolResult.success("Settings navigation requested", data = buildJsonObject { put("package_name", activity.activityInfo.packageName) })
    }
    private fun adjustVolume(context: Context, direction: Int): BuiltinToolResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val expected = (before + direction).coerceIn(0, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, expected, 0)
        return if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == expected)
            BuiltinToolResult.success("Media volume verified")
        else BuiltinToolResult.failure("VOLUME_NOT_CHANGED", "Volume could not be verified")
    }
    private fun alias(name: String): String = when (name) {
        "וואטסאפ", "ואטסאפ" -> "WhatsApp"
        "וואטסאפ ביזנס", "ואטסאפ ביזנס" -> "WhatsApp Business"
        "טלגרם" -> "Telegram"
        "ג׳ימייל", "ג'ימייל", "גימייל" -> "Gmail"
        "כרום" -> "Chrome"
        "ספוטיפיי" -> "Spotify"
        "יוטיוב" -> "YouTube"
        "מצלמה", "המצלמה" -> "Camera"
        "הגדרות", "ההגדרות" -> "Settings"
        else -> name
    }
}
