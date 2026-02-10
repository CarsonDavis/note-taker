package com.carsondavis.notetaker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsondavis.notetaker.data.repository.NoteRepository
import com.carsondavis.notetaker.ui.components.SubmissionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class NoteUiState(
    val noteText: String = "",
    val topic: String? = null,
    val isTopicLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submissions: List<SubmissionItem> = emptyList(),
    val submitError: String? = null
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    init {
        observeSubmissions()
        fetchTopic()
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

    private fun fetchTopic() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTopicLoading = true) }
            val topic = repository.fetchCurrentTopic()
            _uiState.update { it.copy(topic = topic, isTopicLoading = false) }
        }
    }

    fun clearSubmitSuccess() {
        _uiState.update { it.copy(submitSuccess = false) }
    }

    fun updateNoteText(text: String) {
        _uiState.update { it.copy(noteText = text, submitError = null) }
    }

    fun submit() {
        val text = _uiState.value.noteText.trim()
        if (text.isEmpty() || _uiState.value.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            val result = repository.submitNote(text)
            if (result.isSuccess) {
                fetchTopic()
            }
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(noteText = "", isSubmitting = false, submitSuccess = true)
                } else {
                    it.copy(
                        isSubmitting = false,
                        submitError = result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }
            }
        }
    }
}
