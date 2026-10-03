package com.carsondavis.notetaker.speech.cloud

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.util.Base64
import android.util.Log
import com.carsondavis.notetaker.BuildConfig
import com.carsondavis.notetaker.speech.ListeningState
import com.carsondavis.notetaker.speech.VoiceEngine
import com.carsondavis.notetaker.speech.VoiceError
import com.carsondavis.notetaker.speech.VoiceErrorKind
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
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Cloud [VoiceRecognizer] (M46, rearchitected in M49): streams microphone audio to
 * OpenAI's realtime transcription API and emits ordered transcript segments.
 *
 * M49 transport tier — capture is decoupled from the socket:
 *  - The mic (AudioRecord) + a ~60s PCM ring buffer live for the WHOLE dictation
 *    session; the WebSocket is just transport. The capture thread always writes to the
 *    ring and is the single sender: a send cursor chases the write cursor, so live
 *    streaming and backlog replay are the same code path.
 *  - The replay watermark is the last CONTIGUOUSLY TRANSCRIBED utterance boundary:
 *    per-item `audio_end_ms` is recorded at `input_audio_buffer.speech_stopped` and the
 *    watermark advances only when that item's `transcription.completed` (or `.failed`)
 *    arrives in item order. Committed-but-untranscribed utterances therefore replay.
 *    (Empirically verified 2026-07-15: `committed` carries no audio_end_ms; the
 *    completed transcript lags the commit by 0.7–1.3s on every utterance.)
 *  - `audio_end_ms` is relative to EACH session's stream, so the absolute watermark is
 *    `sessionBase + 48 * audio_end_ms` and sessionBase is re-recorded at every
 *    reconnect's replay start (empirical: a new session restarts near 0).
 *  - Replay starts [PAD_BYTES] (300ms) BEFORE the watermark: server VAD's
 *    prefix_padding reaches backward, so the next utterance's audio_start_ms is
 *    routinely ~300ms before the previous audio_end_ms (empirical). The overlap is
 *    trailing silence of already-transcribed audio — harmless to re-feed.
 *  - LISTENING is gated on the `session.updated` ack, never `session.created`:
 *    empirically a created session has `transcription: null` and transcribes NOTHING
 *    (the 2026-07-15 fake-LISTENING incident). Audio is only sent after the ack.
 *  - Reconnects happen in-engine ([Phase.RECOVERING]) with backoff; each attempt is
 *    deadlined through the ack; recovery has a hard budget, after which the engine
 *    emits exactly ONE terminal [VoiceError]. Transient problems never leave the engine.
 *  - `queueSize()` is a stall detector only (it can't account for the kernel's TCP send
 *    buffer): a queue that stays backed up while live → cancel + reconnect proactively
 *    instead of waiting 20–40s for the ping timeout.
 */
