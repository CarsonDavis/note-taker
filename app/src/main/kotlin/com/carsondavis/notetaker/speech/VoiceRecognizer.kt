package com.carsondavis.notetaker.speech

import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over a continuous voice-to-text engine (M46), so the app can switch
 * between the on-device Android [SpeechRecognizerManager] and a cloud streaming
 * service ([com.carsondavis.notetaker.speech.cloud.OpenAiRecognizer]) via a setting.
 *
 * Implementations expose recognized text two ways:
 *  - [partialText]: the live, still-changing transcript for the current utterance.
 *  - the `onSegmentFinalized` callback passed to the implementation's constructor:
 *    a finalized chunk of text to append to the note.
 *
 * [listeningState] drives the mic UI. Errors are surfaced via the implementation's
 * `onError` callback.
 */
interface VoiceRecognizer {
    val listeningState: StateFlow<ListeningState>
    val partialText: StateFlow<String>

    /** Whether this engine can run right now (e.g. recognizer present, or key set). */
    val isAvailable: Boolean

    fun start()
    fun stop()
    fun destroy()
}

/** Which engine raised an error — drives whether a cloud→on-device fallback is offered. */
enum class VoiceEngine { ON_DEVICE, CLOUD }

/**
 * Why a voice engine failed. [TRANSIENT] is worth a silent retry; the rest are fatal
 * and (for [VoiceEngine.CLOUD]) trigger the automatic fallback to on-device.
 */
enum class VoiceErrorKind { TRANSIENT, OUT_OF_CREDIT, INVALID_KEY, OTHER }

/** A structured voice-engine failure, replacing the old plain-string error callback. */
data class VoiceError(
    val message: String,
    val kind: VoiceErrorKind,
    val engine: VoiceEngine
)
