package com.carsondavis.notetaker.speech

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.carsondavis.notetaker.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ListeningState {
    IDLE, LISTENING, RESTARTING
}

/** Logcat tag for M45 dictation dead-window diagnostics: `adb logcat -s SpeechTiming`. */
private const val TAG = "SpeechTiming"

class SpeechRecognizerManager(
    private val context: Context,
    private val onSegmentFinalized: (String) -> Unit,
    private val onError: (String) -> Unit
) : VoiceRecognizer {
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { /* held for entire session */ }
        .build()

    private val _listeningState = MutableStateFlow(ListeningState.IDLE)
    override val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * M45 diagnostics: elapsedRealtime when audio capture last stopped
     * (onEndOfSpeech / onError). Used to measure the dead window — the gap until
     * the next recognizer reaches onReadyForSpeech, during which no audio is captured.
     */
    private var lastCaptureEndAt: Long = 0L

    private fun logTiming(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "${SystemClock.elapsedRealtime()} | $msg")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            val gap = if (lastCaptureEndAt > 0L) SystemClock.elapsedRealtime() - lastCaptureEndAt else -1L
            logTiming("onReadyForSpeech — DEAD WINDOW since last capture end: ${gap}ms")
            _listeningState.value = ListeningState.LISTENING
        }

        override fun onBeginningOfSpeech() {
            logTiming("onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            lastCaptureEndAt = SystemClock.elapsedRealtime()
            logTiming("onEndOfSpeech — audio capture ended")
        }

        override fun onError(error: Int) {
            lastCaptureEndAt = SystemClock.elapsedRealtime()
            logTiming("onError code=$error")
            when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Reuse outran the engine resetting — fall back to a full recreate.
                    _partialText.value = ""
                    logTiming("recognizer busy — full recreate")
                    _listeningState.value = ListeningState.RESTARTING
                    recreateAndStart()
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    // Transient — restart to keep listening
                    _partialText.value = ""
                    restart()
                }
                else -> {
                    // Real error — stop and notify
                    _listeningState.value = ListeningState.IDLE
                    _partialText.value = ""
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing audio permission"
                        else -> "Speech recognition error ($error)"
                    }
                    onError(message)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            logTiming("onResults — segment='${text ?: ""}'")
            if (!text.isNullOrEmpty()) {
                onSegmentFinalized(text)
            }
            _partialText.value = ""
            // Continue listening for next segment
            restart()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            _partialText.value = text ?: ""
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun start() {
        if (!isAvailable) {
            onError("Speech recognition not available")
            return
        }
        logTiming("session start")
        audioManager.requestAudioFocus(focusRequest)
        ensureRecognizer()
        beginListening()
    }

    override fun stop() {
        logTiming("session stop")
        _listeningState.value = ListeningState.IDLE
        _partialText.value = ""
        handler.removeCallbacksAndMessages(null)
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {}
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    override fun destroy() {
        stop()
        try {
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    /**
     * M45 fix (F1): restart by **reusing** the existing recognizer rather than
     * `destroy()` + `createSpeechRecognizer()`. Reuse skips the recognition-service
     * re-bind that dominated the ~215 ms dead window measured in the M45 baseline.
     * We only hop to the next main-loop tick (`handler.post`) to leave the recognizer
     * callback before calling `startListening()` again — no fixed delay.
     */
    private fun restart() {
        if (_listeningState.value == ListeningState.IDLE) return
        logTiming("restart() — reuse recognizer (no destroy)")
        _listeningState.value = ListeningState.RESTARTING
        handler.post {
            if (_listeningState.value == ListeningState.IDLE) return@post
            ensureRecognizer()
            beginListening()
        }
    }

    /**
     * Fallback when reuse races ahead of the engine resetting (ERROR_RECOGNIZER_BUSY)
     * or `startListening()` throws: do the old destroy + recreate with a short delay.
     */
    private fun recreateAndStart() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
        handler.postDelayed({
            if (_listeningState.value == ListeningState.IDLE) return@postDelayed
            ensureRecognizer()
            beginListening()
        }, 150)
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(listener)
            }
        }
    }

    private fun beginListening() {
        logTiming("startListening")
        try {
            recognizer?.startListening(createIntent())
        } catch (e: Exception) {
            logTiming("startListening threw (${e.message}) — recreating")
            recreateAndStart()
        }
    }

    private fun createIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }
}