class OpenAiRecognizer(
    private val apiKey: String,
    context: Context,
    private val onSegmentFinalized: (String) -> Unit,
    private val onError: (VoiceError) -> Unit
) : VoiceRecognizer {

    // ---------------------------------------------------------------- public surface

    private val _listeningState = MutableStateFlow(ListeningState.IDLE)
    override val listeningState: StateFlow<ListeningState> = _listeningState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    override val isAvailable: Boolean
        get() = apiKey.isNotBlank()

    // ---------------------------------------------------------------- collaborators

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val connectivity = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { /* held for entire session */ }
        .build()

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** Single-threaded scheduler for reconnect backoff + attempt deadlines. Lives for
     *  the engine instance; tasks self-invalidate via [generation]. */
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "openai-reconnect").apply { isDaemon = true }
    }

    // ---------------------------------------------------------------- session state

    private enum class Phase { STOPPED, CONNECTING, READY, RECOVERING }

    @Volatile private var phase = Phase.STOPPED
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var running = false // capture thread liveness
    /** Bumped by stop()/start(); scheduled tasks and socket callbacks from a previous
     *  generation self-discard. */
    @Volatile private var generation = 0

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null

    /** Wall-clock when the current connect/recovery episode began (budget anchor). */
    @Volatile private var episodeStartMs = 0L
    private var attemptCount = 0

    // ---------------------------------------------------------------- ring buffer
    //
    // Byte offsets are ABSOLUTE stream positions (total PCM bytes captured since
    // start()). Writes happen only on the capture thread; sendCursor/watermark
    // mutations are guarded by [streamLock].

    private val ring = ByteArray(RING_CAPACITY)
    @Volatile private var totalWritten = 0L
    private val streamLock = Any()
    /** Next absolute offset the sender will transmit. */
    private var sendCursor = 0L
    /** Absolute offset of the last contiguously transcribed utterance boundary. */
    private var watermark = 0L
    /** Absolute offset corresponding to the CURRENT session's audio_start = 0ms. */
    private var sessionBase = 0L

    private val oldestRetained: Long get() = maxOf(0L, totalWritten - RING_CAPACITY)

    private fun ringWrite(src: ByteArray, len: Int) {
        var pos = (totalWritten % RING_CAPACITY).toInt()
        var copied = 0
        while (copied < len) {
            val n = minOf(len - copied, RING_CAPACITY - pos)
            System.arraycopy(src, copied, ring, pos, n)
            copied += n
            pos = (pos + n) % RING_CAPACITY
        }
        totalWritten += len
        synchronized(streamLock) {
            // Overflow clamp: if the writer lapped un-sent audio, drop oldest (logged).
            if (sendCursor < oldestRetained) {
                log("ring overflow — dropping ${oldestRetained - sendCursor} unsent bytes")
                sendCursor = oldestRetained
            }
        }
    }

    private fun ringRead(absOffset: Long, dst: ByteArray, len: Int): Int {
        val n = minOf(len.toLong(), totalWritten - absOffset).toInt()
        if (n <= 0) return 0
        var pos = (absOffset % RING_CAPACITY).toInt()
        var copied = 0
        while (copied < n) {
            val c = minOf(n - copied, RING_CAPACITY - pos)
            System.arraycopy(ring, pos, dst, copied, c)
            copied += c
            pos = (pos + c) % RING_CAPACITY
        }
        return n
    }

    // ---------------------------------------------------------------- ordering state
    //
    // Segment ordering (M46) + per-item end offsets (M49). All guarded by [orderLock].
    // Purged wholesale at each session boundary: un-transcribed audio re-transcribes in
    // the new session under new item ids, so old-session records must not linger — a
    // permanently unfinalizable head id would hold back every future segment.

    private val orderLock = Any()
    private val segmentOrder = mutableListOf<String>()
    private val segmentText = mutableMapOf<String, String>()
    private val itemEndMs = mutableMapOf<String, Long>()
    private var emittedCount = 0
    private val currentSegment = StringBuilder()

    private fun noteItem(id: String) {
        if (id.isNotEmpty() && id !in segmentOrder) segmentOrder.add(id)
    }

    /** Emit finalized segments in speech order; advance the watermark as items complete
     *  contiguously. Call under [orderLock]. */
    private fun flushOrderedLocked() {
        while (emittedCount < segmentOrder.size) {
            val id = segmentOrder[emittedCount]
            val text = segmentText[id] ?: break // earlier segment not finalized yet — wait
            if (text.isNotEmpty()) onSegmentFinalized(text)
            itemEndMs[id]?.let { endMs ->
                synchronized(streamLock) {
                    val abs = sessionBase + endMs * BYTES_PER_MS
                    if (abs > watermark) watermark = abs
                }
            }
            emittedCount++
        }
    }

    /** Session boundary: drop ALL old-session ordering records. Audio past the
     *  watermark replays into the new session under new item ids. */
    private fun purgeOrderingForNewSession() {
        synchronized(orderLock) {
            segmentOrder.clear()
            segmentText.clear()
            itemEndMs.clear()
            emittedCount = 0
            currentSegment.setLength(0)
        }
        _partialText.value = ""
    }

    // ---------------------------------------------------------------- lifecycle

    override fun start() {
        if (apiKey.isBlank()) {
            fail("No OpenAI API key set — add one in Settings", VoiceErrorKind.INVALID_KEY)
            return
        }
        if (running) return
        log("start — capture up, connecting")
        generation++
        synchronized(streamLock) {
            totalWritten = 0L; sendCursor = 0L; watermark = 0L; sessionBase = 0L
        }
        purgeOrderingForNewSession()
        if (!startCapture()) return // fail() already emitted
        audioManager.requestAudioFocus(focusRequest)
        phase = Phase.CONNECTING
        _listeningState.value = ListeningState.CONNECTING
        episodeStartMs = now()
        attemptCount = 0
        beginAttempt(generation)
    }

    override fun stop() {
        log("stop (phase=$phase)")
        generation++
        running = false
        phase = Phase.STOPPED
        _listeningState.value = ListeningState.IDLE
        _partialText.value = ""
        try { captureThread?.join(300) } catch (_: Exception) {}
        captureThread = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        val unsent: Long
        synchronized(streamLock) { unsent = totalWritten - maxOf(sendCursor, watermark) }
        if (unsent > BYTES_PER_MS * 500) log("stop with ~${unsent / BYTES_PER_MS}ms un-transcribed audio abandoned")
        try { webSocket?.close(1000, "client stop") } catch (_: Exception) {}
        webSocket = null
        synchronized(orderLock) {
            segmentOrder.clear(); segmentText.clear(); itemEndMs.clear()
            emittedCount = 0; currentSegment.setLength(0)
        }
    }

    override fun destroy() {
        stop()
        scheduler.shutdownNow()
        // Release OkHttp resources for this engine instance (engines are swapped, not pooled).
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // ---------------------------------------------------------------- capture

    @SuppressLint("MissingPermission") // caller verifies RECORD_AUDIO before start()
    private fun startCapture(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            fail("Microphone doesn't support 24 kHz capture", VoiceErrorKind.OTHER)
            return false
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, SAMPLE_RATE) // ~0.5s hardware buffer
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            fail("Microphone unavailable", VoiceErrorKind.OTHER)
            return false
        }
        audioRecord = record
        record.startRecording()
        running = true
        val gen = generation
        captureThread = thread(name = "openai-audio") { captureLoop(gen, record) }
        return true
    }

    /** Capture thread: always write to the ring; when READY, drain toward the socket
     *  (live audio and backlog are the same cursor-chase) and watch for stalls. */
    private fun captureLoop(gen: Int, record: AudioRecord) {
        val chunk = ByteArray(LIVE_CHUNK)
        var stallTicks = 0
        var lastQueue = 0L
        while (running && gen == generation) {
            val n = record.read(chunk, 0, chunk.size)
            if (n <= 0) {
                // Mic revoked or device error — terminal, never hot-spin holding the mic.
                log("AudioRecord.read returned $n — terminal")
                scheduler.execute { if (gen == generation) goTerminal("Microphone read failed", VoiceErrorKind.OTHER) }
                return
            }
            ringWrite(chunk, n)
            val ws = webSocket
            if (phase == Phase.READY && ws != null) {
                if (!drainSome(ws)) continue // transport failed mid-send; recovery already triggered
                // Stall detection: a queue that stays big and non-draining means the
                // socket is alive-but-dead (kernel buffer full upstream). Proactively
                // reconnect instead of waiting for the ping timeout.
                val q = ws.queueSize()
                if (q > STALL_QUEUE_BYTES && q >= lastQueue) stallTicks++ else stallTicks = 0
                lastQueue = q
                if (stallTicks >= STALL_TICKS) {
                    log("stall detected (queue=$q for $stallTicks ticks) — proactive reconnect")
                    stallTicks = 0
                    transportLost(gen, cancelSocket = true)
                }
            } else {
                stallTicks = 0; lastQueue = 0
            }
        }
    }

    /** Send buffered audio from sendCursor toward totalWritten, paced by queueSize so a
     *  large backlog can't flood OkHttp's queue (and so the stall detector stays
     *  meaningful). Returns false if the transport failed. */
    private fun drainSome(ws: WebSocket): Boolean {
        val tmp = ByteArray(DRAIN_CHUNK)
        while (true) {
            val cursor: Long
            synchronized(streamLock) { cursor = sendCursor }
            if (cursor >= totalWritten) return true
            if (ws.queueSize() > DRAIN_PACE_BYTES) return true // let the queue drain first
            val n = ringRead(cursor, tmp, minOf(DRAIN_CHUNK.toLong(), totalWritten - cursor).toInt())
            if (n <= 0) return true
            val b64 = Base64.encodeToString(tmp, 0, n, Base64.NO_WRAP)
            val evt = JSONObject().put("type", "input_audio_buffer.append").put("audio", b64)
            if (!ws.send(evt.toString())) {
                log("send() returned false — transport failed")
                transportLost(generation, cancelSocket = true)
                return false
            }
            synchronized(streamLock) { if (sendCursor == cursor) sendCursor = cursor + n }
        }
    }

    // ---------------------------------------------------------------- connection

    private fun beginAttempt(gen: Int) {
        if (gen != generation || !running) return
        val elapsed = now() - episodeStartMs
        val offline = connectivity.activeNetwork == null
        val budget = if (offline) OFFLINE_BUDGET_MS else RECOVERY_BUDGET_MS
        if (elapsed > budget) {
            goTerminal(
                if (offline) "No network connection" else "Cloud transcription unreachable",
                VoiceErrorKind.OTHER
            )
            return
        }
        if (offline) {
            // Provably no network: don't burn a socket attempt; poll cheaply.
            log("offline — rechecking in ${OFFLINE_POLL_MS}ms (elapsed ${elapsed}ms)")
            scheduler.schedule({ beginAttempt(gen) }, OFFLINE_POLL_MS, TimeUnit.MILLISECONDS)
            return
        }
        attemptCount++
        val attempt = attemptCount
        log("connect attempt #$attempt (elapsed ${elapsed}ms)")
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?intent=transcription")
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        val ws = client.newWebSocket(request, socketListener)
        webSocket = ws
        // Deadline covers connect + session.updated ack. Empirical: connect alone can
        // take 10s on bad service, so the deadline is generous; the episode budget is
        // the real cap.
        scheduler.schedule({
            if (gen == generation && running && phase != Phase.READY && webSocket === ws) {
                log("attempt #$attempt deadline — no session ack; retrying")
                try { ws.cancel() } catch (_: Exception) {}
                // onFailure from the cancel is ignored (stale ws) — schedule here.
                webSocket = null
                scheduleNextAttempt(gen)
            }
        }, ATTEMPT_DEADLINE_MS, TimeUnit.MILLISECONDS)
    }

    private fun scheduleNextAttempt(gen: Int) {
        if (gen != generation || !running) return
        val backoff = minOf(BACKOFF_BASE_MS shl minOf(attemptCount, 3), BACKOFF_MAX_MS)
        scheduler.schedule({ beginAttempt(gen) }, backoff, TimeUnit.MILLISECONDS)
    }

    /** Transport died mid-session (failure, server close, stall, failed send). Keep
     *  capturing; move to RECOVERING; start a fresh recovery episode if not in one. */
    private fun transportLost(gen: Int, cancelSocket: Boolean) {
        if (gen != generation || !running) return
        val ws = webSocket
        webSocket = null
        if (cancelSocket && ws != null) try { ws.cancel() } catch (_: Exception) {}
        if (phase != Phase.RECOVERING && phase != Phase.CONNECTING) {
            phase = Phase.RECOVERING
            _listeningState.value = ListeningState.RECOVERING
            episodeStartMs = now()
            attemptCount = 0
        }
        purgeOrderingForNewSession()
        scheduler.execute { scheduleNextAttempt(gen) }
    }

    private fun goTerminal(message: String, kind: VoiceErrorKind) {
        if (!running) return
        log("terminal: $message")
        val unsent: Long
        synchronized(streamLock) { unsent = totalWritten - watermark }
        if (unsent > 0) log("abandoning ~${unsent / BYTES_PER_MS}ms of un-transcribed audio")
        stop()
        fail(message, kind)
    }

    // ---------------------------------------------------------------- socket callbacks

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            if (ws !== webSocket) return
            log("ws open — sending session config")
            val update = JSONObject().apply {
                put("type", "session.update")
                put("session", JSONObject().apply {
                    put("type", "transcription")
                    put("audio", JSONObject().apply {
                        put("input", JSONObject().apply {
                            put("format", JSONObject().apply {
                                put("type", "audio/pcm")
                                put("rate", SAMPLE_RATE)
                            })
                            put("transcription", JSONObject().apply {
                                put("model", "gpt-transcribe")
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
            // No audio yet — sends are gated on the session.updated ack (a created
            // session has transcription:null and transcribes nothing).
        }

        override fun onMessage(ws: WebSocket, text: String) {
            if (ws !== webSocket) return
            handleEvent(text)
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            if (ws !== webSocket) return
            log("ws failure: ${t.message} (http ${response?.code})")
            when (response?.code) {
                401, 403 -> goTerminal("Cloud transcription error: invalid API key", VoiceErrorKind.INVALID_KEY)
                429 -> goTerminal("Cloud transcription error: rate limited or out of credit", VoiceErrorKind.OUT_OF_CREDIT)
                else -> transportLost(generation, cancelSocket = false)
            }
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            if (ws !== webSocket) {
                log("stale ws closed: $code $reason — ignored")
                return
            }
            log("ws closed by server: $code $reason")
            transportLost(generation, cancelSocket = false)
        }
    }

    private fun handleEvent(text: String) {
        val json = try { JSONObject(text) } catch (_: Exception) { return }
        val type = json.optString("type")
        if (BuildConfig.DEBUG && type != "conversation.item.input_audio_transcription.delta") {
            log("event: $type | ${text.take(2000)}")
        }
        when (type) {
            "session.updated" -> {
                // THE readiness signal: config (model + language) is live server-side.
                synchronized(streamLock) {
                    val replayFrom = maxOf(oldestRetained, watermark - PAD_BYTES, 0L)
                    sendCursor = replayFrom
                    sessionBase = replayFrom
                }
                phase = Phase.READY
                _listeningState.value = ListeningState.LISTENING
                log("session ready — replaying from ${synchronized(streamLock) { sendCursor }} / $totalWritten")
            }
            "session.created" -> { /* not ready yet — transcription is null until updated */ }
            "input_audio_buffer.speech_stopped" -> {
                val id = json.optString("item_id")
                val endMs = json.optLong("audio_end_ms", -1L)
                if (id.isNotEmpty() && endMs >= 0) {
                    synchronized(orderLock) { itemEndMs[id] = endMs }
                }
            }
            "input_audio_buffer.committed", "conversation.item.added", "conversation.item.created" -> {
                val id = json.optString("item_id").ifEmpty {
                    json.optJSONObject("item")?.optString("id") ?: ""
                }
                synchronized(orderLock) { noteItem(id) }
            }
            "conversation.item.input_audio_transcription.delta" -> {
                val delta = json.optString("delta")
                if (delta.isNotEmpty()) {
                    val display: String
                    synchronized(orderLock) {
                        currentSegment.append(delta)
                        display = currentSegment.toString()
                    }
                    _partialText.value = display
                }
            }
            "conversation.item.input_audio_transcription.completed" -> {
                val transcript = json.optString("transcript").trim()
                val itemId = json.optString("item_id")
                log("segment completed [$itemId]: '$transcript'")
                synchronized(orderLock) {
                    noteItem(itemId)
                    segmentText[itemId] = transcript
                    flushOrderedLocked()
                    currentSegment.setLength(0)
                }
                _partialText.value = ""
            }
            "conversation.item.input_audio_transcription.failed" -> {
                // A dead item must not wedge the ordering queue: record empty text so
                // flushOrdered can advance past it. Its audio stays behind the watermark
                // question mark — but the server consumed it; nothing to replay.
                val itemId = json.optString("item_id")
                log("transcription FAILED for [$itemId] — recording empty segment")
                synchronized(orderLock) {
                    noteItem(itemId)
                    segmentText[itemId] = ""
                    flushOrderedLocked()
                    currentSegment.setLength(0)
                }
                _partialText.value = ""
            }
            "error" -> {
                val err = json.optJSONObject("error")
                val msg = err?.optString("message")?.takeIf { it.isNotEmpty() } ?: "unknown error"
                val code = err?.optString("code") ?: ""
                log("api error: $msg (code=$code)")
                when (code) {
                    "insufficient_quota" -> goTerminal("Cloud transcription error: $msg", VoiceErrorKind.OUT_OF_CREDIT)
                    "invalid_api_key" -> goTerminal("Cloud transcription error: $msg", VoiceErrorKind.INVALID_KEY)
                    else -> {
                        // Unknown mid-session API error: treat as transport-level and
                        // let recovery decide; a persistent one exhausts the budget.
                        try { webSocket?.close(1000, "api error") } catch (_: Exception) {}
                        transportLost(generation, cancelSocket = true)
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- misc

    private fun fail(message: String, kind: VoiceErrorKind) =
        onError(VoiceError(message, kind, VoiceEngine.CLOUD))

    private fun now() = android.os.SystemClock.elapsedRealtime()

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d("OpenAiRecognizer", msg)
    }

    private companion object {
        const val SAMPLE_RATE = 24000
        /** 24kHz × 16-bit mono = 48 bytes of PCM per millisecond. */
        const val BYTES_PER_MS = 48L
        /** ~60s of audio; empirically most recoverable outages are far shorter. */
        const val RING_CAPACITY = (60_000L * BYTES_PER_MS).toInt() // 2.88 MB
        /** Live read size: ~66ms, matches the pre-M49 chunking. */
        const val LIVE_CHUNK = 3200
        /** Backlog drain chunk: 1s of PCM per append event. */
        const val DRAIN_CHUNK = (1000L * BYTES_PER_MS).toInt()
        /** Pause draining while OkHttp's queue holds more than ~1s of encoded audio. */
        const val DRAIN_PACE_BYTES = 90_000L
        /** Queue persistently above ~3s of encoded audio and not draining = stall. */
        const val STALL_QUEUE_BYTES = 200_000L
        /** Consecutive ~66ms capture ticks the stall must persist (~2s). */
        const val STALL_TICKS = 30
        /** VAD prefix_padding reaches ~300ms back before the watermark (empirical). */
        const val PAD_BYTES = 300L * BYTES_PER_MS
        /** Connect + session.updated ack deadline (connect alone took 10s on bad service). */
        const val ATTEMPT_DEADLINE_MS = 15_000L
        /** Hard cap on a recovery episode before terminal failure → fallback. Applies
         *  when a network is PRESENT but broken (captive portal, dead proxy) — there
         *  the on-device fallback genuinely works, so fail over quickly. */
        const val RECOVERY_BUDGET_MS = 20_000L
        /** Provably offline (airplane mode, no signal): wait out the ring instead.
         *  Field test 2026-07-15: this device's on-device engine returns only NO_MATCH
         *  offline (no offline language pack), so falling back early buys nothing and
         *  abandons recoverable audio — an 18s loss in the field test that a longer
         *  budget would have fully replayed. Buffered words stay recoverable for the
         *  ring's full 60s; past that we're dropping oldest audio anyway, so go
         *  terminal and say so. */
        const val OFFLINE_BUDGET_MS = 60_000L
        const val OFFLINE_POLL_MS = 1_500L
        const val BACKOFF_BASE_MS = 1_000L
        const val BACKOFF_MAX_MS = 8_000L
    }
}
