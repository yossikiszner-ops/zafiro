package com.niki914.zafiro.app.voice

/** A recognizer callback belongs to one input lease; late results cannot submit echoed speech. */
internal class RecognitionTurn(private val canAccept: () -> Boolean) {
    private var generation = 0
    var active = false
        private set
    fun begin(): Int { active = true; return ++generation }
    fun owns(token: Int): Boolean = active && token == generation
    fun accepts(token: Int): Boolean = owns(token) && canAccept()
    fun finish(token: Int): Boolean {
        val accepted = accepts(token)
        if (token == generation) cancel()
        return accepted
    }
    fun cancel() { generation++; active = false }
}
