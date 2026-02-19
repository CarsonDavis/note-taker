package com.carsondavis.notetaker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsondavis.notetaker.data.api.GitHubDirectoryEntry
import com.carsondavis.notetaker.data.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val currentPath: String = "",
    val pathSegments: List<String> = emptyList(),
    val entries: List<GitHubDirectoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val viewingFile: String? = null,
    val fileContent: String? = null,
    val isFileLoading: Boolean = false
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        loadDirectory("")
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    viewingFile = null,
                    fileContent = null
                )
            }

            val result = repository.fetchDirectoryContents(path)
            result.onSuccess { entries ->
                _uiState.update {
                    it.copy(
                        currentPath = path,
                        pathSegments = if (path.isEmpty()) emptyList() else path.split("/"),
                        entries = entries,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                val message = if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                    "Session expired. Please disconnect and sign back in from Settings."
                } else {
                    e.message ?: "Failed to load directory"
                }
                _uiState.update {
                    it.copy(isLoading = false, error = message)
                }
            }
        }
    }

    fun openFile(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(viewingFile = path, isFileLoading = true, fileContent = null)
            }

            val result = repository.fetchFileContent(path)
            result.onSuccess { content ->
                _uiState.update {
                    it.copy(fileContent = content, isFileLoading = false)
                }
            }.onFailure { e ->
                val message = if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                    "Session expired. Please disconnect and sign back in from Settings."
                } else {
                    e.message ?: "Failed to load file"
                }
                _uiState.update {
                    it.copy(isFileLoading = false, error = message)
                }
            }
        }
    }

    /** Returns true if navigation was handled (went up one level), false if already at root with no file open */
    fun navigateUp(): Boolean {
        val state = _uiState.value

        // If viewing a file, go back to directory
        if (state.viewingFile != null) {
            _uiState.update { it.copy(viewingFile = null, fileContent = null) }
            return true
        }

        // If in a subdirectory, go up one level
        if (state.currentPath.isNotEmpty()) {
            val parent = state.currentPath.substringBeforeLast("/", "")
            loadDirectory(parent)
            return true
        }

        return false
    }
}
