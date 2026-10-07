package com.niki914.zafiro.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Main-thread system recognition. Partial results are display-only, never agent requests. */
internal class LiveSpeechInput(
    private val context: Context,
    private val canAccept: () -> Boolean,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onUnavailable: () -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private val turn = RecognitionTurn(canAccept)
    val listening: Boolean get() = turn.active

    fun start() {
        if (listening) return
        val token = turn.begin()
        val owned = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = owned
        owned.setRecognitionListener(object : RecognitionListener {
            private fun current() = turn.accepts(token)
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                if (current()) onPartial(result(partialResults))
            }
            override fun onResults(results: Bundle?) {
                if (!turn.owns(token)) return
                val accepted = turn.finish(token)
                val text = result(results)
                cancel()
                if (accepted && text.isNotBlank()) onFinal(text)
            }
            override fun onError(error: Int) {
                if (!turn.owns(token)) return
                val accepted = turn.finish(token)
                cancel()
                if (accepted && error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) onUnavailable()
            }
        })
        val language = context.resources.configuration.locales[0].toLanguageTag()
        owned.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }
    fun cancel() {
        turn.cancel()
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
    }
    private fun result(bundle: Bundle?) = bundle
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
}
