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
    val voiceEngineNotice: VoiceEngineNotice? = null
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
    // user explicitly changes the mode in Settings, retries cloud, or keeps on-device.
    private var engineOverride: String? = null
    private var cloudTransientRetries = 0

    private fun onSegmentFinalized(segment: String) {
        confirmedText = if (confirmedText.isEmpty()) segment else "$confirmedText $segment"
        _uiState.update { it.copy(noteText = confirmedText) }
    }

    private fun onVoiceError(error: VoiceError) {
        // Cloud errors arrive on the OkHttp WebSocket thread; the fallback path creates and
        // starts a SpeechRecognizer, which must run on the main thread. viewModelScope is
        // Main.immediate, so everything below is marshaled there.
        viewModelScope.launch {
            _uiState.update { it.copy(listeningState = ListeningState.IDLE) }

            val isCloud = error.engine == VoiceEngine.CLOUD
            // A transient cloud blip (dropped connection): try to reconnect a couple of times
            // silently before bothering the user. The counter resets once we reach LISTENING.
            if (isCloud && error.kind == VoiceErrorKind.TRANSIENT && cloudTransientRetries < MAX_CLOUD_RETRIES) {
                cloudTransientRetries++
                val engine = voiceRecognizer
                delay(800)
                if (voiceRecognizer === engine && voiceIntended()) engine.start()
                return@launch
            }

            if (isCloud) {
                // Fatal cloud failure (out of credit, bad key, or exhausted retries) — keep the
                // user dictating by falling back to on-device for the rest of this session.
                fallbackToOnDevice(error)
            } else {
                // On-device errors keep the old transient-snackbar behavior.
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
        cloudTransientRetries = 0
        engineOverride = AuthManager.VOICE_MODE_ON_DEVICE
        switchEngine(AuthManager.VOICE_MODE_ON_DEVICE, currentKey, startIfIntended = voiceIntended())
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
                        switchEngine(engineOverride ?: mode, key, startIfIntended = voiceIntended())
                    }
                }
        }
    }

    /**
     * Swap the live voice engine. Whether to (re)start it is decided by the user's intent
     * ([voiceIntended]) — NOT the outgoing engine's momentary [ListeningState]. The old code
     * read `listeningState != IDLE`, but on cold start that's still IDLE during the async
     * on-device warm-up, so a config-driven swap could leave the new engine never started →
     * "the mic isn't active when I open the app."
     */
    private fun switchEngine(mode: String, key: String?, startIfIntended: Boolean) {
        recognizerStateJob?.cancel()
        voiceRecognizer.destroy()
        cloudTransientRetries = 0
        voiceRecognizer = if (mode == AuthManager.VOICE_MODE_CLOUD && !key.isNullOrBlank()) {
            OpenAiRecognizer(key, ::onSegmentFinalized, ::onVoiceError)
        } else {
            SpeechRecognizerManager(context, ::onSegmentFinalized, ::onVoiceError)
        }
        _uiState.update { it.copy(speechAvailable = voiceRecognizer.isAvailable) }
        observeRecognizerState()
        if (startIfIntended && _uiState.value.permissionGranted && voiceRecognizer.isAvailable) {
            _uiState.update { it.copy(inputMode = InputMode.VOICE) }
            voiceRecognizer.start()
        }
    }

    /** User accepted the on-device fallback — persist it as the preference and clear the banner. */
    fun keepOnDevice() {
        engineOverride = null
        _uiState.update { it.copy(voiceEngineNotice = null) }
        viewModelScope.launch { authManager.setVoiceMode(AuthManager.VOICE_MODE_ON_DEVICE) }
    }

    /** User wants to retry cloud (e.g. after topping up). Uses the still-saved key. */
    fun retryCloud() {
        engineOverride = null
        cloudTransientRetries = 0
        _uiState.update { it.copy(voiceEngineNotice = null) }
        if (!currentKey.isNullOrBlank()) {
            switchEngine(AuthManager.VOICE_MODE_CLOUD, currentKey, startIfIntended = voiceIntended())
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
                    // A healthy session clears the transient-retry budget.
                    if (state == ListeningState.LISTENING) cloudTransientRetries = 0
                    _uiState.update { it.copy(listeningState = state) }
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

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(permissionGranted = granted) }
        if (granted && _uiState.value.speechAvailable) {
            startVoiceInput()
        } else {
            _uiState.update { it.copy(inputMode = InputMode.KEYBOARD) }
        }
    }

    fun startVoiceInput() {
        if (!_uiState.value.permissionGranted || !_uiState.value.speechAvailable) return
        // Sync confirmedText from whatever is currently in noteText
        confirmedText = _uiState.value.noteText.trim()
        _uiState.update { it.copy(inputMode = InputMode.VOICE) }
        voiceRecognizer.start()
    }

    fun switchToKeyboard() {
        voiceRecognizer.stop()
        // Keep current noteText as-is for keyboard editing
        confirmedText = _uiState.value.noteText.trim()
        _uiState.update { it.copy(inputMode = InputMode.KEYBOARD) }
    }

    fun stopVoiceInput() {
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

    fun submit() {
        val text = _uiState.value.noteText.trim()
        if (text.isEmpty() || _uiState.value.isSubmitting) return

        val wasVoice = _uiState.value.inputMode == InputMode.VOICE
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

    private companion object {
        /** Silent cloud reconnect attempts before falling back to on-device. */
        const val MAX_CLOUD_RETRIES = 2
    }
}
