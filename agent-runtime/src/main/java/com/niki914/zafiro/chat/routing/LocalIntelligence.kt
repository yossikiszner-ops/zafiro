package com.niki914.zafiro.chat.routing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

enum class IntelligenceMode { FastLocal, Balanced, CloudQuality }
data class LocalInterpretation(val command: String, val confidence: Double)
data class RouteDiagnostics(val route: String, val routingMs: Long, val escalation: String? = null)

/** Host provides optional inference. This never provides an Android action authority. */
object LocalIntelligence {
    val mode = MutableStateFlow(IntelligenceMode.Balanced)
    val allowCloudFallback = MutableStateFlow(true)
    val allowMessageSending = MutableStateFlow(false)
    fun requireCloudPermission() {
        check(mode.value != IntelligenceMode.FastLocal || allowCloudFallback.value) {
            diagnostics.value = diagnostics.value.copy(route = "LOCAL_ONLY_BLOCKED", escalation = "Cloud fallback disabled")
            "Local-only mode: no supported local plan. Enable cloud fallback in Local AI settings to continue."
        }
    }
    val diagnostics = MutableStateFlow(RouteDiagnostics("NONE", 0))
    @Volatile var interpreter: (suspend (String) -> LocalInterpretation?)? = null

    suspend fun suggest(input: String): String? {
        if (mode.value == IntelligenceMode.CloudQuality) return null
        val domain = TinyIntentRouter.classify(input)
        if (domain.domain != TinyIntentRouter.Domain.Android && domain.margin >= 0.1) return null
        val budgetMs = when {
            mode.value == IntelligenceMode.FastLocal && !allowCloudFallback.value -> 30_000L
            mode.value == IntelligenceMode.FastLocal -> 1_600L
            else -> 500L
        }
        val result = try { withTimeoutOrNull(budgetMs) { interpreter?.invoke(input) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null } ?: return null
        return result.command.takeIf { result.confidence >= 0.98 && grounded(input, it) }
    }
    internal fun grounded(input: String, command: String): Boolean {
        val source = input.lowercase()
        if (Regex("(?:\\bnot\\b|\\bdon't\\b|(?:^| )אל |(?:^| )לא )").containsMatchIn(source)) return false
        return when (val action = DirectCommand.parse(command)) {
            is DirectCommand.Open -> source.contains(action.app.lowercase())
            DirectCommand.Back -> Regex("back|previous|אחורה|קודם").containsMatchIn(source)
            DirectCommand.Home -> Regex("home|מסך הבית").containsMatchIn(source)
            is DirectCommand.OpenSettings -> when (action.screen) {
                DirectCommand.SettingsScreen.Bluetooth -> Regex("bluetooth|בלוטוס").containsMatchIn(source)
                DirectCommand.SettingsScreen.BatterySaver -> Regex("battery|סוללה").containsMatchIn(source)
            }
            is DirectCommand.Volume -> Regex("volume|ווליום|עוצמת הקול").containsMatchIn(source)
            null -> LocalMessagePlan.parse(command)?.let {
                Regex("^(?:please )?(?:send\\b|tell\\b|שלח |תשלח )", RegexOption.IGNORE_CASE).containsMatchIn(input.trim()) &&
                    input.contains(it.recipient, true) && input.contains(it.content, true) &&
                    Regex("whatsapp|וואטסאפ|ואטסאפ", RegexOption.IGNORE_CASE).containsMatchIn(input)
            } == true
        }
    }
}
