package com.carsondavis.notetaker.speech.cloud

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.carsondavis.notetaker.BuildConfig
import com.carsondavis.notetaker.speech.ListeningState
import com.carsondavis.notetaker.speech.VoiceRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Cloud [VoiceRecognizer] (M46) that streams microphone audio to OpenAI's realtime
 * transcription API over a WebSocket and emits transcripts. Unlike the on-device
 * [com.carsondavis.notetaker.speech.SpeechRecognizerManager], this keeps one
 * continuous audio stream open — there is no destroy/recreate restart cycle, so the
 * word-drop dead window does not exist here.
 *
 * BYOK: the user supplies their own OpenAI API key (stored encrypted by AuthManager).
 *
 * Audio: OpenAI's `pcm16` input format is 24 kHz, mono, 16-bit little-endian PCM,
 * which is exactly what we capture with AudioRecord and send base64-encoded.
 */
class OpenAiRecognizer(
    private val apiKey: String,
    private val onSegmentFinalized: (String) -> Unit,
    private val onError: (String) -> Unit
) : VoiceRecognizer {

    private val _listeningState = MutableStateFlow(ListeningState.IDLE)
    override val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    override val isAvailable: Boolean
        get() = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    @Volatile private var recording = false
    private var captureThread: Thread? = null

    /** Accumulates the current segment's streamed delta text for live display. */
    private val currentSegment = StringBuilder()

    /**
     * Ordering buffer (M46). OpenAI returns finalized transcripts asynchronously, so a
     * later utterance can complete before an earlier one — which shuffled segments in
     * the saved note. `conversation.item.added` fires in the order speech was committed,
     * so we record that order and only emit a segment once all earlier ones have landed.
     */
    private val segmentOrder = mutableListOf<String>()   // item_ids in speech order
    private val segmentText = mutableMapOf<String, String>()
    private var emittedCount = 0

    private fun noteItem(id: String) {
        if (id.isNotEmpty() && id !in segmentOrder) segmentOrder.add(id)
    }

    private fun flushOrdered() {
        while (emittedCount < segmentOrder.size) {
            val id = segmentOrder[emittedCount]
            val text = segmentText[id] ?: break // earlier segment not finalized yet — wait
            if (text.isNotEmpty()) onSegmentFinalized(text)
            emittedCount++
        }
    }

    private fun resetOrdering() {
        segmentOrder.clear()
        segmentText.clear()
        emittedCount = 0
        currentSegment.setLength(0)
    }

    override fun start() {
        if (apiKey.isBlank()) {
            onError("No OpenAI API key set — add one in Settings")
            return
        }
        if (recording || webSocket != null) return
        log("start — connecting")
        resetOrdering()
        _listeningState.value = ListeningState.RESTARTING // "connecting"
        // GA Realtime API (the beta shape + OpenAI-Beta header were retired).
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?intent=transcription")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        webSocket = client.newWebSocket(request, socketListener)
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            log("ws open")
            // GA session shape: session.type=transcription, nested audio.input config.
            val update = JSONObject().apply {
                put("type", "session.update")
                put("session", JSONObject().apply {
                    put("type", "transcription")
                    put("audio", JSONObject().apply {
                        put("input", JSONObject().apply {
                            put("format", JSONObject().apply {
                                put("type", "audio/pcm")
                                put("rate", 24000)
                            })
                            put("transcription", JSONObject().apply {
                                put("model", "gpt-4o-transcribe")
                                // Lock transcription to English. Without a language
                                // hint the API auto-detects and occasionally guesses
                                // wrong (e.g. Japanese / a Nordic language). ISO-639-1.
                                put("language", "en")
                            })
                            put("turn_detection", JSONObject().apply {
                                put("type", "server_vad")
                                put("silence_duration_ms", 500)
                            })
                        })
                    })
                })
            }
            ws.send(update.toString())
            startAudioCapture(ws)
        }

        override fun onMessage(ws: WebSocket, text: String) {
            handleEvent(text)
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            log("ws failure: ${t.message} (http ${response?.code})")
            val detail = when (response?.code) {
                401 -> "invalid API key"
                429 -> "rate limited or out of credit"
                else -> t.message ?: "connection failed"
            }
            stopAudioCapture()
            webSocket = null
            _listeningState.value = ListeningState.IDLE
            _partialText.value = ""
            onError("Cloud transcription error: $detail")
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            log("ws closed: $code $reason")
            _listeningState.value = ListeningState.IDLE
        }
    }

    private fun handleEvent(text: String) {
        val json = try { JSONObject(text) } catch (_: Exception) { return }
        val type = json.optString("type")
        log("event: $type")
        when (type) {
            "session.created", "session.updated",
            "transcription_session.created", "transcription_session.updated" -> {
                _listeningState.value = ListeningState.LISTENING
            }
            "conversation.item.added", "conversation.item.created" -> {
                // Fires in the order speech was committed — defines segment ordering.
                noteItem(json.optString("item_id").ifEmpty { json.optJSONObject("item")?.optString("id") ?: "" })
            }
            "conversation.item.input_audio_transcription.delta" -> {
                val delta = json.optString("delta")
                if (delta.isNotEmpty()) {
                    currentSegment.append(delta)
                    _partialText.value = currentSegment.toString()
                }
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val transcript = json.optString("transcript").trim()
                val itemId = json.optString("item_id")
                log("segment completed [$itemId]: '$transcript'")
                noteItem(itemId)           // in case 'added' wasn't seen for this id
                segmentText[itemId] = transcript
                flushOrdered()             // emit in speech order, holding back early finals
                currentSegment.setLength(0)
                _partialText.value = ""
            }
            "error" -> {
                val msg = json.optJSONObject("error")?.optString("message") ?: "unknown error"
                log("api error: $msg")
                onError("Cloud transcription error: $msg")
            }
        }
    }

    @SuppressLint("MissingPermission") // caller verifies RECORD_AUDIO before start()
    private fun startAudioCapture(ws: WebSocket) {
        val sampleRate = 24000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            onError("Microphone doesn't support 24 kHz capture")
            return
        }
        val bufferSize = maxOf(minBuf, sampleRate) // generous (~0.5s of 16-bit mono)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError("Microphone unavailable")
            return
        }
        audioRecord = record
        record.startRecording()
        recording = true
        captureThread = thread(name = "openai-audio") {
            val chunk = ByteArray(3200) // ~66ms at 24kHz/16-bit mono
            while (recording) {
                val n = record.read(chunk, 0, chunk.size)
                if (n > 0) {
                    val b64 = Base64.encodeToString(chunk, 0, n, Base64.NO_WRAP)
                    val evt = JSONObject().apply {
                        put("type", "input_audio_buffer.append")
                        put("audio", b64)
                    }
                    ws.send(evt.toString())
                }
            }
        }
    }

    private fun stopAudioCapture() {
        recording = false
        try { captureThread?.join(300) } catch (_: Exception) {}
        captureThread = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    override fun stop() {
        log("stop")
        _listeningState.value = ListeningState.IDLE
        _partialText.value = ""
        resetOrdering()
        stopAudioCapture()
        try { webSocket?.close(1000, "client stop") } catch (_: Exception) {}
        webSocket = null
    }

    override fun destroy() {
        stop()
    }

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d("OpenAiRecognizer", msg)
    }
}
