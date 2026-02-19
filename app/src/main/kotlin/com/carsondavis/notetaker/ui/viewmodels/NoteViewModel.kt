package com.carsondavis.notetaker.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsondavis.notetaker.data.auth.AuthManager
import com.carsondavis.notetaker.data.repository.NoteRepository
import com.carsondavis.notetaker.data.repository.SubmitResult
import com.carsondavis.notetaker.speech.ListeningState
import com.carsondavis.notetaker.speech.SpeechRecognizerManager
import com.carsondavis.notetaker.ui.components.SubmissionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class InputMode { VOICE, KEYBOARD }

data class NoteUiState(
    val noteText: String = "",
    val topic: String? = null,
    val isTopicLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitQueued: Boolean = false,
    val pendingCount: Int = 0,
    val submissions: List<SubmissionItem> = emptyList(),
    val submitError: String? = null,
    val inputMode: InputMode = InputMode.VOICE,
    val listeningState: ListeningState = ListeningState.IDLE,
    val speechAvailable: Boolean = false,
    val permissionGranted: Boolean = false
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

    private val speechManager = SpeechRecognizerManager(
        context = context,
        onSegmentFinalized = { segment ->
            confirmedText = if (confirmedText.isEmpty()) segment else "$confirmedText $segment"
            _uiState.update { it.copy(noteText = confirmedText) }
        },
        onError = { message ->
            _uiState.update { it.copy(
                listeningState = ListeningState.IDLE,
                submitError = message
            ) }
        }
    )

    init {
        _uiState.update { it.copy(speechAvailable = speechManager.isAvailable) }
        observeSubmissions()
        observePendingCount()
        observeSpeechState()
        fetchTopic()
        checkOnboarding()
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

    private fun observeSpeechState() {
        viewModelScope.launch {
            speechManager.listeningState.collect { state ->
                _uiState.update { it.copy(listeningState = state) }
            }
        }
        viewModelScope.launch {
            speechManager.partialText.collect { partial ->
                if (_uiState.value.inputMode == InputMode.VOICE) {
                    val display = if (confirmedText.isEmpty()) partial
                        else if (partial.isEmpty()) confirmedText
                        else "$confirmedText $partial"
                    _uiState.update { it.copy(noteText = display) }
                }
            }
        }
    }

    private fun fetchTopic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTopicLoading = true) }
            val topic = repository.fetchCurrentTopic()
            _uiState.update { it.copy(topic = topic, isTopicLoading = false) }
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
        speechManager.start()
    }

    fun switchToKeyboard() {
        speechManager.stop()
        // Keep current noteText as-is for keyboard editing
        confirmedText = _uiState.value.noteText.trim()
        _uiState.update { it.copy(inputMode = InputMode.KEYBOARD) }
    }

    fun stopVoiceInput() {
        speechManager.stop()
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
        speechManager.stop()

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            val result = repository.submitNote(text)

            result.onSuccess { submitResult ->
                when (submitResult) {
                    SubmitResult.SENT -> {
                        confirmedText = ""
                        fetchTopic()
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
                    speechManager.start()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }
}
