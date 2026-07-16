package com.carsondavis.notetaker.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.data.repository.NoteRepository
import com.carsondavis.notetaker.data.repository.SubmitResult
import com.carsondavis.notetaker.speech.ListeningState
import com.carsondavis.notetaker.speech.SpeechRecognizerManager
import com.carsondavis.notetaker.speech.VoiceEngine
import com.carsondavis.notetaker.speech.VoiceError
import com.carsondavis.notetaker.speech.VoiceErrorKind
import com.carsondavis.notetaker.speech.VoiceRecognizer
import com.carsondavis.notetaker.speech.cloud.OpenAiRecognizer
import com.carsondavis.notetaker.ui.components.SubmissionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class InputMode { VOICE, KEYBOARD }

/**
 * Shown as a persistent banner when cloud transcription fails and the app has
 * automatically fallen back to on-device for the rest of this session. The user's
 * saved OpenAI key is untouched — [canRetryCloud] reflects whether a key is still set.
 */
data class VoiceEngineNotice(
    val reason: String,
    val canRetryCloud: Boolean
)

data class NoteUiState(
    val noteText: String = "",
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitQueued: Boolean = false,
    val pendingCount: Int = 0,
    val submissions: List<SubmissionItem> = emptyList(),
    val submitError: String? = null,
    val inputMode: InputMode = InputMode.VOICE,
    val listeningState: ListeningState = ListeningState.IDLE,
    val speechAvailable: Boolean = false,
    val permissionGranted: Boolean = false,
    val voiceEngineNotice: VoiceEngineNotice? = null,
    /** Submit was tapped while the cloud engine is reconnecting — buffered speech may
     *  not be transcribed yet. UI shows a confirm dialog instead of silently saving a
     *  note missing its tail. */
    val showRecoverySubmitConfirm: Boolean = false
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val authManager: AuthManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteUiState())
    private val _showOnboarding = MutableStateFlow(false)
    val showOnboarding: StateFlow<Boolean> = _showOnboarding.asStateFlow()
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    // Accumulates finalized speech segments
    private var confirmedText: String = ""

    // The active voice engine — swapped between on-device and cloud per the user's
    // "Voice Input" setting (M46). Initialized to on-device so it's ready before the
    // settings flow emits; observeVoiceConfig() rebuilds it if the setting differs.
    private var voiceRecognizer: VoiceRecognizer =
        SpeechRecognizerManager(context, ::onSegmentFinalized, ::onVoiceError)
    private var currentMode: String = AuthManager.VOICE_MODE_ON_DEVICE
    private var currentKey: String? = null
    private var recognizerStateJob: Job? = null

    // Session-only engine override after a cloud failure: forces on-device without
    // changing the saved voiceMode preference or the stored key. Cleared when the
    // user explicitly changes the mode in Settings, retries cloud, or starts a new
    // dictation session (cloud is re-tried automatically each session).
    private var engineOverride: String? = null

    // Watchdog (M48, hardened M49): true while the mic is DELIBERATELY off (keyboard
    // switch, submit, app pause) — the one thing that distinguishes "user stopped"
    // from "engine died". Only resumed-screen user paths clear it; switchEngine never
    // touches it. When the engine lands on IDLE and this flag is false,
    // maybeRearmVoice() restarts it on a self-rescheduling backoff loop.
    private var voicePaused = false
    private var rearmJob: Job? = null

    private fun onSegmentFinalized(segment: String) {
        confirmedText = if (confirmedText.isEmpty()) segment else "$confirmedText $segment"
        _uiState.update { it.copy(noteText = confirmedText) }
    }

    private fun onVoiceError(error: VoiceError) {
        // Cloud errors arrive on the OkHttp WebSocket thread; the fallback path creates and
        // starts a SpeechRecognizer, which must run on the main thread. viewModelScope is
        // Main.immediate, so everything below is marshaled there.
        //
        // M49: every VoiceError is TERMINAL. The cloud engine recovers from transient
        // transport problems internally (RECOVERING state, in-engine reconnect + replay)
        // and emits exactly one error at give-up — a second retry authority here meant
        // double sessions and double billing.
        viewModelScope.launch {
            if (error.engine == VoiceEngine.CLOUD) {
                // Keep the user dictating by falling back to on-device for this session.
                fallbackToOnDevice(error)
            } else {
                // On-device errors keep the transient-snackbar behavior; the watchdog
                // handles re-arming.
                _uiState.update { it.copy(submitError = error.message) }
            }
        }
    }

    /**
     * Cloud transcription failed — switch the live engine to on-device for this session
     * and raise a persistent banner. Does NOT call [AuthManager.setVoiceMode] or touch the
     * stored key, so the user's preference and BYOK key survive untouched.
     */
    private fun fallbackToOnDevice(error: VoiceError) {
        engineOverride = AuthManager.VOICE_MODE_ON_DEVICE
        switchEngine(AuthManager.VOICE_MODE_ON_DEVICE, currentKey)
        val reason = when (error.kind) {
            VoiceErrorKind.OUT_OF_CREDIT ->
                "High-accuracy voice stopped — your OpenAI account is out of credit. Switched to on-device."
            VoiceErrorKind.INVALID_KEY ->
                "High-accuracy voice stopped — your OpenAI API key was rejected. Switched to on-device."
            else ->
                "High-accuracy voice stopped — switched to on-device."
        }
        _uiState.update {
            it.copy(voiceEngineNotice = VoiceEngineNotice(reason, canRetryCloud = !currentKey.isNullOrBlank()))
        }
    }

    /** Whether the user currently wants the mic running — the basis for auto-start decisions. */
    private fun voiceIntended(): Boolean =
        _uiState.value.inputMode == InputMode.VOICE && _uiState.value.permissionGranted

    init {
        _uiState.update { it.copy(speechAvailable = voiceRecognizer.isAvailable) }
        observeSubmissions()
        observePendingCount()
        observeRecognizerState()
        observeVoiceConfig()
        checkOnboarding()
    }

    private fun observeVoiceConfig() {
        viewModelScope.launch {
            combine(authManager.voiceMode, authManager.openAiKey) { mode, key -> mode to key }
                .distinctUntilChanged()
                .collect { (mode, key) ->
                    // An explicit mode change in Settings is the user's choice — it wins
                    // over any session fallback override.
                    if (mode != currentMode) engineOverride = null
                    val changed = mode != currentMode || key != currentKey
                    currentMode = mode
                    currentKey = key
                    if (changed) {
                        switchEngine(engineOverride ?: mode, key)
                    }
                }
        }
    }

    /**
     * Swap the live voice engine. M49 invariant: the swap starts the new engine iff
     * voice is intended AND not deliberately paused, and NEVER mutates voicePaused or
     * inputMode. On the Settings screen (ON_PAUSE set voicePaused=true) a mode/key
     * change builds the engine cold and the mic comes up on return via ON_RESUME's
     * startVoiceInput — the old force-clear here was the Settings-screen hot-mic bug.
     * Cold start still works: voicePaused is false at launch, so the config-driven
     * swap (or the permission callback) starts it immediately (M47 fix preserved).
     */
    private fun switchEngine(mode: String, key: String?) {
        recognizerStateJob?.cancel()
        voiceRecognizer.destroy()
        voiceRecognizer = if (mode == AuthManager.VOICE_MODE_CLOUD && !key.isNullOrBlank()) {
            OpenAiRecognizer(key, context, ::onSegmentFinalized, ::onVoiceError)
        } else {
            SpeechRecognizerManager(context, ::onSegmentFinalized, ::onVoiceError)
        }
        _uiState.update { it.copy(speechAvailable = voiceRecognizer.isAvailable) }
        observeRecognizerState()
        if (voiceIntended() && !voicePaused && voiceRecognizer.isAvailable) {
            voiceRecognizer.start()
        }
    }

    /** User wants to retry cloud (e.g. after topping up). Uses the still-saved key. */
    fun retryCloud() {
        engineOverride = null
        _uiState.update { it.copy(voiceEngineNotice = null) }
        if (!currentKey.isNullOrBlank()) {
            switchEngine(AuthManager.VOICE_MODE_CLOUD, currentKey)
        }
    }

    /** Dismiss the banner but stay on on-device for the rest of this session. */
    fun dismissVoiceNotice() {
        _uiState.update { it.copy(voiceEngineNotice = null) }
    }

    private fun checkOnboarding() {
        viewModelScope.launch {
            val shown = authManager.onboardingShown.first()
            if (!shown) {
                _showOnboarding.value = true
            }
        }
    }

    fun dismissOnboarding() {
        _showOnboarding.value = false
        viewModelScope.launch {
            authManager.markOnboardingShown()
        }
    }

    private fun observeSubmissions() {
        viewModelScope.launch {
            repository.recentSubmissions.collect { entities ->
                val items = entities.map { entity ->
                    val time = Instant.ofEpochMilli(entity.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .format(timeFormatter)
                    SubmissionItem(
                        time = time,
                        preview = entity.preview,
                        success = entity.success
                    )
                }
                _uiState.update { it.copy(submissions = items) }
            }
        }
    }

    private fun observePendingCount() {
        viewModelScope.launch {
            repository.pendingCount.collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
    }

    private fun observeRecognizerState() {
        val recognizer = voiceRecognizer
        recognizerStateJob = viewModelScope.launch {
            launch {
                recognizer.listeningState.collect { state ->
                    _uiState.update { it.copy(listeningState = state) }
                    if (state == ListeningState.IDLE) maybeRearmVoice(recognizer)
                }
            }
            launch {
                recognizer.partialText.collect { partial ->
                    if (_uiState.value.inputMode == InputMode.VOICE) {
                        val display = if (confirmedText.isEmpty()) partial
                            else if (partial.isEmpty()) confirmedText
                            else "$confirmedText $partial"
                        _uiState.update { it.copy(noteText = display) }
                    }
                }
            }
        }
    }

    /**
     * Watchdog: the engine settled on IDLE while the user still wants the mic on and
     * nothing deliberate stopped it — re-arm on a SELF-RESCHEDULING backoff loop
     * (1s doubling to 8s). M49: the old single-shot version was edge-triggered on IDLE
     * emissions; StateFlow dedups equal values, so a start() that failed without
     * leaving IDLE never re-fired it and the watchdog starved after one attempt. The
     * loop persists until the engine leaves IDLE or the user no longer wants voice.
     */
    private fun maybeRearmVoice(engine: VoiceRecognizer) {
        if (rearmJob?.isActive == true) return
        rearmJob = viewModelScope.launch {
            var backoffMs = REARM_INITIAL_MS
            while (true) {
                delay(backoffMs)
                // Re-check everything at fire time: the world may have changed during
                // the delay (keyboard switch, engine swap, another path restarted it).
                if (voicePaused || !voiceIntended() || _uiState.value.isSubmitting) return@launch
                if (voiceRecognizer !== engine) return@launch
                if (voiceRecognizer.listeningState.value != ListeningState.IDLE) return@launch
                voiceRecognizer.start()
                backoffMs = (backoffMs * 2).coerceAtMost(REARM_MAX_MS)
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (granted && _uiState.value.speechAvailable) {
            startVoiceInput()
        } else {
            _uiState.update { it.copy(inputMode = InputMode.KEYBOARD) }
        }
    }

    fun startVoiceInput() {
        if (!_uiState.value.permissionGranted) return
        voicePaused = false
        // Sync confirmedText from whatever is currently in noteText
        confirmedText = _uiState.value.noteText.trim()
        _uiState.update { it.copy(inputMode = InputMode.VOICE) }
        // A new dictation session (app resume, mic button) retries the user's preferred
        // engine: a cloud failure parks us on-device only for the rest of THAT session,
        // not until process death. If cloud fails again, fallbackToOnDevice() re-raises
        // the banner.
        if (engineOverride != null) {
            engineOverride = null
            if (currentMode != AuthManager.VOICE_MODE_ON_DEVICE) {
                _uiState.update { it.copy(voiceEngineNotice = null) }
                switchEngine(currentMode, currentKey) // starts: voice intended + not paused here
                return
            }
        }
        if (!_uiState.value.speechAvailable) return
        voiceRecognizer.start()
    }

    fun switchToKeyboard() {
        voicePaused = true
        voiceRecognizer.stop()
        // Keep current noteText as-is for keyboard editing
        confirmedText = _uiState.value.noteText.trim()
        _uiState.update { it.copy(inputMode = InputMode.KEYBOARD) }
    }

    fun stopVoiceInput() {
        voicePaused = true // also guards the watchdog against re-arming a backgrounded app
        voiceRecognizer.stop()
    }

    fun clearSubmitSuccess() {
        _uiState.update { it.copy(submitSuccess = false) }
    }

    fun clearSubmitQueued() {
        _uiState.update { it.copy(submitQueued = false) }
    }

    fun updateNoteText(text: String) {
        _uiState.update { it.copy(noteText = text, submitError = null) }
        if (_uiState.value.inputMode == InputMode.KEYBOARD) {
            confirmedText = text.trim()
        }
    }

    fun submit(force: Boolean = false) {
        val text = _uiState.value.noteText.trim()
        if (text.isEmpty() || _uiState.value.isSubmitting) return

        // M49: during CONNECTING/RECOVERING the cloud engine may hold seconds of
        // spoken-but-untranscribed audio in its ring buffer; submitting now would
        // silently save a note missing its tail. Confirm instead of discard.
        val state = _uiState.value.listeningState
        if (!force && (state == ListeningState.RECOVERING || state == ListeningState.CONNECTING)) {
            _uiState.update { it.copy(showRecoverySubmitConfirm = true) }
            return
        }
        _uiState.update { it.copy(showRecoverySubmitConfirm = false) }

        val wasVoice = _uiState.value.inputMode == InputMode.VOICE
        voicePaused = true
        voiceRecognizer.stop()

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            val result = repository.submitNote(text)

            result.onSuccess { submitResult ->
                when (submitResult) {
                    SubmitResult.SENT -> {
                        confirmedText = ""
                        _uiState.update {
                            it.copy(noteText = "", isSubmitting = false, submitSuccess = true)
                        }
                    }
                    SubmitResult.QUEUED -> {
                        confirmedText = ""
                        _uiState.update {
                            it.copy(noteText = "", isSubmitting = false, submitQueued = true)
                        }
                    }
                    SubmitResult.AUTH_FAILED -> {
                        // Preserve note text so user doesn't lose their work
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                submitError = "Session expired. Please disconnect and sign back in from Settings."
                            )
                        }
                        return@onSuccess // Don't restart voice
                    }
                }
                // Restart voice if we were in voice mode
                if (wasVoice && _uiState.value.permissionGranted && _uiState.value.speechAvailable) {
                    voicePaused = false
                    _uiState.update { it.copy(inputMode = InputMode.VOICE) }
                    voiceRecognizer.start()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecognizer.destroy()
    }

    /** User declined to submit while reconnecting — keep dictating. */
    fun dismissRecoverySubmitConfirm() {
        _uiState.update { it.copy(showRecoverySubmitConfirm = false) }
    }

    private companion object {
        /** Watchdog re-arm backoff: starts here, doubles per attempt, capped below. */
        const val REARM_INITIAL_MS = 1000L
        const val REARM_MAX_MS = 8000L
    }
}
