package com.carsondavis.notetaker.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsondavis.notetaker.data.api.GitHubApi
import com.carsondavis.notetaker.data.auth.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val token: String = "",
    val repo: String = "",
    val isValidating: Boolean = false,
    val isSetupComplete: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val api: GitHubApi,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun updateToken(token: String) {
        _uiState.update { it.copy(token = token, error = null) }
    }

    fun updateRepo(repo: String) {
        _uiState.update { it.copy(repo = repo, error = null) }
    }

    fun submit() {
        val token = _uiState.value.token.trim()
        val repo = _uiState.value.repo.trim()

        if (token.isBlank()) {
            _uiState.update { it.copy(error = "Token is required") }
            return
        }

        val parts = repo.split("/")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            _uiState.update { it.copy(error = "Repo must be in owner/repo format") }
            return
        }

        _uiState.update { it.copy(isValidating = true, error = null) }

        viewModelScope.launch {
            try {
                val user = api.getUser("Bearer $token")
                authManager.saveAuth(token, user.login)
                authManager.saveRepo(parts[0], parts[1])
                _uiState.update { it.copy(isValidating = false, isSetupComplete = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        error = "Invalid token or network error: ${e.message}"
                    )
                }
            }
        }
    }
}
