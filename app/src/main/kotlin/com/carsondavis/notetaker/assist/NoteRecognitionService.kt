package com.carsondavis.notetaker.assist

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Stub RecognitionService required by VoiceInteractionServiceInfo on Android 16.
 * The app does not perform speech recognition — it relies on the keyboard's
 * built-in voice-to-text. This stub exists solely to satisfy the system's
 * validation that a recognitionService is specified.
 */
class NoteRecognitionService : RecognitionService() {

    override fun onStartListening(intent: Intent?, callback: Callback?) {
        callback?.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
