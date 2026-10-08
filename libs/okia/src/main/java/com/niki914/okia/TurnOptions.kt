package com.niki914.okia

import com.niki914.okia.loop.LoopOptions

/**
 * 每次 send 的回合级参数，覆盖 config 一次。
 * Design source: okia 骨架 TurnOptions。
 */
data class TurnOptions(
    val systemPrompt: String? = null,
    val model: String? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val loopOptions: LoopOptions? = null,
    /** Deterministic host actions use the same cancellable turn and persisted conversation. */
    val localAction: LocalTurnAction? = null
)

fun interface LocalTurnAction {
    suspend fun run(onEvent: suspend (com.niki914.okia.event.TurnEvent) -> Unit): com.niki914.okia.message.AssistantMessage
}


/** Host terminal status with its readable response; failure/cancellation must not look like success. */
class LocalTurnStopped(
    val response: com.niki914.okia.message.AssistantMessage,
    val cancelled: Boolean = false,
) : Exception()
