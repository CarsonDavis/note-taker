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
    /** Mic deliberately off, or the engine terminally failed. */
    IDLE,
    /** Actively transcribing (cloud: session config acked by the server). */
    LISTENING,
    /** On-device between-segment restart blip (~100ms); shows as "Listening…". */
    RESTARTING,
    /** Cloud: initial connect, before the server acks the session config. Mic is HOT
     *  and buffering — words spoken now are sent once the session is ready. */
    CONNECTING,
    /** Cloud: mid-session transport recovery. Mic is HOT and buffering; audio replays
     *  from the last server-transcribed boundary once reconnected. */
    RECOVERING
}

/** Logcat tag for M45 dictation dead-window diagnostics: `adb logcat -s SpeechTiming`. */
private const val TAG = "SpeechTiming"

/** Consecutive CLIENT/NETWORK error restarts allowed before failing loudly. */
private const val MAX_RECOVERY_RESTARTS = 3

class SpeechRecognizerManager(
    private val context: Context,
    private val onSegmentFinalized: (String) -> Unit,
    private val onError: (VoiceError) -> Unit
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

    /**
     * Consecutive recoverable errors (CLIENT/NETWORK/NETWORK_TIMEOUT) without the engine
     * reaching onReadyForSpeech in between. Field evidence (S24 Ultra, 2026-07-09 logcat):
     * a spurious ERROR_CLIENT fired 8ms after startListening mid restart-loop while the
     * engine kept working, and a transient ERROR_NETWORK fired mid-utterance — both hit
     * the fatal branch, set IDLE, and every later restart() was suppressed because IDLE
     * reads as "user stopped". The mic silently died until the user tapped the text box.
     * These codes now restart, bounded by [MAX_RECOVERY_RESTARTS] so a genuinely broken
     * engine (mic revoked, offline) still fails loudly instead of hot-looping.
     */
    private var consecutiveRecoveryRestarts = 0

    /**
     * M49: true between stop() and the next start(). Replaces the old IDLE-state gate
     * for ignoring post-stop errors — reusing IDLE for that conflated "deliberately
     * stopped" with "engine died" and silently swallowed errors during watchdog restart
     * attempts (the review's critical finding: swallowed error → no state emission →
     * watchdog starves). Set BEFORE stopListening() so the spurious ERROR_CLIENT every
     * deliberate stop fires stays suppressed.
     */
    @Volatile private var stopped = true

    private fun logTiming(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "${SystemClock.elapsedRealtime()} | $msg")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            val gap = if (lastCaptureEndAt > 0L) SystemClock.elapsedRealtime() - lastCaptureEndAt else -1L
            logTiming("onReadyForSpeech — DEAD WINDOW since last capture end: ${gap}ms")
            consecutiveRecoveryRestarts = 0 // engine is healthy again
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
            if (stopped) {
                logTiming("onError code=$error after deliberate stop — ignored")
                return
            }
            logTiming("onError code=$error")
            when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    // Reuse outran the engine resetting — full recreate, but budgeted:
                    // an endlessly-busy engine used to loop destroy/create forever,
                    // pinned at RESTARTING where the watchdog can't see it.
                    _partialText.value = ""
                    if (consecutiveRecoveryRestarts < MAX_RECOVERY_RESTARTS) {
                        consecutiveRecoveryRestarts++
                        logTiming("recognizer busy — recreate ${consecutiveRecoveryRestarts}/$MAX_RECOVERY_RESTARTS")
                        _listeningState.value = ListeningState.RESTARTING
                        recreateAndStart()
                    } else {
                        logTiming("recognizer busy — budget exhausted, going fatal")
                        _listeningState.value = ListeningState.IDLE
                        onError(VoiceError("Speech recognizer stuck busy", VoiceErrorKind.OTHER, VoiceEngine.ON_DEVICE))
                    }
                }
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                    // Transient — restart to keep listening
                    _partialText.value = ""
                    restart()
                }
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                    // Recoverable (see consecutiveRecoveryRestarts doc). Post-stop
                    // ERROR_CLIENT is handled by the `stopped` guard above, so a
                    // genuine error while idle-but-wanted is no longer swallowed.
                    _partialText.value = ""
                    if (consecutiveRecoveryRestarts < MAX_RECOVERY_RESTARTS) {
                        consecutiveRecoveryRestarts++
                        logTiming("recoverable error $error — recreate ${consecutiveRecoveryRestarts}/$MAX_RECOVERY_RESTARTS")
                        // Recreate with delay, NOT instant reuse-restart: field evidence
                        // (2026-07-09, 21:07:58 logcat) showed four ERROR_CLIENTs inside 5ms —
                        // each instant startListening into a still-busy engine threw the next
                        // error, burning the whole budget before onReadyForSpeech could reset
                        // it. destroy + 150ms breaks the collision and clears wedged state.
                        _listeningState.value = ListeningState.RESTARTING
                        recreateAndStart()
                    } else {
                        logTiming("recoverable error $error — budget exhausted, going fatal")
                        _listeningState.value = ListeningState.IDLE
                        val message = if (error == SpeechRecognizer.ERROR_CLIENT)
                            "Speech recognition keeps failing" else "Network error"
                        onError(VoiceError(message, VoiceErrorKind.OTHER, VoiceEngine.ON_DEVICE))
                    }
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
                    onError(VoiceError(message, VoiceErrorKind.OTHER, VoiceEngine.ON_DEVICE))
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
            onError(VoiceError("Speech recognition not available", VoiceErrorKind.OTHER, VoiceEngine.ON_DEVICE))
            return
        }
        logTiming("session start")
        stopped = false
        // Announce the start immediately: start() leaving state at IDLE made the
        // engine lie to the watchdog (its restart attempt looked like nothing
        // happened). LISTENING still only comes from onReadyForSpeech.
        _listeningState.value = ListeningState.RESTARTING
        audioManager.requestAudioFocus(focusRequest)
        ensureRecognizer()
        beginListening()
    }

    override fun stop() {
        logTiming("session stop")
        stopped = true // before stopListening(): suppresses the spurious post-stop ERROR_CLIENT
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
