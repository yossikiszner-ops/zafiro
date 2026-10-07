package com.niki914.zafiro.app.voice

import android.app.*
import android.content.Intent
import android.os.*
import android.speech.*
import androidx.core.app.NotificationCompat
import com.niki914.zafiro.app.MainActivity
import com.niki914.zafiro.app.R
import com.niki914.zafiro.remoteview.glass.ZafiroGlassPhase
import kotlinx.coroutines.*

/** User-started, visible microphone owner. Never starts from boot or silently restarts. */
class VoiceForegroundService : Service(), RecognitionListener {
    companion object {
        const val LISTEN = "com.niki914.zafiro.voice.LISTEN"
        const val WAKE = "com.niki914.zafiro.voice.WAKE"
        private const val STOP = "com.niki914.zafiro.voice.STOP"
        private const val NOTIFICATION = 1042
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var wakeMode = false
    private var recognizer: SpeechRecognizer? = null
    private var recognizing = false
    private var retries = 0
    private var restart: Job? = null
    private var expiry: Job? = null
    private var destroyed = false
    private val session get() = VoiceController.session
    override fun onCreate() {
        super.onCreate()
        VoiceController.initialize(applicationContext)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("zafiro_voice", getString(R.string.voice_settings), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try { startForeground(NOTIFICATION, NotificationCompat.Builder(this, "zafiro_voice")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.voice_settings))
            .setContentText(getString(R.string.voice_background_active)).setContentIntent(open)
            .setOngoing(true).setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.voice_background_stop), stop).build()) } catch (_: SecurityException) {
            session.microphoneFailed(); stopSelf(); return
        }
        scope.launch {
            session.status.collect { status ->
                if (!started) return@collect
                if (status.problem != null) { closeRecognizer(); stopSelf() }
                else if (!status.active && wakeMode) scheduleWake()
                else if (!status.active && !recognizing) stopSelf()
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        started = true
        when (intent?.action) {
            STOP -> { VoiceController.stop(this); stopSelf() }
            WAKE -> {
                if (session.status.value.active) session.stop()
                wakeMode = true
                if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                    session.reportFailure(VoiceProblem.WakeUnavailable); stopSelf()
                } else {
                    scheduleWake()
                    expiry?.cancel()
                    expiry = scope.launch { delay(30 * 60 * 1000L); stopSelf() }
                }
            }
            LISTEN -> { wakeMode = false; closeRecognizer(); VoiceController.configure(this); session.start() }
        }
        return START_NOT_STICKY
    }
    private fun scheduleWake() {
        if (destroyed || !wakeMode || recognizing || restart?.isActive == true) return
        restart = scope.launch {
            delay(750)
            if (!wakeMode || destroyed || session.status.value.active) return@launch
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    val local = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(this@VoiceForegroundService).also {
                        recognizer = it; it.setRecognitionListener(this@VoiceForegroundService)
                    }
                    recognizing = true
                    local.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3))
                } catch (_: Exception) { recognizing = false; session.microphoneFailed(); stopSelf() }
            }
        }
    }
    private fun closeRecognizer() {
        restart?.cancel(); restart = null
        recognizing = false
        recognizer?.cancel(); recognizer?.destroy(); recognizer = null
    }
    override fun onResults(results: Bundle?) {
        if (!recognizing || !wakeMode || destroyed) return
        recognizing = false; retries = 0
        val command = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.let { WakeCommand.parse(it) }
        if (command != null) {
            closeRecognizer()
            scope.launch {
                delay(200) // RecognitionService releases the input before AudioRecord starts.
                if (!wakeMode || destroyed) return@launch
                session.start()
                if (command.isNotBlank() && session.status.value.active) session.submitRecognized(command)
            }
        } else scheduleWake()
    }
    override fun onError(error: Int) {
        if (!recognizing || !wakeMode || destroyed) return
        recognizing = false
        if (destroyed || !wakeMode) return
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) scheduleWake()
        else if (++retries <= 3 && error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) scheduleWake()
        else { session.reportFailure(VoiceProblem.WakeUnavailable); stopSelf() }
    }
    override fun onDestroy() {
        destroyed = true; wakeMode = false; closeRecognizer(); expiry?.cancel()
        val problem = session.status.value.problem
        session.stop()
        if (problem != null) session.reportFailure(problem)
        scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

internal object WakeCommand {
    private val phrase = Regex("^\\s*(?:hey\\s+)?jarvis\\b[\\s,.!:;-]*(.*)$", RegexOption.IGNORE_CASE)
    fun parse(text: String): String? = phrase.matchEntire(text)?.groupValues?.get(1)?.trim()
}
